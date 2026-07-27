import { useCallback, useEffect, useMemo, useRef, useState, type TouchEvent } from 'react';
import { apiRequest } from '../../api/client';
import { Dropdown, type DropdownOption } from '../common/Dropdown';
import type {
  BeveragePreferenceResponse,
  BeverageItemResponse,
  Branch,
  DailyAttendanceBoardResponse,
  DailyLeaveStatusResponse,
  MemberBeverageResponse,
  RoomLayoutItemResponse,
  RoomLayoutResponse,
} from '../../types/domain';

type BeverageServingSheetPanelProps = {
  branches: Branch[];
  mode?: 'serving' | 'making';
};

const TIMETABLE = [
  { label: '출근', startTime: '08:00', endTime: '09:00', isBreak: false },
  { label: '1교시', startTime: '09:00', endTime: '10:30', isBreak: false },
  { label: '쉬는시간', startTime: '10:30', endTime: '10:45', isBreak: true },
  { label: '2교시', startTime: '10:45', endTime: '12:05', isBreak: false },
  { label: '점심', startTime: '12:05', endTime: '13:20', isBreak: true },
  { label: '3교시', startTime: '13:20', endTime: '14:30', isBreak: false },
  { label: '쉬는시간', startTime: '14:30', endTime: '14:45', isBreak: true },
  { label: '4교시', startTime: '14:45', endTime: '16:15', isBreak: false },
  { label: '쉬는시간', startTime: '16:15', endTime: '16:30', isBreak: true },
  { label: '5교시', startTime: '16:30', endTime: '17:50', isBreak: false },
  { label: '저녁', startTime: '17:50', endTime: '19:05', isBreak: true },
  { label: '6교시', startTime: '19:05', endTime: '20:25', isBreak: false },
  { label: '쉬는시간', startTime: '20:25', endTime: '20:40', isBreak: true },
  { label: '7교시', startTime: '20:40', endTime: '22:00', isBreak: false },
];

const CUSTOM_DRINK_VALUE = '__custom__';

const BASE_DRINK_OPTIONS: DropdownOption[] = [
  { value: '', label: '음료를 선택해주세요' },
  { value: '선식', label: '선식' },
  { value: '해독', label: '해독' },
  { value: '아아', label: '아아' },
  { value: '뜨아', label: '뜨아' },
  { value: '텀아아', label: '텀아아' },
  { value: '텀뜨아', label: '텀뜨아' },
];
const EDIT_DRINK_OPTIONS: DropdownOption[] = [
  ...BASE_DRINK_OPTIONS,
  { value: CUSTOM_DRINK_VALUE, label: '직접 입력' },
];

export function BeverageServingSheetPanel({ branches, mode = 'serving' }: BeverageServingSheetPanelProps) {
  const isMakingMode = mode === 'making';
  const branch = branches[0] || { id: 1, name: '망미점' };
  const [rooms, setRooms] = useState<RoomLayoutResponse[]>([]);
  const [beverages, setBeverages] = useState<MemberBeverageResponse[]>([]);
  const [dailyLeaveStatuses, setDailyLeaveStatuses] = useState<DailyLeaveStatusResponse[]>([]);
  const [leaveMemberIds, setLeaveMemberIds] = useState<Set<number>>(() => new Set());
  const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null);
  const [editingSeat, setEditingSeat] = useState<EditingSeat | null>(null);
  const [emptySeatNumber, setEmptySeatNumber] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const makingTouchStartRef = useRef<{ x: number; y: number } | null>(null);
  const selectedRoom = rooms.find((room) => room.id === selectedRoomId) || rooms[0] || null;
  const selectedRoomIndex = Math.max(0, rooms.findIndex((room) => room.id === selectedRoom?.id));
  const beveragesBySeat = useMemo(() => {
    const map = new Map<number, MemberBeverageResponse>();
    beverages.forEach((beverage) => {
      if (beverage.seatNumber != null && beverage.seatNumber > 0) {
        map.set(beverage.seatNumber, beverage);
      }
    });

    return map;
  }, [beverages]);
  const summary = useMemo(
    () => createBeverageSummary(beverages, dailyLeaveStatuses, new Date()),
    [beverages, dailyLeaveStatuses]
  );

  const loadSheet = useCallback(async () => {
    try {
      const params = new URLSearchParams({ branchId: String(branch.id) });
      const leaveParams = new URLSearchParams({ branchId: String(branch.id), date: toDateKey(new Date()) });
      if (isMakingMode) {
        const [beverageResponse, leaveResponse] = await Promise.all([
          apiRequest<MemberBeverageResponse[]>(`/api/beverages/members?${params.toString()}`),
          apiRequest<DailyLeaveStatusResponse[]>(`/api/leaves/daily-status?${leaveParams.toString()}`),
        ]);
        setRooms([]);
        setBeverages(beverageResponse);
        setDailyLeaveStatuses(leaveResponse);
        setLeaveMemberIds(new Set());
        setSelectedRoomId(null);
        setMessage(null);
        return;
      }

      const attendanceParams = new URLSearchParams({ branchId: String(branch.id), date: toDateKey(new Date()) });
      const [roomResponse, beverageResponse, attendanceResponse, leaveResponse] = await Promise.all([
        apiRequest<RoomLayoutResponse[]>(`/api/rooms?${params.toString()}`),
        apiRequest<MemberBeverageResponse[]>(`/api/beverages/members?${params.toString()}`),
        apiRequest<DailyAttendanceBoardResponse>(`/api/attendances/daily-board?${attendanceParams.toString()}`),
        apiRequest<DailyLeaveStatusResponse[]>(`/api/leaves/daily-status?${leaveParams.toString()}`),
      ]);
      setRooms(roomResponse);
      setBeverages(beverageResponse);
      setDailyLeaveStatuses(leaveResponse);
      setLeaveMemberIds(toCurrentLeaveMemberIds(attendanceResponse, new Date()));
      setSelectedRoomId((current) => current || roomResponse[0]?.id || null);
      setMessage(null);
    } catch (error) {
      setRooms([]);
      setBeverages([]);
      setDailyLeaveStatuses([]);
      setMessage(error instanceof Error ? error.message : `${isMakingMode ? '음료 제조 정보' : '음료 서빙표'}를 불러오지 못했습니다.`);
    }
  }, [branch.id, isMakingMode]);

  useEffect(() => {
    void loadSheet();
  }, [loadSheet]);

  useEffect(() => {
    // 모바일 브라우저가 이전 제조 화면을 BFCache에서 복원해도, 저장된 음료 정보를 다시 받는다.
    const refresh = () => void loadSheet();
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') {
        refresh();
      }
    };
    const channel = typeof BroadcastChannel === 'undefined'
      ? null
      : new BroadcastChannel('studyfactory-beverage-updates');

    window.addEventListener('pageshow', refresh);
    window.addEventListener('focus', refresh);
    window.addEventListener('beverage-preferences-updated', refresh);
    document.addEventListener('visibilitychange', refreshWhenVisible);
    channel?.addEventListener('message', refresh);

    return () => {
      window.removeEventListener('pageshow', refresh);
      window.removeEventListener('focus', refresh);
      window.removeEventListener('beverage-preferences-updated', refresh);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
      channel?.removeEventListener('message', refresh);
      channel?.close();
    };
  }, [loadSheet]);

  const openBeverageForm = (item: RoomLayoutItemResponse, beverage: MemberBeverageResponse | null) => {
    if (item.number == null) {
      return;
    }

    if (!beverage) {
      setEmptySeatNumber(item.number);
      return;
    }

    setEditingSeat({ item, beverage });
  };

  const updateBeverage = (memberId: number, response: BeveragePreferenceResponse) => {
    setBeverages((current) =>
      current.map((beverage) =>
        beverage.memberId === memberId
          ? { ...beverage, drinks: response.drinks, drinkNotes: response.drinkNotes, items: response.items, createdAt: response.createdAt, updatedAt: response.updatedAt }
          : beverage
      )
    );
    setEditingSeat((current) =>
      current && current.beverage.memberId === memberId
        ? { ...current, beverage: { ...current.beverage, drinks: response.drinks, drinkNotes: response.drinkNotes, items: response.items, createdAt: response.createdAt, updatedAt: response.updatedAt } }
        : current
    );
    window.dispatchEvent(new Event('beverage-preferences-updated'));
    if (typeof BroadcastChannel !== 'undefined') {
      const channel = new BroadcastChannel('studyfactory-beverage-updates');
      channel.postMessage({ memberId, updatedAt: response.updatedAt });
      channel.close();
    }
  };

  const goToServingSheet = () => {
    window.location.assign('/managerdashboard?view=beverage_serving_sheet');
  };

  const handleMakingTouchStart = (event: TouchEvent<HTMLElement>) => {
    event.stopPropagation();
    const touch = event.touches[0];
    makingTouchStartRef.current = { x: touch.clientX, y: touch.clientY };
  };

  const handleMakingTouchEnd = (event: TouchEvent<HTMLElement>) => {
    event.stopPropagation();
    const start = makingTouchStartRef.current;
    makingTouchStartRef.current = null;
    if (!start) {
      return;
    }

    const touch = event.changedTouches[0];
    const deltaX = touch.clientX - start.x;
    const deltaY = touch.clientY - start.y;
    if (deltaX < -54 && Math.abs(deltaY) < 72 && Math.abs(deltaX) > Math.abs(deltaY) * 1.25) {
      goToServingSheet();
    }
  };

  return (
    <div
      className={`beverage-serving-panel${isMakingMode ? ' beverage-making-panel' : ''}`}
      onTouchStart={isMakingMode ? handleMakingTouchStart : undefined}
      onTouchEnd={isMakingMode ? handleMakingTouchEnd : undefined}
    >
      <header className="beverage-serving-title">
        <a className="beverage-serving-back" href="/managerdashboard?view=staff-page" aria-label="스텝페이지로 돌아가기">
          <BackIcon />
        </a>
        <h2>{isMakingMode ? '음료 제조' : '음료 서빙'}</h2>
        <span className="beverage-serving-branch">{branch.name}</span>
        <a
          className="beverage-serving-shortcut"
          href={isMakingMode ? '/managerdashboard?view=beverage_serving_sheet' : '/managerdashboard?view=beverage_making_sheet'}
        >
          <span>{isMakingMode ? '음료 서빙' : '음료 제조'}</span>
          <ForwardIcon />
        </a>
      </header>

      {message ? (
        <p className="beverage-serving-empty">{message}</p>
      ) : isMakingMode ? (
        <BeverageSummaryBoard summary={summary} />
      ) : selectedRoom ? (
        <>
          <div className="beverage-serving-room-tabs">
            {rooms.map((room) => (
              <button
                className={selectedRoom.id === room.id ? 'active' : ''}
                key={room.id}
                type="button"
                onClick={() => setSelectedRoomId(room.id)}
              >
                {room.name}
              </button>
            ))}
          </div>
          <div className="beverage-serving-room-viewport">
            <div className="beverage-serving-room-track" style={{ transform: `translateX(-${selectedRoomIndex * 100}%)` }}>
              {rooms.map((room) => (
                <section className="beverage-serving-room" key={room.id}>
                  <div className="beverage-serving-room-heading">
                    <strong>{room.name}</strong>
                    <span>{room.rows} x {room.cols}</span>
                  </div>
                  <div
                    className="beverage-serving-grid"
                    style={{
                      gridTemplateColumns: `repeat(${room.cols}, minmax(0, 1fr))`,
                      gridTemplateRows: `repeat(${room.rows}, minmax(34px, auto))`,
                    }}
                  >
                    {room.items.map((item) => (
                      <RoomItemCell
                        beverage={item.number != null ? beveragesBySeat.get(item.number) || null : null}
                        item={item}
                        key={item.id}
                        leaveMemberIds={leaveMemberIds}
                        onOpen={openBeverageForm}
                      />
                    ))}
                  </div>
                </section>
              ))}
            </div>
          </div>
        </>
      ) : (
        <p className="beverage-serving-empty">등록된 작업실이 없습니다.</p>
      )}

      {editingSeat && (
        <BeverageSeatModal
          editingSeat={editingSeat}
          onClose={() => setEditingSeat(null)}
          onSaved={updateBeverage}
        />
      )}

      {emptySeatNumber && (
        <EmptySeatModal
          seatNumber={emptySeatNumber}
          onClose={() => setEmptySeatNumber(null)}
        />
      )}
    </div>
  );
}

type BeverageSummary = {
  changedMembers: SummaryMember[];
  afterEightLeaveMembers: SummaryMember[];
  tumblerCounts: SummaryCount[];
  cupCounts: SummaryCount[];
};

type SummaryMember = {
  id: string | number;
  label: string;
  status: string;
};

type SummaryCount = {
  name: string;
  count: number;
  deduction: number;
  memberNames: string[];
};

function BeverageSummaryBoard({ summary }: { summary: BeverageSummary }) {
  return (
    <section
      className={`beverage-summary-board${summary.changedMembers.length === 0 ? ' has-empty-changes' : ''}`}
      aria-label="음료 제조 요약"
    >
      <BeverageSummaryColumn
        title="음료 수정"
        emptyText="내역 없음"
        columns={4}
        layout="changes"
        items={summary.changedMembers.map((member) => ({
          key: member.id,
          left: member.label,
          right: member.status,
        }))}
      />
      <BeverageSummaryColumn
        title="8시 이후 신청휴무"
        emptyText="8시 이후 신청휴무 없음"
        columns={4}
        layout="late-leave"
        items={summary.afterEightLeaveMembers.map((member) => ({
          key: member.id,
          left: member.label,
          right: member.status,
        }))}
      />
      <BeverageSummaryColumn
        title="텀블러 수량"
        emptyText="텀블러 음료 없음"
        columns={3}
        layout="tumbler"
        items={summary.tumblerCounts.map((count) => ({
          key: count.name,
          left: count.name,
          right: formatCountLabel(count),
          deduction: count.deduction > 0 ? `(-${count.deduction})` : undefined,
          detail: count.memberNames.join('\n'),
          variant: 'tumbler',
        }))}
      />
      <BeverageSummaryColumn
        title="컵 수량"
        emptyText="컵 음료 없음"
        columns={4}
        layout="cup"
        items={summary.cupCounts.map((count) => ({
          key: count.name,
          left: count.name,
          right: formatCountLabel(count),
          deduction: count.deduction > 0 ? `(-${count.deduction})` : undefined,
          detail: count.memberNames.join('\n'),
          variant: 'cup',
        }))}
      />
    </section>
  );
}

type BeverageSummaryColumnProps = {
  title: string;
  emptyText: string;
  columns: 2 | 3 | 4;
  layout: 'changes' | 'late-leave' | 'tumbler' | 'cup';
  items: Array<{
    key: string | number;
    left: string;
    right?: string;
    deduction?: string;
    detail?: string;
    variant?: 'tumbler' | 'cup';
  }>;
};

function BeverageSummaryColumn({ title, emptyText, columns, layout, items }: BeverageSummaryColumnProps) {
  return (
    <article className={`beverage-summary-column summary-${layout} cols-${columns}${items.length === 0 ? ' is-empty' : ''}`}>
      <h3>{title}</h3>
      {items.length === 0 ? (
        <p>{emptyText}</p>
      ) : (
        <ul>
          {items.map((item) => (
            <li className={item.variant ? `is-${item.variant}` : undefined} key={item.key}>
              <div>
                <strong>{item.left}</strong>
                {item.detail && <small>{item.detail}</small>}
              </div>
              {(item.right || item.deduction) && (
                <span>
                  {item.right}
                  {item.deduction && <em>{item.deduction}</em>}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

type EditingSeat = {
  item: RoomLayoutItemResponse;
  beverage: MemberBeverageResponse;
};

type RoomItemCellProps = {
  item: RoomLayoutItemResponse;
  beverage: MemberBeverageResponse | null;
  leaveMemberIds: Set<number>;
  onOpen: (item: RoomLayoutItemResponse, beverage: MemberBeverageResponse | null) => void;
};

function RoomItemCell({ item, beverage, leaveMemberIds, onOpen }: RoomItemCellProps) {
  if (item.type === 'DOOR') {
    return (
      <div className="beverage-serving-cell door" style={{ gridColumn: item.x, gridRow: item.y }}>
        문
      </div>
    );
  }

  const drinks = beverage ? parseDrinks(beverage.drinks) : [];
  const drinkNotes = beverage?.drinkNotes || {};
  const memberName = beverage?.memberName?.trim();
  const title = memberName ? `${item.number}.${memberName}` : `${item.number}.`;
  const onLeave = Boolean(beverage && leaveMemberIds.has(beverage.memberId));
  const className = [
    'beverage-serving-cell',
    beverage ? 'assigned' : 'empty-seat',
    onLeave ? 'on-leave' : '',
  ].filter(Boolean).join(' ');

  return (
    <button
      className={className}
      type="button"
      style={{ gridColumn: item.x, gridRow: item.y }}
      onClick={() => onOpen(item, beverage)}
    >
      <strong>{title}</strong>
      {drinks.length > 0 ? (
        <small>
          {drinks.map((drink, index) => (
            <span className={isTumblerDrink(drink) ? 'tumbler-drink' : undefined} key={`${drink}-${index}`}>
              {index > 0 ? ', ' : ''}{drink}
            </span>
          ))}
        </small>
      ) : null}
      {(() => {
        const notes = drinks.map((drink) => drinkNotes[drink]?.trim()).filter((note): note is string => Boolean(note));
        return notes.length > 0 ? <em>{notes.join(', ')}</em> : null;
      })()}
    </button>
  );
}

function EmptySeatModal({ seatNumber, onClose }: { seatNumber: number; onClose: () => void }) {
  return (
    <div className="beverage-seat-modal-backdrop" role="presentation">
      <section className="beverage-seat-modal empty-seat-modal" role="alertdialog" aria-modal="true" aria-labelledby="beverage-empty-seat-title">
        <header>
          <div>
            <small>{seatNumber}번 자리</small>
            <h2 id="beverage-empty-seat-title">등록된 회원 없음</h2>
          </div>
          <button type="button" onClick={onClose}>닫기</button>
        </header>
        <p>이 자리에 등록된 회원이 없습니다.</p>
      </section>
    </div>
  );
}

type BeverageSeatModalProps = {
  editingSeat: EditingSeat;
  onClose: () => void;
  onSaved: (memberId: number, response: BeveragePreferenceResponse) => void;
};

type DrinkDraft = {
  id?: number;
  name: string;
  note: string;
};

function BeverageSeatModal({ editingSeat, onClose, onSaved }: BeverageSeatModalProps) {
  const { item, beverage } = editingSeat;
  const [drinkInput, setDrinkInput] = useState('');
  const [drinkItems, setDrinkItems] = useState<DrinkDraft[]>(() => toDrinkDrafts(beverage));
  const [isComposing, setIsComposing] = useState(false);
  const [editingDrinkIndex, setEditingDrinkIndex] = useState<number | null>(null);
  const [editingDrinkValue, setEditingDrinkValue] = useState('');
  const [editingCustomDrink, setEditingCustomDrink] = useState(false);
  const [drinkDropdownOpen, setDrinkDropdownOpen] = useState(false);
  const [editingNoteIndex, setEditingNoteIndex] = useState<number | null>(null);
  const [editingNoteValue, setEditingNoteValue] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setDrinkInput('');
    setDrinkItems(toDrinkDrafts(beverage));
    setEditingDrinkIndex(null);
    setEditingDrinkValue('');
    setEditingCustomDrink(false);
    setDrinkDropdownOpen(false);
    setEditingNoteIndex(null);
    setEditingNoteValue('');
  }, [beverage.memberId, beverage.drinks, beverage.drinkNotes, beverage.items]);

  const drinks = drinkItems.map((drink) => drink.name);

  const addDrink = () => {
    const nextDrinks = parseDrinks(drinkInput);
    if (nextDrinks.length === 0) {
      return;
    }

    setDrinkItems((current) => [...current, ...nextDrinks.map((name) => ({ name, note: '' }))]);
    setDrinkInput('');
  };

  const removeDrink = (index: number) => {
    setDrinkItems((current) => current.filter((_, currentIndex) => currentIndex !== index));
    if (editingDrinkIndex === index) {
      setEditingDrinkIndex(null);
      setEditingDrinkValue('');
      setEditingCustomDrink(false);
      setDrinkDropdownOpen(false);
    }
  };

  const startEditingDrink = (index: number) => {
    setEditingDrinkIndex(index);
    setEditingDrinkValue(drinkItems[index].name);
    setEditingCustomDrink(!BASE_DRINK_OPTIONS.some((option) => option.value && option.value === drinkItems[index].name));
    setDrinkDropdownOpen(false);
  };

  const saveEditedDrink = () => {
    const nextDrink = editingDrinkValue.trim();
    if (editingDrinkIndex == null || !nextDrink) {
      return;
    }

    setDrinkItems((current) => current.map((drink, index) => index === editingDrinkIndex ? { ...drink, name: nextDrink } : drink));
    setEditingDrinkIndex(null);
    setEditingDrinkValue('');
    setEditingCustomDrink(false);
    setDrinkDropdownOpen(false);
  };

  const cancelEditingDrink = () => {
    setEditingDrinkIndex(null);
    setEditingDrinkValue('');
    setEditingCustomDrink(false);
    setDrinkDropdownOpen(false);
  };

  const startEditingNote = (index: number) => {
    setEditingNoteIndex(index);
    setEditingNoteValue(drinkItems[index].note || '');
  };

  const saveEditedNote = () => {
    if (editingNoteIndex == null) {
      return;
    }

    const nextNote = editingNoteValue.trim();
    setDrinkItems((current) => current.map((drink, index) => index === editingNoteIndex ? { ...drink, note: nextNote } : drink));
    setEditingNoteIndex(null);
    setEditingNoteValue('');
  };

  const deleteNote = (index: number) => {
    setDrinkItems((current) => current.map((drink, currentIndex) => currentIndex === index ? { ...drink, note: '' } : drink));
    if (editingNoteIndex === index) {
      setEditingNoteIndex(null);
      setEditingNoteValue('');
    }
  };

  const save = async () => {
    try {
      setSaving(true);
      const response = await apiRequest<BeveragePreferenceResponse>(`/api/beverages/members/${beverage.memberId}`, {
        method: 'PATCH',
        body: JSON.stringify({
          items: drinkItems.map(({ name, note }) => ({ name, note })),
        }),
      });
      onSaved(beverage.memberId, response);
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="beverage-seat-modal-backdrop" role="presentation">
      <section className="beverage-seat-modal" role="dialog" aria-modal="true" aria-labelledby="beverage-seat-modal-title">
        <header>
          <div>
            <small>{item.number}번 자리</small>
            <h2 id="beverage-seat-modal-title">{beverage.memberName}</h2>
          </div>
          <button type="button" onClick={onClose}>닫기</button>
        </header>

        <label className="beverage-seat-modal-label" htmlFor="beverage-seat-drink">음료 입력</label>
        <div className="beverage-seat-modal-input-row">
          <input
            id="beverage-seat-drink"
            value={drinkInput}
            placeholder="음료명을 입력하세요"
            disabled={saving}
            onChange={(event) => setDrinkInput(event.target.value)}
            onCompositionStart={() => setIsComposing(true)}
            onCompositionEnd={() => setIsComposing(false)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !isComposing) {
                event.preventDefault();
                addDrink();
              }
            }}
          />
          <button type="button" disabled={saving} onClick={addDrink}>추가</button>
        </div>

        <ol className="beverage-seat-modal-drinks">
          {drinks.length === 0 ? (
            <li className="empty">입력된 음료가 없습니다.</li>
          ) : (
            drinks.map((drink, index) => (
              <li key={`${drink}-${index}`}>
                {editingDrinkIndex === index ? (
                  <div className="beverage-seat-modal-drink-edit">
                    {editingCustomDrink ? (
                      <input
                        aria-label={`${index + 1}번째 음료 직접 입력`}
                        autoFocus
                        disabled={saving}
                        placeholder="음료를 직접 입력하세요"
                        value={editingDrinkValue}
                        onChange={(event) => setEditingDrinkValue(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault();
                            saveEditedDrink();
                          }
                        }}
                      />
                    ) : (
                      <Dropdown
                        classNamePrefix="form-dropdown"
                        disabled={saving}
                        label={`${index + 1}번째 음료 메뉴 선택`}
                        open={drinkDropdownOpen}
                        options={EDIT_DRINK_OPTIONS}
                        placeholderClass={!editingDrinkValue}
                        selectedOption={EDIT_DRINK_OPTIONS.find((option) => option.value === editingDrinkValue) || BASE_DRINK_OPTIONS[0]}
                        onToggle={() => setDrinkDropdownOpen((open) => !open)}
                        onSelect={(value) => {
                          setDrinkDropdownOpen(false);
                          if (value === CUSTOM_DRINK_VALUE) {
                            setEditingDrinkValue('');
                            setEditingCustomDrink(true);
                            return;
                          }
                          setEditingDrinkValue(value);
                        }}
                      />
                    )}
                    <div className="beverage-seat-modal-edit-actions">
                      <button className="save-edit" type="button" disabled={saving || !editingDrinkValue.trim()} onClick={saveEditedDrink}>완료</button>
                      <button className="cancel-edit" type="button" disabled={saving} onClick={cancelEditingDrink}>취소</button>
                    </div>
                  </div>
                ) : (
                  <>
                    <span>{index + 1}. {drink}</span>
                    <div className="beverage-seat-modal-item-actions">
                      <button className="edit-drink" type="button" disabled={saving} onClick={() => startEditingDrink(index)}>수정</button>
                      <button type="button" disabled={saving} onClick={() => removeDrink(index)}>삭제</button>
                    </div>
                  </>
                )}
              </li>
            ))
          )}
        </ol>

        {drinks.length > 0 && (
          <div className="beverage-seat-modal-notes">
            <span className="beverage-seat-modal-label">음료별 참고사항</span>
            {drinks.map((drink, index) => (
              <div className="beverage-seat-modal-note" key={`${drink}-${index}`}>
                <strong>{drink}</strong>
                {editingNoteIndex === index ? (
                  <div className="beverage-seat-modal-note-edit">
                    <input
                      aria-label={`${drink} 참고사항`}
                      autoFocus
                      value={editingNoteValue}
                      placeholder="참고사항을 입력하세요"
                      disabled={saving}
                      onChange={(event) => setEditingNoteValue(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          saveEditedNote();
                        }
                      }}
                    />
                    <button type="button" className="save-note" disabled={saving} onClick={saveEditedNote}>완료</button>
                    <button type="button" className="cancel-note" disabled={saving} onClick={() => {
                      setEditingNoteIndex(null);
                      setEditingNoteValue('');
                    }}>취소</button>
                  </div>
                ) : (
                  <>
                    <span className={drinkItems[index].note.trim() ? 'has-note' : 'empty-note'}>{drinkItems[index].note.trim() || '참고사항 없음'}</span>
                    <div className="beverage-seat-modal-note-actions">
                      <button type="button" disabled={saving} onClick={() => startEditingNote(index)}>수정</button>
                      <button type="button" className="delete-note" disabled={saving || !drinkItems[index].note.trim()} onClick={() => deleteNote(index)}>삭제</button>
                    </div>
                  </>
                )}
              </div>
            ))}
          </div>
        )}

        <div className="beverage-seat-modal-actions">
          <button className="beverage-seat-modal-cancel" type="button" disabled={saving} onClick={onClose}>취소</button>
          <button className="beverage-seat-modal-save" type="button" disabled={saving} onClick={save}>
            {saving ? '저장중' : '저장'}
          </button>
        </div>
      </section>
    </div>
  );
}

function parseDrinks(drinks: string) {
  return drinks
    .split(/\n|,/)
    .map((drink) => drink.trim())
    .filter(Boolean)
    .filter((drink) => !isExcludedDrinkName(drink));
}

function toDrinkDrafts(beverage: MemberBeverageResponse): DrinkDraft[] {
  if (beverage.items?.length) {
    return beverage.items.map((item) => ({ id: item.id, name: item.name, note: item.note || '' }));
  }

  return parseDrinks(beverage.drinks).map((name) => ({ name, note: beverage.drinkNotes?.[name] || '' }));
}

function isTumblerDrink(drink: string) {
  return drink.includes('텀') || drink.includes('텀블러');
}

function formatTumblerRequester(beverage: MemberBeverageResponse, note: string) {
  if (!note) {
    return beverage.memberName;
  }

  return `${beverage.memberName} [${note.replace(/\s+/g, ' ')}]`;
}

function formatCupRequester(beverage: MemberBeverageResponse, note: string) {

  if (!note) {
    return null;
  }

  return `${beverage.memberName} [${note.replace(/\s+/g, ' ')}]`;
}

function createBeverageSummary(
  beverages: MemberBeverageResponse[],
  dailyLeaveStatuses: DailyLeaveStatusResponse[],
  now: Date
): BeverageSummary {
  const countableBeverages = beverages.filter((beverage) => isWorkingMember(beverage, now));
  const countableMemberIds = new Set(countableBeverages.map((beverage) => beverage.memberId));
  const changedMembers = countableBeverages
    .filter(hasAssignedSeat)
    .map((beverage) => toTodayBeverageChange(beverage, now))
    .filter((change): change is SummaryMember => change !== null)
    .sort(compareSummaryMembers);
  const afterEightTodayLeaves = dailyLeaveStatuses.filter(
    (status) => countableMemberIds.has(status.memberId) && isTodayLeaveRequestedAfterEight(status, now)
  );
  const afterEightLeaveMembers = Array.from(
    new Map(
      afterEightTodayLeaves.map((status) => [
        status.memberId,
        {
          id: `${status.memberId}-${status.createdAt}-${status.leaveType}`,
          label: formatLeaveMemberLabel(status),
          status: toLeaveTypeLabel(status),
        },
      ])
    ).values()
  ).sort(compareSummaryMembers);
  // 오전에 제공하는 음료이므로, 당일 월차·오전반차는 신청 시각과 관계없이
  // 제조 수량에서는 제외한다. 오후반차는 오전 음료를 받으므로 제외하지 않는다.
  const beverageLeaveMemberIds = new Set(
    dailyLeaveStatuses
      .filter((status) => countableMemberIds.has(status.memberId) && isTodayBeverageLeave(status, now))
      .map((status) => status.memberId)
  );
  const tumblerCounts = new Map<string, number>();
  const cupCounts = new Map<string, number>();
  const tumblerDeductions = new Map<string, number>();
  const cupDeductions = new Map<string, number>();
  const tumblerMemberNames = new Map<string, string[]>();
  const cupMemberNames = new Map<string, string[]>();

  countableBeverages.forEach((beverage) => {
    beverageDrinkItems(beverage).forEach(({ name: drink, note }) => {
      const normalizedDrink = normalizeDrinkName(drink);
      const targetCounts = isTumblerDrink(drink) ? tumblerCounts : cupCounts;
      const targetDeductions = isTumblerDrink(drink) ? tumblerDeductions : cupDeductions;
      targetCounts.set(normalizedDrink, (targetCounts.get(normalizedDrink) || 0) + 1);
      if (beverageLeaveMemberIds.has(beverage.memberId)) {
        targetDeductions.set(normalizedDrink, (targetDeductions.get(normalizedDrink) || 0) + 1);
      } else if (isTumblerDrink(drink)) {
        const names = tumblerMemberNames.get(normalizedDrink) || [];
        names.push(formatTumblerRequester(beverage, note));
        tumblerMemberNames.set(normalizedDrink, names);
      } else {
        const requester = formatCupRequester(beverage, note);
        if (requester) {
          const names = cupMemberNames.get(normalizedDrink) || [];
          names.push(requester);
          cupMemberNames.set(normalizedDrink, names);
        }
      }
    });
  });

  return {
    changedMembers,
    afterEightLeaveMembers,
    tumblerCounts: toSortedCounts(tumblerCounts, tumblerDeductions, tumblerMemberNames),
    cupCounts: toSortedCounts(cupCounts, cupDeductions, cupMemberNames, 'cup'),
  };
}

function beverageDrinkItems(beverage: MemberBeverageResponse) {
  if (beverage.items?.length) {
    return beverage.items.map((item) => ({ name: item.name, note: item.note?.trim() || '' }));
  }

  return parseDrinks(beverage.drinks).map((name) => ({ name, note: beverage.drinkNotes?.[name]?.trim() || '' }));
}

function toTodayBeverageChange(beverage: MemberBeverageResponse, now: Date): SummaryMember | null {
  const createdAt = toDate(beverage.createdAt);
  const updatedAt = toDate(beverage.updatedAt);
  const createdToday = Boolean(createdAt && isSameDate(createdAt, now));
  const updatedToday = Boolean(updatedAt && isSameDate(updatedAt, now));

  if (!createdToday && !updatedToday) {
    return null;
  }

  return {
    id: beverage.memberId,
    label: formatMemberLabel(beverage),
    status: isChangedBeverage(createdAt, updatedAt) ? '변경' : '신청',
  };
}

function hasAssignedSeat(beverage: MemberBeverageResponse) {
  return beverage.seatNumber != null && beverage.seatNumber > 0;
}

function isWorkingMember(beverage: MemberBeverageResponse, now: Date) {
  if (!beverage.joinDate) {
    return true;
  }

  return beverage.joinDate <= toDateKey(now);
}

function isChangedBeverage(createdAt: Date | null, updatedAt: Date | null) {
  if (!createdAt || !updatedAt) {
    return false;
  }

  return Math.abs(updatedAt.getTime() - createdAt.getTime()) > 1000;
}

function formatMemberLabel(beverage: MemberBeverageResponse) {
  if (beverage.seatNumber != null && beverage.seatNumber > 0) {
    return `${beverage.seatNumber}번 ${beverage.memberName}`;
  }

  return beverage.memberName;
}

function formatLeaveMemberLabel(status: DailyLeaveStatusResponse) {
  if (status.seatNumber != null && status.seatNumber > 0) {
    return `${status.seatNumber}번 ${status.name}`;
  }

  return status.name;
}

function toLeaveTypeLabel(status: DailyLeaveStatusResponse) {
  if (status.label) {
    return status.label;
  }
  const { leaveType } = status;
  if (leaveType === 'FULL') {
    return '월차';
  }
  if (leaveType === 'MORNING') {
    return '오전 반차';
  }

  return '오후 반차';
}

function normalizeDrinkName(drink: string) {
  return drink.replace(/\s+/g, '');
}

function isExcludedDrinkName(drink: string) {
  const normalizedDrink = normalizeDrinkName(drink).toLowerCase();

  return normalizedDrink === '없음' || normalizedDrink === 'x' || normalizedDrink === '안먹음';
}

function toSortedCounts(
  counts: Map<string, number>,
  deductions: Map<string, number>,
  memberNames = new Map<string, string[]>(),
  order: 'default' | 'cup' = 'default'
) {
  return Array.from(counts.entries())
    .map(([name, count]) => ({
      name,
      count,
      deduction: deductions.get(name) || 0,
      memberNames: memberNames.get(name) || [],
    }))
    .sort((first, second) => {
      if (order === 'cup') {
        const cupOrder = compareCupDrinkNames(first.name, second.name);
        if (cupOrder !== 0) {
          return cupOrder;
        }
      }

      if (second.count !== first.count) {
        return second.count - first.count;
      }

      return first.name.localeCompare(second.name, 'ko');
    });
}

const CUP_DRINK_PRIORITY = ['아아', '선식', '해독'];

function compareCupDrinkNames(first: string, second: string) {
  const firstFamily = getCupDrinkFamily(first);
  const secondFamily = getCupDrinkFamily(second);

  if (firstFamily !== secondFamily) {
    return firstFamily - secondFamily;
  }

  if (firstFamily === CUP_DRINK_PRIORITY.length) {
    return 0;
  }

  const familyName = CUP_DRINK_PRIORITY[firstFamily];
  const firstIsBase = first === familyName;
  const secondIsBase = second === familyName;

  if (firstIsBase !== secondIsBase) {
    return firstIsBase ? -1 : 1;
  }

  return first.localeCompare(second, 'ko');
}

function getCupDrinkFamily(drink: string) {
  const familyIndex = CUP_DRINK_PRIORITY.findIndex((family) => drink.includes(family));

  return familyIndex < 0 ? CUP_DRINK_PRIORITY.length : familyIndex;
}

function formatCountLabel(count: SummaryCount) {
  return String(count.count);
}

function compareSummaryMembers(first: SummaryMember, second: SummaryMember) {
  return first.label.localeCompare(second.label, 'ko', { numeric: true });
}

function toDate(value?: string | null) {
  if (!value) {
    return null;
  }

  return new Date(value);
}

function isSameDate(first: Date, second: Date) {
  return first.getFullYear() === second.getFullYear()
    && first.getMonth() === second.getMonth()
    && first.getDate() === second.getDate();
}

function isTodayLeaveRequestedAfterEight(status: DailyLeaveStatusResponse, now: Date) {
  const leaveDate = new Date(`${status.leaveDate}T00:00:00`);
  if (status.source === 'SPECIAL_LEAVE' && status.requestedAfterEight != null) {
    return status.leaveType !== 'AFTERNOON'
      && isSameDate(leaveDate, now)
      && status.requestedAfterEight;
  }
  const requestedAt = new Date(status.createdAt);
  const eight = new Date(now);
  eight.setHours(8, 0, 0, 0);
  const nine = new Date(now);
  nine.setHours(9, 0, 0, 0);

  return status.leaveType !== 'AFTERNOON'
    && isSameDate(leaveDate, now)
    && isSameDate(requestedAt, now)
    && requestedAt >= eight
    && requestedAt < nine;
}

function isTodayBeverageLeave(status: DailyLeaveStatusResponse, now: Date) {
  const leaveDate = new Date(`${status.leaveDate}T00:00:00`);

  return status.leaveType !== 'AFTERNOON' && isSameDate(leaveDate, now);
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function toCurrentLeaveMemberIds(board: DailyAttendanceBoardResponse, now: Date) {
  const currentSlot = toCurrentAttendanceSlot(now);
  const ids = new Set<number>();
  if (currentSlot == null) {
    return ids;
  }

  board.rows.forEach((row) => {
    if (row.memberId == null) {
      return;
    }

    const currentStatus = row.slots[currentSlot - 1];
    if (currentStatus && currentStatus !== 'X' && currentStatus !== 'O') {
      ids.add(row.memberId);
    }
  });

  return ids;
}

function toCurrentAttendanceSlot(now: Date) {
  const currentMinutes = now.getHours() * 60 + now.getMinutes();
  const currentIndex = TIMETABLE.findIndex((entry) => {
    const startMinutes = toMinutes(entry.startTime);
    const endMinutes = toMinutes(entry.endTime);

    return currentMinutes >= startMinutes && currentMinutes < endMinutes;
  });

  if (currentIndex < 0) {
    if (currentMinutes < toMinutes(TIMETABLE[0].startTime)) {
      return 1;
    }

    return null;
  }

  const currentEntry = TIMETABLE[currentIndex];
  if (!currentEntry.isBreak) {
    return toClassSlot(currentEntry.label) || 1;
  }

  for (let index = currentIndex + 1; index < TIMETABLE.length; index += 1) {
    const nextSlot = toClassSlot(TIMETABLE[index].label);
    if (nextSlot != null) {
      return nextSlot;
    }
  }

  return null;
}

function toClassSlot(label: string) {
  const match = label.match(/^(\d)교시$/);
  if (!match) {
    return null;
  }

  return Number(match[1]);
}

function toMinutes(time: string) {
  const [hour, minute] = time.split(':').map(Number);

  return hour * 60 + minute;
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

function ForwardIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}
