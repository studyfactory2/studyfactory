import { useEffect, useState } from 'react';
import { AdminGridPanel } from '../components/manager/AdminGridPanel';
import { DailyLeaveStatusPanel } from '../components/manager/DailyLeaveStatusPanel';
import { ManagerTabs } from '../components/manager/ManagerTabs';
import { ManagerTopBar } from '../components/manager/ManagerTopBar';
import { MemberStatusPanel } from '../components/manager/MemberStatusPanel';
import { OtherLeaveRequestPanel } from '../components/manager/OtherLeaveRequestPanel';
import { PlaceholderPanel } from '../components/manager/PlaceholderPanel';
import { PreRegistrationPanel } from '../components/manager/PreRegistrationPanel';
import { StaffAttendancePanel } from '../components/manager/StaffAttendancePanel';
import { StaffPagePanel } from '../components/manager/StaffPagePanel';
import { StaffSeatManagementPanel } from '../components/manager/StaffSeatManagementPanel';
import { StaffWorkPanel } from '../components/manager/StaffWorkPanel';
import { VacationHistoryPanel } from '../components/manager/VacationHistoryPanel';
import { ADMIN_MENUS, STAFF_MENUS, resolveAdminMenuId, type AdminMenuId } from '../constants/adminMenus';
import { useManagerOptions } from '../hooks/useManagerOptions';
import { ManagerLayout } from '../layouts/ManagerLayout';

export function ManagerDashboardScreen() {
  const role = localStorage.getItem('memberRole');
  const memberName = localStorage.getItem('memberName') || '사용자';
  const staffExtraViews = ['daily_leave_status', 'seat_management'];
  const [currentView, setCurrentView] = useState<AdminMenuId>(() => resolveManagerViewFromUrl(role, staffExtraViews));
  const [slideDirection, setSlideDirection] = useState<'next' | 'previous'>('next');
  const { branches, certifications } = useManagerOptions(role === 'ADMIN' || role === 'STAFF');

  useEffect(() => {
    const syncViewFromUrl = () => {
      setCurrentView(resolveManagerViewFromUrl(role, staffExtraViews));
    };

    window.addEventListener('popstate', syncViewFromUrl);
    return () => window.removeEventListener('popstate', syncViewFromUrl);
  }, [role]);

  useEffect(() => {
    const interceptManagerLink = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }
      const target = event.target instanceof Element ? event.target.closest<HTMLAnchorElement>('a[href]') : null;
      if (!target) {
        return;
      }
      const url = new URL(target.href);
      if (url.origin !== window.location.origin || url.pathname !== '/managerdashboard') {
        return;
      }

      event.preventDefault();
      changeView(resolveAdminMenuId(url.searchParams.get('view')));
    };

    document.addEventListener('click', interceptManagerLink);
    return () => document.removeEventListener('click', interceptManagerLink);
  }, [currentView, role]);

  const changeView = (nextView: AdminMenuId) => {
    const resolvedView = resolveAllowedView(nextView, role, staffExtraViews);
    if (resolvedView === currentView) {
      return;
    }

    setSlideDirection(toViewOrder(resolvedView) > toViewOrder(currentView) ? 'next' : 'previous');
    setCurrentView(resolvedView);
    window.history.pushState(null, '', `/managerdashboard?view=${resolvedView}`);
  };

  if (role === 'STAFF') {
    return (
      <ManagerLayout>
        <ManagerTopBar />
        <ManagerTabs currentView={currentView} menus={STAFF_MENUS} onViewChange={changeView} />
        <section className={`manager-card manager-dashboard-main${currentView === 'attendance' ? ' staff-attendance-card' : ''}`}>
          <div className={`manager-slide-panel slide-${slideDirection}`} key={currentView}>
            <ManagerPanel
              branches={branches}
              certifications={certifications}
              currentView={currentView}
              editableStaffWork={false}
            />
          </div>
        </section>
        <div className="manager-pagination" aria-hidden="true">
          <span className="active" />
          <span />
          <span />
          <span />
        </div>
      </ManagerLayout>
    );
  }

  if (role !== 'ADMIN') {
    return (
      <ManagerLayout>
        <section className="manager-card member-dashboard-card">
          <p>Study Factory</p>
          <h1>{memberName}님</h1>
          <span>회원 화면은 별도로 설계될 예정입니다.</span>
        </section>
      </ManagerLayout>
    );
  }

  return (
    <ManagerLayout>
      <ManagerTopBar />
      <ManagerTabs currentView={currentView} onViewChange={changeView} />
      <section className={`manager-card manager-dashboard-main${currentView === 'attendance' ? ' staff-attendance-card' : ''}`}>
        <div className={`manager-slide-panel slide-${slideDirection}`} key={currentView}>
          <ManagerPanel
            branches={branches}
            certifications={certifications}
            currentView={currentView}
            editableStaffWork={true}
          />
        </div>
      </section>
      <div className="manager-pagination" aria-hidden="true">
        <span className="active" />
        <span />
        <span />
        <span />
      </div>
    </ManagerLayout>
  );
}

type ManagerPanelProps = {
  branches: ReturnType<typeof useManagerOptions>['branches'];
  certifications: ReturnType<typeof useManagerOptions>['certifications'];
  currentView: AdminMenuId;
  editableStaffWork: boolean;
};

function ManagerPanel({ branches, certifications, currentView, editableStaffWork }: ManagerPanelProps) {
  if (currentView === 'grid') {
    return <AdminGridPanel />;
  }

  if (currentView === 'register') {
    return <PreRegistrationPanel branches={branches} certifications={certifications} />;
  }

  if (currentView === 'status') {
    return <MemberStatusPanel branches={branches} certifications={certifications} />;
  }

  if (currentView === 'attendance') {
    return <StaffAttendancePanel />;
  }

  if (currentView === 'staff-work') {
    return <StaffWorkPanel branches={branches} editable={editableStaffWork} />;
  }

  if (currentView === 'staff-page') {
    return <StaffPagePanel />;
  }

  if (currentView === 'daily_leave_status') {
    return <DailyLeaveStatusPanel branches={branches} />;
  }

  if (currentView === 'seat_management') {
    return <StaffSeatManagementPanel branches={branches} />;
  }

  if (currentView === 'vacation_history') {
    return <VacationHistoryPanel branches={branches} />;
  }

  if (currentView === 'other_leave_request') {
    return <OtherLeaveRequestPanel branches={branches} />;
  }

  return <PlaceholderPanel currentView={currentView} />;
}

function resolveManagerViewFromUrl(role: string | null, staffExtraViews: string[]) {
  const searchParams = new URLSearchParams(window.location.search);
  return resolveAllowedView(resolveAdminMenuId(searchParams.get('view')), role, staffExtraViews);
}

function resolveAllowedView(view: AdminMenuId, role: string | null, staffExtraViews: string[]) {
  if (role !== 'STAFF') {
    return view;
  }
  if (STAFF_MENUS.some((menu) => menu.id === view) || staffExtraViews.includes(view)) {
    return view;
  }

  return 'attendance';
}

function toViewOrder(view: AdminMenuId) {
  const visibleView = view === 'register' || view === 'status' || view === 'vacation_history' || view === 'other_leave_request'
    ? 'grid'
    : view === 'daily_leave_status' || view === 'seat_management'
      ? 'staff-page'
      : view;
  const tabOrder = ADMIN_MENUS.findIndex((menu) => menu.id === visibleView);
  if (tabOrder >= 0) {
    return tabOrder;
  }

  return ADMIN_MENUS.length;
}
