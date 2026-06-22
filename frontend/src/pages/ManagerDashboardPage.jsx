import React from 'react';

const ADMIN_MENUS = [
  { id: 'register', label: '관리자페이지' },
  { id: 'attendance', label: '출석부' },
  { id: 'staff-work', label: '스탭 업무 현황' },
  { id: 'staff-page', label: '스텝페이지' },
];

export default function ManagerDashboardPage() {
  const role = localStorage.getItem('memberRole');
  const memberName = localStorage.getItem('memberName') || '사용자';
  const searchParams = new URLSearchParams(window.location.search);
  const currentView = searchParams.get('view') || 'register';

  if (role !== 'ADMIN') {
    return (
      <main className="dashboard-page member-dashboard">
        <section className="dashboard-shell">
          <p className="dashboard-eyebrow">Study Factory</p>
          <h1>{memberName}님</h1>
          <p className="dashboard-description">회원 화면은 별도로 설계될 예정입니다.</p>
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard-page">
      <aside className="dashboard-sidebar">
        <div>
          <p className="dashboard-eyebrow">Study Factory</p>
          <h1>관리자 페이지</h1>
        </div>
        <nav className="dashboard-nav" aria-label="관리자 메뉴">
          {ADMIN_MENUS.map((menu) => (
            <a
              className={`dashboard-nav-item${currentView === menu.id ? ' active' : ''}`}
              href={`/managerdashboard?view=${menu.id}`}
              key={menu.id}
            >
              {menu.label}
            </a>
          ))}
        </nav>
      </aside>
      <section className="dashboard-content">
        <div className="dashboard-toolbar">
          <div>
            <p className="dashboard-eyebrow">ADMIN</p>
            <h2>{getCurrentMenuLabel(currentView)}</h2>
          </div>
          <span>{memberName}</span>
        </div>
        <div className="dashboard-empty-state">
          <strong>{getCurrentMenuLabel(currentView)}</strong>
          <p>관리자 기능 화면이 연결될 영역입니다.</p>
        </div>
      </section>
    </main>
  );
}

function getCurrentMenuLabel(currentView) {
  return ADMIN_MENUS.find((menu) => menu.id === currentView)?.label || '관리자페이지';
}
