import { LoginScreen } from '../screens/LoginScreen';
import { ManagerDashboardScreen } from '../screens/ManagerDashboardScreen';
import { MemberDashboardScreen } from '../screens/MemberDashboardScreen';

export function AppRoutes() {
  if (window.location.pathname === '/managerdashboard') {
    return <ManagerDashboardScreen />;
  }

  if (window.location.pathname === '/memberdashboard') {
    return <MemberDashboardScreen />;
  }

  return <LoginScreen />;
}
