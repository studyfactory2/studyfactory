import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { apiRequest } from '../../api/client';
import type { Branch, Certification, MemberResponse, MemberRole } from '../../types/domain';
import { Dropdown, type DropdownOption } from '../common/Dropdown';

type MemberStatusPanelProps = {
  branches: Branch[];
  certifications: Certification[];
};

type MemberEditState = {
  branchId: string;
  role: MemberRole;
  name: string;
  seatNumber: string;
  joinDate: string;
  preparingCertifications: string[];
  preparingCertificationInput: string;
};

const ROLE_OPTIONS: Array<{ value: MemberRole; label: string }> = [
  { value: 'MEMBER', label: '회원' },
  { value: 'STAFF', label: '스탭' },
  { value: 'ADMIN', label: '관리자' },
];
const SEAT_OPTIONS = [
  { value: '', label: '좌석 선택' },
  ...Array.from({ length: 102 }, (_, index) => {
    const seatNumber = String(index + 1);
    return { value: seatNumber, label: `${seatNumber}번` };
  }),
];

export function MemberStatusPanel({ branches, certifications }: MemberStatusPanelProps) {
  const branchOptions = [{ value: '', label: '전체 지점' }, ...toBranchOptions(branches)];
  const editBranchOptions = branches.length > 0 ? toBranchOptions(branches) : [{ value: '', label: '지점 선택' }];
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [name, setName] = useState('');
  const [submittedName, setSubmittedName] = useState('');
  const [selectedBranchId, setSelectedBranchId] = useState('');
  const [branchOpen, setBranchOpen] = useState(false);
  const [editingMemberId, setEditingMemberId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<MemberEditState | null>(null);
  const [editBranchOpen, setEditBranchOpen] = useState(false);
  const [editRoleOpen, setEditRoleOpen] = useState(false);
  const [editSeatOpen, setEditSeatOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<MemberResponse | null>(null);
  const [preparingDeleteTarget, setPreparingDeleteTarget] = useState<string | null>(null);
  const [modalMessage, setModalMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');
  const selectedBranchOption = branchOptions.find((branch) => branch.value === selectedBranchId) || branchOptions[0];

  useEffect(() => {
    void loadMembers();
  }, [submittedName, selectedBranchId]);

  const loadMembers = async () => {
    setLoading(true);
    setMessage('');
    try {
      const params = new URLSearchParams();
      if (submittedName.trim()) {
        params.set('name', submittedName.trim());
      }
      if (selectedBranchId) {
        params.set('branchId', selectedBranchId);
      }
      const query = params.toString();
      const responses = await apiRequest<MemberResponse[]>(`/api/members${query ? `?${query}` : ''}`);
      setMembers(responses);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '사원 현황을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const submitSearch = () => {
    setSubmittedName(name);
  };

  const startEdit = (member: MemberResponse) => {
    setEditingMemberId(member.id);
    setEditDraft({
      branchId: String(member.branchId),
      role: member.role,
      name: member.name,
      seatNumber: member.seatNumber ? String(member.seatNumber) : '',
      joinDate: member.joinDate || member.expectedJoinDate || '',
      preparingCertifications: toPreparingCertificationList(member.preparingCertifications, findCertification(certifications, member.certificationId)),
      preparingCertificationInput: '',
    });
    setEditBranchOpen(false);
    setEditRoleOpen(false);
    setEditSeatOpen(false);
    setMessage('');
  };

  const cancelEdit = () => {
    setEditingMemberId(null);
    setEditDraft(null);
    setEditBranchOpen(false);
    setEditRoleOpen(false);
    setEditSeatOpen(false);
  };

  const changeEditDraft = (field: keyof MemberEditState, value: string) => {
    setEditDraft((current) => {
      if (!current) {
        return current;
      }

      return { ...current, [field]: value };
    });
  };

  const addPreparingCertification = () => {
    setEditDraft((current) => {
      if (!current) {
        return current;
      }

      const nextCertification = current.preparingCertificationInput.trim();
      if (!nextCertification || current.preparingCertifications.includes(nextCertification)) {
        return { ...current, preparingCertificationInput: '' };
      }

      return {
        ...current,
        preparingCertifications: [...current.preparingCertifications, nextCertification],
        preparingCertificationInput: '',
      };
    });
  };

  const removePreparingCertification = (certification: string) => {
    setEditDraft((current) => {
      if (!current) {
        return current;
      }

      return {
        ...current,
        preparingCertifications: current.preparingCertifications.filter((item) => item !== certification),
      };
    });
    setPreparingDeleteTarget(null);
  };

  const submitEdit = async (memberId: number) => {
    if (!editDraft) {
      return;
    }

    if (!editDraft.branchId || !editDraft.name.trim() || !editDraft.joinDate) {
      setMessage('지점, 이름, 입사일을 입력해주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      await apiRequest<MemberResponse>(`/api/members/${memberId}`, {
        method: 'PATCH',
        body: JSON.stringify({
          branchId: Number(editDraft.branchId),
          name: editDraft.name.trim(),
          role: editDraft.role,
          seatNumber: editDraft.seatNumber ? Number(editDraft.seatNumber) : null,
          joinDate: editDraft.joinDate,
          certificationId: null,
          preparingCertifications: editDraft.preparingCertifications.join('\n'),
        }),
      });
      cancelEdit();
      setModalMessage('사원 정보가 수정되었습니다.');
      await loadMembers();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '사원 정보 수정에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const deleteMember = async () => {
    if (!deleteTarget) {
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      await apiRequest<void>(`/api/members/${deleteTarget.id}`, {
        method: 'DELETE',
      });
      if (editingMemberId === deleteTarget.id) {
        cancelEdit();
      }
      setDeleteTarget(null);
      setModalMessage('사원 정보가 삭제되었습니다.');
      await loadMembers();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '사원 정보 삭제에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="member-status-panel">
      <header className="member-status-header">
        <button type="button" aria-label="뒤로가기" onClick={() => { window.history.pushState(null, '', '/managerdashboard?view=grid'); window.dispatchEvent(new PopStateEvent('popstate')); }}>
          <BackIcon />
        </button>
        <h2>사원 현황</h2>
        <div className="member-status-search">
          <label>
            <SearchIcon />
            <input
              aria-label="이름 검색"
              placeholder="이름"
              value={name}
              onChange={(event) => setName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  submitSearch();
                }
              }}
            />
          </label>
          <button type="button" onClick={submitSearch}>검색</button>
        </div>
        <div className="member-status-branch">
          <Dropdown
            classNamePrefix="form-dropdown"
            label="지점"
            open={branchOpen}
            options={branchOptions}
            selectedOption={selectedBranchOption}
            onToggle={() => setBranchOpen((current) => !current)}
            onSelect={(value) => {
              setSelectedBranchId(value);
              setBranchOpen(false);
            }}
          />
        </div>
      </header>
      {loading ? (
        <p className="member-status-empty">로딩 중...</p>
      ) : message ? (
        <p className="member-status-empty">{message}</p>
      ) : members.length === 0 ? (
        <p className="member-status-empty">조회된 사원이 없습니다.</p>
      ) : (
        <div className="member-status-list">
          {members.map((member) => (
            <article className="member-status-card" key={member.id}>
              {editingMemberId === member.id && editDraft ? (
                <div className="member-status-edit-form">
                  <div className="member-status-edit-heading">
                    <strong>{editDraft.name}</strong>
                    <div>
                      <button type="button" aria-label={`${editDraft.name} 저장`} disabled={submitting} onClick={() => submitEdit(member.id)}>
                        <SaveIcon />
                      </button>
                      <button type="button" aria-label={`${editDraft.name} 수정 닫기`} onClick={cancelEdit}>
                        <CloseIcon />
                      </button>
                    </div>
                  </div>
                  <div className="form-field">
                    <span>지점</span>
                    <Dropdown
                      classNamePrefix="form-dropdown"
                      label="지점"
                      open={editBranchOpen}
                      options={editBranchOptions}
                      selectedOption={editBranchOptions.find((branch) => branch.value === editDraft.branchId) || editBranchOptions[0]}
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
                    <span>구분</span>
                    <Dropdown
                      classNamePrefix="form-dropdown"
                      label="구분"
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
                  <div className="form-field">
                    <span>좌석 (망미점 1~102)</span>
                    <Dropdown
                      classNamePrefix="form-dropdown"
                      label="좌석"
                      open={editSeatOpen}
                      options={SEAT_OPTIONS}
                      selectedOption={SEAT_OPTIONS.find((seat) => seat.value === editDraft.seatNumber) || SEAT_OPTIONS[0]}
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
                  </div>
                  <div className="member-status-preparing-field">
                    <span>준비자격증</span>
                    {editDraft.preparingCertifications.length > 0 && (
                      <div className="member-status-preparing-list">
                        {editDraft.preparingCertifications.map((certification) => (
                          <button key={certification} type="button" onClick={() => setPreparingDeleteTarget(certification)}>
                            {certification}
                            <span>×</span>
                          </button>
                        ))}
                      </div>
                    )}
                    <div className="member-status-preparing-input">
                      <input
                        list="member-status-certification-options"
                        placeholder="입력하거나 선택하세요"
                        value={editDraft.preparingCertificationInput}
                        onChange={(event) => changeEditDraft('preparingCertificationInput', event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault();
                            addPreparingCertification();
                          }
                        }}
                      />
                      <button type="button" aria-label="준비자격증 추가" onClick={addPreparingCertification}>+</button>
                    </div>
                    <datalist id="member-status-certification-options">
                      {certifications.map((certification) => (
                        <option key={certification.id} value={certification.content} />
                      ))}
                    </datalist>
                  </div>
                </div>
              ) : (
                <>
                  <div className="member-status-info">
                    <strong>{member.name}</strong>
                    <span>
                      <em>{findBranchName(branches, member.branchId)}</em>
                      <i>|</i>
                      <b>{toRoleLabel(member.role)}</b>
                      <i>|</i>
                      <em>{member.seatNumber ? `좌석 ${member.seatNumber}번` : '좌석 미정'}</em>
                    </span>
                    {member.certificationId && <small>{findCertification(certifications, member.certificationId)}</small>}
                  </div>
                  <div className="member-status-actions">
                    <button type="button" aria-label={`${member.name} 수정`} onClick={() => startEdit(member)}>
                      <EditIcon />
                    </button>
                    <button type="button" aria-label={`${member.name} 삭제`} onClick={() => setDeleteTarget(member)}>
                      <TrashIcon />
                    </button>
                  </div>
                </>
              )}
            </article>
          ))}
        </div>
      )}
      {createPortal(
        <>
          {preparingDeleteTarget && (
            <div className="pre-register-modal-backdrop" role="presentation">
              <section className="pre-register-modal" role="dialog" aria-modal="true" aria-labelledby="preparing-certification-delete-title">
                <h2 id="preparing-certification-delete-title">삭제 확인</h2>
                <p>삭제하시겠습니까?</p>
                <div className="pre-register-modal-actions">
                  <button className="pre-register-modal-cancel" type="button" onClick={() => setPreparingDeleteTarget(null)}>닫기</button>
                  <button className="pre-register-modal-danger" type="button" onClick={() => removePreparingCertification(preparingDeleteTarget)}>삭제</button>
                </div>
              </section>
            </div>
          )}
          {deleteTarget && (
            <div className="pre-register-modal-backdrop" role="presentation">
              <section className="pre-register-modal" role="dialog" aria-modal="true" aria-labelledby="member-status-delete-title">
                <h2 id="member-status-delete-title">삭제 확인</h2>
                <p>{deleteTarget.name}님의 사원 정보를 삭제하시겠습니까?</p>
                <div className="pre-register-modal-actions">
                  <button className="pre-register-modal-cancel" type="button" disabled={submitting} onClick={() => setDeleteTarget(null)}>닫기</button>
                  <button className="pre-register-modal-danger" type="button" disabled={submitting} onClick={deleteMember}>
                    {submitting ? '삭제 중' : '삭제'}
                  </button>
                </div>
              </section>
            </div>
          )}
          {modalMessage && (
            <div className="pre-register-modal-backdrop" role="presentation">
              <section className="pre-register-modal" role="alertdialog" aria-modal="true" aria-labelledby="member-status-success-title">
                <h2 id="member-status-success-title">완료</h2>
                <p>{modalMessage}</p>
                <button type="button" onClick={() => setModalMessage('')}>확인</button>
              </section>
            </div>
          )}
        </>,
        document.body
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
  if (!certificationId) {
    return '';
  }

  return certifications.find((certification) => certification.id === certificationId)?.content || '';
}

function toPreparingCertificationList(value?: string | null, fallbackCertification?: string) {
  const certifications = value
    ? value
    .split(/\n|,/)
    .map((item) => item.trim())
    .filter(Boolean)
    : [];

  if (certifications.length > 0 || !fallbackCertification) {
    return certifications;
  }

  return [fallbackCertification];
}

function toRoleLabel(role: MemberResponse['role']) {
  return ROLE_OPTIONS.find((option) => option.value === role)?.label || role;
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="10.5" cy="10.5" r="5.5" />
      <path d="m15 15 4 4" />
    </svg>
  );
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 20h4.5L19 9.5 14.5 5 4 15.5V20Z" />
      <path d="m13.5 6 4.5 4.5" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 7h14" />
      <path d="M9 7V5h6v2" />
      <path d="M7 7l1 13h8l1-13" />
      <path d="M10 11v5" />
      <path d="M14 11v5" />
    </svg>
  );
}

function SaveIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 4h12l2 2v14H5V4Z" />
      <path d="M8 4v6h8V4" />
      <path d="M8 20v-6h8v6" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6 6l12 12" />
      <path d="M18 6 6 18" />
    </svg>
  );
}
