import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { Branch, MemberResponse } from '../../types/domain';

type StaffSeatManagementPanelProps = {
  branches: Branch[];
};

const MAX_SEAT_NUMBER = 102;

export function StaffSeatManagementPanel({ branches }: StaffSeatManagementPanelProps) {
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [releaseTarget, setReleaseTarget] = useState<MemberResponse | null>(null);
  const [releaseSubmitting, setReleaseSubmitting] = useState(false);
  const [assignSeatNumber, setAssignSeatNumber] = useState<number | null>(null);
  const [assignTarget, setAssignTarget] = useState<MemberResponse | null>(null);
  const [assignSearch, setAssignSearch] = useState('');
  const [assignSubmittingId, setAssignSubmittingId] = useState<number | null>(null);
  const [completeMessage, setCompleteMessage] = useState<string | null>(null);
  const branch = branches[0] || { id: 1, name: '망미점' };
  const filteredMembers = useMemo(() => {
    const keyword = assignSearch.trim().toLowerCase();
    if (!keyword) {
      return members;
    }

    return members.filter((member) => member.name.toLowerCase().includes(keyword));
  }, [assignSearch, members]);
  const rows = useMemo(() => {
    const membersBySeat = new Map<number, MemberResponse>();
    members.forEach((member) => {
      if (member.seatNumber && member.seatNumber > 0) {
        membersBySeat.set(member.seatNumber, member);
      }
    });

    return Array.from({ length: MAX_SEAT_NUMBER }, (_, index) => {
      const seatNumber = index + 1;
      return {
        seatNumber,
        member: membersBySeat.get(seatNumber) || null,
      };
    });
  }, [members]);

  useEffect(() => {
    void loadMembers();
  }, [branch.id]);

  const loadMembers = async () => {
    try {
      const params = new URLSearchParams({ branchId: String(branch.id) });
      const response = await apiRequest<MemberResponse[]>(`/api/members?${params.toString()}`);
      setMembers(response);
      setMessage(null);
    } catch (error) {
      setMembers([]);
      setMessage(error instanceof Error ? error.message : '좌석 현황을 불러오지 못했습니다.');
    }
  };

  const releaseSeat = async () => {
    if (!releaseTarget) {
      return;
    }

    try {
      setReleaseSubmitting(true);
      const updatedMember = await apiRequest<MemberResponse>(`/api/seats/assignments/members/${releaseTarget.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ seatNumber: null }),
      });
      setMembers((current) => current.map((member) => (member.id === updatedMember.id ? updatedMember : member)));
      setReleaseTarget(null);
      setCompleteMessage('좌석 배정이 해제되었습니다.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '좌석 배정을 해제하지 못했습니다.');
      setReleaseTarget(null);
    } finally {
      setReleaseSubmitting(false);
    }
  };

  const openAssignModal = (seatNumber: number) => {
    setAssignSeatNumber(seatNumber);
    setAssignTarget(null);
    setAssignSearch('');
  };

  const closeAssignModal = () => {
    setAssignSeatNumber(null);
    setAssignTarget(null);
    setAssignSearch('');
  };

  const assignSeat = async () => {
    if (!assignSeatNumber || !assignTarget || assignTarget.seatNumber) {
      return;
    }

    try {
      setAssignSubmittingId(assignTarget.id);
      const updatedMember = await apiRequest<MemberResponse>(`/api/seats/assignments/members/${assignTarget.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ seatNumber: assignSeatNumber }),
      });
      setMembers((current) => current.map((currentMember) => (currentMember.id === updatedMember.id ? updatedMember : currentMember)));
      closeAssignModal();
      setCompleteMessage('좌석 배정이 완료되었습니다.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '좌석을 배정하지 못했습니다.');
      closeAssignModal();
    } finally {
      setAssignSubmittingId(null);
    }
  };

  return (
    <div className="staff-seat-panel">
      <header className="staff-seat-header">
        <a href="/managerdashboard?view=staff-page" aria-label="스텝페이지로 돌아가기">
          <BackIcon />
        </a>
        <h2>사원 좌석 관리</h2>
        <span>{branch.name}</span>
      </header>

      {message ? (
        <p className="staff-seat-empty">{message}</p>
      ) : (
        <div className="staff-seat-list">
          {rows.map(({ seatNumber, member }) => (
            <article className={`staff-seat-card${member ? '' : ' empty'}`} key={seatNumber} data-seat-number={seatNumber}>
              <div className="staff-seat-number">{seatNumber}</div>
              <div className="staff-seat-member">
                <strong>{member?.name || '공석'}</strong>
                <span>{member ? toRoleLabel(member.role) : ''}</span>
              </div>
              <button
                className={member ? 'release' : 'assign'}
                type="button"
                data-seat-number={seatNumber}
                onClick={() => {
                  if (member) {
                    setReleaseTarget(member);
                    return;
                  }
                  openAssignModal(seatNumber);
                }}
              >
                {member ? '배정 해제' : '배정 하기'}
              </button>
            </article>
          ))}
        </div>
      )}
      {releaseTarget && (
        <div className="staff-seat-modal-backdrop" role="presentation">
          <section className="staff-seat-small-modal" role="dialog" aria-modal="true" aria-labelledby="staff-seat-release-title">
            <h2 id="staff-seat-release-title">좌석 배정 해제</h2>
            <p>{releaseTarget.name} 사원의 좌석 배정을 해제하시겠습니까?</p>
            <div className="staff-seat-modal-actions">
              <button className="staff-seat-modal-cancel" type="button" disabled={releaseSubmitting} onClick={() => setReleaseTarget(null)}>취소</button>
              <button className="staff-seat-modal-danger" type="button" disabled={releaseSubmitting} onClick={releaseSeat}>
                {releaseSubmitting ? '해제 중' : '확인'}
              </button>
            </div>
          </section>
        </div>
      )}
      {assignSeatNumber && !assignTarget && (
        <div className="staff-seat-modal-backdrop" role="presentation">
          <section className="staff-seat-assign-modal" role="dialog" aria-modal="true" aria-labelledby="staff-seat-assign-title">
            <header className="staff-seat-assign-header">
              <h2 id="staff-seat-assign-title">{assignSeatNumber}번 좌석 배정</h2>
              <button type="button" aria-label="닫기" onClick={closeAssignModal}>
                <CloseIcon />
              </button>
            </header>
            <label className="staff-seat-search">
              <SearchIcon />
              <input
                autoFocus
                value={assignSearch}
                placeholder="이름 검색..."
                onChange={(event) => setAssignSearch(event.target.value)}
              />
            </label>
            <div className="staff-seat-candidate-list">
              {filteredMembers.length === 0 ? (
                <p className="staff-seat-candidate-empty">검색 결과가 없습니다.</p>
              ) : (
                filteredMembers.map((member) => {
                  const assigned = Boolean(member.seatNumber);
                  return (
                    <button
                      className="staff-seat-candidate"
                      type="button"
                      key={member.id}
                      disabled={assigned || assignSubmittingId !== null}
                      onClick={() => setAssignTarget(member)}
                    >
                      <span>
                        <strong>{member.name}</strong>
                        <small>{branch.name} | {toRoleLabel(member.role)}</small>
                      </span>
                      {assigned ? (
                        <em>{member.seatNumber}번 사용중</em>
                      ) : (
                        <i aria-hidden="true">{assignSubmittingId === member.id ? '' : null}</i>
                      )}
                    </button>
                  );
                })
              )}
            </div>
          </section>
        </div>
      )}
      {assignSeatNumber && assignTarget && (
        <div className="staff-seat-modal-backdrop" role="presentation">
          <section className="staff-seat-small-modal" role="dialog" aria-modal="true" aria-labelledby="staff-seat-assign-confirm-title">
            <h2 id="staff-seat-assign-confirm-title">좌석 배정</h2>
            <p>{assignTarget.name} 사원을 {assignSeatNumber}번 좌석에 배정하시겠습니까?</p>
            <div className="staff-seat-modal-actions">
              <button className="staff-seat-modal-cancel" type="button" disabled={assignSubmittingId !== null} onClick={() => setAssignTarget(null)}>취소</button>
              <button className="staff-seat-modal-confirm" type="button" disabled={assignSubmittingId !== null} onClick={assignSeat}>
                {assignSubmittingId !== null ? '배정 중' : '배정'}
              </button>
            </div>
          </section>
        </div>
      )}
      {completeMessage && (
        <div className="staff-seat-modal-backdrop" role="presentation">
          <section className="staff-seat-small-modal" role="alertdialog" aria-modal="true" aria-labelledby="staff-seat-complete-title">
            <h2 id="staff-seat-complete-title">완료</h2>
            <p>{completeMessage}</p>
            <button className="staff-seat-alert-close" type="button" onClick={() => setCompleteMessage(null)}>확인</button>
          </section>
        </div>
      )}
    </div>
  );
}

function toRoleLabel(role: MemberResponse['role']) {
  if (role === 'ADMIN') {
    return '관리자';
  }
  if (role === 'STAFF') {
    return '스탭';
  }

  return '회원';
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 5-7 7 7 7" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M18 6 6 18" />
      <path d="m6 6 12 12" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  );
}
