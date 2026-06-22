import { getAdminMenuLabel, type AdminMenuId } from '../../constants/adminMenus';

type PlaceholderPanelProps = {
  currentView: AdminMenuId;
};

export function PlaceholderPanel({ currentView }: PlaceholderPanelProps) {
  const label = getAdminMenuLabel(currentView);

  return (
    <div className="placeholder-panel">
      <strong>{label}</strong>
      <p>화면 연결 영역입니다.</p>
    </div>
  );
}
