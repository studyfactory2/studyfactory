import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { apiRequest } from '../../api/client';
import type { Branch, FixedLeaveGenerationResponse, FixedLeaveManagementResponse, StaffScheduleDayOfWeek } from '../../types/domain';

type FixedLeaveManagementPanelProps = {
  branches: Branch[];
};

type FixedLeaveGroup = {
  memberId: number;
  memberName: string;
  branchId: number;
  items: FixedLeaveManagementResponse[];
};

const DAY_LABELS: Record<StaffScheduleDayOfWeek, string> = {
  MONDAY: '월요일',
  TUESDAY: '화요일',
  WEDNESDAY: '수요일',
  THURSDAY: '목요일',
  FRIDAY: '금요일',
  SATURDAY: '토요일',
  SUNDAY: '일요일',
};

export function FixedLeaveManagementPanel({ branches }: FixedLeaveManagementPanelProps) {
  const [fixedLeaves, setFixedLeaves] = useState<FixedLeaveManagementResponse[]>([]);
  const [name, setName] = useState('');
  const [submittedName, setSubmittedName] = useState('');
  const [message, setMessage] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<FixedLeaveManagementResponse | null>(null);
  const [generateConfirmOpen, setGenerateConfirmOpen] = useState(false);
  const [modalMessage, setModalMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const generationRange = getFixedGenerationRange();

  useEffect(() => {
    void loadFixedLeaves();
  }, [submittedName]);

  const groupedFixedLeaves = useMemo(() => {
    const groups = new Map<number, FixedLeaveGroup>();
    fixedLeaves.forEach((fixedLeave) => {
      const current = groups.get(fixedLeave.memberId);
      if (current) {
        current.items.push(fixedLeave);
        return;
      }

      groups.set(fixedLeave.memberId, {
        memberId: fixedLeave.memberId,
        memberName: fixedLeave.memberName,
        branchId: fixedLeave.branchId,
        items: [fixedLeave],
      });
    });

    return Array.from(groups.values());
  }, [fixedLeaves]);

  const loadFixedLeaves = async () => {
    setMessage('');
    try {
      const params = new URLSearchParams();
      if (submittedName.trim()) {
        params.set('name', submittedName.trim());
      }
      const query = params.toString();
      const responses = await apiRequest<FixedLeaveManagementResponse[]>(`/api/leaves/fixed${query ? `?${query}` : ''}`);
      setFixedLeaves(responses);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '고정 휴무 목록을 불러오지 못했습니다.');
    }
  };

  const submitSearch = () => {
    setSubmittedName(name);
  };

  const deleteFixedLeave = async () => {
    if (!deleteTarget) {
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      await apiRequest<void>(`/api/leaves/fixed/${deleteTarget.id}`, {
        method: 'DELETE',
      });
      setDeleteTarget(null);
      await loadFixedLeaves();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '고정 휴무 삭제에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const generateFixedLeaves = async () => {
    setSubmitting(true);
    setMessage('');
    try {
      const response = await apiRequest<FixedLeaveGenerationResponse>('/api/leaves/fixed/generate', {
        method: 'POST',
      });
      setGenerateConfirmOpen(false);
      setModalMessage(`${response.startDate} ~ ${response.endDate} 고정 휴무 ${response.createdCount}건이 생성되었습니다.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '고정 휴무 생성에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed-leave-panel">
      <header className="fixed-leave-header">
        <button type="button" aria-label="뒤로가기" onClick={() => { window.history.pushState(null, '', '/managerdashboard?view=grid'); window.dispatchEvent(new PopStateEvent('popstate')); }}>
          <BackIcon />
        </button>
        <h2>고정 기타 휴무 관리</h2>
        <div className="fixed-leave-manual">
          <button type="button" onClick={() => setGenerateConfirmOpen(true)}>
            <PlayIcon />
            수동 생성
          </button>
          <span>매주 월요일 00:00 (KST) 자동 생성</span>
        </div>
      </header>
      <label className="fixed-leave-search">
        <SearchIcon />
        <input
          aria-label="이름 검색"
          placeholder="이름 검색..."
          value={name}
          onChange={(event) => {
            setName(event.target.value);
            setSubmittedName(event.target.value);
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              submitSearch();
            }
          }}
        />
      </label>
      {message && <p className="fixed-leave-message">{message}</p>}
      {groupedFixedLeaves.length === 0 ? (
        <p className="fixed-leave-empty">등록된 고정 기타 휴무가 없습니다.</p>
      ) : (
        <div className="fixed-leave-list">
          {groupedFixedLeaves.map((group) => (
            <article className="fixed-leave-card" key={group.memberId}>
              <div className="fixed-leave-member">
                <span>{findBranchName(branches, group.branchId)}</span>
                <strong>{group.memberName}</strong>
              </div>
              <div className="fixed-leave-items">
                {group.items.map((item) => (
                  <div className="fixed-leave-item" key={item.id}>
                    <span>매주 {DAY_LABELS[item.dayOfWeek]}</span>
                    <strong>{item.reason}</strong>
                    <em>{formatSlots(item.slots)}</em>
                    <button type="button" aria-label={`${item.reason} 고정 휴무 삭제`} onClick={() => setDeleteTarget(item)}>
                      <TrashIcon />
                    </button>
                  </div>
                ))}
              </div>
            </article>
          ))}
        </div>
      )}
      {deleteTarget &&
        createPortal(
          <div className="pre-register-modal-backdrop" role="presentation">
            <section className="pre-register-modal" role="dialog" aria-modal="true" aria-labelledby="fixed-leave-delete-title">
              <h2 id="fixed-leave-delete-title">삭제 확인</h2>
              <p>{deleteTarget.memberName}님의 고정 기타 휴무를 삭제하시겠습니까?</p>
              <div className="pre-register-modal-actions">
                <button className="pre-register-modal-cancel" type="button" disabled={submitting} onClick={() => setDeleteTarget(null)}>닫기</button>
                <button className="pre-register-modal-danger" type="button" disabled={submitting} onClick={deleteFixedLeave}>
                  {submitting ? '삭제 중' : '삭제'}
                </button>
              </div>
            </section>
          </div>,
          document.body
        )}
      {generateConfirmOpen &&
        createPortal(
          <div className="pre-register-modal-backdrop" role="presentation">
            <section className="pre-register-modal fixed-leave-generate-modal" role="dialog" aria-modal="true" aria-labelledby="fixed-leave-generate-title">
              <h2 id="fixed-leave-generate-title">고정 휴무 생성</h2>
              <p>
                이번 주 + 다음 주({generationRange.startDate} ~ {generationRange.endDate})의 고정 휴무를 생성하시겠습니까?
                <br />
                (이미 존재하는 기록은 덮어씌워질 수 있습니다.)
              </p>
              <div className="pre-register-modal-actions">
                <button className="pre-register-modal-cancel" type="button" disabled={submitting} onClick={() => setGenerateConfirmOpen(false)}>취소</button>
                <button type="button" disabled={submitting} onClick={generateFixedLeaves}>{submitting ? '생성 중' : '확인'}</button>
              </div>
            </section>
          </div>,
          document.body
        )}
      {modalMessage &&
        createPortal(
          <div className="pre-register-modal-backdrop" role="presentation">
            <section className="pre-register-modal" role="alertdialog" aria-modal="true" aria-labelledby="fixed-leave-generate-success-title">
              <h2 id="fixed-leave-generate-success-title">완료</h2>
              <p>{modalMessage}</p>
              <button type="button" onClick={() => setModalMessage('')}>확인</button>
            </section>
          </div>,
          document.body
        )}
    </div>
  );
}

function findBranchName(branches: Branch[], branchId: number) {
  return branches.find((branch) => branch.id === branchId)?.name || '지점';
}

function formatSlots(slots: string) {
  const values = slots
    .split(',')
    .map((slot) => slot.trim())
    .filter(Boolean);
  if (values.length === 1) {
    return `(${values[0]}교시)`;
  }

  return `(${values.join(', ')}교시)`;
}

function getFixedGenerationRange() {
  const today = new Date();
  const day = today.getDay();
  const mondayOffset = day === 0 ? -6 : 1 - day;
  const start = new Date(today);
  start.setDate(today.getDate() + mondayOffset);
  const end = new Date(start);
  end.setDate(start.getDate() + 13);

  return {
    startDate: toDateText(start),
    endDate: toDateText(end),
  };
}

function toDateText(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 5-7 7 7 7" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <path d="m16 16 4 4" />
    </svg>
  );
}

function PlayIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M8 5v14l11-7Z" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 7h16" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
      <path d="M6 7l1 14h10l1-14" />
      <path d="M9 7V4h6v3" />
    </svg>
  );
}
