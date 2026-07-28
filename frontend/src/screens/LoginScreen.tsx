import { LoginForm } from '../components/login/LoginForm';
import { PasswordForm } from '../components/login/PasswordForm';
import { VerifyForm } from '../components/login/VerifyForm';
import { useLoginScreen } from '../hooks/useLoginScreen';
import { LoginLayout } from '../layouts/LoginLayout';

export function LoginScreen() {
  const screen = useLoginScreen();

  if (screen.sessionRestoring) {
    return (
      <LoginLayout>
        <div className="session-restoring" role="status">로그인 정보를 확인하고 있어요.</div>
      </LoginLayout>
    );
  }

  return (
    <LoginLayout>
      {screen.mode !== 'login' && (
        <div className="card-header">
          <h2>사원등록</h2>
        </div>
      )}
      {screen.message && <p className={`message ${screen.message.type}`}>{screen.message.text}</p>}
      {screen.mode === 'login' && (
        <LoginForm
          form={screen.form}
          loading={screen.loading}
          onChange={screen.changeForm}
          onSubmit={screen.handleLogin}
          onSignupClick={screen.openVerify}
        />
      )}
      {screen.mode === 'verify' && (
        <VerifyForm
          form={screen.form}
          branches={screen.branches}
          branchLoading={screen.branchLoading}
          branchDropdownOpen={screen.branchDropdownOpen}
          loading={screen.loading}
          verifiedMembers={screen.verifiedMembers}
          onChange={screen.changeForm}
          onBranchDropdownToggle={() => screen.setBranchDropdownOpen((current) => !current)}
          onBranchSelect={screen.selectBranch}
          onCandidateSelect={screen.selectVerifiedMember}
          onSubmit={screen.handleVerifySignup}
          onLoginClick={screen.openLogin}
        />
      )}
      {screen.mode === 'password' && (
        <PasswordForm
          branches={screen.branches}
          form={screen.form}
          member={screen.verifiedMember}
          loading={screen.loading}
          onChange={screen.changeForm}
          onSubmit={screen.handleSignup}
          onBackClick={screen.openVerify}
        />
      )}
    </LoginLayout>
  );
}
