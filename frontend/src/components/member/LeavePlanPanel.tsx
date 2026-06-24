import { useMemo, useState } from 'react';

type LeaveType = '월차' | '오전반차' | '오후반차';

const WEEKDAYS = [
  { label: '일', className: 'sunday' },
  { label: '월', className: '' },
  { label: '화', className: '' },
  { label: '수', className: '' },
  { label: '목', className: '' },
  { label: '금', className: '' },
  { label: '토', className: 'saturday' },
];

function getDateClassName(date: Date, selectedDate: string, today: string) {
  const classNames = ['calendar-day'];
  const dateKey = toDateKey(date);
  const weekday = date.getDay();

  if (dateKey < today) {
    classNames.push('past');
  }

  if (dateKey === selectedDate) {
    classNames.push('selected');
  }

  if (weekday === 0) {
    classNames.push('sunday');
  }

  if (weekday === 6) {
    classNames.push('saturday');
  }

  return classNames.join(' ');
}

export function LeavePlanPanel() {
  const [selectedLeaveType, setSelectedLeaveType] = useState<LeaveType | null>(null);
  const today = useMemo(() => toDateKey(new Date()), []);
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(new Date()));
  const [selectedDate, setSelectedDate] = useState(today);
  const days = useMemo(() => getMonthDays(visibleMonth), [visibleMonth]);
  const emptyDays = useMemo(() => Array.from({ length: visibleMonth.getDay() }, (_, index) => index), [visibleMonth]);

  const moveMonth = (amount: number) => {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1));
  };

  return (
    <div className="member-panel">
      <div className="member-calendar-header">
        <button type="button" aria-label="이전 달" onClick={() => moveMonth(-1)}>‹</button>
        <strong>{visibleMonth.getFullYear()}년 {visibleMonth.getMonth() + 1}월</strong>
        <button type="button" aria-label="다음 달" onClick={() => moveMonth(1)}>›</button>
      </div>
      <div className="member-calendar-grid" aria-label="휴무 달력">
        {WEEKDAYS.map((day) => (
          <span className={`calendar-weekday ${day.className}`} key={day.label}>
            {day.label}
          </span>
        ))}
        {emptyDays.map((day) => (
          <span className="calendar-empty" key={`empty-${day}`} />
        ))}
        {days.map((date) => {
          const dateKey = toDateKey(date);
          const past = dateKey < today;

          return (
            <button
              className={getDateClassName(date, selectedDate, today)}
              disabled={past}
              type="button"
              key={dateKey}
              onClick={() => setSelectedDate(dateKey)}
            >
              {date.getDate()}
            </button>
          );
        })}
      </div>
      <div className="leave-type-actions">
        <button
          className={selectedLeaveType === '월차' ? 'monthly active' : 'monthly'}
          type="button"
          onClick={() => setSelectedLeaveType('월차')}
        >
          월차
        </button>
        <button
          className={selectedLeaveType === '오전반차' ? 'active' : ''}
          type="button"
          onClick={() => setSelectedLeaveType('오전반차')}
        >
          오전반차
        </button>
        <button
          className={selectedLeaveType === '오후반차' ? 'active' : ''}
          type="button"
          onClick={() => setSelectedLeaveType('오후반차')}
        >
          오후반차
        </button>
      </div>
      <button className="member-primary-action" type="button">
        <span className="check-icon" aria-hidden="true">✓</span>
        신청하기
      </button>
      <section className="member-list-box">
        <p>내역이 없습니다.</p>
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

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}
