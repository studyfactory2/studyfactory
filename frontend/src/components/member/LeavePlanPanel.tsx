const DAYS = Array.from({ length: 30 }, (_, index) => index + 1);

export function LeavePlanPanel() {
  return (
    <div className="member-panel">
      <div className="member-calendar-header">
        <button type="button" aria-label="이전 달">‹</button>
        <strong>2026년 6월</strong>
        <button type="button" aria-label="다음 달">›</button>
      </div>
      <div className="member-calendar-grid" aria-label="휴무 달력">
        {['일', '월', '화', '수', '목', '금', '토'].map((day) => (
          <span className="calendar-weekday" key={day}>{day}</span>
        ))}
        {DAYS.map((day) => (
          <button className={day === 22 ? 'selected' : ''} type="button" key={day}>
            {day}
          </button>
        ))}
      </div>
      <div className="leave-type-actions">
        <button type="button">월차</button>
        <button type="button">오전반차</button>
        <button type="button">오후반차</button>
      </div>
      <button className="member-primary-action" type="button">신청하기</button>
      <section className="member-list-box">
        <p>내역이 없습니다.</p>
      </section>
    </div>
  );
}
