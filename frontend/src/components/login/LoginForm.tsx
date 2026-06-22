import type { ChangeEvent, FormEvent } from 'react';
import type { LoginFormState } from '../../hooks/useLoginScreen';
import { InstallGuide } from './InstallGuide';
import { LoginField } from './LoginField';

type LoginFormProps = {
  form: LoginFormState;
  loading: boolean;
  onChange: (key: keyof LoginFormState) => (event: ChangeEvent<HTMLInputElement>) => void;
  onSignupClick: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function LoginForm({ form, loading, onChange, onSignupClick, onSubmit }: LoginFormProps) {
  return (
    <form className="form" onSubmit={onSubmit}>
      <LoginField label="이름">
        <input
          type="text"
          placeholder="이름 (예: 김공장)"
          value={form.loginName}
          onChange={onChange('loginName')}
          autoComplete="username"
        />
      </LoginField>
      <LoginField label="비밀번호">
        <input
          type="password"
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
        사원등록
      </button>
      <InstallGuide />
    </form>
  );
}
