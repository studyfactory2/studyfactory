import { ADMIN_MENUS, type AdminMenu, type AdminMenuId } from '../../constants/adminMenus';

type ManagerTabsProps = {
  currentView: AdminMenuId;
  menus?: AdminMenu[];
};

export function ManagerTabs({ currentView, menus = ADMIN_MENUS }: ManagerTabsProps) {
  const visibleView = currentView === 'register' || currentView === 'status' || currentView === 'vacation_history' || currentView === 'other_leave_request'
    ? 'grid'
    : currentView === 'daily_leave_status'
      ? 'staff-page'
    : currentView;
  const currentIndex = Math.max(menus.findIndex((menu) => menu.id === visibleView), 0);
  const previousMenu = menus[currentIndex - 1];
  const currentMenu = menus[currentIndex];
  const nextMenu = menus[currentIndex + 1];

  return (
    <nav className="manager-tabs" aria-label="관리자 메뉴">
      <div className="adjacent-tab previous-tab">{previousMenu && <span>{previousMenu.label}</span>}</div>
      {previousMenu ? (
        <a className="tab-arrow previous-arrow" href={`/managerdashboard?view=${previousMenu.id}`} aria-label="이전 페이지">
          ‹
        </a>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      <a className="current-tab" href={`/managerdashboard?view=${currentMenu.id}`} aria-current="page">
        {currentMenu.label}
      </a>
      {nextMenu ? (
        <a className="tab-arrow next-arrow" href={`/managerdashboard?view=${nextMenu.id}`} aria-label="다음 페이지">
          ›
        </a>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      <div className="adjacent-tab next-tab">{nextMenu && <span>{nextMenu.label}</span>}</div>
    </nav>
  );
}
