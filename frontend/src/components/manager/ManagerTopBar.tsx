import { LogoutIcon } from './LogoutIcon';

export function ManagerTopBar() {
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
    <div className="manager-topbar">
      <button className="round-action" type="button" aria-label="로그아웃" onClick={logout}>
        <LogoutIcon />
      </button>
      <div className="round-logo">
        <img src="/studyfactory-character-transparent.png" alt="자격증공장" />
      </div>
      <button className="round-action" type="button" aria-label="새로고침" onClick={() => window.location.reload()}>
        <span>↻</span>
      </button>
    </div>
  );
}
