import React from 'react';
import LoginPage from './pages/LoginPage.jsx';
import ManagerDashboardPage from './pages/ManagerDashboardPage.jsx';

export default function App() {
  if (window.location.pathname === '/managerdashboard') {
    return <ManagerDashboardPage />;
  }

  return <LoginPage />;
}
