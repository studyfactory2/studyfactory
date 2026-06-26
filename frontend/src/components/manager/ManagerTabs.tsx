import { ADMIN_MENUS, type AdminMenu, type AdminMenuId } from '../../constants/adminMenus';

type ManagerTabsProps = {
  currentView: AdminMenuId;
  menus?: AdminMenu[];
  onViewChange?: (view: AdminMenuId) => void;
};

export function ManagerTabs({ currentView, menus = ADMIN_MENUS, onViewChange }: ManagerTabsProps) {
  const visibleView = currentView === 'register' || currentView === 'status' || currentView === 'vacation_history' || currentView === 'other_leave_request'
    ? 'grid'
    : currentView === 'daily_leave_status' || currentView === 'seat_management'
      ? 'staff-page'
    : currentView;
  const currentIndex = Math.max(menus.findIndex((menu) => menu.id === visibleView), 0);
  const previousMenu = menus[currentIndex - 1];
  const currentMenu = menus[currentIndex];
  const nextMenu = menus[currentIndex + 1];
  const changeView = (view: AdminMenuId) => {
    if (onViewChange) {
      onViewChange(view);
    }
  };

  return (
    <nav className="manager-tabs" aria-label="관리자 메뉴">
      {previousMenu ? (
        <button className="adjacent-tab previous-tab" type="button" onClick={() => changeView(previousMenu.id)}>
          <span>{previousMenu.label}</span>
        </button>
      ) : (
        <span className="adjacent-tab previous-tab" />
      )}
      {previousMenu ? (
        <button className="tab-arrow previous-arrow" type="button" aria-label="이전 페이지" onClick={() => changeView(previousMenu.id)}>
          ‹
        </button>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      <span className="current-tab" aria-current="page">
        {currentMenu.label}
      </span>
      {nextMenu ? (
        <button className="tab-arrow next-arrow" type="button" aria-label="다음 페이지" onClick={() => changeView(nextMenu.id)}>
          ›
        </button>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      {nextMenu ? (
        <button className="adjacent-tab next-tab" type="button" onClick={() => changeView(nextMenu.id)}>
          <span>{nextMenu.label}</span>
        </button>
      ) : (
        <span className="adjacent-tab next-tab" />
      )}
    </nav>
  );
}
