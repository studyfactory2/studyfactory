import { LoginScreen } from '../screens/LoginScreen';
import { ManagerDashboardScreen } from '../screens/ManagerDashboardScreen';

export function AppRoutes() {
  if (window.location.pathname === '/managerdashboard') {
    return <ManagerDashboardScreen />;
  }

  return <LoginScreen />;
}
