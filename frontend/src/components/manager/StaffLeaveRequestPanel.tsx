import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { LeaveResponse, LeaveType as ApiLeaveType, MonthlyLeaveCalendarResponse } from '../../types/domain';

type StaffLeaveType = '월차' | '오전반차' | '오후반차';
type Message = {
  type: 'success' | 'error';
  text: string;
};

const WEEKDAYS = [
  { label: '일', className: 'sunday' },
  { label: '월', className: '' },
  { label: '화', className: '' },
  { label: '수', className: '' },
  { label: '목', className: '' },
  { label: '금', className: '' },
  { label: '토', className: 'saturday' },
];

export function StaffLeaveRequestPanel() {
  const today = useMemo(() => toDateKey(new Date()), []);
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(new Date()));
  const [selectedDate, setSelectedDate] = useState(today);
  const [selectedLeaveType, setSelectedLeaveType] = useState<StaffLeaveType>('오전반차');
  const [leaves, setLeaves] = useState<LeaveResponse[]>([]);
  const [monthlyLeaves, setMonthlyLeaves] = useState<MonthlyLeaveCalendarResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<Message | null>(null);
  const days = useMemo(() => getMonthDays(visibleMonth), [visibleMonth]);
  const emptyDays = useMemo(() => Array.from({ length: visibleMonth.getDay() }, (_, index) => index), [visibleMonth]);
  const leavesByDate = useMemo(() => groupMonthlyLeavesByDate(monthlyLeaves), [monthlyLeaves]);
  const normalLeavesByDate = useMemo(() => groupLeavesByDate(leaves), [leaves]);
  const visibleMonthLeaves = useMemo(() => {
    return [...monthlyLeaves].sort((first, second) => (
      second.leaveDate.localeCompare(first.leaveDate) || toSourceOrder(first.source) - toSourceOrder(second.source)
    ));
  }, [monthlyLeaves]);

  useEffect(() => {
    void loadLeaves();
  }, []);

  useEffect(() => {
    void loadMonthlyLeaves();
  }, [visibleMonth]);

  const loadLeaves = async () => {
    setLoading(true);
    try {
      const responses = await apiRequest<LeaveResponse[]>('/api/leaves/me');
      setLeaves(responses);
      setMessage(null);
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '휴무 내역을 불러오지 못했습니다.' });
    } finally {
      setLoading(false);
    }
  };

  const loadMonthlyLeaves = async () => {
    const memberId = localStorage.getItem('memberId');
    if (!memberId) {
      setMonthlyLeaves([]);
      return;
    }

    try {
      const params = new URLSearchParams({
        memberId,
        year: String(visibleMonth.getFullYear()),
        month: String(visibleMonth.getMonth() + 1),
      });
      const responses = await apiRequest<MonthlyLeaveCalendarResponse[]>(`/api/leaves/monthly-calendar?${params.toString()}`);
      setMonthlyLeaves(responses);
    } catch (error) {
      setMonthlyLeaves([]);
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '월간 휴무 내역을 불러오지 못했습니다.' });
    }
  };

  const moveMonth = (amount: number) => {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1));
  };

  const submitLeave = async () => {
    if (selectedDate < today) {
      setMessage({ type: 'error', text: '오늘보다 이전 날짜는 휴무 신청을 할 수 없습니다.' });
      return;
    }

    setSubmitting(true);
    setMessage(null);
    try {
      await apiRequest<LeaveResponse>('/api/leaves', {
        method: 'POST',
        body: JSON.stringify({
          leaveDate: selectedDate,
          leaveType: toApiLeaveType(selectedLeaveType),
        }),
      });
      setMessage({ type: 'success', text: '휴무 신청이 완료되었습니다.' });
      await loadLeaves();
      await loadMonthlyLeaves();
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '휴무 신청에 실패했습니다.' });
    } finally {
      setSubmitting(false);
    }
  };

  const deleteLeave = async (leave: LeaveResponse) => {
    setSubmitting(true);
    setMessage(null);
    try {
      await apiRequest<void>(`/api/leaves/${leave.id}`, { method: 'DELETE' });
      setMessage({ type: 'success', text: '휴무 신청이 취소되었습니다.' });
      await loadLeaves();
      await loadMonthlyLeaves();
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '휴무 신청 취소에 실패했습니다.' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="staff-leave-panel">
      <header className="staff-leave-title">
        <a href="/managerdashboard?view=staff-page" aria-label="스텝페이지로 돌아가기">
          <BackIcon />
        </a>
        <h2>스탭 휴무 신청</h2>
      </header>

      <section className="staff-leave-calendar-card">
        <div className="staff-leave-calendar-month">
          <button type="button" aria-label="이전 달" onClick={() => moveMonth(-1)}>‹</button>
          <strong>{visibleMonth.getFullYear()}년 {visibleMonth.getMonth() + 1}월</strong>
          <button type="button" aria-label="다음 달" onClick={() => moveMonth(1)}>›</button>
        </div>
        <div className="staff-leave-calendar-grid" aria-label="스탭 휴무 달력">
          {WEEKDAYS.map((weekday) => (
            <span className={`staff-leave-weekday ${weekday.className}`} key={weekday.label}>
              {weekday.label}
            </span>
          ))}
          {emptyDays.map((day) => (
            <span className="staff-leave-calendar-empty" key={`empty-${day}`} />
          ))}
          {days.map((date) => {
            const dateKey = toDateKey(date);
            const dayLeaves = leavesByDate.get(dateKey) || [];

            return (
              <button
                className={getCalendarDayClassName(date, dateKey, selectedDate, today)}
                disabled={dateKey < today}
                type="button"
                key={dateKey}
                onClick={() => setSelectedDate(dateKey)}
              >
                <span>{date.getDate()}</span>
                {dayLeaves.length > 0 && (
                  <span className="staff-leave-badges">
                    {dayLeaves.slice(0, 2).map((leave, index) => (
                      <span className={`staff-leave-badge ${toMonthlyLeaveBadgeClassName(leave)}`} key={`${leave.leaveDate}-${leave.label}-${leave.source}-${index}`}>
                        {toCalendarLeaveLabel(leave)}
                      </span>
                    ))}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </section>

      <div className="staff-leave-types">
        {(['월차', '오전반차', '오후반차'] as StaffLeaveType[]).map((leaveType) => (
          <button
            className={selectedLeaveType === leaveType ? 'active' : ''}
            type="button"
            key={leaveType}
            onClick={() => setSelectedLeaveType(leaveType)}
          >
            {leaveType}
          </button>
        ))}
      </div>
      <button className="staff-leave-submit" type="button" disabled={submitting || selectedDate < today} onClick={() => void submitLeave()}>
        <CheckIcon />
        {submitting ? '신청 중' : '신청하기'}
      </button>
      {message && <p className={`staff-leave-message ${message.type}`}>{message.text}</p>}

      <section className="staff-leave-history">
        {loading ? (
          <p className="staff-leave-empty">휴무 내역을 불러오는 중입니다.</p>
        ) : visibleMonthLeaves.length === 0 ? (
          <p className="staff-leave-empty">이번 달 휴무 신청 내역이 없습니다.</p>
        ) : (
          visibleMonthLeaves.map((leave, index) => {
            const normalLeave = findNormalLeave(normalLeavesByDate.get(leave.leaveDate) || [], leave);
            const canDelete = leave.source === 'LEAVE' && normalLeave && leave.leaveDate >= today;

            return (
            <article className={`staff-leave-history-card ${toMonthlyLeaveHistoryClassName(leave)}`} key={`${leave.leaveDate}-${leave.label}-${leave.source}-${index}`}>
              <div>
                <strong>{formatCompactDate(leave.leaveDate)}</strong>
                <span className={`staff-leave-history-badge ${toMonthlyLeaveBadgeClassName(leave)}`}>
                  {toHistoryLeaveLabel(leave)}
                </span>
              </div>
              <div>
                {shouldShowSlots(leave) && <span>1, 2, 3, 4, 5, 6, 7교시</span>}
                {canDelete && (
                  <button type="button" disabled={submitting} onClick={() => void deleteLeave(normalLeave)}>
                    <TrashIcon />
                    취소
                  </button>
                )}
              </div>
            </article>
            );
          })
        )}
      </section>
    </div>
  );
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function getMonthDays(month: Date) {
  const lastDate = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  return Array.from({ length: lastDate }, (_, index) => new Date(month.getFullYear(), month.getMonth(), index + 1));
}

function groupLeavesByDate(leaves: LeaveResponse[]) {
  const grouped = new Map<string, LeaveResponse[]>();
  for (const leave of leaves) {
    grouped.set(leave.leaveDate, [...(grouped.get(leave.leaveDate) || []), leave]);
  }

  return grouped;
}

function groupMonthlyLeavesByDate(leaves: MonthlyLeaveCalendarResponse[]) {
  const grouped = new Map<string, MonthlyLeaveCalendarResponse[]>();
  for (const leave of leaves) {
    grouped.set(leave.leaveDate, [...(grouped.get(leave.leaveDate) || []), leave]);
  }

  return grouped;
}

function getCalendarDayClassName(date: Date, dateKey: string, selectedDate: string, today: string) {
  const classNames = ['staff-leave-day'];
  if (dateKey < today) {
    classNames.push('past');
  }
  if (dateKey === selectedDate) {
    classNames.push('selected');
  }
  if (date.getDay() === 0) {
    classNames.push('sunday');
  }
  if (date.getDay() === 6) {
    classNames.push('saturday');
  }

  return classNames.join(' ');
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function toMonthKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function toApiLeaveType(leaveType: StaffLeaveType): ApiLeaveType {
  if (leaveType === '월차') {
    return 'FULL';
  }
  if (leaveType === '오전반차') {
    return 'MORNING';
  }

  return 'AFTERNOON';
}

function toCalendarLeaveLabel(leave: MonthlyLeaveCalendarResponse) {
  if (leave.source !== 'LEAVE') {
    return leave.label;
  }
  const leaveType = toApiLeaveTypeFromLabel(leave.label);
  if (leaveType === 'FULL') {
    return '월차';
  }
  if (leaveType === 'MORNING') {
    return '오전';
  }

  return '오후';
}

function toHistoryLeaveLabel(leave: MonthlyLeaveCalendarResponse) {
  if (leave.source !== 'LEAVE') {
    return leave.label;
  }
  const leaveType = toApiLeaveTypeFromLabel(leave.label);
  if (leaveType === 'FULL') {
    return '월차';
  }
  if (leaveType === 'MORNING') {
    return '오전반차';
  }

  return '오후반차';
}

function toLeaveBadgeClassName(leaveType: ApiLeaveType) {
  if (leaveType === 'AFTERNOON') {
    return 'afternoon';
  }
  return 'leave';
}

function toMonthlyLeaveBadgeClassName(leave: MonthlyLeaveCalendarResponse) {
  if (leave.source !== 'LEAVE') {
    return 'fixed';
  }

  return toLeaveBadgeClassName(toApiLeaveTypeFromLabel(leave.label));
}

function toMonthlyLeaveHistoryClassName(leave: MonthlyLeaveCalendarResponse) {
  if (leave.source !== 'LEAVE') {
    return 'half';
  }
  if (toApiLeaveTypeFromLabel(leave.label) === 'FULL') {
    return 'full';
  }

  return 'half';
}

function toApiLeaveTypeFromLabel(label: string): ApiLeaveType {
  if (label === '월차') {
    return 'FULL';
  }
  if (label === '오전') {
    return 'MORNING';
  }

  return 'AFTERNOON';
}

function findNormalLeave(normalLeaves: LeaveResponse[], monthlyLeave: MonthlyLeaveCalendarResponse) {
  if (monthlyLeave.source !== 'LEAVE') {
    return undefined;
  }

  const leaveType = toApiLeaveTypeFromLabel(monthlyLeave.label);
  return normalLeaves.find((leave) => leave.leaveType === leaveType);
}

function shouldShowSlots(leave: MonthlyLeaveCalendarResponse) {
  return leave.source !== 'LEAVE';
}

function toSourceOrder(source: MonthlyLeaveCalendarResponse['source']) {
  if (source === 'LEAVE') {
    return 0;
  }
  if (source === 'FIXED_LEAVE') {
    return 1;
  }

  return 2;
}

function formatCompactDate(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00`);
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

  return `${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}(${weekdays[date.getDay()]})`;
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 5-7 7 7 7" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M20 6 9 17l-5-5" />
      <circle cx="12" cy="12" r="9" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
      <path d="M10 11v5" />
      <path d="M14 11v5" />
    </svg>
  );
}
