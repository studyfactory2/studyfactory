import { AdminGridPanel } from '../components/manager/AdminGridPanel';
import { ManagerTabs } from '../components/manager/ManagerTabs';
import { ManagerTopBar } from '../components/manager/ManagerTopBar';
import { MemberStatusPanel } from '../components/manager/MemberStatusPanel';
import { OtherLeaveRequestPanel } from '../components/manager/OtherLeaveRequestPanel';
import { PlaceholderPanel } from '../components/manager/PlaceholderPanel';
import { PreRegistrationPanel } from '../components/manager/PreRegistrationPanel';
import { StaffAttendancePanel } from '../components/manager/StaffAttendancePanel';
import { VacationHistoryPanel } from '../components/manager/VacationHistoryPanel';
import { STAFF_MENUS, resolveAdminMenuId } from '../constants/adminMenus';
import { useManagerOptions } from '../hooks/useManagerOptions';
import { ManagerLayout } from '../layouts/ManagerLayout';

export function ManagerDashboardScreen() {
  const role = localStorage.getItem('memberRole');
  const memberName = localStorage.getItem('memberName') || '사용자';
  const searchParams = new URLSearchParams(window.location.search);
  const requestedView = resolveAdminMenuId(searchParams.get('view'));
  const currentView = role === 'STAFF' && !STAFF_MENUS.some((menu) => menu.id === requestedView) ? 'attendance' : requestedView;
  const { branches, certifications } = useManagerOptions(role === 'ADMIN');

  if (role === 'STAFF') {
    return (
      <ManagerLayout>
        <ManagerTopBar />
        <ManagerTabs currentView={currentView} menus={STAFF_MENUS} />
        <section className="manager-card staff-attendance-card">
          {currentView === 'attendance' ? (
            <StaffAttendancePanel />
          ) : (
            <PlaceholderPanel currentView={currentView} />
          )}
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
      <ManagerTabs currentView={currentView} />
      <section className={`manager-card${currentView === 'attendance' ? ' staff-attendance-card' : ''}`}>
        {currentView === 'grid' ? (
          <AdminGridPanel />
        ) : currentView === 'register' ? (
          <PreRegistrationPanel branches={branches} certifications={certifications} />
        ) : currentView === 'status' ? (
          <MemberStatusPanel branches={branches} certifications={certifications} />
        ) : currentView === 'attendance' ? (
          <StaffAttendancePanel />
        ) : currentView === 'vacation_history' ? (
          <VacationHistoryPanel branches={branches} />
        ) : currentView === 'other_leave_request' ? (
          <OtherLeaveRequestPanel branches={branches} />
        ) : (
          <PlaceholderPanel currentView={currentView} />
        )}
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
