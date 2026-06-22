import { useState } from 'react';

type LeaveType = '월차' | '오전반차' | '오후반차';

const TODAY = 22;
const DAYS = Array.from({ length: 30 }, (_, index) => index + 1);
const EMPTY_DAYS = Array.from({ length: new Date(2026, 5, 1).getDay() }, (_, index) => index);
const WEEKDAYS = [
  { label: '일', className: 'sunday' },
  { label: '월', className: '' },
  { label: '화', className: '' },
  { label: '수', className: '' },
  { label: '목', className: '' },
  { label: '금', className: '' },
  { label: '토', className: 'saturday' },
];

function getDateClassName(day: number) {
  const classNames = ['calendar-day'];
  const weekday = new Date(2026, 5, day).getDay();

  if (day < TODAY) {
    classNames.push('past');
  }

  if (day === TODAY) {
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

  return (
    <div className="member-panel">
      <div className="member-calendar-header">
        <button type="button" aria-label="이전 달">‹</button>
        <strong>2026년 6월</strong>
        <button type="button" aria-label="다음 달">›</button>
      </div>
      <div className="member-calendar-grid" aria-label="휴무 달력">
        {WEEKDAYS.map((day) => (
          <span className={`calendar-weekday ${day.className}`} key={day.label}>
            {day.label}
          </span>
        ))}
        {EMPTY_DAYS.map((day) => (
          <span className="calendar-empty" key={`empty-${day}`} />
        ))}
        {DAYS.map((day) => (
          <button className={getDateClassName(day)} disabled={day < TODAY} type="button" key={day}>
            {day}
          </button>
        ))}
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
