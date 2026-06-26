import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { apiRequest } from '../../api/client';
import { Dropdown, type DropdownOption } from '../common/Dropdown';
import type { Branch, MemberRole, Certification, PreRegistrationResponse } from '../../types/domain';

type PreRegistrationPanelProps = {
  branches: Branch[];
  certifications: Certification[];
};

type PreRegistrationFormState = {
  branchId: string;
  role: MemberRole;
  name: string;
  seatNumber: string;
  expectedJoinDate: string;
  certification: string;
  drinkSetting: string;
  drinkNote: string;
  memberNote: string;
};

const FALLBACK_BRANCH: Branch = { id: 1, name: '망미점' };
const ROLE_OPTIONS: Array<{ value: MemberRole; label: string }> = [
  { value: 'MEMBER', label: '회원' },
  { value: 'STAFF', label: '스탭' },
  { value: 'ADMIN', label: '관리자' },
];

export function PreRegistrationPanel({ branches, certifications }: PreRegistrationPanelProps) {
  const branchOptions = branches.length > 0 ? branches : [FALLBACK_BRANCH];
  const [selectedBranchId, setSelectedBranchId] = useState('');
  const [selectedRole, setSelectedRole] = useState<MemberRole>('MEMBER');
  const [name, setName] = useState('');
  const [seatNumber, setSeatNumber] = useState('');
  const [expectedJoinDate, setExpectedJoinDate] = useState('');
  const [certification, setCertification] = useState('');
  const [drinkSetting, setDrinkSetting] = useState('');
  const [drinkNote, setDrinkNote] = useState('');
  const [memberNote, setMemberNote] = useState('');
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [modalMessage, setModalMessage] = useState('');
  const [pendingMembers, setPendingMembers] = useState<PreRegistrationResponse[]>([]);
  const [pendingLoading, setPendingLoading] = useState(false);
  const [editingMemberId, setEditingMemberId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<PreRegistrationFormState | null>(null);
  const [editBranchOpen, setEditBranchOpen] = useState(false);
  const [editRoleOpen, setEditRoleOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<PreRegistrationResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [editingSubmitting, setEditingSubmitting] = useState(false);
  const [branchOpen, setBranchOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);

  useEffect(() => {
    if (selectedBranchId || branchOptions.length === 0) {
      return;
    }

    setSelectedBranchId(String(branchOptions[0].id));
  }, [branchOptions, selectedBranchId]);

  useEffect(() => {
    void loadPendingMembers();
  }, []);

  const selectedBranch = branchOptions.find((branch) => String(branch.id) === selectedBranchId) || branchOptions[0];
  const selectedRoleOption = ROLE_OPTIONS.find((role) => role.value === selectedRole) || ROLE_OPTIONS[0];

  const loadPendingMembers = async () => {
    setPendingLoading(true);
    try {
      const responses = await apiRequest<PreRegistrationResponse[]>('/api/pre-registrations/pending');
      setPendingMembers(responses);
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '등록 대기현황을 불러오지 못했습니다.' });
    } finally {
      setPendingLoading(false);
    }
  };

  const submitPreRegistration = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!selectedBranchId || !name.trim() || !expectedJoinDate) {
      setMessage({ type: 'error', text: '지점, 이름, 입사예정일을 입력해주세요.' });
      return;
    }

    setSubmitting(true);
    setMessage(null);
    try {
      await apiRequest<PreRegistrationResponse>('/api/pre-registrations', {
        method: 'POST',
        body: JSON.stringify(toRequestBody({
          branchId: selectedBranchId,
          role: selectedRole,
          name,
          seatNumber,
          expectedJoinDate,
          certification,
          drinkSetting,
          drinkNote,
          memberNote,
        })),
      });
      resetCreateForm();
      setModalMessage('사전 사원등록이 완료되었습니다.');
      await loadPendingMembers();
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '사전 사원등록에 실패했습니다.' });
    } finally {
      setSubmitting(false);
    }
  };

  const editPendingMember = (member: PreRegistrationResponse) => {
    setEditingMemberId(member.id);
    setEditDraft({
      branchId: String(member.branchId),
      role: member.role,
      name: member.name,
      seatNumber: member.seatNumber ? String(member.seatNumber) : '',
      expectedJoinDate: member.expectedJoinDate || '',
      certification: findCertification(certifications, member.certificationId),
      drinkSetting: member.drinkSetting || '',
      drinkNote: member.drinkNote || '',
      memberNote: member.memberNote || '',
    });
    setEditBranchOpen(false);
    setEditRoleOpen(false);
    setMessage(null);
  };

  const submitInlineEdit = async (memberId: number) => {
    if (!editDraft) {
      return;
    }

    if (!editDraft.branchId || !editDraft.name.trim() || !editDraft.expectedJoinDate) {
      setMessage({ type: 'error', text: '지점, 이름, 입사예정일을 입력해주세요.' });
      return;
    }

    setEditingSubmitting(true);
    setMessage(null);
    try {
      await apiRequest<PreRegistrationResponse>(`/api/pre-registrations/${memberId}`, {
        method: 'PATCH',
        body: JSON.stringify(toRequestBody(editDraft)),
      });
      resetInlineEdit();
      setModalMessage('사전 사원등록이 수정되었습니다.');
      await loadPendingMembers();
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '사전 사원등록 수정에 실패했습니다.' });
    } finally {
      setEditingSubmitting(false);
    }
  };

  const deletePendingMember = async () => {
    if (!deleteTarget) {
      return;
    }

    setMessage(null);
    try {
      await apiRequest<void>(`/api/pre-registrations/${deleteTarget.id}`, {
        method: 'DELETE',
      });
      setDeleteTarget(null);
      setModalMessage('사전 사원등록이 삭제되었습니다.');
      if (editingMemberId === deleteTarget.id) {
        resetInlineEdit();
      }
      await loadPendingMembers();
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '사전 사원등록 삭제에 실패했습니다.' });
    }
  };

  const toRequestBody = (formState: PreRegistrationFormState) => ({
    branchId: Number(formState.branchId),
    name: formState.name.trim(),
    role: formState.role,
    seatNumber: formState.seatNumber ? Number(formState.seatNumber) : null,
    expectedJoinDate: formState.expectedJoinDate,
    certification: formState.certification.trim() || null,
    drinkSetting: formState.drinkSetting.trim(),
    drinkNote: formState.drinkNote.trim(),
    memberNote: formState.memberNote.trim(),
  });

  const resetCreateForm = () => {
    setName('');
    setSeatNumber('');
    setExpectedJoinDate('');
    setCertification('');
    setDrinkSetting('');
    setDrinkNote('');
    setMemberNote('');
  };

  const resetInlineEdit = () => {
    setEditingMemberId(null);
    setEditDraft(null);
    setEditBranchOpen(false);
    setEditRoleOpen(false);
  };

  const changeEditDraft = (field: keyof PreRegistrationFormState, value: string) => {
    setEditDraft((current) => {
      if (!current) {
        return current;
      }

      return { ...current, [field]: value };
    });
  };

  return (
    <div className="pre-register-panel">
      <header className="panel-title">
        <button type="button" aria-label="뒤로가기" onClick={() => { window.history.pushState(null, '', '/managerdashboard?view=grid'); window.dispatchEvent(new PopStateEvent('popstate')); }}>
          ‹
        </button>
        <h1>사원 사전 등록</h1>
      </header>
      <form className="pre-register-form" onSubmit={submitPreRegistration}>
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
          <input type="text" placeholder="이름을 입력하여 주세요." value={name} onChange={(event) => setName(event.target.value)} />
        </label>
        <label>
          <span>좌석 번호</span>
          <input
            type="number"
            min="1"
            placeholder="번호"
            value={seatNumber}
            onChange={(event) => setSeatNumber(event.target.value.replace(/[^0-9]/g, ''))}
          />
        </label>
        <label>
          <span>입사예정일</span>
          <input type="date" value={expectedJoinDate} onChange={(event) => setExpectedJoinDate(event.target.value)} />
        </label>
        <label>
          <span>자격증</span>
          <input
            list="certification-options"
            type="text"
            placeholder="자격증 입력 또는 선택"
            value={certification}
            onChange={(event) => setCertification(event.target.value)}
          />
          <datalist id="certification-options">
            {certifications.map((certification) => (
              <option key={certification.id} value={certification.content} />
            ))}
          </datalist>
        </label>
        <label className="full-field">
          <span>음료 설정 (선택사항)</span>
          <input
            type="text"
            placeholder="예: 선식, 텀블러 아아"
            value={drinkSetting}
            onChange={(event) => setDrinkSetting(event.target.value)}
          />
        </label>
        <label className="full-field">
          <span>음료 참고사항</span>
          <textarea placeholder="음료 참고사항" rows={1} value={drinkNote} onChange={(event) => setDrinkNote(event.target.value)} />
        </label>
        <label className="full-field">
          <span>회원 참고사항</span>
          <textarea placeholder="참고사항을 입력하세요." rows={1} value={memberNote} onChange={(event) => setMemberNote(event.target.value)} />
        </label>
        {message && <p className={`pre-register-message ${message.type}`}>{message.text}</p>}
        <button className="register-submit" type="submit" disabled={submitting}>
          {submitting ? '등록 중' : '등록하기'}
        </button>
      </form>
      <section className="waiting-panel">
        <h2>등록 대기 현황 ({pendingMembers.length})</h2>
        {pendingLoading ? (
          <p>등록 대기현황을 불러오는 중입니다.</p>
        ) : pendingMembers.length === 0 ? (
          <p>대기 중인 인원이 없습니다.</p>
        ) : (
          <div className="waiting-member-list">
            {pendingMembers.map((member) => (
              <article className="waiting-member-card" key={member.id}>
                {editingMemberId === member.id && editDraft ? (
                  <div className="waiting-edit-form">
                    <div className="form-field">
                      <span>지점</span>
                      <Dropdown
                        classNamePrefix="form-dropdown"
                        label="지점"
                        open={editBranchOpen}
                        options={toBranchOptions(branchOptions)}
                        selectedOption={{
                          value: editDraft.branchId,
                          label: branchOptions.find((branch) => String(branch.id) === editDraft.branchId)?.name || '지점을 선택해주세요',
                        }}
                        onToggle={() => {
                          setEditBranchOpen((current) => !current);
                          setEditRoleOpen(false);
                        }}
                        onSelect={(value) => {
                          changeEditDraft('branchId', value);
                          setEditBranchOpen(false);
                        }}
                      />
                    </div>
                    <div className="form-field">
                      <span>사원 구분</span>
                      <Dropdown
                        classNamePrefix="form-dropdown"
                        label="사원 구분"
                        open={editRoleOpen}
                        options={ROLE_OPTIONS}
                        selectedOption={ROLE_OPTIONS.find((role) => role.value === editDraft.role) || ROLE_OPTIONS[0]}
                        onToggle={() => {
                          setEditRoleOpen((current) => !current);
                          setEditBranchOpen(false);
                        }}
                        onSelect={(value) => {
                          changeEditDraft('role', value);
                          setEditRoleOpen(false);
                        }}
                      />
                    </div>
                    <label className="wide-field">
                      <span>이름 (로그인 ID)</span>
                      <input value={editDraft.name} onChange={(event) => changeEditDraft('name', event.target.value)} />
                    </label>
                    <label>
                      <span>좌석 번호</span>
                      <input
                        inputMode="numeric"
                        value={editDraft.seatNumber}
                        onChange={(event) => changeEditDraft('seatNumber', event.target.value.replace(/[^0-9]/g, ''))}
                      />
                    </label>
                    <label>
                      <span>입사예정일</span>
                      <input type="date" value={editDraft.expectedJoinDate} onChange={(event) => changeEditDraft('expectedJoinDate', event.target.value)} />
                    </label>
                    <label>
                      <span>자격증</span>
                      <input
                        list="certification-options"
                        value={editDraft.certification}
                        onChange={(event) => changeEditDraft('certification', event.target.value)}
                      />
                    </label>
                    <label className="full-field">
                      <span>음료 설정 (선택사항)</span>
                      <input value={editDraft.drinkSetting} onChange={(event) => changeEditDraft('drinkSetting', event.target.value)} />
                    </label>
                    <label className="full-field">
                      <span>음료 참고사항</span>
                      <textarea rows={1} value={editDraft.drinkNote} onChange={(event) => changeEditDraft('drinkNote', event.target.value)} />
                    </label>
                    <label className="full-field">
                      <span>회원 참고사항</span>
                      <textarea rows={1} value={editDraft.memberNote} onChange={(event) => changeEditDraft('memberNote', event.target.value)} />
                    </label>
                    <div className="waiting-edit-actions">
                      <button type="button" onClick={resetInlineEdit}>취소</button>
                      <button type="button" disabled={editingSubmitting} onClick={() => submitInlineEdit(member.id)}>
                        {editingSubmitting ? '저장 중' : '저장'}
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <div>
                      <strong>{member.name}</strong>
                      <span>{findBranchName(branchOptions, member.branchId)} · {toRoleLabel(member.role)}</span>
                      <small>
                        {member.expectedJoinDate || '입사예정일 없음'}
                        {member.seatNumber ? ` · ${member.seatNumber}번` : ''}
                      </small>
                    </div>
                    <div className="waiting-member-actions">
                      <button type="button" onClick={() => editPendingMember(member)}>수정</button>
                      <button type="button" onClick={() => setDeleteTarget(member)}>삭제</button>
                    </div>
                  </>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
      {deleteTarget && (
        <div className="pre-register-modal-backdrop" role="presentation">
          <section className="pre-register-modal" role="dialog" aria-modal="true" aria-labelledby="pre-register-delete-title">
            <h2 id="pre-register-delete-title">삭제 확인</h2>
            <p>{deleteTarget.name}님의 사전등록 정보를 삭제하시겠습니까?</p>
            <div className="pre-register-modal-actions">
              <button className="pre-register-modal-cancel" type="button" onClick={() => setDeleteTarget(null)}>닫기</button>
              <button className="pre-register-modal-danger" type="button" onClick={deletePendingMember}>삭제</button>
            </div>
          </section>
        </div>
      )}
      {modalMessage && (
        <div className="pre-register-modal-backdrop" role="presentation">
          <section className="pre-register-modal" role="alertdialog" aria-modal="true" aria-labelledby="pre-register-success-title">
            <h2 id="pre-register-success-title">완료</h2>
            <p>{modalMessage}</p>
            <button type="button" onClick={() => setModalMessage('')}>확인</button>
          </section>
        </div>
      )}
    </div>
  );
}

function toBranchOptions(branches: Branch[]): DropdownOption[] {
  return branches.map((branch) => ({ value: String(branch.id), label: branch.name }));
}

function findBranchName(branches: Branch[], branchId: number) {
  return branches.find((branch) => branch.id === branchId)?.name || `지점 ${branchId}`;
}

function findCertification(certifications: Certification[], certificationId?: number | null) {
  return certifications.find((certification) => certification.id === certificationId)?.content || '';
}

function toRoleLabel(role: MemberRole) {
  return ROLE_OPTIONS.find((option) => option.value === role)?.label || role;
}
