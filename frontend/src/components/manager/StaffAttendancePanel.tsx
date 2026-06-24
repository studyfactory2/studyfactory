import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { apiRequest } from '../../api/client';
import type { DailyAttendanceBoardResponse } from '../../types/domain';

const SLOT_LABELS = [1, 2, 3, 4, 5, 6, 7];

export function StaffAttendancePanel() {
  const [board, setBoard] = useState<DailyAttendanceBoardResponse | null>(null);
  const [selectedDate, setSelectedDate] = useState(() => toDateKey(new Date()));
  const [name, setName] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const branchId = localStorage.getItem('branchId');
  const filteredRows = useMemo(() => {
    const rows = board?.rows || [];
    const keyword = name.trim();
    if (!keyword) {
      return rows;
    }

    return rows.filter((row) => row.name.includes(keyword) || String(row.seatNumber || '').includes(keyword));
  }, [board, name]);
  const todoCount = useMemo(() => filteredRows.filter((row) => row.slots.some((slot) => slot !== 'X')).length, [filteredRows]);
  const noteCount = useMemo(() => filteredRows.filter((row) => row.slots.some((slot) => slot !== 'X' && slot !== '월차')).length, [filteredRows]);

  useEffect(() => {
    void loadBoard(selectedDate);
  }, [selectedDate]);

  const loadBoard = async (date: string) => {
    setLoading(true);
    setMessage('');
    try {
      const params = new URLSearchParams({ date });
      if (branchId) {
        params.set('branchId', branchId);
      }
      const response = await apiRequest<DailyAttendanceBoardResponse>(`/api/attendances/daily-board?${params.toString()}`);
      setBoard(response);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '출석부를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const moveDate = (amount: number) => {
    const date = new Date(`${selectedDate}T00:00:00`);
    date.setDate(date.getDate() + amount);
    setSelectedDate(toDateKey(date));
  };

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!name.trim()) {
      setSearchOpen(false);
    }
  };

  return (
    <div className="staff-attendance-panel">
      <div className="staff-attendance-tools">
        <div className="staff-search-slot">
          {searchOpen ? (
            <form className="staff-attendance-search" onSubmit={submitSearch}>
              <SearchIcon />
              <input
                aria-label="이름검색"
                autoFocus
                placeholder="이름 검색"
                value={name}
                onBlur={() => {
                  if (!name.trim()) {
                    setSearchOpen(false);
                  }
                }}
                onChange={(event) => setName(event.target.value)}
              />
              <button type="submit" aria-label="검색" />
            </form>
          ) : (
            <button className="staff-search-button" type="button" onClick={() => setSearchOpen(true)}>
              <SearchIcon />
              <span>이름검색</span>
            </button>
          )}
        </div>
        <div className="staff-date-control">
          <button type="button" aria-label="이전 날짜" onClick={() => moveDate(-1)}>
            ‹
          </button>
          <strong>{formatKoreanDate(selectedDate)}</strong>
          <button type="button" aria-label="다음 날짜" onClick={() => moveDate(1)}>
            ›
          </button>
        </div>
      </div>

      <div className="staff-attendance-actions">
        <button type="button" disabled>회원건의</button>
        <button type="button" disabled>반찬신청</button>
        <button type="button">할일목록 <b>{todoCount}</b></button>
        <button type="button">출석참고 <b>{noteCount}</b></button>
      </div>

      {loading ? (
        <p className="staff-attendance-message">불러오는 중입니다.</p>
      ) : message ? (
        <p className="staff-attendance-message">{message}</p>
      ) : (
        <div className="staff-attendance-table-wrap">
          <table className="staff-attendance-table">
            <colgroup>
              <col className="staff-seat-column" />
              <col className="staff-name-column" />
              {SLOT_LABELS.map((slot) => (
                <col className="staff-slot-column" key={slot} />
              ))}
            </colgroup>
            <thead>
              <tr>
                <th rowSpan={2}>좌석</th>
                <th rowSpan={2}>이름</th>
                <th colSpan={7}>{formatShortDate(selectedDate)}</th>
              </tr>
              <tr>
                {SLOT_LABELS.map((slot) => (
                  <th key={slot}>{slot}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filteredRows.map((row) => {
                const emptySeat = row.name === '공석';
                const rowClassName = row.seatNumber == null ? 'unassigned-row' : emptySeat ? 'empty-seat-row' : '';

                return (
                  <tr className={rowClassName} key={`${row.seatNumber || 'unassigned'}-${row.name}`}>
                    <td>{row.seatNumber ?? '-'}</td>
                    <td>{row.name}</td>
                    {SLOT_LABELS.map((slot, index) => {
                      const status = row.slots[index] || 'X';

                      return (
                        <td className={toStatusClassName(status, emptySeat)} key={slot}>
                          <span>{status}</span>
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="staff-attendance-command-bar" aria-hidden="true">
            <button className="command-present" type="button">O</button>
            <button className="command-absent" type="button">X</button>
            <button className="command-other" type="button">기타</button>
            <button className="command-undo" type="button">↩</button>
          </div>
        </div>
      )}
    </div>
  );
}

function toDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function formatKoreanDate(date: string) {
  const parsed = new Date(`${date}T00:00:00`);
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

  return `${parsed.getFullYear()}.${String(parsed.getMonth() + 1).padStart(2, '0')}.${String(parsed.getDate()).padStart(2, '0')} (${weekdays[parsed.getDay()]})`;
}

function formatShortDate(date: string) {
  const parsed = new Date(`${date}T00:00:00`);
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

  return `${parsed.getMonth() + 1}.${parsed.getDate()}(${weekdays[parsed.getDay()]})`;
}

function toStatusClassName(status: string, emptySeat: boolean) {
  if (emptySeat) {
    return 'status-empty-seat';
  }

  if (status === 'X') {
    return 'status-absent';
  }
  if (status === 'O') {
    return 'status-present';
  }

  return 'status-leave';
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="10.5" cy="10.5" r="5.5" />
      <path d="m15 15 4 4" />
    </svg>
  );
}
