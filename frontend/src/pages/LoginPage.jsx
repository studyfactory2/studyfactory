import React from 'react';
import { useEffect } from 'react';
import { useState } from 'react';
import { apiRequest } from '../api/client.js';

const INITIAL_FORM = {
  loginName: '',
  loginPassword: '',
  signupName: '',
  signupBranchId: '',
  signupPassword: '',
  signupPasswordConfirm: '',
};

export default function LoginPage() {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState(INITIAL_FORM);
  const [verifiedMember, setVerifiedMember] = useState(null);
  const [branches, setBranches] = useState([]);
  const [branchLoading, setBranchLoading] = useState(false);
  const [branchLoaded, setBranchLoaded] = useState(false);
  const [branchDropdownOpen, setBranchDropdownOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);

  useEffect(() => {
    if (mode !== 'verify' || branches.length > 0 || branchLoading || branchLoaded) {
      return;
    }

    loadBranches();
  }, [mode, branches.length, branchLoading, branchLoaded]);

  const changeForm = (key) => (event) => {
    setForm((current) => ({ ...current, [key]: event.target.value }));
  };

  const selectBranch = (branchId) => {
    setForm((current) => ({ ...current, signupBranchId: String(branchId) }));
    setBranchDropdownOpen(false);
  };

  const showMessage = (text, type = 'info') => {
    setMessage({ text, type });
  };

  const openLogin = () => {
    setMode('login');
    setBranchDropdownOpen(false);
    setMessage(null);
  };

  const openVerify = () => {
    setMode('verify');
    setBranchDropdownOpen(false);
    setMessage(null);
  };

  const loadBranches = async () => {
    setBranchLoading(true);
    try {
      const branchResponses = await apiRequest('/api/branches');
      setBranches(branchResponses);
      if (branchResponses.length > 0) {
        setForm((current) => {
          if (current.signupBranchId) {
            return current;
          }

          return { ...current, signupBranchId: String(branchResponses[0].id) };
        });
      }
    } catch {
      showMessage('지점 목록을 불러오지 못했습니다.', 'error');
    } finally {
      setBranchLoading(false);
      setBranchLoaded(true);
    }
  };

  const handleLogin = async (event) => {
    event.preventDefault();
    if (!form.loginName.trim() || !form.loginPassword.trim()) {
      showMessage('이름과 비밀번호를 입력해주세요.', 'error');
      return;
    }

    setLoading(true);
    setMessage(null);
    try {
      const tokens = await apiRequest('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          name: form.loginName.trim(),
          password: form.loginPassword,
        }),
      });
      const payload = decodeTokenPayload(tokens.accessToken);
      localStorage.setItem('accessToken', tokens.accessToken);
      localStorage.setItem('refreshToken', tokens.refreshToken);
      localStorage.setItem('memberName', payload.name || form.loginName.trim());
      localStorage.setItem('memberId', payload.sub || '');
      localStorage.setItem('branchId', payload.branchId || '');

      window.location.href = '/memberdashboard';
    } catch (error) {
      setLoading(false);
      showMessage(error.message || '로그인에 실패했습니다.', 'error');
    }
  };

  const handleVerifySignup = async (event) => {
    event.preventDefault();
    if (!form.signupName.trim() || !form.signupBranchId) {
      showMessage('이름과 지점을 입력해주세요.', 'error');
      return;
    }

    setLoading(true);
    setMessage(null);
    try {
      const member = await apiRequest('/api/members/pre-registration/verify', {
        method: 'POST',
        body: JSON.stringify({
          name: form.signupName.trim(),
          branchId: Number(form.signupBranchId),
        }),
      });
      setVerifiedMember(member);
      setMode('password');
      showMessage('사전등록 정보를 확인했습니다. 사용할 비밀번호를 설정해주세요.', 'success');
    } catch (error) {
      showMessage(error.message || '사전등록 정보를 찾지 못했습니다.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (event) => {
    event.preventDefault();
    if (!form.signupPassword || form.signupPassword.length !== 4) {
      showMessage('비밀번호는 4자리로 입력해주세요.', 'error');
      return;
    }
    if (form.signupPassword !== form.signupPasswordConfirm) {
      showMessage('비밀번호 확인이 일치하지 않습니다.', 'error');
      return;
    }

    setLoading(true);
    setMessage(null);
    try {
      await apiRequest('/api/members/signup', {
        method: 'POST',
        body: JSON.stringify({
          name: verifiedMember.name,
          branchId: verifiedMember.branchId,
          password: form.signupPassword,
        }),
      });
      setForm((current) => ({
        ...current,
        loginName: verifiedMember.name,
        loginPassword: '',
        signupPassword: '',
        signupPasswordConfirm: '',
      }));
      setVerifiedMember(null);
      setMode('login');
      showMessage('가입이 완료되었습니다! 로그인해 주세요.', 'success');
    } catch (error) {
      showMessage(error.message || '가입에 실패했습니다.', 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      <BrandPanel />
      <section className="login-card">
        <div className="card-header">
          <p>자격증공장</p>
          <h2>{mode === 'login' ? '로그인' : '사원등록'}</h2>
        </div>
        {message && <p className={`message ${message.type}`}>{message.text}</p>}
        {mode === 'login' && (
          <LoginForm
            form={form}
            loading={loading}
            onChange={changeForm}
            onSubmit={handleLogin}
            onSignupClick={openVerify}
          />
        )}
        {mode === 'verify' && (
          <VerifyForm
            form={form}
            branches={branches}
            branchLoading={branchLoading}
            branchDropdownOpen={branchDropdownOpen}
            loading={loading}
            onChange={changeForm}
            onBranchDropdownToggle={() => setBranchDropdownOpen((current) => !current)}
            onBranchSelect={selectBranch}
            onSubmit={handleVerifySignup}
            onLoginClick={openLogin}
          />
        )}
        {mode === 'password' && (
          <PasswordForm
            form={form}
            member={verifiedMember}
            loading={loading}
            onChange={changeForm}
            onSubmit={handleSignup}
            onBackClick={openVerify}
          />
        )}
      </section>
    </main>
  );
}

function BrandPanel() {
  return (
    <section className="brand-panel" aria-label="자격증공장">
      <img className="brand-character" src="/studyfactory-character.png" alt="Study Factory 캐릭터" />
      <p className="eyebrow">Study Factory</p>
    </section>
  );
}

function LoginForm({ form, loading, onChange, onSubmit, onSignupClick }) {
  return (
    <form className="form" onSubmit={onSubmit}>
      <Field label="이름">
        <input
          type="text"
          placeholder="이름 (예: 김공장)"
          value={form.loginName}
          onChange={onChange('loginName')}
          autoComplete="username"
        />
      </Field>
      <Field label="비밀번호">
        <input
          type="password"
          placeholder="비밀번호 (4자리)"
          value={form.loginPassword}
          onChange={onChange('loginPassword')}
          maxLength={4}
          inputMode="numeric"
          autoComplete="current-password"
        />
      </Field>
      <button className="primary-button" type="submit" disabled={loading}>
        {loading ? '로그인 중...' : '로그인'}
      </button>
      <button className="ghost-button" type="button" onClick={onSignupClick}>
        사원등록
      </button>
      <InstallGuide />
    </form>
  );
}

function VerifyForm({
  form,
  branches,
  branchLoading,
  branchDropdownOpen,
  loading,
  onChange,
  onBranchDropdownToggle,
  onBranchSelect,
  onSubmit,
  onLoginClick,
}) {
  const selectedBranch = branches.find((branch) => String(branch.id) === String(form.signupBranchId));

  return (
    <form className="form" onSubmit={onSubmit}>
      <Field label="이름">
        <input
          type="text"
          placeholder="이름을 입력하세요"
          value={form.signupName}
          onChange={onChange('signupName')}
          autoComplete="name"
        />
      </Field>
      <Field label="지점">
        <CustomBranchDropdown
          branches={branches}
          disabled={branchLoading || branches.length === 0}
          loading={branchLoading}
          open={branchDropdownOpen}
          selectedBranch={selectedBranch}
          onToggle={onBranchDropdownToggle}
          onSelect={onBranchSelect}
        />
      </Field>
      <button className="primary-button" type="submit" disabled={loading}>
        {loading ? '확인 중...' : '확인'}
      </button>
      <button className="ghost-button" type="button" onClick={onLoginClick}>
        로그인으로 돌아가기
      </button>
    </form>
  );
}

function CustomBranchDropdown({ branches, disabled, loading, open, selectedBranch, onToggle, onSelect }) {
  const buttonText = loading ? '지점 목록을 불러오는 중...' : selectedBranch?.name || '지점을 선택해주세요';

  return (
    <div className="custom-select">
      <button
        className={`custom-select-button${open ? ' open' : ''}${!selectedBranch ? ' placeholder' : ''}`}
        type="button"
        disabled={disabled}
        onClick={onToggle}
        aria-label={buttonText}
        aria-expanded={open}
        aria-haspopup="listbox"
        data-testid="branch-dropdown-button"
      >
        <span>{buttonText}</span>
      </button>
      {open && (
        <div className="custom-select-menu" role="listbox">
          {branches.map((branch) => (
            <button
              className={`custom-select-option${selectedBranch?.id === branch.id ? ' selected' : ''}`}
              key={branch.id}
              type="button"
              role="option"
              aria-selected={selectedBranch?.id === branch.id}
              aria-label={branch.name}
              data-testid={`branch-option-${branch.id}`}
              onClick={() => onSelect(branch.id)}
            >
              {branch.name}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function PasswordForm({ form, member, loading, onChange, onSubmit, onBackClick }) {
  return (
    <form className="form" onSubmit={onSubmit}>
      <h2>비밀번호 설정</h2>
      <div className="summary">
        <strong>가입 정보</strong>
        <dl>
          <SummaryItem label="이름" value={member?.name} />
          <SummaryItem label="지점" value={member?.branchId} />
          <SummaryItem label="좌석" value={member?.seatNumber} />
          <SummaryItem label="입사예정일" value={member?.expectedJoinDate} />
        </dl>
      </div>
      <Field label="비밀번호">
        <input
          type="password"
          placeholder="사용하실 비밀번호 (4자리)"
          value={form.signupPassword}
          onChange={onChange('signupPassword')}
          maxLength={4}
          inputMode="numeric"
          autoComplete="new-password"
        />
      </Field>
      <Field label="비밀번호 확인">
        <input
          type="password"
          placeholder="비밀번호 확인"
          value={form.signupPasswordConfirm}
          onChange={onChange('signupPasswordConfirm')}
          maxLength={4}
          inputMode="numeric"
          autoComplete="new-password"
        />
      </Field>
      <button className="primary-button" type="submit" disabled={loading}>
        {loading ? '등록 중...' : '가입 완료'}
      </button>
      <button className="ghost-button" type="button" onClick={onBackClick}>
        이전으로
      </button>
    </form>
  );
}

function Field({ label, children }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}

function SummaryItem({ label, value }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value || '-'}</dd>
    </div>
  );
}

function InstallGuide() {
  return (
    <details className="install-guide">
      <summary>홈 화면에 추가하기</summary>
      <div>
        <h3>홈 화면에 추가</h3>
        <p>
          Safari 또는 Chrome의 공유 메뉴에서 <strong>홈 화면에 추가</strong>를 선택해주세요.
        </p>
      </div>
    </details>
  );
}

function decodeTokenPayload(token) {
  try {
    const payload = token.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(escape(atob(normalized))));
  } catch {
    return {};
  }
}
