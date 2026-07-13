import { useEffect, useState } from 'react';
import { BeveragePanel } from '../components/member/BeveragePanel';
import { LeavePlanPanel } from '../components/member/LeavePlanPanel';
import { MemberTabs } from '../components/member/MemberTabs';
import { SideDishPanel } from '../components/member/SideDishPanel';
import { SuggestionPanel } from '../components/member/SuggestionPanel';
import { WeeklyPlanPanel } from '../components/member/WeeklyPlanPanel';
import { ManagerTopBar } from '../components/manager/ManagerTopBar';
import { MEMBER_MENUS, resolveMemberMenuId, type MemberMenuId } from '../constants/memberMenus';
import { ManagerLayout } from '../layouts/ManagerLayout';

export function MemberDashboardScreen() {
  const [currentView, setCurrentView] = useState(() => {
    const searchParams = new URLSearchParams(window.location.search);
    return resolveMemberMenuId(searchParams.get('view'));
  });
  const [slideDirection, setSlideDirection] = useState<'next' | 'previous'>('next');

  useEffect(() => {
    const syncViewFromUrl = () => {
      const searchParams = new URLSearchParams(window.location.search);
      setCurrentView(resolveMemberMenuId(searchParams.get('view')));
    };

    window.addEventListener('popstate', syncViewFromUrl);
    return () => window.removeEventListener('popstate', syncViewFromUrl);
  }, []);

  const changeView = (nextView: MemberMenuId) => {
    if (nextView === currentView) {
      return;
    }

    const currentIndex = MEMBER_MENUS.findIndex((menu) => menu.id === currentView);
    const nextIndex = MEMBER_MENUS.findIndex((menu) => menu.id === nextView);
    setSlideDirection(nextIndex > currentIndex ? 'next' : 'previous');
    setCurrentView(nextView);
    window.history.pushState(null, '', `/memberdashboard?view=${nextView}`);
  };

  return (
    <ManagerLayout>
      <ManagerTopBar />
      <MemberTabs currentView={currentView} onViewChange={changeView} />
      <section className="manager-card member-dashboard-main">
        <div className={`member-slide-panel slide-${slideDirection}`} key={currentView}>
          <MemberPanel currentView={currentView} />
        </div>
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

  if (currentView === 'weekly-plan') {
    return <WeeklyPlanPanel />;
  }

  return <LeavePlanPanel />;
}
