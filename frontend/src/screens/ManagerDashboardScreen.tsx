import { AdminGridPanel } from '../components/manager/AdminGridPanel';
import { ManagerTabs } from '../components/manager/ManagerTabs';
import { ManagerTopBar } from '../components/manager/ManagerTopBar';
import { MemberStatusPanel } from '../components/manager/MemberStatusPanel';
import { PlaceholderPanel } from '../components/manager/PlaceholderPanel';
import { PreRegistrationPanel } from '../components/manager/PreRegistrationPanel';
import { VacationHistoryPanel } from '../components/manager/VacationHistoryPanel';
import { resolveAdminMenuId } from '../constants/adminMenus';
import { useManagerOptions } from '../hooks/useManagerOptions';
import { ManagerLayout } from '../layouts/ManagerLayout';

export function ManagerDashboardScreen() {
  const role = localStorage.getItem('memberRole');
  const memberName = localStorage.getItem('memberName') || '사용자';
  const searchParams = new URLSearchParams(window.location.search);
  const currentView = resolveAdminMenuId(searchParams.get('view'));
  const { branches, certifications } = useManagerOptions(role === 'ADMIN');

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
      <section className="manager-card">
        {currentView === 'grid' ? (
          <AdminGridPanel />
        ) : currentView === 'register' ? (
          <PreRegistrationPanel branches={branches} certifications={certifications} />
        ) : currentView === 'status' ? (
          <MemberStatusPanel branches={branches} certifications={certifications} />
        ) : currentView === 'vacation_history' ? (
          <VacationHistoryPanel branches={branches} />
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
