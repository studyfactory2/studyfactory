import { MEMBER_MENUS, type MemberMenuId } from '../../constants/memberMenus';

type MemberTabsProps = {
  currentView: MemberMenuId;
  onViewChange: (view: MemberMenuId) => void;
};

export function MemberTabs({ currentView, onViewChange }: MemberTabsProps) {
  const currentIndex = Math.max(MEMBER_MENUS.findIndex((menu) => menu.id === currentView), 0);
  const previousMenu = MEMBER_MENUS[currentIndex - 1];
  const currentMenu = MEMBER_MENUS[currentIndex];
  const nextMenu = MEMBER_MENUS[currentIndex + 1];

  return (
    <nav className="manager-tabs" aria-label="회원 메뉴">
      <div className="adjacent-tab previous-tab">{previousMenu && <span>{previousMenu.label}</span>}</div>
      {previousMenu ? (
        <button className="tab-arrow previous-arrow" type="button" aria-label="이전 페이지" onClick={() => onViewChange(previousMenu.id)}>
          ‹
        </button>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      <button className="current-tab" type="button" aria-current="page">
        {currentMenu.label}
      </button>
      {nextMenu ? (
        <button className="tab-arrow next-arrow" type="button" aria-label="다음 페이지" onClick={() => onViewChange(nextMenu.id)}>
          ›
        </button>
      ) : (
        <span className="tab-arrow-placeholder" />
      )}
      <div className="adjacent-tab next-tab">{nextMenu && <span>{nextMenu.label}</span>}</div>
    </nav>
  );
}
