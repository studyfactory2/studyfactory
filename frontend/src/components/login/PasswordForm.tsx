import type { ChangeEvent, FormEvent } from 'react';
import type { LoginFormState } from '../../hooks/useLoginScreen';
import type { Branch, PreRegistrationVerifyResponse } from '../../types/domain';
import { LoginField } from './LoginField';

type PasswordFormProps = {
  branches: Branch[];
  form: LoginFormState;
  loading: boolean;
  member: PreRegistrationVerifyResponse | null;
  onBackClick: () => void;
  onChange: (key: keyof LoginFormState) => (event: ChangeEvent<HTMLInputElement>) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function PasswordForm({ branches, form, loading, member, onBackClick, onChange, onSubmit }: PasswordFormProps) {
  const branchName = branches.find((branch) => branch.id === member?.branchId)?.name || member?.branchId;

  return (
    <form className="form" onSubmit={onSubmit}>
      <h2>비밀번호 설정</h2>
      <div className="summary">
        <strong>가입 정보</strong>
        <dl>
          <SummaryItem label="이름" value={member?.name} />
          <SummaryItem label="지점" value={branchName} />
          <SummaryItem label="좌석" value={member?.seatNumber} />
          <SummaryItem label="입사예정일" value={member?.expectedJoinDate} />
        </dl>
      </div>
      <LoginField label="비밀번호">
        <input
          type="password"
          placeholder="사용하실 비밀번호 (4자리)"
          value={form.signupPassword}
          onChange={onChange('signupPassword')}
          maxLength={4}
          inputMode="numeric"
          autoComplete="new-password"
        />
      </LoginField>
      <LoginField label="비밀번호 확인">
        <input
          type="password"
          placeholder="비밀번호 확인"
          value={form.signupPasswordConfirm}
          onChange={onChange('signupPasswordConfirm')}
          maxLength={4}
          inputMode="numeric"
          autoComplete="new-password"
        />
      </LoginField>
      <button className="primary-button" type="submit" disabled={loading}>
        {loading ? '등록 중...' : '가입 완료'}
      </button>
      <button className="ghost-button" type="button" onClick={onBackClick}>
        이전으로
      </button>
    </form>
  );
}

function SummaryItem({ label, value }: { label: string; value?: number | string | null }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value || '-'}</dd>
    </div>
  );
}
