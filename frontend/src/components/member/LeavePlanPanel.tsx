import { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { apiRequest } from '../../api/client';
import type { LeaveResponse, LeaveType as ApiLeaveType, MemberLeavePlanResponse } from '../../types/domain';

type LeaveType = '월차' | '오전반차' | '오후반차';
type Message = {
  type: 'success' | 'error';
  text: string;
};

const WEEKDAYS = [
  { label: '일', className: 'sunday' },
  { label: '월', className: '' },
  { label: '화', className: '' },
  { label: '수', className: '' },
  { label: '목', className: '' },
  { label: '금', className: '' },
  { label: '토', className: 'saturday' },
];

function getDateClassName(date: Date, selectedDate: string, today: string) {
  const classNames = ['calendar-day'];
  const dateKey = toDateKey(date);
  const weekday = date.getDay();

  if (dateKey < today) {
    classNames.push('past');
  }

  if (dateKey === selectedDate) {
    classNames.push('selected');
  }

  if (weekday === 0) {
    classNames.push('sunday');
  }

  if (weekday === 6) {
    classNames.push('saturday');
  }

  return classNames.join(' ');
}

export function LeavePlanPanel() {
  const [selectedLeaveType, setSelectedLeaveType] = useState<LeaveType | null>(null);
  const today = useMemo(() => toDateKey(new Date()), []);
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(new Date()));
  const [selectedDate, setSelectedDate] = useState(today);
  const [leaves, setLeaves] = useState<MemberLeavePlanResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<Message | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [completeOpen, setCompleteOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<MemberLeavePlanResponse | null>(null);
  const [deleteCompleteOpen, setDeleteCompleteOpen] = useState(false);
  const days = useMemo(() => getMonthDays(visibleMonth), [visibleMonth]);
  const emptyDays = useMemo(() => Array.from({ length: visibleMonth.getDay() }, (_, index) => index), [visibleMonth]);
  const leavesByDate = useMemo(() => {
    const grouped = new Map<string, MemberLeavePlanResponse[]>();
    for (const leave of leaves) {
      grouped.set(leave.leaveDate, [...(grouped.get(leave.leaveDate) || []), leave]);
    }

    return grouped;
  }, [leaves]);

  useEffect(() => {
    void loadLeaves();
  }, []);

  const moveMonth = (amount: number) => {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1));
  };

  const loadLeaves = async () => {
    setLoading(true);
    try {
      const responses = await apiRequest<MemberLeavePlanResponse[]>('/api/leaves/me/plan');
      setLeaves(responses);
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '휴무 내역을 불러오지 못했습니다.' });
    } finally {
      setLoading(false);
    }
  };

  const openConfirm = () => {
    if (!selectedLeaveType) {
      setMessage({ type: 'error', text: '휴가 종류를 선택해주세요.' });
      return;
    }
    if (selectedDate < today) {
      setMessage({ type: 'error', text: '오늘보다 이전 날짜는 휴무 신청을 할 수 없습니다.' });
      return;
    }

    setMessage(null);
    setConfirmOpen(true);
  };

  const submitLeave = async () => {
    if (!selectedLeaveType) {
      return;
    }

    setSubmitting(true);
    setMessage(null);
    try {
      await apiRequest<LeaveResponse>('/api/leaves', {
        method: 'POST',
        body: JSON.stringify({
          leaveDate: selectedDate,
          leaveType: toApiLeaveType(selectedLeaveType),
        }),
      });
      setConfirmOpen(false);
      setCompleteOpen(true);
      await loadLeaves();
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '휴무 신청에 실패했습니다.' });
    } finally {
      setSubmitting(false);
    }
  };

  const deleteLeave = async () => {
    if (!deleteTarget?.id) {
      return;
    }

    setSubmitting(true);
    setMessage(null);
    try {
      await apiRequest<void>(`/api/leaves/${deleteTarget.id}`, {
        method: 'DELETE',
      });
      setDeleteTarget(null);
      setDeleteCompleteOpen(true);
      await loadLeaves();
    } catch (error) {
      setMessage({ type: 'error', text: error instanceof Error ? error.message : '휴무 신청 취소에 실패했습니다.' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="member-panel">
      <div className="member-calendar-header">
        <button type="button" aria-label="이전 달" onClick={() => moveMonth(-1)}>‹</button>
        <strong>{visibleMonth.getFullYear()}년 {visibleMonth.getMonth() + 1}월</strong>
        <button type="button" aria-label="다음 달" onClick={() => moveMonth(1)}>›</button>
      </div>
      <div className="member-calendar-grid" aria-label="휴무 달력">
        {WEEKDAYS.map((day) => (
          <span className={`calendar-weekday ${day.className}`} key={day.label}>
            {day.label}
          </span>
        ))}
        {emptyDays.map((day) => (
          <span className="calendar-empty" key={`empty-${day}`} />
        ))}
        {days.map((date) => {
          const dateKey = toDateKey(date);
          const past = dateKey < today;
          const dayLeaves = leavesByDate.get(dateKey) || [];

          return (
            <button
              className={getDateClassName(date, selectedDate, today)}
              disabled={past}
              type="button"
              key={dateKey}
              onClick={() => setSelectedDate(dateKey)}
            >
              <span className="member-calendar-date-number">{date.getDate()}</span>
              {dayLeaves.length > 0 && (
                <span className="member-calendar-leave-badges">
                  {dayLeaves.slice(0, 2).map((leave, index) => (
                    <span
                      className={`member-calendar-leave-badge ${toLeaveBadgeClassName(leave)}`}
                      key={`${leave.source}-${leave.id ?? leave.label}-${index}`}
                    >
                      {toCalendarLeaveLabel(leave)}
                    </span>
                  ))}
                </span>
              )}
            </button>
          );
        })}
      </div>
      <div className="leave-type-actions">
        <button
          className={selectedLeaveType === '월차' ? 'monthly active' : 'monthly'}
          type="button"
          onClick={() => setSelectedLeaveType('월차')}
        >
          월차
        </button>
        <button
          className={selectedLeaveType === '오전반차' ? 'active' : ''}
          type="button"
          onClick={() => setSelectedLeaveType('오전반차')}
        >
          오전반차
        </button>
        <button
          className={selectedLeaveType === '오후반차' ? 'active' : ''}
          type="button"
          onClick={() => setSelectedLeaveType('오후반차')}
        >
          오후반차
        </button>
      </div>
      <button className="member-primary-action" type="button" disabled={submitting} onClick={openConfirm}>
        <span className="check-icon" aria-hidden="true">✓</span>
        {submitting ? '신청 중' : '신청하기'}
      </button>
      {message && <p className={`side-dish-message ${message.type}`}>{message.text}</p>}
      <section className="member-list-box">
        {loading ? (
          <p>휴무 내역을 불러오는 중입니다.</p>
        ) : leaves.length === 0 ? (
          <p>내역이 없습니다.</p>
        ) : (
          <div className="leave-history-list">
            {leaves.map((leave, index) => {
              const canCancel = leave.source === 'LEAVE' && leave.leaveDate >= today && Boolean(leave.id);
              const isManagerLeave = leave.source === 'SPECIAL_LEAVE';

              return (
                <article
                  className={`leave-history-card${canCancel ? '' : ' without-cancel'}${isManagerLeave ? ' manager-leave' : ''}`}
                  key={`${leave.source}-${leave.id ?? leave.leaveDate}-${leave.label}-${index}`}
                >
                  <div>
                    <strong>{formatCompactLeaveDate(leave.leaveDate)}</strong>
                  </div>
                  <div className="leave-history-actions">
                    <span>{leave.label}</span>
                    {canCancel && (
                      <button type="button" onClick={() => setDeleteTarget(leave)}>⊗ 취소</button>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
      {confirmOpen &&
        createPortal(
          <LeaveConfirmModal
            dateLabel={formatLeaveDate(selectedDate)}
            leaveType={selectedLeaveType}
            submitting={submitting}
            onClose={() => setConfirmOpen(false)}
            onConfirm={submitLeave}
          />,
          document.body
        )}
      {completeOpen &&
        createPortal(
          <LeaveCompleteModal onClose={() => setCompleteOpen(false)} />,
          document.body
        )}
      {deleteTarget &&
        createPortal(
          <LeaveDeleteConfirmModal
            leave={deleteTarget}
            submitting={submitting}
            onClose={() => setDeleteTarget(null)}
            onConfirm={deleteLeave}
          />,
          document.body
        )}
      {deleteCompleteOpen &&
        createPortal(
          <LeaveDeleteCompleteModal onClose={() => setDeleteCompleteOpen(false)} />,
          document.body
        )}
    </div>
  );
}

type LeaveConfirmModalProps = {
  dateLabel: string;
  leaveType: LeaveType | null;
  submitting: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

function LeaveConfirmModal({ dateLabel, leaveType, submitting, onClose, onConfirm }: LeaveConfirmModalProps) {
  return (
    <div className="side-dish-modal-backdrop" role="presentation">
      <section className="side-dish-small-modal" role="dialog" aria-modal="true" aria-labelledby="leave-confirm-title">
        <h2 id="leave-confirm-title">휴무 신청</h2>
        <p>{dateLabel} {leaveType}를 신청하시겠습니까?</p>
        <div className="side-dish-modal-actions">
          <button className="side-dish-modal-cancel" type="button" disabled={submitting} onClick={onClose}>닫기</button>
          <button className="side-dish-modal-submit" type="button" disabled={submitting} onClick={onConfirm}>
            {submitting ? '신청 중' : '확인'}
          </button>
        </div>
      </section>
    </div>
  );
}

function LeaveCompleteModal({ onClose }: { onClose: () => void }) {
  return (
    <div className="side-dish-modal-backdrop" role="presentation">
      <section className="side-dish-small-modal" role="alertdialog" aria-modal="true" aria-labelledby="leave-complete-title">
        <h2 id="leave-complete-title">신청 완료</h2>
        <p>휴무 신청이 완료되었습니다.</p>
        <button className="side-dish-alert-close" type="button" onClick={onClose}>확인</button>
      </section>
    </div>
  );
}

type LeaveDeleteConfirmModalProps = {
  leave: MemberLeavePlanResponse;
  submitting: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

function LeaveDeleteConfirmModal({ leave, submitting, onClose, onConfirm }: LeaveDeleteConfirmModalProps) {
  return (
    <div className="side-dish-modal-backdrop" role="presentation">
      <section className="side-dish-small-modal" role="dialog" aria-modal="true" aria-labelledby="leave-delete-title">
        <h2 id="leave-delete-title">휴무 취소</h2>
        <p>{formatLeaveDate(leave.leaveDate)} {leave.label} 신청을 취소하시겠습니까?</p>
        <div className="side-dish-modal-actions">
          <button className="side-dish-modal-cancel" type="button" disabled={submitting} onClick={onClose}>닫기</button>
          <button className="side-dish-modal-danger" type="button" disabled={submitting} onClick={onConfirm}>
            {submitting ? '취소 중' : '취소'}
          </button>
        </div>
      </section>
    </div>
  );
}

function LeaveDeleteCompleteModal({ onClose }: { onClose: () => void }) {
  return (
    <div className="side-dish-modal-backdrop" role="presentation">
      <section className="side-dish-small-modal" role="alertdialog" aria-modal="true" aria-labelledby="leave-delete-complete-title">
        <h2 id="leave-delete-complete-title">취소 완료</h2>
        <p>휴무 신청이 취소되었습니다.</p>
        <button className="side-dish-alert-close" type="button" onClick={onClose}>확인</button>
      </section>
    </div>
  );
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function getMonthDays(month: Date) {
  const lastDate = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();

  return Array.from({ length: lastDate }, (_, index) => new Date(month.getFullYear(), month.getMonth(), index + 1));
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function toApiLeaveType(leaveType: LeaveType): ApiLeaveType {
  if (leaveType === '월차') {
    return 'FULL';
  }
  if (leaveType === '오전반차') {
    return 'MORNING';
  }

  return 'AFTERNOON';
}

function toCalendarLeaveLabel(leave: MemberLeavePlanResponse) {
  if (leave.source === 'SPECIAL_LEAVE') {
    return leave.label;
  }
  if (leave.leaveType === 'FULL') {
    return '월차';
  }
  if (leave.leaveType === 'MORNING') {
    return '오전';
  }

  return '오후';
}

function toLeaveBadgeClassName(leave: MemberLeavePlanResponse) {
  if (leave.source === 'SPECIAL_LEAVE') {
    return 'manager';
  }
  if (leave.leaveType === 'FULL') {
    return 'full';
  }
  if (leave.leaveType === 'MORNING') {
    return 'morning';
  }

  return 'afternoon';
}

function formatLeaveDate(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00`);
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

  return `${date.getFullYear()}. ${String(date.getMonth() + 1).padStart(2, '0')}. ${String(date.getDate()).padStart(2, '0')}. (${weekdays[date.getDay()]})`;
}

function formatCompactLeaveDate(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00`);
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

  return `${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}(${weekdays[date.getDay()]})`;
}
