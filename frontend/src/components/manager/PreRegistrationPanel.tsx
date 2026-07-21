import { useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { apiRequest } from '../../api/client';
import { Dropdown, type DropdownOption } from '../common/Dropdown';
import type { Branch, MemberRole, Certification, MemberResponse, PreRegistrationResponse } from '../../types/domain';

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
};

const FALLBACK_BRANCH: Branch = { id: 1, name: '망미점' };
const MAX_SEAT_NUMBER = 102;
const ROLE_OPTIONS: Array<{ value: MemberRole; label: string }> = [
  { value: 'MEMBER', label: '회원' },
  { value: 'STAFF', label: '스탭' },
  { value: 'ADMIN', label: '관리자' },
];
const DRINK_OPTIONS: DropdownOption[] = [
  { value: '선식', label: '선식' },
  { value: '해독쥬스', label: '해독쥬스' },
  { value: '없음', label: '없음' },
];
const NAMEPLATE_OPTIONS = [
  '2026공기업합격 합격자',
  '5급공채',
  '7급공무원',
  '7급우정',
  '9급고용노동',
  '9급교육행정',
  '9급교행',
  '9급세무직',
  '9급수산직',
  '9급일반행정',
  '9급일행',
  '감정평가사',
  '검찰직',
  '경찰',
  '공기업',
  '공무원',
  '관세사',
  '국토교통부 항공교통관제사',
  '노무사',
  '로스쿨',
  '변리사',
  '변호사',
  '보험계리사',
  '부산교통공사',
  '세무사',
  '세무사2차',
  '세무직',
  '소방',
  '손해사정사',
  '수능',
  '약대편입',
  '역사임용',
  '울산대 의예과',
  '은행',
  '일행',
  '주택금융공사',
  '중앙대 전자공학과',
  '초등임용',
  '취업',
  '치의학전문대학원',
  '토목직공무원',
  '편입',
  '한국은행',
  '해양경찰',
  '회계사',
  '회계사2차',
];
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const KOREAN_HOLIDAYS: Record<string, string[]> = {
  '2026': [
    '2026-01-01',
    '2026-02-16',
    '2026-02-17',
    '2026-02-18',
    '2026-03-01',
    '2026-03-02',
    '2026-05-05',
    '2026-05-24',
    '2026-05-25',
    '2026-06-06',
    '2026-08-15',
    '2026-08-17',
    '2026-09-24',
    '2026-09-25',
    '2026-09-26',
    '2026-10-03',
    '2026-10-05',
    '2026-10-09',
    '2026-12-25',
  ],
  '2027': [
    '2027-01-01',
    '2027-02-06',
    '2027-02-07',
    '2027-02-08',
    '2027-03-01',
    '2027-05-05',
    '2027-05-13',
    '2027-06-06',
    '2027-08-15',
    '2027-08-16',
    '2027-09-14',
    '2027-09-15',
    '2027-09-16',
    '2027-10-03',
    '2027-10-04',
    '2027-10-09',
    '2027-10-11',
    '2027-12-25',
  ],
};

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
  const [seatOpen, setSeatOpen] = useState(false);
  const [editSeatOpen, setEditSeatOpen] = useState(false);
  const [joinDateOpen, setJoinDateOpen] = useState(false);
  const [editJoinDateOpen, setEditJoinDateOpen] = useState(false);
  const [joinDateMonth, setJoinDateMonth] = useState(() => startOfMonth(new Date()));
  const [editJoinDateMonth, setEditJoinDateMonth] = useState(() => startOfMonth(new Date()));
  const [drinkOpen, setDrinkOpen] = useState(false);
  const [editDrinkOpen, setEditDrinkOpen] = useState(false);
  const [nameplateOpen, setNameplateOpen] = useState(false);
  const [editNameplateOpen, setEditNameplateOpen] = useState(false);
  const [showCustomDrink, setShowCustomDrink] = useState(false);
  const [showEditCustomDrink, setShowEditCustomDrink] = useState(false);
  const [membersByBranchId, setMembersByBranchId] = useState<Record<string, MemberResponse[]>>({});

  useEffect(() => {
    if (selectedBranchId || branchOptions.length === 0) {
      return;
    }

    setSelectedBranchId(String(branchOptions[0].id));
  }, [branchOptions, selectedBranchId]);

  useEffect(() => {
    void loadPendingMembers();
  }, []);

  useEffect(() => {
    if (!selectedBranchId) {
      return;
    }

    void loadBranchMembers(selectedBranchId);
  }, [selectedBranchId]);

  useEffect(() => {
    if (!editDraft?.branchId) {
      return;
    }

    void loadBranchMembers(editDraft.branchId);
  }, [editDraft?.branchId]);

  const selectedBranch = branchOptions.find((branch) => String(branch.id) === selectedBranchId) || branchOptions[0];
  const selectedRoleOption = ROLE_OPTIONS.find((role) => role.value === selectedRole) || ROLE_OPTIONS[0];
  const availableSeatOptions = useMemo(() => {
    return toAvailableSeatOptions(membersByBranchId[selectedBranchId] || []);
  }, [membersByBranchId, selectedBranchId]);
  const editAvailableSeatOptions = useMemo(() => {
    if (!editDraft) {
      return [];
    }

    return toAvailableSeatOptions(membersByBranchId[editDraft.branchId] || [], editingMemberId);
  }, [editDraft, editingMemberId, membersByBranchId]);
  const nameplateOptions = useMemo(() => {
    return Array.from(new Set([
      ...NAMEPLATE_OPTIONS,
      ...certifications.map((item) => item.content.trim()).filter(Boolean),
    ]));
  }, [certifications]);

  const loadBranchMembers = async (branchId: string) => {
    try {
      const params = new URLSearchParams({ branchId });
      const responses = await apiRequest<MemberResponse[]>(`/api/members?${params.toString()}`);
      setMembersByBranchId((current) => ({ ...current, [branchId]: responses }));
    } catch {
      setMembersByBranchId((current) => ({ ...current, [branchId]: [] }));
    }
  };

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

    if (!selectedBranchId || !name.trim()) {
      setMessage({ type: 'error', text: '지점과 이름을 입력해주세요.' });
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
        })),
      });
      resetCreateForm();
      setModalMessage('사전 사원등록이 완료되었습니다.');
      await loadPendingMembers();
      await loadBranchMembers(selectedBranchId);
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
    });
    setShowEditCustomDrink(toDrinkParts(member.drinkSetting || '').customText.length > 0);
    setEditJoinDateMonth(toDateMonth(member.expectedJoinDate || ''));
    setEditBranchOpen(false);
    setEditRoleOpen(false);
    setEditJoinDateOpen(false);
    setEditDrinkOpen(false);
    setMessage(null);
  };

  const submitInlineEdit = async (memberId: number) => {
    if (!editDraft) {
      return;
    }

    if (!editDraft.branchId || !editDraft.name.trim()) {
      setMessage({ type: 'error', text: '지점과 이름을 입력해주세요.' });
      return;
    }

    setEditingSubmitting(true);
    setMessage(null);
    const editedBranchId = editDraft.branchId;
    try {
      await apiRequest<PreRegistrationResponse>(`/api/pre-registrations/${memberId}`, {
        method: 'PATCH',
        body: JSON.stringify(toRequestBody(editDraft)),
      });
      resetInlineEdit();
      setModalMessage('사전 사원등록이 수정되었습니다.');
      await loadPendingMembers();
      await loadBranchMembers(editedBranchId);
      if (editedBranchId !== selectedBranchId) {
        await loadBranchMembers(selectedBranchId);
      }
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
    const deletedBranchId = String(deleteTarget.branchId);
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
      await loadBranchMembers(deletedBranchId);
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '사전 사원등록 삭제에 실패했습니다.' });
    }
  };

  const toRequestBody = (formState: PreRegistrationFormState) => ({
    branchId: Number(formState.branchId),
    name: formState.name.trim(),
    role: formState.role,
    seatNumber: formState.seatNumber ? Number(formState.seatNumber) : null,
    expectedJoinDate: formState.expectedJoinDate || null,
    certification: formState.certification.trim() || null,
    drinkSetting: formState.drinkSetting.trim(),
    drinkNote: formState.drinkNote.trim(),
  });

  const resetCreateForm = () => {
    setName('');
    setSeatNumber('');
    setExpectedJoinDate('');
    setCertification('');
    setDrinkSetting('');
    setDrinkNote('');
    setShowCustomDrink(false);
    setDrinkOpen(false);
    setNameplateOpen(false);
    setJoinDateOpen(false);
  };

  const resetInlineEdit = () => {
    setEditingMemberId(null);
    setEditDraft(null);
    setEditBranchOpen(false);
    setEditRoleOpen(false);
    setEditSeatOpen(false);
    setEditDrinkOpen(false);
    setEditNameplateOpen(false);
    setEditJoinDateOpen(false);
    setShowEditCustomDrink(false);
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
              setSeatOpen(false);
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
              setSeatOpen(false);
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
        <label className="full-field">
          <span>좌석 번호</span>
          <SeatNumberField
            value={seatNumber}
            open={seatOpen}
            options={availableSeatOptions}
            onChange={setSeatNumber}
            onToggle={() => {
              setSeatOpen((current) => !current);
              setBranchOpen(false);
              setRoleOpen(false);
            }}
            onSelect={(value) => {
              setSeatNumber(value);
              setSeatOpen(false);
            }}
          />
        </label>
        <label>
          <span>입사예정일</span>
          <JoinDateField
            value={expectedJoinDate}
            open={joinDateOpen}
            visibleMonth={joinDateMonth}
            onChange={(value) => {
              setExpectedJoinDate(value);
              setJoinDateOpen(false);
            }}
            onMoveMonth={(amount) => setJoinDateMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1))}
            onToggle={() => {
              setJoinDateMonth(toDateMonth(expectedJoinDate));
              setJoinDateOpen((current) => !current);
              setBranchOpen(false);
              setRoleOpen(false);
              setSeatOpen(false);
              setDrinkOpen(false);
            }}
          />
        </label>
        <div className="form-field nameplate-form-field">
          <span>명패 내용</span>
          <NameplateField
            value={certification}
            open={nameplateOpen}
            options={nameplateOptions}
            onChange={setCertification}
            onSelect={(value) => {
              setCertification(value);
              setNameplateOpen(false);
            }}
            onToggle={() => setNameplateOpen((current) => !current)}
          />
        </div>
        <label className="full-field">
          <span>음료 설정 (선택사항)</span>
          <DrinkSettingField
            value={drinkSetting}
            open={drinkOpen}
            showCustomInput={showCustomDrink}
            onChange={setDrinkSetting}
            onToggle={() => {
              setDrinkOpen((current) => !current);
              setBranchOpen(false);
              setRoleOpen(false);
              setSeatOpen(false);
            }}
            onAddCustomInput={() => setShowCustomDrink(true)}
            onClose={() => setDrinkOpen(false)}
          />
        </label>
        <label className="full-field">
          <span>음료 참고사항</span>
          <textarea placeholder="음료 참고사항" rows={1} value={drinkNote} onChange={(event) => setDrinkNote(event.target.value)} />
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
                          setEditSeatOpen(false);
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
                          setEditSeatOpen(false);
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
                    <label className="full-field">
                      <span>좌석 번호</span>
                      <SeatNumberField
                        value={editDraft.seatNumber}
                        open={editSeatOpen}
                        options={editAvailableSeatOptions}
                        onChange={(value) => changeEditDraft('seatNumber', value)}
                        onToggle={() => {
                          setEditSeatOpen((current) => !current);
                          setEditBranchOpen(false);
                          setEditRoleOpen(false);
                        }}
                        onSelect={(value) => {
                          changeEditDraft('seatNumber', value);
                          setEditSeatOpen(false);
                        }}
                      />
                    </label>
                    <label>
                      <span>입사예정일</span>
                      <JoinDateField
                        value={editDraft.expectedJoinDate}
                        open={editJoinDateOpen}
                        visibleMonth={editJoinDateMonth}
                        onChange={(value) => {
                          changeEditDraft('expectedJoinDate', value);
                          setEditJoinDateOpen(false);
                        }}
                        onMoveMonth={(amount) => setEditJoinDateMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1))}
                        onToggle={() => {
                          setEditJoinDateMonth(toDateMonth(editDraft.expectedJoinDate));
                          setEditJoinDateOpen((current) => !current);
                          setEditBranchOpen(false);
                          setEditRoleOpen(false);
                          setEditSeatOpen(false);
                          setEditDrinkOpen(false);
                        }}
                      />
                    </label>
                    <div className="form-field nameplate-form-field">
                      <span>명패 내용</span>
                      <NameplateField
                        value={editDraft.certification}
                        open={editNameplateOpen}
                        options={nameplateOptions}
                        onChange={(value) => changeEditDraft('certification', value)}
                        onSelect={(value) => {
                          changeEditDraft('certification', value);
                          setEditNameplateOpen(false);
                        }}
                        onToggle={() => setEditNameplateOpen((current) => !current)}
                      />
                    </div>
                    <label className="full-field">
                      <span>음료 설정 (선택사항)</span>
                      <DrinkSettingField
                        value={editDraft.drinkSetting}
                        open={editDrinkOpen}
                        showCustomInput={showEditCustomDrink}
                        onChange={(value) => changeEditDraft('drinkSetting', value)}
                        onToggle={() => {
                          setEditDrinkOpen((current) => !current);
                          setEditBranchOpen(false);
                          setEditRoleOpen(false);
                          setEditSeatOpen(false);
                        }}
                        onAddCustomInput={() => setShowEditCustomDrink(true)}
                        onClose={() => setEditDrinkOpen(false)}
                      />
                    </label>
                    <label className="full-field">
                      <span>음료 참고사항</span>
                      <textarea rows={1} value={editDraft.drinkNote} onChange={(event) => changeEditDraft('drinkNote', event.target.value)} />
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

function toAvailableSeatOptions(members: MemberResponse[], excludeMemberId?: number | null): DropdownOption[] {
  const assignedSeats = new Set<number>();
  members.forEach((member) => {
    if (excludeMemberId && member.id === excludeMemberId) {
      return;
    }
    if (member.seatNumber && member.seatNumber > 0) {
      assignedSeats.add(member.seatNumber);
    }
  });

  return Array.from({ length: MAX_SEAT_NUMBER }, (_, index) => index + 1)
    .filter((seatNumber) => !assignedSeats.has(seatNumber))
    .map((seatNumber) => ({ value: String(seatNumber), label: `${seatNumber}번` }));
}

type SeatNumberFieldProps = {
  value: string;
  open: boolean;
  options: DropdownOption[];
  onChange: (value: string) => void;
  onSelect: (value: string) => void;
  onToggle: () => void;
};

function SeatNumberField({ value, open, options, onChange, onSelect, onToggle }: SeatNumberFieldProps) {
  const selectedOption = value ? { value, label: `${value}번` } : { value: '', label: options.length > 0 ? '빈 좌석 선택' : '빈 좌석 없음' };

  return (
    <div className="seat-number-field">
      <input
        inputMode="numeric"
        min="1"
        placeholder="번호 직접 입력"
        value={value}
        onChange={(event) => onChange(event.target.value.replace(/[^0-9]/g, ''))}
      />
      <Dropdown
        classNamePrefix="form-dropdown"
        disabled={options.length === 0}
        label="빈 좌석"
        open={open}
        options={options}
        placeholderClass={!value}
        selectedOption={selectedOption}
        onToggle={onToggle}
        onSelect={onSelect}
      />
    </div>
  );
}

type NameplateFieldProps = {
  value: string;
  open: boolean;
  options: string[];
  onChange: (value: string) => void;
  onSelect: (value: string) => void;
  onToggle: () => void;
};

function NameplateField({ value, open, options, onChange, onSelect, onToggle }: NameplateFieldProps) {
  const fieldRef = useRef<HTMLDivElement>(null);
  const normalizedValue = value.trim().toLocaleLowerCase('ko-KR');
  const filteredOptions = normalizedValue
    ? options.filter((option) => option.toLocaleLowerCase('ko-KR').includes(normalizedValue))
    : options;

  useEffect(() => {
    if (!open) return;

    const closeOnOutsidePointer = (event: PointerEvent) => {
      if (!fieldRef.current?.contains(event.target as Node)) onToggle();
    };

    document.addEventListener('pointerdown', closeOnOutsidePointer);
    return () => document.removeEventListener('pointerdown', closeOnOutsidePointer);
  }, [open, onToggle]);

  return (
    <div className={`nameplate-field${open ? ' open' : ''}`} ref={fieldRef}>
      <input
        type="text"
        placeholder="명패 문구 입력 또는 선택"
        value={value}
        onFocus={() => {
          if (!open) onToggle();
        }}
        onChange={(event) => onChange(event.target.value)}
      />
      <button type="button" aria-label="명패 내용 목록 열기" aria-expanded={open} onClick={onToggle} />
      {open && (
        <div className="nameplate-options" role="listbox" aria-label="명패 내용">
          {filteredOptions.length > 0 ? filteredOptions.map((option) => (
            <button
              className={option === value ? 'selected' : ''}
              type="button"
              role="option"
              aria-selected={option === value}
              key={option}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => onSelect(option)}
            >
              {option}
            </button>
          )) : (
            <p>직접 입력한 문구로 등록할 수 있어요.</p>
          )}
        </div>
      )}
    </div>
  );
}

type JoinDateFieldProps = {
  value: string;
  open: boolean;
  visibleMonth: Date;
  onChange: (value: string) => void;
  onMoveMonth: (amount: number) => void;
  onToggle: () => void;
};

function JoinDateField({ value, open, visibleMonth, onChange, onMoveMonth, onToggle }: JoinDateFieldProps) {
  const fieldRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;

    const closeOnOutsidePointer = (event: PointerEvent) => {
      if (!fieldRef.current?.contains(event.target as Node)) onToggle();
    };

    document.addEventListener('pointerdown', closeOnOutsidePointer);
    return () => document.removeEventListener('pointerdown', closeOnOutsidePointer);
  }, [open, onToggle]);

  return (
    <div className="join-date-field" ref={fieldRef}>
      <button className={`join-date-button${open ? ' open' : ''}${value ? '' : ' empty'}`} type="button" onClick={onToggle}>
        <span>{value ? formatDateLabel(value) : '연도. 월. 일.'}</span>
        <CalendarIcon />
      </button>
      {open && (
        <div className="join-date-calendar" role="dialog" aria-label="입사예정일 선택">
          <header>
            <strong>{visibleMonth.getFullYear()}년 {visibleMonth.getMonth() + 1}월</strong>
            <div>
              <button type="button" aria-label="이전 달" onClick={() => onMoveMonth(-1)}>‹</button>
              <button type="button" aria-label="다음 달" onClick={() => onMoveMonth(1)}>›</button>
            </div>
          </header>
          <div className="join-date-weekdays">
            {WEEKDAYS.map((day, index) => (
              <span className={index === 0 ? 'holiday' : index === 6 ? 'saturday' : ''} key={day}>{day}</span>
            ))}
          </div>
          <div className="join-date-calendar-grid">
            {getCalendarCells(visibleMonth).map((date, index) => {
              if (!date) {
                return <span aria-hidden="true" key={`empty-${index}`} />;
              }

              const dateKey = toDateKey(date);
              const day = date.getDay();
              const holiday = isKoreanHoliday(dateKey);
              const className = [
                dateKey === value ? 'selected' : '',
                holiday || day === 0 ? 'holiday' : '',
                !holiday && day === 6 ? 'saturday' : '',
              ].filter(Boolean).join(' ');

              return (
                <button className={className} type="button" key={dateKey} onClick={() => onChange(dateKey)}>
                  {date.getDate()}
                </button>
              );
            })}
          </div>
          <footer>
            <button type="button" onClick={() => onChange('')}>선택 안 함</button>
            <button type="button" onClick={() => onChange(toDateKey(new Date()))}>오늘</button>
          </footer>
        </div>
      )}
    </div>
  );
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M7 4v3" />
      <path d="M17 4v3" />
      <path d="M5 9h14" />
      <path d="M6 6h12a1.5 1.5 0 0 1 1.5 1.5v11A1.5 1.5 0 0 1 18 20H6a1.5 1.5 0 0 1-1.5-1.5v-11A1.5 1.5 0 0 1 6 6Z" />
    </svg>
  );
}

type DrinkSettingFieldProps = {
  value: string;
  open: boolean;
  showCustomInput: boolean;
  onChange: (value: string) => void;
  onToggle: () => void;
  onAddCustomInput: () => void;
  onClose: () => void;
};

function DrinkSettingField({ value, open, showCustomInput, onChange, onToggle, onAddCustomInput, onClose }: DrinkSettingFieldProps) {
  const { baseDrink, customText } = toDrinkParts(value);
  const selectedOption = baseDrink
    ? { value: baseDrink, label: baseDrink }
    : { value: '', label: '음료를 선택해주세요' };

  const changeBaseDrink = (nextBaseDrink: string) => {
    onChange(toDrinkSettingValue(nextBaseDrink, customText));
    onClose();
  };

  const changeCustomText = (nextCustomText: string) => {
    onChange(toDrinkSettingValue(baseDrink, nextCustomText));
  };

  return (
    <div className="drink-setting-field">
      <div className="drink-setting-main">
        <Dropdown
          classNamePrefix="form-dropdown"
          label="음료 설정"
          open={open}
          options={DRINK_OPTIONS}
          placeholderClass={!baseDrink}
          selectedOption={selectedOption}
          onToggle={onToggle}
          onSelect={changeBaseDrink}
        />
        <button type="button" aria-label="음료 직접 입력 추가" onClick={onAddCustomInput}>
          +
        </button>
      </div>
      {showCustomInput && (
        <input
          type="text"
          placeholder="예: 콜라, 아아"
          value={customText}
          onChange={(event) => changeCustomText(event.target.value)}
        />
      )}
    </div>
  );
}

function toDrinkParts(value: string) {
  const drinks = parseDrinkItems(value);
  const baseDrink = drinks.find((drink) => DRINK_OPTIONS.some((option) => option.value === drink)) || '';
  const customText = drinks.filter((drink) => drink !== baseDrink).join(', ');

  return { baseDrink, customText };
}

function toDrinkSettingValue(baseDrink: string, customText: string) {
  return [baseDrink, ...parseDrinkItems(customText)].filter(Boolean).join(',');
}

function parseDrinkItems(value: string) {
  return value
    .split(/[,\n\r]+/)
    .map((drink) => drink.trim())
    .filter(Boolean);
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function toDateMonth(value: string) {
  if (!value) {
    return startOfMonth(new Date());
  }

  return startOfMonth(new Date(`${value}T00:00:00`));
}

function getCalendarCells(month: Date) {
  const firstDay = new Date(month.getFullYear(), month.getMonth(), 1).getDay();
  const lastDate = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const cells: Array<Date | null> = Array.from({ length: firstDay }, () => null);

  for (let day = 1; day <= lastDate; day += 1) {
    cells.push(new Date(month.getFullYear(), month.getMonth(), day));
  }

  while (cells.length % 7 !== 0) {
    cells.push(null);
  }

  return cells;
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function formatDateLabel(value: string) {
  const date = new Date(`${value}T00:00:00`);

  return `${date.getFullYear()}. ${date.getMonth() + 1}. ${date.getDate()}.`;
}

function isKoreanHoliday(dateKey: string) {
  return KOREAN_HOLIDAYS[dateKey.slice(0, 4)]?.includes(dateKey) || false;
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
