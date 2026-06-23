import { ADMIN_MENUS, type AdminMenuId } from '../../constants/adminMenus';

type ManagerTabsProps = {
  currentView: AdminMenuId;
};

export function ManagerTabs({ currentView }: ManagerTabsProps) {
  const visibleView = currentView === 'register' ? 'grid' : currentView;
  const currentIndex = Math.max(ADMIN_MENUS.findIndex((menu) => menu.id === visibleView), 0);
  const previousMenu = ADMIN_MENUS[currentIndex - 1];
  const currentMenu = ADMIN_MENUS[currentIndex];
  const nextMenu = ADMIN_MENUS[currentIndex + 1];

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
