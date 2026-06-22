import { BeveragePanel } from '../components/member/BeveragePanel';
import { LeavePlanPanel } from '../components/member/LeavePlanPanel';
import { MemberTabs } from '../components/member/MemberTabs';
import { SideDishPanel } from '../components/member/SideDishPanel';
import { SuggestionPanel } from '../components/member/SuggestionPanel';
import { ManagerTopBar } from '../components/manager/ManagerTopBar';
import { MEMBER_MENUS, resolveMemberMenuId, type MemberMenuId } from '../constants/memberMenus';
import { ManagerLayout } from '../layouts/ManagerLayout';

export function MemberDashboardScreen() {
  const searchParams = new URLSearchParams(window.location.search);
  const currentView = resolveMemberMenuId(searchParams.get('view'));

  return (
    <ManagerLayout>
      <ManagerTopBar />
      <MemberTabs currentView={currentView} />
      <section className="manager-card member-dashboard-main">
        <MemberPanel currentView={currentView} />
      </section>
      <div className="manager-pagination" aria-hidden="true">
        {MEMBER_MENUS.map((menu) => (
          <span className={menu.id === currentView ? 'active' : ''} key={menu.id} />
        ))}
      </div>
    </ManagerLayout>
  );
}

function MemberPanel({ currentView }: { currentView: MemberMenuId }) {
  if (currentView === 'side-dish') {
    return <SideDishPanel />;
  }

  if (currentView === 'suggestion') {
    return <SuggestionPanel />;
  }

  if (currentView === 'beverage') {
    return <BeveragePanel />;
  }

  return <LeavePlanPanel />;
}
