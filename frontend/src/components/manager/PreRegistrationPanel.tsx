import { useEffect, useState } from 'react';
import { Dropdown, type DropdownOption } from '../common/Dropdown';
import type { Branch, MemberRole, NameplateContent } from '../../types/domain';

type PreRegistrationPanelProps = {
  branches: Branch[];
  nameplates: NameplateContent[];
};

const FALLBACK_BRANCH: Branch = { id: 1, name: '망미점' };
const ROLE_OPTIONS: Array<{ value: MemberRole; label: string }> = [
  { value: 'MEMBER', label: '회원' },
  { value: 'STAFF', label: '스탭' },
  { value: 'ADMIN', label: '관리자' },
];

export function PreRegistrationPanel({ branches, nameplates }: PreRegistrationPanelProps) {
  const branchOptions = branches.length > 0 ? branches : [FALLBACK_BRANCH];
  const [selectedBranchId, setSelectedBranchId] = useState('');
  const [selectedRole, setSelectedRole] = useState<MemberRole>('MEMBER');
  const [branchOpen, setBranchOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);

  useEffect(() => {
    if (selectedBranchId || branchOptions.length === 0) {
      return;
    }

    setSelectedBranchId(String(branchOptions[0].id));
  }, [branchOptions, selectedBranchId]);

  const selectedBranch = branchOptions.find((branch) => String(branch.id) === selectedBranchId) || branchOptions[0];
  const selectedRoleOption = ROLE_OPTIONS.find((role) => role.value === selectedRole) || ROLE_OPTIONS[0];

  return (
    <div className="pre-register-panel">
      <header className="panel-title">
        <button type="button" aria-label="뒤로가기">
          ‹
        </button>
        <h1>사원 사전 등록</h1>
      </header>
      <form className="pre-register-form">
        <div className="form-field">
          <span>지점</span>
          <Dropdown
            classNamePrefix="form-dropdown"
            label="지점"
            open={branchOpen}
            options={toBranchOptions(branchOptions)}
            selectedOption={{ value: String(selectedBranch?.id), label: selectedBranch?.name || '지점을 선택해주세요' }}
            onToggle={() => {
              setBranchOpen((current) => !current);
              setRoleOpen(false);
            }}
            onSelect={(value) => {
              setSelectedBranchId(value);
              setBranchOpen(false);
            }}
          />
        </div>
        <div className="form-field">
          <span>사원 구분</span>
          <Dropdown
            classNamePrefix="form-dropdown"
            label="사원 구분"
            open={roleOpen}
            options={ROLE_OPTIONS}
            selectedOption={selectedRoleOption}
            onToggle={() => {
              setRoleOpen((current) => !current);
              setBranchOpen(false);
            }}
            onSelect={(value) => {
              setSelectedRole(value as MemberRole);
              setRoleOpen(false);
            }}
          />
        </div>
        <label className="wide-field">
          <span>이름 (로그인 ID)</span>
          <input type="text" placeholder="이름을 입력하여 주세요." />
        </label>
        <label>
          <span>좌석 번호</span>
          <input type="number" placeholder="번호" />
        </label>
        <label>
          <span>입사예정일</span>
          <input type="date" />
        </label>
        <label>
          <span>명패 내용</span>
          <input list="nameplate-options" type="text" placeholder="명패 문구 입력 또는 선택" />
          <datalist id="nameplate-options">
            {nameplates.map((nameplate) => (
              <option key={nameplate.id} value={nameplate.content} />
            ))}
          </datalist>
        </label>
        <label className="full-field">
          <span>음료 설정 (선택사항)</span>
          <input type="text" placeholder="예: 선식, 텀블러 아아" />
        </label>
        <label className="full-field">
          <span>음료 참고사항</span>
          <textarea placeholder="음료 참고사항" rows={1} />
        </label>
        <label className="full-field">
          <span>회원 참고사항</span>
          <textarea placeholder="참고사항을 입력하세요." rows={1} />
        </label>
        <button className="register-submit" type="button">
          등록하기
        </button>
      </form>
      <section className="waiting-panel">
        <h2>등록 대기 현황 (0)</h2>
        <p>대기 중인 인원이 없습니다.</p>
      </section>
    </div>
  );
}

function toBranchOptions(branches: Branch[]): DropdownOption[] {
  return branches.map((branch) => ({ value: String(branch.id), label: branch.name }));
}
