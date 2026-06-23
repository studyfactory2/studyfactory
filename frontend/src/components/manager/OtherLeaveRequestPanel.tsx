import { useEffect, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { Branch, MemberResponse, SpecialLeaveResponse } from '../../types/domain';

type OtherLeaveRequestPanelProps = {
  branches: Branch[];
};

const SLOT_OPTIONS = [1, 2, 3, 4, 5, 6, 7];
const REASON_OPTIONS = ['지각', '병원', '카페', '쉼', '운동', '알바', '스터디', '집공', '예정', '아픔', '모의', '시험', '그만둠', '늦잠', '교회', '기타'];

type SpecialLeaveSlotEntry = {
  key: string;
  specialLeaveId: number;
  leaveDate: string;
  slot: number;
  reason: string;
  recurring: boolean;
};

export function OtherLeaveRequestPanel({ branches }: OtherLeaveRequestPanelProps) {
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [selectedMember, setSelectedMember] = useState<MemberResponse | null>(null);
  const [visibleMonth, setVisibleMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [selectedDates, setSelectedDates] = useState<string[]>([]);
  const [selectedSlots, setSelectedSlots] = useState<number[]>([]);
  const [selectedReason, setSelectedReason] = useState('');
  const [customReason, setCustomReason] = useState('');
  const [entries, setEntries] = useState<SpecialLeaveResponse[]>([]);
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [entriesLoading, setEntriesLoading] = useState(false);
  const [deletingSlotKey, setDeletingSlotKey] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadMembers();
    }, 180);

    return () => window.clearTimeout(timeoutId);
  }, [name]);

  const loadMembers = async () => {
    setLoading(true);
    setMessage('');
    try {
      const params = new URLSearchParams();
      if (name.trim()) {
        params.set('name', name.trim());
      }
      const query = params.toString();
      const responses = await apiRequest<MemberResponse[]>(`/api/members${query ? `?${query}` : ''}`);
      setMembers(responses);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '사원 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const openMemberDetail = (member: MemberResponse) => {
    setSelectedMember(member);
    setVisibleMonth(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
    setSelectedDates([]);
    setSelectedSlots([]);
    setSelectedReason('');
    setCustomReason('');
    setEntries([]);
    setMessage('');
    void loadSpecialLeaves(member.id);
  };

  const loadSpecialLeaves = async (memberId: number) => {
    setEntriesLoading(true);
    setMessage('');
    try {
      const responses = await apiRequest<SpecialLeaveResponse[]>(`/api/leaves/special?memberId=${memberId}`);
      setEntries(responses);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '기타 휴무 신청 내역을 불러오지 못했습니다.');
    } finally {
      setEntriesLoading(false);
    }
  };

  const closeMemberDetail = () => {
    setSelectedMember(null);
    setDeletingSlotKey('');
    setMessage('');
  };

  const moveMonth = (amount: number) => {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1));
    setSelectedDates([]);
  };

  const toggleDate = (date: string) => {
    setSelectedDates((current) => (
      current.includes(date) ? current.filter((item) => item !== date) : [...current, date].sort()
    ));
  };

  const toggleSlot = (slot: number) => {
    setSelectedSlots((current) => (
      current.includes(slot) ? current.filter((item) => item !== slot) : [...current, slot].sort((a, b) => a - b)
    ));
  };

  const toggleAllSlots = () => {
    setSelectedSlots((current) => (current.length === SLOT_OPTIONS.length ? [] : SLOT_OPTIONS));
  };

  const submitSpecialLeave = async (recurring: boolean) => {
    if (!selectedMember) {
      return;
    }
    const reason = selectedReason === '기타' ? customReason.trim() : selectedReason;
    if (selectedDates.length === 0 || selectedSlots.length === 0 || !reason) {
      setMessage('날짜, 교시, 사유를 선택해주세요.');
      return;
    }

    try {
      await apiRequest<SpecialLeaveResponse[]>('/api/leaves/special', {
        method: 'POST',
        body: JSON.stringify({
          memberId: selectedMember.id,
          leaveDates: selectedDates,
          slots: selectedSlots,
          reason: selectedReason,
          customReason: selectedReason === '기타' ? customReason.trim() : null,
          recurring,
        }),
      });
      setSelectedDates([]);
      setSelectedSlots([]);
      setSelectedReason('');
      setCustomReason('');
      await loadSpecialLeaves(selectedMember.id);
      setMessage('기타 휴무 신청이 완료되었습니다.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '기타 휴무 신청에 실패했습니다.');
    }
  };

  const deleteSpecialLeaveSlot = async (entry: SpecialLeaveSlotEntry) => {
    if (!selectedMember) {
      return;
    }

    setDeletingSlotKey(entry.key);
    setMessage('');
    try {
      await apiRequest<void>(`/api/leaves/special/${entry.specialLeaveId}/slots/${entry.slot}`, {
        method: 'DELETE',
      });
      await loadSpecialLeaves(selectedMember.id);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '기타 휴무 삭제에 실패했습니다.');
    } finally {
      setDeletingSlotKey('');
    }
  };

  if (selectedMember) {
    const calendarCells = getCalendarCells(visibleMonth);
    const slotEntries = expandSpecialLeaveEntries(entries);

    return (
      <div className="other-leave-panel">
        <header className="other-leave-detail-header">
          <button type="button" aria-label="뒤로가기" onClick={closeMemberDetail}>
            <BackIcon />
          </button>
          <h2>{selectedMember.name} 기타 휴무 신청</h2>
        </header>
        <section className="other-leave-detail">
          <div className="other-leave-calendar-box">
            <div className="other-leave-calendar-frame">
              <div className="other-leave-month">
                <button type="button" aria-label="이전 달" onClick={() => moveMonth(-1)}>
                  <BackIcon />
                </button>
                <strong>{formatKoreanMonth(visibleMonth)}</strong>
                <button type="button" aria-label="다음 달" onClick={() => moveMonth(1)}>
                  <ChevronIcon />
                </button>
              </div>
              <div className="other-leave-weekdays" aria-hidden="true">
                {WEEKDAYS.map((weekday) => (
                  <span className={weekday.className} key={weekday.label}>{weekday.label}</span>
                ))}
              </div>
              <div className="other-leave-calendar" aria-label={`${selectedMember.name} 기타 휴무 날짜 선택`}>
                {calendarCells.map((day, index) => (
                  day ? (
                    <button
                      className={selectedDates.includes(toDateKey(day)) ? 'selected' : ''}
                      type="button"
                      key={toDateKey(day)}
                      onClick={() => toggleDate(toDateKey(day))}
                    >
                      <span className={getDayClassName(day)}>{day.getDate()}</span>
                    </button>
                  ) : (
                    <span key={`blank-${index}`} />
                  )
                ))}
              </div>
              <p className="other-leave-guide">날짜를 선택하세요 (다중 선택 가능)</p>
            </div>
          </div>
          <div className="other-leave-slots" aria-label="교시 선택">
            <span>교시</span>
            <div>
              {SLOT_OPTIONS.map((slot) => (
                <button
                  className={selectedSlots.includes(slot) ? 'selected' : ''}
                  type="button"
                  key={slot}
                  onClick={() => toggleSlot(slot)}
                >
                  {slot}
                </button>
              ))}
            </div>
            <button type="button" onClick={toggleAllSlots}>전체 선택</button>
          </div>
          <div className="other-leave-reasons" aria-label="사유 선택">
            <span>사유</span>
            <div>
              {REASON_OPTIONS.map((reason) => (
                reason === '기타' ? (
                  <div className="other-leave-custom-reason" key={reason}>
                    <button
                      className={selectedReason === reason ? 'selected' : ''}
                      type="button"
                      onClick={() => setSelectedReason(reason)}
                    >
                      {reason}
                    </button>
                    <input
                      disabled={selectedReason !== '기타'}
                      maxLength={8}
                      placeholder="사유 입력"
                      value={customReason}
                      onChange={(event) => {
                        setSelectedReason('기타');
                        setCustomReason(event.target.value);
                      }}
                    />
                  </div>
                ) : (
                  <button
                    className={selectedReason === reason ? 'selected' : ''}
                    type="button"
                    key={reason}
                    onClick={() => setSelectedReason(reason)}
                  >
                    {reason}
                  </button>
                )
              ))}
            </div>
          </div>
          {message && <p className="other-leave-message">{message}</p>}
          <div className="other-leave-submit-actions">
            <button type="button" onClick={() => submitSpecialLeave(false)}>신청하기</button>
            <button type="button" onClick={() => submitSpecialLeave(true)}>매주 고정 신청</button>
          </div>
          <section className="other-leave-entries">
            <h3>신청 내역 (기타 휴무)</h3>
            {entriesLoading ? (
              <p>신청 내역을 불러오는 중입니다.</p>
            ) : slotEntries.length === 0 ? (
              <p>신청 내역이 없습니다.</p>
            ) : (
              <div>
                {slotEntries.map((entry) => (
                  <article className="other-leave-entry-card" key={entry.key}>
                    <span>
                      <strong>{entry.leaveDate}</strong>
                      <small>
                        {entry.reason} ({entry.slot}교시)
                        {entry.recurring ? ' · 매주 고정' : ''}
                      </small>
                    </span>
                    <button
                      type="button"
                      aria-label={`${entry.leaveDate} ${entry.reason} ${entry.slot}교시 삭제`}
                      disabled={deletingSlotKey === entry.key}
                      onClick={() => { void deleteSpecialLeaveSlot(entry); }}
                    >
                      <TrashIcon />
                    </button>
                  </article>
                ))}
              </div>
            )}
          </section>
        </section>
      </div>
    );
  }

  return (
    <div className="other-leave-panel">
      <header className="other-leave-header">
        <button type="button" aria-label="뒤로가기" onClick={() => { window.location.href = '/managerdashboard?view=grid'; }}>
          <BackIcon />
        </button>
        <h2>사원 선택</h2>
      </header>
      <label className="other-leave-search">
        <SearchIcon />
        <input
          aria-label="이름 검색"
          placeholder="이름 검색"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </label>
      {loading ? (
        <p className="other-leave-empty">불러오는 중입니다.</p>
      ) : message ? (
        <p className="other-leave-empty">{message}</p>
      ) : members.length === 0 ? (
        <p className="other-leave-empty">조회된 사원이 없습니다.</p>
      ) : (
        <div className="other-leave-list">
          {members.map((member) => (
            <button className="other-leave-card" type="button" key={member.id} onClick={() => openMemberDetail(member)}>
              <span>
                <strong>{member.name}</strong>
                <small>{findBranchName(branches, member.branchId)}</small>
              </span>
              <ChevronIcon />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

const WEEKDAYS = [
  { label: '일', className: 'sunday' },
  { label: '월', className: '' },
  { label: '화', className: '' },
  { label: '수', className: '' },
  { label: '목', className: '' },
  { label: '금', className: '' },
  { label: '토', className: 'saturday' },
];

function findBranchName(branches: Branch[], branchId: number) {
  return branches.find((branch) => branch.id === branchId)?.name || `지점 ${branchId}`;
}

function getMonthDays(month: Date) {
  const lastDate = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();

  return Array.from({ length: lastDate }, (_, index) => new Date(month.getFullYear(), month.getMonth(), index + 1));
}

function getCalendarCells(month: Date) {
  const days = getMonthDays(month);
  const firstDay = days[0]?.getDay() || 0;

  return [...Array.from<Date | null>({ length: firstDay }).fill(null), ...days];
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function formatKoreanMonth(date: Date) {
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월`;
}

function getDayClassName(date: Date) {
  if (date.getDay() === 0) {
    return 'sunday';
  }
  if (date.getDay() === 6) {
    return 'saturday';
  }
  return '';
}

function formatReason(entry: SpecialLeaveResponse) {
  if (entry.reason === '기타' && entry.customReason) {
    return entry.customReason;
  }

  return entry.reason;
}

function expandSpecialLeaveEntries(entries: SpecialLeaveResponse[]): SpecialLeaveSlotEntry[] {
  return entries
    .flatMap((entry) => parseSlots(entry.slots).map((slot) => ({
      key: `${entry.id}-${slot}`,
      specialLeaveId: entry.id,
      leaveDate: entry.leaveDate,
      slot,
      reason: formatReason(entry),
      recurring: entry.recurring,
    })))
    .sort((first, second) => {
      const dateOrder = second.leaveDate.localeCompare(first.leaveDate);
      if (dateOrder !== 0) {
        return dateOrder;
      }

      return second.slot - first.slot;
    });
}

function parseSlots(slots: string) {
  return slots
    .split(',')
    .map((slot) => slot.trim())
    .filter(Boolean)
    .map(Number)
    .filter((slot) => Number.isInteger(slot))
    .sort((first, second) => second - first);
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="10.5" cy="10.5" r="5.5" />
      <path d="m15 15 4 4" />
    </svg>
  );
}

function ChevronIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="m6 6 1 15h10l1-15" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
    </svg>
  );
}
