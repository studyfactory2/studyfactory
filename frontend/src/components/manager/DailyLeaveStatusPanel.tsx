import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { apiRequest } from '../../api/client';
import { Dropdown } from '../common/Dropdown';
import type { Branch, DailyLeaveStatusResponse, LeaveType } from '../../types/domain';

type DailyLeaveStatusPanelProps = {
  branches: Branch[];
};

const LEAVE_FILTERS: Array<{ value: LeaveType; label: string }> = [
  { value: 'FULL', label: '월차' },
  { value: 'MORNING', label: '오전' },
  { value: 'AFTERNOON', label: '오후' },
];
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

export function DailyLeaveStatusPanel({ branches }: DailyLeaveStatusPanelProps) {
  const [branchId, setBranchId] = useState(() => branches[0]?.id ? String(branches[0].id) : '');
  const [branchOpen, setBranchOpen] = useState(false);
  const [searchName, setSearchName] = useState('');
  const [selectedDate, setSelectedDate] = useState(() => toDateKey(new Date()));
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState(() => new Date());
  const [selectedLeaveTypes, setSelectedLeaveTypes] = useState<LeaveType[]>(() => LEAVE_FILTERS.map((filter) => filter.value));
  const [statuses, setStatuses] = useState<DailyLeaveStatusResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const branchOptions = useMemo(() => branches.map((branch) => ({ value: String(branch.id), label: branch.name })), [branches]);
  const selectedBranchOption = branchOptions.find((option) => option.value === branchId) || branchOptions[0] || { value: '', label: '망미점' };
  const calendarCells = useMemo(() => getCalendarCells(visibleMonth), [visibleMonth]);

  useEffect(() => {
    if (branchId || !branches[0]?.id) {
      return;
    }

    setBranchId(String(branches[0].id));
  }, [branchId, branches]);

  useEffect(() => {
    void loadStatuses();
  }, [branchId, selectedDate]);

  const filteredStatuses = useMemo(() => statuses.filter((status) => selectedLeaveTypes.includes(status.leaveType)), [statuses, selectedLeaveTypes]);

  const loadStatuses = async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams({ date: selectedDate });
      if (searchName.trim()) {
        params.set('name', searchName.trim());
      }
      if (branchId) {
        params.set('branchId', branchId);
      }

      const response = await apiRequest<DailyLeaveStatusResponse[]>(`/api/leaves/daily-status?${params.toString()}`);
      setStatuses(response);
      setMessage(null);
    } catch (error) {
      setStatuses([]);
      setMessage(error instanceof Error ? error.message : '휴무 현황을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void loadStatuses();
  };

  const selectBranch = (value: string) => {
    setBranchId(value);
    setBranchOpen(false);
  };

  const changeMonth = (amount: number) => {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1));
  };

  const selectDate = (date: Date) => {
    setSelectedDate(toDateKey(date));
    setVisibleMonth(new Date(date.getFullYear(), date.getMonth(), 1));
    setCalendarOpen(false);
  };

  const toggleLeaveType = (leaveType: LeaveType) => {
    setSelectedLeaveTypes((current) => {
      if (current.includes(leaveType)) {
        return current.filter((selectedType) => selectedType !== leaveType);
      }

      return [...current, leaveType];
    });
  };

  return (
    <div className="daily-leave-panel">
      <div className="daily-leave-title">
        <a href="/managerdashboard?view=staff-page" aria-label="스텝페이지로 돌아가기">
          <BackIcon />
        </a>
        <h2>일별 사원 휴무 현황</h2>
      </div>

      <div className="daily-leave-controls">
        <form className="daily-leave-search" onSubmit={submitSearch}>
          <SearchIcon />
          <input
            value={searchName}
            placeholder="이름 검색"
            onChange={(event) => setSearchName(event.target.value)}
          />
        </form>
        <Dropdown
          classNamePrefix="custom-select"
          label="지점"
          open={branchOpen}
          options={branchOptions.length > 0 ? branchOptions : [{ value: '', label: '망미점' }]}
          selectedOption={selectedBranchOption}
          onSelect={selectBranch}
          onToggle={() => setBranchOpen((current) => !current)}
        />
      </div>

      <div className="daily-leave-calendar-field">
        <button className="daily-leave-date" type="button" onClick={() => setCalendarOpen((current) => !current)}>
          <span>{formatDateLabel(selectedDate)}</span>
          <CalendarIcon />
        </button>
        {calendarOpen && (
          <div className="daily-leave-calendar-popover">
            <div className="daily-leave-calendar-header">
              <button type="button" aria-label="이전 달" onClick={() => changeMonth(-1)}>‹</button>
              <strong>{visibleMonth.getFullYear()}년 {visibleMonth.getMonth() + 1}월</strong>
              <button type="button" aria-label="다음 달" onClick={() => changeMonth(1)}>›</button>
            </div>
            <div className="daily-leave-calendar-grid" aria-label="휴무 현황 날짜 선택">
              {WEEKDAYS.map((weekday, index) => (
                <span className={`calendar-weekday${index === 0 ? ' sunday' : ''}${index === 6 ? ' saturday' : ''}`} key={weekday}>{weekday}</span>
              ))}
              {calendarCells.map((date, index) => {
                if (!date) {
                  return <span className="calendar-empty" key={`empty-${index}`} />;
                }
                const dateKey = toDateKey(date);
                const day = date.getDay();

                return (
                  <button
                    className={`${dateKey === selectedDate ? 'selected' : ''}${day === 0 ? ' sunday' : ''}${day === 6 ? ' saturday' : ''}`}
                    type="button"
                    key={dateKey}
                    onClick={() => selectDate(date)}
                  >
                    {date.getDate()}
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </div>

      <div className="daily-leave-filter-buttons">
        {LEAVE_FILTERS.map((filter) => (
          <button
            className={selectedLeaveTypes.includes(filter.value) ? 'active' : 'inactive'}
            type="button"
            key={filter.value}
            onClick={() => toggleLeaveType(filter.value)}
          >
            {filter.label}
          </button>
        ))}
      </div>

      <div className="daily-leave-list">
        {loading ? (
          <p className="daily-leave-empty">불러오는 중입니다.</p>
        ) : message ? (
          <p className="daily-leave-empty">{message}</p>
        ) : filteredStatuses.length === 0 ? (
          <p className="daily-leave-empty">휴무 내역이 없습니다.</p>
        ) : (
          filteredStatuses.map((status) => (
            <article className={`daily-leave-card ${toLeaveCardClassName(status.leaveType)}`} key={`${status.name}-${status.createdAt}-${status.leaveType}`}>
              <div>
                <strong>{status.name}</strong>
                <span>{status.branch}</span>
              </div>
              <div>
                <strong>{toLeaveTypeLabel(status.leaveType)}</strong>
                <span>{formatCreatedAt(status.createdAt)}</span>
              </div>
            </article>
          ))
        )}
      </div>
    </div>
  );
}

function toLeaveTypeLabel(leaveType: LeaveType) {
  if (leaveType === 'FULL') {
    return '월차';
  }
  if (leaveType === 'MORNING') {
    return '오전반차';
  }

  return '오후반차';
}

function toLeaveCardClassName(leaveType: LeaveType) {
  if (leaveType === 'AFTERNOON') {
    return 'afternoon';
  }

  return 'morning';
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function getCalendarCells(month: Date) {
  const firstDate = new Date(month.getFullYear(), month.getMonth(), 1);
  const lastDate = new Date(month.getFullYear(), month.getMonth() + 1, 0);
  const cells: Array<Date | null> = [];

  for (let index = 0; index < firstDate.getDay(); index += 1) {
    cells.push(null);
  }
  for (let day = 1; day <= lastDate.getDate(); day += 1) {
    cells.push(new Date(month.getFullYear(), month.getMonth(), day));
  }

  return cells;
}

function formatDateLabel(value: string) {
  const date = new Date(`${value}T00:00:00`);

  return `${date.getFullYear()} . ${date.getMonth() + 1} . ${date.getDate()} . (${WEEKDAYS[date.getDay()]})`;
}

function formatCreatedAt(value: string) {
  const date = new Date(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');

  return `${year}.${month}.${day}(${WEEKDAYS[date.getDay()]}) ${hour}:${minute}`;
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
      <circle cx="11" cy="11" r="6" />
      <path d="m16 16 4 4" />
    </svg>
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
