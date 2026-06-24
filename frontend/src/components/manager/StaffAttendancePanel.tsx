import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { apiRequest } from '../../api/client';
import type { DailyAttendanceBoardResponse } from '../../types/domain';

const SLOT_LABELS = [1, 2, 3, 4, 5, 6, 7];
const OTHER_REASON_OPTIONS = ['지각', '조회', '외출', '이동', '시험', '컨디션'];
type SelectedSlot = {
  memberId: number;
  name: string;
  slot: number;
};
type SlotStatusUpdateType = 'PRESENT' | 'ABSENT' | 'OTHER';

export function StaffAttendancePanel() {
  const [board, setBoard] = useState<DailyAttendanceBoardResponse | null>(null);
  const [selectedDate, setSelectedDate] = useState(() => toDateKey(new Date()));
  const [name, setName] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [selectedSlot, setSelectedSlot] = useState<SelectedSlot | null>(null);
  const [otherModalOpen, setOtherModalOpen] = useState(false);
  const [otherDate, setOtherDate] = useState(() => toDateKey(new Date()));
  const [otherSlot, setOtherSlot] = useState<number | null>(null);
  const [selectedOtherReason, setSelectedOtherReason] = useState('');
  const [otherReason, setOtherReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');
  const branchId = localStorage.getItem('branchId');
  const filteredRows = useMemo(() => {
    const rows = board?.rows || [];
    const keyword = name.trim();
    if (!keyword) {
      return rows;
    }

    return rows.filter((row) => row.name.includes(keyword) || String(row.seatNumber || '').includes(keyword));
  }, [board, name]);
  const todoCount = useMemo(() => filteredRows.filter((row) => row.slots.some((slot) => slot !== 'X')).length, [filteredRows]);
  const noteCount = useMemo(() => filteredRows.filter((row) => row.slots.some((slot) => slot !== 'X' && slot !== '월차')).length, [filteredRows]);
  const otherCalendar = useMemo(() => createMonthCalendar(otherDate), [otherDate]);

  useEffect(() => {
    void loadBoard(selectedDate);
  }, [selectedDate]);

  const loadBoard = async (date: string) => {
    setLoading(true);
    setMessage('');
    try {
      const params = new URLSearchParams({ date });
      if (branchId) {
        params.set('branchId', branchId);
      }
      const response = await apiRequest<DailyAttendanceBoardResponse>(`/api/attendances/daily-board?${params.toString()}`);
      setBoard(response);
      setSelectedSlot(null);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '출석부를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const moveDate = (amount: number) => {
    const date = new Date(`${selectedDate}T00:00:00`);
    date.setDate(date.getDate() + amount);
    setSelectedDate(toDateKey(date));
  };

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) {
      setSearchOpen(false);
    }
  };

  const updateSlotStatus = async (status: SlotStatusUpdateType, reason?: string, date = selectedDate, slot: number | null | undefined = selectedSlot?.slot) => {
    if (!selectedSlot) {
      setMessage('변경할 교시를 먼저 선택해주세요.');
      return;
    }
    if (!slot) {
      setMessage('변경할 교시를 선택해주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      await apiRequest<void>('/api/attendances/daily-board/slot', {
        method: 'PATCH',
        body: JSON.stringify({
          memberId: selectedSlot.memberId,
          date,
          slot,
          status,
          reason,
        }),
      });
      await loadBoard(selectedDate);
      setOtherModalOpen(false);
      setOtherReason('');
      setSelectedOtherReason('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '출석 상태 변경에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const submitOtherReason = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await updateSlotStatus('OTHER', otherReason.trim() || selectedOtherReason || '기타', otherDate, otherSlot);
  };

  const createFixedLeave = async () => {
    if (!selectedSlot) {
      setMessage('고정신청할 사원을 먼저 선택해주세요.');
      return;
    }
    if (!otherSlot) {
      setMessage('고정신청할 교시를 선택해주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      await apiRequest<void>('/api/leaves/fixed', {
        method: 'POST',
        body: JSON.stringify({
          memberId: selectedSlot.memberId,
          leaveDate: otherDate,
          slots: [otherSlot],
          reason: otherReason.trim() || selectedOtherReason || '기타',
        }),
      });
      await loadBoard(selectedDate);
      setOtherModalOpen(false);
      setOtherReason('');
      setSelectedOtherReason('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '고정 휴무 신청에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const openOtherModal = () => {
    if (!selectedSlot) {
      return;
    }
    setOtherDate(selectedDate);
    setOtherSlot(selectedSlot.slot);
    setSelectedOtherReason('');
    setOtherReason('');
    setOtherModalOpen(true);
  };

  const moveOtherMonth = (amount: number) => {
    const date = new Date(`${otherDate}T00:00:00`);
    date.setMonth(date.getMonth() + amount);
    setOtherDate(toDateKey(date));
  };

  return (
    <div className="staff-attendance-panel">
      <div className="staff-attendance-tools">
        <div className="staff-search-slot">
          {searchOpen ? (
            <form className="staff-attendance-search" onSubmit={submitSearch}>
              <SearchIcon />
              <input
                aria-label="이름검색"
                autoFocus
                placeholder="이름 검색"
                value={name}
                onBlur={() => {
                  if (!name.trim()) {
                    setSearchOpen(false);
                  }
                }}
                onChange={(event) => setName(event.target.value)}
              />
              <button type="submit" aria-label="검색" />
            </form>
          ) : (
            <button className="staff-search-button" type="button" onClick={() => setSearchOpen(true)}>
              <SearchIcon />
              <span>이름검색</span>
            </button>
          )}
        </div>
        <div className="staff-date-control">
          <button type="button" aria-label="이전 날짜" onClick={() => moveDate(-1)}>
            ‹
          </button>
          <strong>{formatKoreanDate(selectedDate)}</strong>
          <button type="button" aria-label="다음 날짜" onClick={() => moveDate(1)}>
            ›
          </button>
        </div>
      </div>

      <div className="staff-attendance-actions">
        <button className="suggestion-tag" type="button" disabled>회원건의</button>
        <button className="meal-tag" type="button" disabled>반찬신청</button>
        <button className="todo-tag" type="button" disabled={todoCount === 0}>할일목록 <b>{todoCount}</b></button>
        <button className="note-tag" type="button" disabled={noteCount === 0}>출석참고 <b>{noteCount}</b></button>
      </div>

      {loading ? (
        <p className="staff-attendance-message">불러오는 중입니다.</p>
      ) : message ? (
        <p className="staff-attendance-message">{message}</p>
      ) : (
        <div className="staff-attendance-table-wrap">
          <table className="staff-attendance-table">
            <colgroup>
              <col className="staff-seat-column" />
              <col className="staff-name-column" />
              {SLOT_LABELS.map((slot) => (
                <col className="staff-slot-column" key={slot} />
              ))}
            </colgroup>
            <thead>
              <tr>
                <th rowSpan={2}>좌석</th>
                <th rowSpan={2}>이름</th>
                <th colSpan={7}>{formatShortDate(selectedDate)}</th>
              </tr>
              <tr>
                {SLOT_LABELS.map((slot) => (
                  <th key={slot}>{slot}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filteredRows.map((row) => {
                const emptySeat = row.name === '공석';
                const rowClassName = row.seatNumber == null ? 'unassigned-row' : emptySeat ? 'empty-seat-row' : '';

                return (
                  <tr className={rowClassName} key={`${row.seatNumber || 'unassigned'}-${row.name}`}>
                    <td>{row.seatNumber ?? '-'}</td>
                    <td>{row.name}</td>
                    {SLOT_LABELS.map((slot, index) => {
                      const status = row.slots[index] || 'X';
                      const selected = selectedSlot?.memberId === row.memberId && selectedSlot?.slot === slot;

                      return (
                        <td
                          className={`${toStatusClassName(status, emptySeat)}${selected ? ' selected-slot' : ''}`}
                          key={slot}
                          onClick={() => {
                            if (emptySeat || !row.memberId) {
                              return;
                            }
                            setSelectedSlot({ memberId: row.memberId, name: row.name, slot });
                          }}
                        >
                          <span>{status}</span>
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="staff-attendance-command-bar">
            <button className="command-present" type="button" disabled={!selectedSlot || submitting} onClick={() => updateSlotStatus('PRESENT')}>O</button>
            <button className="command-absent" type="button" disabled={!selectedSlot || submitting} onClick={() => updateSlotStatus('ABSENT')}>X</button>
            <button className="command-other" type="button" disabled={!selectedSlot || submitting} onClick={openOtherModal}>기타</button>
            <button className="command-undo" type="button" disabled={!selectedSlot || submitting} onClick={() => setSelectedSlot(null)}>↩</button>
          </div>
        </div>
      )}
      {otherModalOpen && (
        <div className="attendance-modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            setOtherModalOpen(false);
          }
        }}>
          <form className="attendance-modal" role="dialog" aria-modal="true" onSubmit={submitOtherReason}>
            <h2>{selectedSlot?.name} 출석 상태 선택</h2>

            <section className="attendance-modal-section">
              <strong className="attendance-modal-label">날짜</strong>
              <div className="attendance-calendar-card">
                <div className="attendance-calendar-head">
                  <button type="button" aria-label="이전 달" onClick={() => moveOtherMonth(-1)}>‹</button>
                  <strong>{otherCalendar.year}년 {otherCalendar.month}월</strong>
                  <button type="button" aria-label="다음 달" onClick={() => moveOtherMonth(1)}>›</button>
                </div>
                <div className="attendance-calendar-weekdays">
                  {['일', '월', '화', '수', '목', '금', '토'].map((weekday) => (
                    <span key={weekday}>{weekday}</span>
                  ))}
                </div>
                <div className="attendance-calendar-grid">
                  {otherCalendar.days.map((day, index) => {
                    const dateKey = day ? toDateKey(new Date(otherCalendar.year, otherCalendar.month - 1, day)) : '';
                    const selected = dateKey === otherDate;

                    return day ? (
                      <button
                        className={selected ? 'selected' : ''}
                        key={`${day}-${index}`}
                        type="button"
                        onClick={() => setOtherDate(dateKey)}
                      >
                        {day}
                      </button>
                    ) : (
                      <span key={`empty-${index}`} />
                    );
                  })}
                </div>
              </div>
              <span className="attendance-selected-count">1일 선택됨</span>
            </section>

            <section className="attendance-modal-section">
              <strong className="attendance-modal-label">교시</strong>
              <div className="attendance-period-grid">
                {SLOT_LABELS.map((slot) => (
                  <button
                    className={otherSlot === slot ? 'selected' : ''}
                    key={slot}
                    type="button"
                    onClick={() => setOtherSlot(slot)}
                  >
                    {slot}
                  </button>
                ))}
              </div>
              <button className="attendance-full-select" type="button" disabled>전체 선택</button>
            </section>

            <section className="attendance-modal-section">
              <strong className="attendance-modal-label">사유 선택</strong>
              <div className="attendance-reason-grid">
                {OTHER_REASON_OPTIONS.map((reason) => (
                  <button
                    className={selectedOtherReason === reason ? 'selected' : ''}
                    key={reason}
                    type="button"
                    onClick={() => {
                      setSelectedOtherReason(reason);
                      setOtherReason('');
                    }}
                  >
                    {reason}
                  </button>
                ))}
              </div>
              <strong className="attendance-modal-label">사유 입력</strong>
              <input
                placeholder="3글자 이하 권장"
                value={otherReason}
                onChange={(event) => {
                  setOtherReason(event.target.value);
                  setSelectedOtherReason('');
                }}
              />
            </section>

            <section className="attendance-modal-section">
              <div className="attendance-leave-grid">
                {['월차', '오전반차', '오후반차'].map((reason) => (
                  <button
                    className={reason === '오후반차' ? 'leave-afternoon' : 'leave-red'}
                    key={reason}
                    type="button"
                    onClick={() => {
                      setSelectedOtherReason(reason);
                      setOtherReason('');
                    }}
                  >
                    {reason}
                  </button>
                ))}
              </div>
              <button className="attendance-clear-leave" type="button" disabled={submitting} onClick={() => updateSlotStatus('ABSENT', undefined, otherDate, otherSlot)}>
                휴가취소
              </button>
            </section>

            <div className="attendance-modal-actions">
              <button className="attendance-submit-button" type="submit" disabled={submitting}>{submitting ? '신청 중' : '신청'}</button>
              <button className="attendance-fixed-button" type="button" disabled={submitting} onClick={createFixedLeave}>
                {submitting ? '신청 중' : '고정신청'}
              </button>
            </div>
            <button className="attendance-modal-close" type="button" onClick={() => setOtherModalOpen(false)}>닫기</button>
          </form>
        </div>
      )}
    </div>
  );
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function formatKoreanDate(date: string) {
  const parsed = new Date(`${date}T00:00:00`);
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

  return `${parsed.getFullYear()}.${String(parsed.getMonth() + 1).padStart(2, '0')}.${String(parsed.getDate()).padStart(2, '0')} (${weekdays[parsed.getDay()]})`;
}

function formatShortDate(date: string) {
  const parsed = new Date(`${date}T00:00:00`);
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

  return `${parsed.getMonth() + 1}.${parsed.getDate()}(${weekdays[parsed.getDay()]})`;
}

function createMonthCalendar(date: string) {
  const parsed = new Date(`${date}T00:00:00`);
  const year = parsed.getFullYear();
  const monthIndex = parsed.getMonth();
  const firstDay = new Date(year, monthIndex, 1).getDay();
  const lastDate = new Date(year, monthIndex + 1, 0).getDate();
  const days: Array<number | null> = Array.from({ length: firstDay }, () => null);

  for (let day = 1; day <= lastDate; day += 1) {
    days.push(day);
  }
  while (days.length % 7 !== 0) {
    days.push(null);
  }

  return {
    year,
    month: monthIndex + 1,
    days,
  };
}

function toStatusClassName(status: string, emptySeat: boolean) {
  if (emptySeat) {
    return 'status-empty-seat';
  }

  if (status === 'X') {
    return 'status-absent';
  }
  if (status === 'O') {
    return 'status-present';
  }

  return 'status-leave';
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="10.5" cy="10.5" r="5.5" />
      <path d="m15 15 4 4" />
    </svg>
  );
}
