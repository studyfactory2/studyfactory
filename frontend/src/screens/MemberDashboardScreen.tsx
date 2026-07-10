import { useEffect, useRef, useState, type PointerEvent } from 'react';
import { BeveragePanel } from '../components/member/BeveragePanel';
import { LeavePlanPanel } from '../components/member/LeavePlanPanel';
import { MemberTabs } from '../components/member/MemberTabs';
import { SideDishPanel } from '../components/member/SideDishPanel';
import { SuggestionPanel } from '../components/member/SuggestionPanel';
import { LogoutIcon } from '../components/manager/LogoutIcon';
import { MEMBER_MENUS, resolveMemberMenuId, type MemberMenuId } from '../constants/memberMenus';
import { ManagerLayout } from '../layouts/ManagerLayout';

export function MemberDashboardScreen() {
  const [currentView, setCurrentView] = useState(() => {
    const searchParams = new URLSearchParams(window.location.search);
    return resolveMemberMenuId(searchParams.get('view'));
  });
  const [slideDirection, setSlideDirection] = useState<'next' | 'previous'>('next');
  const swipeStartRef = useRef<{ id: number; scrollY: number; x: number; y: number; time: number } | null>(null);

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

  const moveBySwipe = (direction: 'next' | 'previous') => {
    const currentIndex = MEMBER_MENUS.findIndex((menu) => menu.id === currentView);
    const nextIndex = direction === 'next' ? currentIndex + 1 : currentIndex - 1;
    const nextMenu = MEMBER_MENUS[nextIndex];

    if (!nextMenu) {
      return;
    }

    changeView(nextMenu.id);
  };

  const isSwipeViewport = () => window.innerWidth <= 700;

  const startSwipe = (event: PointerEvent<HTMLElement>) => {
    const target = event.target instanceof Element ? event.target : null;

    if (!event.isPrimary || !isSwipeViewport() || target?.closest('input, textarea, select')) {
      swipeStartRef.current = null;
      return;
    }

    swipeStartRef.current = {
      id: event.pointerId,
      scrollY: window.scrollY,
      x: event.clientX,
      y: event.clientY,
      time: Date.now(),
    };
  };

  const finishSwipe = (event: PointerEvent<HTMLElement>) => {
    const start = swipeStartRef.current;
    swipeStartRef.current = null;

    if (!start || start.id !== event.pointerId || !isSwipeViewport()) {
      return;
    }

    const deltaX = event.clientX - start.x;
    const deltaY = event.clientY - start.y;
    const elapsed = Date.now() - start.time;
    const isHorizontalSwipe = Math.abs(deltaX) >= 64 && Math.abs(deltaX) > Math.abs(deltaY) * 1.45 && elapsed < 900;
    const isPullToRefresh = start.scrollY <= 4 && deltaY >= 110 && deltaY > Math.abs(deltaX) * 1.35 && elapsed < 1200;

    if (isPullToRefresh) {
      window.location.reload();
      return;
    }

    if (!isHorizontalSwipe) {
      return;
    }

    moveBySwipe(deltaX < 0 ? 'next' : 'previous');
  };

  return (
    <ManagerLayout className={`member-dashboard-shell member-view-${currentView}`}>
      <MemberTopBar currentView={currentView} onViewChange={changeView} />
      <MemberTabs currentView={currentView} onViewChange={changeView} />
      <section
        className="manager-card member-dashboard-main"
        onPointerDown={startSwipe}
        onPointerUp={finishSwipe}
        onPointerCancel={() => {
          swipeStartRef.current = null;
        }}
      >
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

  return <LeavePlanPanel />;
}

function MemberTopBar({
  currentView,
  onViewChange,
}: {
  currentView: MemberMenuId;
  onViewChange: (view: MemberMenuId) => void;
}) {
  const logout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('memberName');
    localStorage.removeItem('memberId');
    localStorage.removeItem('branchId');
    localStorage.removeItem('memberRole');
    window.location.href = '/login';
  };

  return (
    <header className="member-topbar">
      <button className="member-wordmark" type="button" onClick={() => onViewChange('leave-plan')} aria-label="휴무계획으로 이동">
        자공
      </button>
      <div className="member-topbar-actions">
        <button className="member-icon-action" type="button" aria-label="로그아웃" onClick={logout}>
          <LogoutIcon />
        </button>
        <button className="member-icon-action" type="button" aria-label="새로고침" onClick={() => window.location.reload()}>
          ↻
        </button>
      </div>
    </header>
  );
}
