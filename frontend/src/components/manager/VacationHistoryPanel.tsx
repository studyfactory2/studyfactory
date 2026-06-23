import { useEffect, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { Branch, MemberResponse, MonthlyLeaveCalendarResponse } from '../../types/domain';
import { Dropdown, type DropdownOption } from '../common/Dropdown';

type VacationHistoryPanelProps = {
  branches: Branch[];
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

export function VacationHistoryPanel({ branches }: VacationHistoryPanelProps) {
  const branchOptions = [{ value: '', label: '전체 지점' }, ...toBranchOptions(branches)];
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [selectedMember, setSelectedMember] = useState<MemberResponse | null>(null);
  const [visibleMonth, setVisibleMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [monthlyLeaves, setMonthlyLeaves] = useState<Record<string, MonthlyLeaveCalendarResponse[]>>({});
  const [name, setName] = useState('');
  const [selectedBranchId, setSelectedBranchId] = useState('');
  const [branchOpen, setBranchOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [message, setMessage] = useState('');
  const selectedBranchOption = branchOptions.find((branch) => branch.value === selectedBranchId) || branchOptions[0];

  useEffect(() => {
    if (selectedMember) {
      return;
    }

    const timeoutId = window.setTimeout(() => {
      void loadMembers();
    }, 180);

    return () => window.clearTimeout(timeoutId);
  }, [name, selectedBranchId, selectedMember]);

  useEffect(() => {
    if (!selectedMember) {
      return;
    }

    void loadMonthlyLeaves(selectedMember, visibleMonth);
  }, [selectedMember, visibleMonth]);

  const loadMembers = async () => {
    setLoading(true);
    setMessage('');
    try {
      const params = new URLSearchParams();
      if (name.trim()) {
        params.set('name', name.trim());
      }
      if (selectedBranchId) {
        params.set('branchId', selectedBranchId);
      }
      const query = params.toString();
      const responses = await apiRequest<MemberResponse[]>(`/api/members${query ? `?${query}` : ''}`);
      setMembers(responses);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '사원별 휴가 현황을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const loadMonthlyLeaves = async (member: MemberResponse, month: Date) => {
    setCalendarLoading(true);
    setMessage('');
    try {
      const params = new URLSearchParams({
        memberId: String(member.id),
        year: String(month.getFullYear()),
        month: String(month.getMonth() + 1),
      });
      const responses = await apiRequest<MonthlyLeaveCalendarResponse[]>(`/api/leaves/monthly-calendar?${params.toString()}`);
      setMonthlyLeaves(toMonthlyLeaves(responses));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '휴가 현황을 불러오지 못했습니다.');
    } finally {
      setCalendarLoading(false);
    }
  };

  const openMemberCalendar = (member: MemberResponse) => {
    setSelectedMember(member);
    setVisibleMonth(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
    setMonthlyLeaves({});
    setMessage('');
  };

  const closeMemberCalendar = () => {
    setSelectedMember(null);
    setMonthlyLeaves({});
    setMessage('');
  };

  const moveMonth = (amount: number) => {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1));
  };

  if (selectedMember) {
    const calendarCells = getCalendarCells(visibleMonth);

    return (
      <div className="vacation-history-panel">
        <header className="vacation-detail-header">
          <button type="button" aria-label="뒤로가기" onClick={closeMemberCalendar}>
            <BackIcon />
          </button>
          <div>
            <h2>{selectedMember.name}</h2>
            <span>{findBranchName(branches, selectedMember.branchId)} 휴가 현황</span>
          </div>
        </header>
        <section className="vacation-calendar-panel">
          <div className="vacation-calendar-month">
            <button type="button" aria-label="이전 달" onClick={() => moveMonth(-1)}>
              <BackIcon />
            </button>
            <strong>{formatMonth(visibleMonth)}</strong>
            <button type="button" aria-label="다음 달" onClick={() => moveMonth(1)}>
              <ChevronIcon />
            </button>
          </div>
          <div className="vacation-calendar-weekdays" aria-hidden="true">
            {WEEKDAYS.map((weekday) => (
              <span className={weekday.className} key={weekday.label}>{weekday.label}</span>
            ))}
          </div>
          <div className="vacation-calendar-grid" aria-label={`${selectedMember.name} 휴가 달력`}>
            {calendarCells.map((day, index) => (
              day ? (
                <div className="vacation-calendar-day" key={toDateKey(day)}>
                  <strong className={getDayClassName(day)}>{day.getDate()}</strong>
                  {(monthlyLeaves[toDateKey(day)] || []).slice(0, 2).map((leaveStatus, leaveIndex) => (
                    <span
                      className={`vacation-leave-badge ${toLeaveBadgeClassName(leaveStatus)}`}
                      key={`${toDateKey(day)}-${leaveStatus.source}-${leaveStatus.label}-${leaveIndex}`}
                    >
                      {leaveStatus.label}
                    </span>
                  ))}
                </div>
              ) : (
                <span className="vacation-calendar-blank" key={`blank-${index}`} />
              )
            ))}
          </div>
          {calendarLoading && <p className="vacation-calendar-message">불러오는 중입니다.</p>}
          {!calendarLoading && message && <p className="vacation-calendar-message">{message}</p>}
        </section>
      </div>
    );
  }

  return (
    <div className="vacation-history-panel">
      <header className="vacation-history-header">
        <button type="button" aria-label="뒤로가기" onClick={() => { window.location.href = '/managerdashboard?view=grid'; }}>
          <BackIcon />
        </button>
        <h2>사원별 휴가 현황</h2>
        <div className="vacation-history-branch">
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
      <label className="vacation-history-search">
        <input
          aria-label="이름 검색"
          placeholder="이름 검색"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </label>
      {loading ? (
        <p className="vacation-history-empty">불러오는 중입니다.</p>
      ) : message ? (
        <p className="vacation-history-empty">{message}</p>
      ) : members.length === 0 ? (
        <p className="vacation-history-empty">조회된 사원이 없습니다.</p>
      ) : (
        <div className="vacation-history-list">
          {members.map((member) => (
            <button className="vacation-history-card" type="button" key={member.id} onClick={() => openMemberCalendar(member)}>
              <span>
                <strong>{member.name}</strong>
                <small>
                  {findBranchName(branches, member.branchId)}
                  <i>|</i>
                  {toRoleLabel(member.role)}
                </small>
              </span>
              <ChevronIcon />
            </button>
          ))}
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

function toRoleLabel(role: MemberResponse['role']) {
  if (role === 'ADMIN') {
    return '관리자';
  }
  if (role === 'STAFF') {
    return '스탭';
  }
  return '회원';
}

function getMonthDays(month: Date) {
  const lastDate = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();

  return Array.from({ length: lastDate }, (_, index) => new Date(month.getFullYear(), month.getMonth(), index + 1));
}

function getCalendarCells(month: Date) {
  const days = getMonthDays(month);
  const firstDay = days[0]?.getDay() || 0;

  return [...Array.from<Date | null>({ length: firstDay }).fill(null), ...days];
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function formatMonth(date: Date) {
  return `${date.getFullYear()}. ${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function getDayClassName(date: Date) {
  if (date.getDay() === 0) {
    return 'sunday';
  }
  if (date.getDay() === 6) {
    return 'saturday';
  }
  return '';
}

function toMonthlyLeaves(responses: MonthlyLeaveCalendarResponse[]) {
  return responses.reduce<Record<string, MonthlyLeaveCalendarResponse[]>>((accumulator, response) => {
    const current = accumulator[response.leaveDate] || [];
    accumulator[response.leaveDate] = [...current, response].sort((first, second) => (
      toSourceOrder(first.source) - toSourceOrder(second.source)
    ));

    return accumulator;
  }, {});
}

function toSourceOrder(source: MonthlyLeaveCalendarResponse['source']) {
  if (source === 'LEAVE') {
    return 1;
  }
  if (source === 'FIXED_LEAVE') {
    return 2;
  }

  return 3;
}

function toLeaveBadgeClassName(leaveStatus: MonthlyLeaveCalendarResponse) {
  if (leaveStatus.source === 'FIXED_LEAVE') {
    return 'fixed';
  }
  if (leaveStatus.source === 'SPECIAL_LEAVE') {
    return 'special';
  }
  if (leaveStatus.label === '오후') {
    return 'afternoon';
  }

  return 'leave';
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

function ChevronIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}
