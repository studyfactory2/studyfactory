import { MEMBER_MENUS, type MemberMenuId } from '../../constants/memberMenus';

type MemberTabsProps = {
  currentView: MemberMenuId;
};

export function MemberTabs({ currentView }: MemberTabsProps) {
  const currentIndex = Math.max(MEMBER_MENUS.findIndex((menu) => menu.id === currentView), 0);
  const previousMenu = MEMBER_MENUS[currentIndex - 1];
  const currentMenu = MEMBER_MENUS[currentIndex];
  const nextMenu = MEMBER_MENUS[currentIndex + 1];

  return (
    <nav className="manager-tabs" aria-label="회원 메뉴">
      <div className="adjacent-tab previous-tab">{previousMenu && <span>{previousMenu.label}</span>}</div>
      {previousMenu ? (
        <a className="tab-arrow previous-arrow" href={`/memberdashboard?view=${previousMenu.id}`} aria-label="이전 페이지">
          ‹
        </a>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      <a className="current-tab" href={`/memberdashboard?view=${currentMenu.id}`} aria-current="page">
        {currentMenu.label}
      </a>
      {nextMenu ? (
        <a className="tab-arrow next-arrow" href={`/memberdashboard?view=${nextMenu.id}`} aria-label="다음 페이지">
          ›
        </a>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      <div className="adjacent-tab next-tab">{nextMenu && <span>{nextMenu.label}</span>}</div>
    </nav>
  );
}
