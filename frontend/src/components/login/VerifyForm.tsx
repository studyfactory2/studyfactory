import type { ChangeEvent, FormEvent } from 'react';
import { Dropdown } from '../common/Dropdown';
import type { Branch, PreRegistrationVerifyResponse } from '../../types/domain';
import type { LoginFormState } from '../../hooks/useLoginScreen';
import { LoginField } from './LoginField';

type VerifyFormProps = {
  branchDropdownOpen: boolean;
  branchLoading: boolean;
  branches: Branch[];
  form: LoginFormState;
  loading: boolean;
  verifiedMembers: PreRegistrationVerifyResponse[];
  onBranchDropdownToggle: () => void;
  onBranchSelect: (branchId: number) => void;
  onChange: (key: keyof LoginFormState) => (event: ChangeEvent<HTMLInputElement>) => void;
  onCandidateSelect: (member: PreRegistrationVerifyResponse) => void;
  onLoginClick: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function VerifyForm({
  branchDropdownOpen,
  branchLoading,
  branches,
  form,
  loading,
  verifiedMembers,
  onBranchDropdownToggle,
  onBranchSelect,
  onChange,
  onCandidateSelect,
  onLoginClick,
  onSubmit,
}: VerifyFormProps) {
  const selectedBranch = branches.find((branch) => String(branch.id) === String(form.signupBranchId));
  const buttonText = branchLoading ? '지점 목록을 불러오는 중...' : selectedBranch?.name || '지점을 선택해주세요';

  return (
    <form className="form" onSubmit={onSubmit}>
      <LoginField label="이름">
        <input
          type="text"
          placeholder="이름을 입력하세요"
          value={form.signupName}
          onChange={onChange('signupName')}
          autoComplete="name"
        />
      </LoginField>
      <LoginField label="지점">
        <Dropdown
          classNamePrefix="custom-select"
          disabled={branchLoading || branches.length === 0}
          label="지점"
          open={branchDropdownOpen}
          options={branches.map((branch) => ({
            value: String(branch.id),
            label: branch.name,
            testId: `branch-option-${branch.id}`,
          }))}
          placeholderClass={!selectedBranch}
          selectedOption={{ value: selectedBranch ? String(selectedBranch.id) : '', label: buttonText }}
          testId="branch-dropdown-button"
          onToggle={onBranchDropdownToggle}
          onSelect={(value) => onBranchSelect(Number(value))}
        />
      </LoginField>
      <button className="primary-button" type="submit" disabled={loading}>
        {loading ? '확인 중...' : '확인'}
      </button>
      {verifiedMembers.length > 1 && (
        <div className="signup-candidate-list">
          <strong>가입할 사원 선택</strong>
          {verifiedMembers.map((member) => (
            <button
              key={member.memberId}
              className="signup-candidate-card"
              type="button"
              onClick={() => onCandidateSelect(member)}
            >
              <span>{member.displayName || member.name}</span>
              <small>
                좌석 {member.seatNumber || '미정'} · 입사예정일 {member.expectedJoinDate || '-'}
              </small>
            </button>
          ))}
        </div>
      )}
      <button className="ghost-button" type="button" onClick={onLoginClick}>
        로그인으로 돌아가기
      </button>
    </form>
  );
}
