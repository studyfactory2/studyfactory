import { useState, type ChangeEvent, type FormEvent } from 'react';
import type { LoginFormState } from '../../hooks/useLoginScreen';
import { InstallGuide } from './InstallGuide';
import { EyeIcon, EyeOffIcon, LoginField } from './LoginField';

type LoginFormProps = {
  form: LoginFormState;
  loading: boolean;
  onChange: (key: keyof LoginFormState) => (event: ChangeEvent<HTMLInputElement>) => void;
  onSignupClick: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function LoginForm({ form, loading, onChange, onSignupClick, onSubmit }: LoginFormProps) {
  const [passwordVisible, setPasswordVisible] = useState(false);

  return (
    <form className="form" onSubmit={onSubmit}>
      <LoginField icon="user" label="이름">
        <input
          type="text"
          placeholder="이름 (예: 김공장)"
          value={form.loginName}
          onChange={onChange('loginName')}
          autoComplete="username"
        />
      </LoginField>
      <LoginField
        icon="lock"
        label="비밀번호"
        trailingButton={(
          <button
            className="login-field-trailing-icon"
            type="button"
            aria-label={passwordVisible ? '비밀번호 숨기기' : '비밀번호 보기'}
            onClick={() => setPasswordVisible((visible) => !visible)}
          >
            {passwordVisible ? <EyeOffIcon /> : <EyeIcon />}
          </button>
        )}
      >
        <input
          type={passwordVisible ? 'text' : 'password'}
          placeholder="비밀번호 (4자리)"
          value={form.loginPassword}
          onChange={onChange('loginPassword')}
          maxLength={4}
          inputMode="numeric"
          autoComplete="current-password"
        />
      </LoginField>
      <button className="primary-button" type="submit" disabled={loading}>
        {loading ? '로그인 중...' : '로그인'}
      </button>
      <button className="ghost-button" type="button" onClick={onSignupClick}>
        <span className="button-icon user-plus-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" focusable="false">
            <circle cx="9" cy="8" r="3.4" />
            <path d="M3.5 19a5.5 5.5 0 0 1 11 0" />
            <path d="M18 8v6" />
            <path d="M15 11h6" />
          </svg>
        </span>
        사원등록
      </button>
      <InstallGuide />
    </form>
  );
}
