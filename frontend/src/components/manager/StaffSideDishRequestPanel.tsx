import { SideDishPanel } from '../member/SideDishPanel';

export function StaffSideDishRequestPanel() {
  return (
    <div className="staff-side-dish-panel">
      <header className="staff-side-dish-title">
        <a href="/managerdashboard?view=staff-page" aria-label="스탭 페이지로 돌아가기">
          <BackIcon />
        </a>
        <h2>반찬 신청</h2>
      </header>
      <SideDishPanel />
    </div>
  );
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}
