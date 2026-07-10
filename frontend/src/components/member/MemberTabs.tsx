import { MEMBER_MENUS, type MemberMenuId } from '../../constants/memberMenus';

type MemberTabsProps = {
  currentView: MemberMenuId;
  onViewChange: (view: MemberMenuId) => void;
};

export function MemberTabs({ currentView, onViewChange }: MemberTabsProps) {
  const currentIndex = Math.max(MEMBER_MENUS.findIndex((menu) => menu.id === currentView), 0);
  const visibleMenus =
    currentView === 'leave-plan'
      ? MEMBER_MENUS.slice(0, 2)
      : currentView === 'beverage'
      ? MEMBER_MENUS.slice(2, 4)
      : MEMBER_MENUS.slice(
          Math.max(0, Math.min(currentIndex - 1, MEMBER_MENUS.length - 3)),
          Math.max(0, Math.min(currentIndex - 1, MEMBER_MENUS.length - 3)) + 3
        );

  return (
    <nav className={`member-segment-tabs count-${visibleMenus.length}`} aria-label="회원 메뉴">
      {visibleMenus.map((menu) => (
        <button
          className={menu.id === currentView ? 'active' : ''}
          type="button"
          aria-current={menu.id === currentView ? 'page' : undefined}
          key={menu.id}
          onClick={() => onViewChange(menu.id)}
        >
          {menu.label.replace(' 신청/변경', '')}
        </button>
      ))}
    </nav>
  );
}
