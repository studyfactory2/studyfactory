import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { apiRequest } from '../../api/client';
import type { Branch, DailyAttendanceBoardResponse, DailySideDishResponse, MealType, SuggestionResponse, TodoResponse } from '../../types/domain';

const SLOT_LABELS = [1, 2, 3, 4, 5, 6, 7];
const LEAVE_REASON_SLOTS: Record<string, number[]> = {
  월차: SLOT_LABELS,
  오전반차: [1, 2, 3, 4],
  오후반차: [4, 5, 6, 7],
};
const OTHER_REASON_OPTIONS = ['지각', '조회', '외출', '이동', '시험', '컨디션'];
const STRONG_DIVIDER_SEATS = new Set([8, 18, 23, 28, 33, 38, 43, 48, 53, 59, 63, 67, 71, 75, 80, 83, 84, 88, 91, 94, 97, 100]);
const SOFT_DIVIDER_SEATS = new Set([10, 12, 14, 16, 51]);
const ROOM_END_DIVIDER_SEATS = new Set([55]);
const BOTTOM_DIVIDER_SEATS = new Set([102]);
const SHADED_NAME_SEATS = new Set([8, 9, 10, 11, 12, 13, 14, 15, 16, 17]);
const DARK_SHADED_NAME_SEATS = new Set([53, 54]);
const ALERT_SHADED_NAME_SEATS = new Set([83]);
type SelectedSlot = {
  memberId: number | null;
  seatNumber: number | null;
  name: string;
  slot: number;
};
type SlotStatusUpdateType = 'PRESENT' | 'ABSENT' | 'OTHER';
type DateSlideDirection = 'previous' | 'next' | null;

export function StaffAttendancePanel() {
  const [board, setBoard] = useState<DailyAttendanceBoardResponse | null>(null);
  const [selectedDate, setSelectedDate] = useState(() => toDateKey(new Date()));
  const [name, setName] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [selectedSlot, setSelectedSlot] = useState<SelectedSlot | null>(null);
  const [otherModalOpen, setOtherModalOpen] = useState(false);
  const [fixedLeaveConflictOpen, setFixedLeaveConflictOpen] = useState(false);
  const [otherCalendarDate, setOtherCalendarDate] = useState(() => toDateKey(new Date()));
  const [otherDates, setOtherDates] = useState<string[]>([]);
  const [otherSlots, setOtherSlots] = useState<number[]>([]);
  const [selectedOtherReason, setSelectedOtherReason] = useState('');
  const [otherReason, setOtherReason] = useState('');
  const [sideDishes, setSideDishes] = useState<DailySideDishResponse[]>([]);
  const [sideDishModalOpen, setSideDishModalOpen] = useState(false);
  const [suggestions, setSuggestions] = useState<SuggestionResponse[]>([]);
  const [suggestionModalOpen, setSuggestionModalOpen] = useState(false);
  const [todoModalOpen, setTodoModalOpen] = useState(false);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [todoBranchId, setTodoBranchId] = useState('');
  const [todoBranchOpen, setTodoBranchOpen] = useState(false);
  const [todos, setTodos] = useState<TodoResponse[]>([]);
  const [todoContent, setTodoContent] = useState('');
  const [todoUrgent, setTodoUrgent] = useState(false);
  const [editingTodoId, setEditingTodoId] = useState<number | null>(null);
  const [editingTodoContent, setEditingTodoContent] = useState('');
  const [replyTodoId, setReplyTodoId] = useState<number | null>(null);
  const [replyContent, setReplyContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');
  const [searchedSeatNumber, setSearchedSeatNumber] = useState<number | null>(null);
  const [dateSlideDirection, setDateSlideDirection] = useState<DateSlideDirection>(null);
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const pendingDateSlideDirectionRef = useRef<DateSlideDirection>(null);
  const branchId = localStorage.getItem('branchId');
  const attendanceRows = useMemo(() => board?.rows || [], [board]);
  const todoItems = useMemo(() => [...todos].sort((first, second) => {
    if (first.completed !== second.completed) {
      return Number(first.completed) - Number(second.completed);
    }
    if (first.priority !== second.priority) {
      return first.priority === 'URGENT' ? -1 : 1;
    }

    return new Date(first.createdAt).getTime() - new Date(second.createdAt).getTime();
  }), [todos]);
  const todoCount = useMemo(() => todoItems.filter((item) => !item.completed).length, [todoItems]);
  const suggestionItems = useMemo(() => [...suggestions].sort((first, second) => {
    if (first.isResolved !== second.isResolved) {
      return Number(first.isResolved) - Number(second.isResolved);
    }

    return new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime();
  }), [suggestions]);
  const unresolvedSuggestionCount = useMemo(() => suggestionItems.filter((suggestion) => !suggestion.isResolved).length, [suggestionItems]);
  const otherCalendar = useMemo(() => createMonthCalendar(otherCalendarDate), [otherCalendarDate]);
  const allOtherSlotsSelected = SLOT_LABELS.every((slot) => otherSlots.includes(slot));
  const lunchSideDishes = useMemo(() => sideDishes.filter((sideDish) => sideDish.mealType === 'LUNCH'), [sideDishes]);
  const dinnerSideDishes = useMemo(() => sideDishes.filter((sideDish) => sideDish.mealType === 'DINNER'), [sideDishes]);
  const selectedTodoBranch = useMemo(() => {
    return branches.find((branch) => String(branch.id) === todoBranchId)
      || branches.find((branch) => branch.name === '망미점')
      || branches[0]
      || { id: 1, name: '망미점' };
  }, [branches, todoBranchId]);

  useEffect(() => {
    void loadBranches();
    void loadSuggestions();
  }, []);

  useEffect(() => {
    void loadBoard(selectedDate);
    void loadSideDishes(selectedDate);
  }, [selectedDate]);

  useEffect(() => {
    void loadTodos(selectedDate);
  }, [selectedDate, todoBranchId]);

  useEffect(() => {
    if (searchedSeatNumber == null) {
      return;
    }

    const frameId = window.requestAnimationFrame(() => {
      const targetRow = tableWrapRef.current?.querySelector<HTMLTableRowElement>(`tr[data-seat-number="${searchedSeatNumber}"]`);
      targetRow?.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
    });

    return () => window.cancelAnimationFrame(frameId);
  }, [attendanceRows, searchedSeatNumber]);

  useEffect(() => {
    if (todoModalOpen) {
      void loadTodos(selectedDate);
    }
  }, [todoModalOpen]);

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
      setDateSlideDirection(pendingDateSlideDirectionRef.current);
      pendingDateSlideDirectionRef.current = null;
      setSelectedSlot(null);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '출석부를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const loadSideDishes = async (date: string) => {
    try {
      const params = new URLSearchParams({ date });
      if (branchId) {
        params.set('branchId', branchId);
      }
      const response = await apiRequest<DailySideDishResponse[]>(`/api/side-dishes/daily?${params.toString()}`);
      setSideDishes(response);
    } catch {
      setSideDishes([]);
    }
  };

  const loadSuggestions = async () => {
    try {
      const response = await apiRequest<SuggestionResponse[]>('/api/suggestions');
      setSuggestions(response);
    } catch {
      setSuggestions([]);
    }
  };

  const resolveSuggestion = async (suggestionId: number) => {
    try {
      const response = await apiRequest<SuggestionResponse>(`/api/suggestions/${suggestionId}/resolve`, { method: 'PATCH' });
      setSuggestions((current) => current.map((suggestion) => suggestion.id === suggestionId ? response : suggestion));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '회원건의를 완료 처리하지 못했습니다.');
    }
  };

  const loadBranches = async () => {
    try {
      const response = await apiRequest<Branch[]>('/api/branches');
      setBranches(response);
      setTodoBranchId((current) => {
        if (current) {
          return current;
        }
        const defaultBranch = response.find((branch) => branch.name === '망미점') || response[0];

        return defaultBranch ? String(defaultBranch.id) : '';
      });
    } catch {
      setBranches([]);
      setTodoBranchId((current) => current || branchId || '1');
    }
  };

  const loadTodos = async (date: string) => {
    const selectedBranchId = todoBranchId || branchId || String(selectedTodoBranch.id);
    if (!selectedBranchId) {
      setTodos([]);
      return;
    }

    try {
      const params = new URLSearchParams({ branchId: selectedBranchId, date });
      const response = await apiRequest<TodoResponse[]>(`/api/todos/daily?${params.toString()}`);
      setTodos(response);
    } catch {
      setTodos([]);
    }
  };

  const createTodo = async () => {
    const selectedBranchId = todoBranchId || branchId || String(selectedTodoBranch.id);
    if (!selectedBranchId || !todoContent.trim()) {
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      await apiRequest<TodoResponse>('/api/todos', {
        method: 'POST',
        body: JSON.stringify({
          branchId: Number(selectedBranchId),
          todoDate: selectedDate,
          content: todoContent.trim(),
          priority: todoUrgent ? 'URGENT' : 'NORMAL',
        }),
      });
      setTodoContent('');
      setTodoUrgent(false);
      await loadTodos(selectedDate);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '할 일을 저장하지 못했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const updateTodoCompletion = async (todo: TodoResponse, completed: boolean) => {
    setTodos((current) => current.map((item) => item.id === todo.id ? { ...item, completed } : item));
    try {
      const updatedTodo = await apiRequest<TodoResponse>(`/api/todos/${todo.id}/completion`, {
        method: 'PATCH',
        body: JSON.stringify({ completed }),
      });
      setTodos((current) => current.map((item) => item.id === updatedTodo.id ? updatedTodo : item));
    } catch (error) {
      setTodos((current) => current.map((item) => item.id === todo.id ? todo : item));
      setMessage(error instanceof Error ? error.message : '할 일 상태를 저장하지 못했습니다.');
    }
  };

  const submitTodoEdit = async (todo: TodoResponse) => {
    if (!editingTodoContent.trim()) {
      return;
    }

    setSubmitting(true);
    try {
      const updatedTodo = await apiRequest<TodoResponse>(`/api/todos/${todo.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ content: editingTodoContent.trim() }),
      });
      setTodos((current) => current.map((item) => item.id === updatedTodo.id ? updatedTodo : item));
      setEditingTodoId(null);
      setEditingTodoContent('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '할 일을 수정하지 못했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const submitTodoReply = async (todo: TodoResponse) => {
    if (!replyContent.trim()) {
      return;
    }

    setSubmitting(true);
    try {
      const updatedTodo = await apiRequest<TodoResponse>(`/api/todos/${todo.id}/reply`, {
        method: 'PATCH',
        body: JSON.stringify({ replyContent: replyContent.trim() }),
      });
      setTodos((current) => current.map((item) => item.id === updatedTodo.id ? updatedTodo : item));
      setReplyTodoId(null);
      setReplyContent('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '답글을 저장하지 못했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const deleteTodo = async (todo: TodoResponse) => {
    setSubmitting(true);
    try {
      await apiRequest<void>(`/api/todos/${todo.id}`, { method: 'DELETE' });
      setTodos((current) => current.filter((item) => item.id !== todo.id));
      if (replyTodoId === todo.id) {
        setReplyTodoId(null);
        setReplyContent('');
      }
      if (editingTodoId === todo.id) {
        setEditingTodoId(null);
        setEditingTodoContent('');
      }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '할 일을 삭제하지 못했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const moveDate = (amount: number) => {
    const date = new Date(`${selectedDate}T00:00:00`);
    date.setDate(date.getDate() + amount);
    pendingDateSlideDirectionRef.current = amount < 0 ? 'previous' : 'next';
    setSelectedDate(toDateKey(date));
  };

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const keyword = name.trim();
    if (!keyword) {
      setSearchedSeatNumber(null);
      setSearchOpen(false);
      return;
    }

    const matchingRow = attendanceRows.find((row) => row.name === keyword || String(row.seatNumber || '') === keyword)
      || attendanceRows.find((row) => row.name.includes(keyword) || String(row.seatNumber || '').includes(keyword));

    if (matchingRow?.seatNumber == null) {
      return;
    }

    setSearchedSeatNumber(matchingRow.seatNumber);
  };

  const updateSlotStatus = async (
    status: SlotStatusUpdateType,
    reason?: string,
    date = selectedDate,
    slot: number | null | undefined = selectedSlot?.slot,
    autoAdvance = false,
  ) => {
    if (!selectedSlot) {
      setMessage('변경할 교시를 먼저 선택해주세요.');
      return;
    }
    if (!selectedSlot.memberId) {
      setMessage('공석은 출석 상태를 변경할 수 없습니다.');
      return;
    }
    if (!slot) {
      setMessage('변경할 교시를 선택해주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    const currentSlot = selectedSlot;
    try {
      await apiRequest<void>('/api/attendances/daily-board/slot', {
        method: 'PATCH',
        body: JSON.stringify({
          memberId: selectedSlot.memberId,
          date,
          slot,
          status,
          reason,
        }),
      });
      if (date === selectedDate) {
        setBoard((current) => current ? {
          ...current,
          rows: current.rows.map((row) => {
            if (row.memberId !== currentSlot.memberId) {
              return row;
            }

            return {
              ...row,
              slots: row.slots.map((currentStatus, index) => index === slot - 1 ? toAttendanceStatusLabel(status, reason) : currentStatus),
            };
          }),
        } : current);
      }
      if (autoAdvance) {
        moveToNextEditableSlot(currentSlot);
      } else {
        setSelectedSlot(currentSlot);
      }
      setOtherModalOpen(false);
      setOtherReason('');
      setSelectedOtherReason('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '출석 상태 변경에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const submitOtherReason = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const reason = otherReason.trim() || selectedOtherReason || '기타';
    await updateOtherSlotStatuses('OTHER', reason, getSlotsForReason(reason));
  };

  const updateOtherSlotStatuses = async (status: SlotStatusUpdateType, reason?: string, targetSlots = otherSlots) => {
    if (!selectedSlot?.memberId) {
      setMessage('변경할 사원을 먼저 선택해주세요.');
      return;
    }
    if (targetSlots.length === 0) {
      setMessage('변경할 교시를 선택해주세요.');
      return;
    }
    if (otherDates.length === 0) {
      setMessage('변경할 날짜를 선택해주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    const currentSlot = selectedSlot;
    try {
      await Promise.all(otherDates.flatMap((date) => targetSlots.map((slot) => apiRequest<void>('/api/attendances/daily-board/slot', {
        method: 'PATCH',
        body: JSON.stringify({
          memberId: currentSlot.memberId,
          date,
          slot,
          status,
          reason,
        }),
      }))));
      if (otherDates.includes(selectedDate)) {
        setBoard((current) => current ? {
          ...current,
          rows: current.rows.map((row) => row.memberId === currentSlot.memberId ? {
            ...row,
            slots: row.slots.map((currentStatus, index) => targetSlots.includes(index + 1)
              ? toAttendanceStatusLabel(status, reason)
              : currentStatus),
          } : row),
        } : current);
      }
      setSelectedSlot(currentSlot);
      setOtherModalOpen(false);
      setOtherSlots([]);
      setOtherDates([]);
      setOtherReason('');
      setSelectedOtherReason('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '출석 상태 변경에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const getSlotsForReason = (reason: string) => LEAVE_REASON_SLOTS[reason] || otherSlots;

  const createFixedLeave = async () => {
    if (!selectedSlot) {
      setMessage('고정신청할 사원을 먼저 선택해주세요.');
      return;
    }
    if (otherSlots.length === 0) {
      setMessage('고정신청할 교시를 선택해주세요.');
      return;
    }
    if (otherDates.length === 0) {
      setMessage('고정신청할 날짜를 선택해주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      await Promise.all(otherDates.map((leaveDate) => apiRequest<void>('/api/leaves/fixed', {
        method: 'POST',
        body: JSON.stringify({
          memberId: selectedSlot.memberId,
          leaveDate,
          slots: otherSlots,
          reason: otherReason.trim() || selectedOtherReason || '기타',
        }),
      })));
      await loadBoard(selectedDate);
      setOtherModalOpen(false);
      setOtherDates([]);
      setOtherReason('');
      setSelectedOtherReason('');
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '고정 휴무 신청에 실패했습니다.';
      if (errorMessage.includes('이미 고정휴무가 있는 교시입니다.')) {
        setFixedLeaveConflictOpen(true);
      } else {
        setMessage(errorMessage);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const resetJoinDateAttendance = async (memberId: number) => {
    setSubmitting(true);
    setMessage('');
    try {
      await apiRequest<void>('/api/attendances/daily-board/member/reset', {
        method: 'PATCH',
        body: JSON.stringify({
          memberId,
          date: selectedDate,
        }),
      });
      setBoard((current) => current ? {
        ...current,
        rows: current.rows.map((row) => row.memberId === memberId ? { ...row, joinDate: null, slots: Array.from({ length: 7 }, () => 'X') } : row),
      } : current);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '신규 회원 출석 초기화에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const isJoinDateRow = (row: DailyAttendanceBoardResponse['rows'][number]) => {
    if (!row.memberId || !row.joinDate) {
      return false;
    }
    const registeredDate = toDatePart(row.createdAt) || row.joinDate;

    return registeredDate <= selectedDate && selectedDate <= row.joinDate;
  };

  const canResetJoinDateAttendance = (row: DailyAttendanceBoardResponse['rows'][number]) => (
    Boolean(row.memberId && row.joinDate === selectedDate && selectedDate === toDateKey(new Date()))
  );

  const moveToNextEditableSlot = (currentSlot = selectedSlot) => {
    if (!currentSlot) {
      return;
    }

    const editableSlots = SLOT_LABELS.flatMap((slot) => attendanceRows
      .filter((row) => row.seatNumber != null && !isJoinDateRow(row))
      .map((row) => ({ memberId: row.memberId ?? null, seatNumber: row.seatNumber as number, name: row.name, slot })));
    const currentIndex = editableSlots.findIndex((slot) => slot.seatNumber === currentSlot.seatNumber && slot.slot === currentSlot.slot);
    const nextSlot = editableSlots[currentIndex + 1];

    if (nextSlot) {
      setSelectedSlot(nextSlot);
      return;
    }

    setSelectedSlot(currentSlot);
  };

  const openOtherModal = () => {
    if (!selectedSlot) {
      return;
    }
    setOtherCalendarDate(selectedDate);
    setOtherDates([selectedDate]);
    setOtherSlots([selectedSlot.slot]);
    setSelectedOtherReason('');
    setOtherReason('');
    setOtherModalOpen(true);
  };

  const moveOtherMonth = (amount: number) => {
    const date = new Date(`${otherCalendarDate}T00:00:00`);
    date.setMonth(date.getMonth() + amount);
    setOtherCalendarDate(toDateKey(date));
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
        <button className={`suggestion-tag${unresolvedSuggestionCount > 0 ? ' has-content' : ''}`} type="button" onClick={() => setSuggestionModalOpen(true)}>
          회원건의
          {unresolvedSuggestionCount > 0 && <b>{unresolvedSuggestionCount}</b>}
        </button>
        <button className={`meal-tag${sideDishes.length > 0 ? ' has-content' : ''}`} type="button" disabled={sideDishes.length === 0} onClick={() => setSideDishModalOpen(true)}>
          반찬신청
          {sideDishes.length > 0 && <b>{sideDishes.length}</b>}
        </button>
        <button className={`todo-tag${todoCount > 0 ? ' has-content' : ''}`} type="button" onClick={() => setTodoModalOpen(true)}>
          출석참고
          {todoCount > 0 && <b>{todoCount}</b>}
        </button>
      </div>

      {loading && !board ? (
        <p className="staff-attendance-message">불러오는 중입니다.</p>
      ) : message ? (
        <p className="staff-attendance-message">{message}</p>
      ) : board ? (
        <div className="staff-attendance-table-wrap" ref={tableWrapRef}>
          <table
            className={`staff-attendance-table${dateSlideDirection ? ` date-slide-${dateSlideDirection}` : ''}`}
            onAnimationEnd={() => setDateSlideDirection(null)}
          >
            <colgroup>
              <col className="staff-seat-column" />
              <col className="staff-name-column" />
              {SLOT_LABELS.map((slot) => (
                <col className="staff-slot-column" key={slot} />
              ))}
            </colgroup>
            <thead>
              <tr>
                <th>좌석</th>
                <th>이름</th>
                {SLOT_LABELS.map((slot) => (
                  <th key={slot}>{slot}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {attendanceRows.map((row) => {
                const emptySeat = row.name === '공석';
                const rowClasses = [
                  row.seatNumber == null ? 'unassigned-row' : emptySeat ? 'empty-seat-row' : '',
                  getDividerClassName(row.seatNumber),
                  getNameCellColorClassName(row.seatNumber),
                  row.seatNumber != null && row.seatNumber >= 55 ? 'second-room-row' : '',
                  row.seatNumber && BOTTOM_DIVIDER_SEATS.has(row.seatNumber) ? 'bottom-divider-row' : '',
                ].filter(Boolean);
                const rowClassName = rowClasses.join(' ');
                const joinDateRow = isJoinDateRow(row);
                const canResetJoinDate = canResetJoinDateAttendance(row);

                return (
                  <tr
                    className={`${rowClassName}${row.seatNumber === searchedSeatNumber ? ' search-target-row' : ''}`}
                    data-seat-number={row.seatNumber ?? undefined}
                    key={`${row.seatNumber || 'unassigned'}-${row.name}`}
                  >
                    <td className="staff-seat-cell">
                      {row.seatNumber == null ? '-' : <span>{row.seatNumber}</span>}
                    </td>
                    <td>{row.name}</td>
                    {joinDateRow ? (
                      <td className="join-date-cell" colSpan={7}>
                        <button type="button" disabled={submitting || !canResetJoinDate} onClick={() => canResetJoinDate && row.memberId && resetJoinDateAttendance(row.memberId)}>
                          {formatJoinDateText(row)}
                        </button>
                      </td>
                    ) : SLOT_LABELS.map((slot, index) => {
                      const status = row.slots[index] || 'X';
                      const statusSource = row.slotSources?.[index] || 'NONE';
                      const selected = selectedSlot?.seatNumber === row.seatNumber && selectedSlot?.slot === slot;

                      return (
                        <td
                          className={`${toStatusClassName(status, emptySeat, statusSource)}${selected ? ' selected-slot' : ''}`}
                          key={slot}
                          onClick={() => {
                            if (row.seatNumber == null) {
                              return;
                            }
                            setSelectedSlot({ memberId: row.memberId ?? null, seatNumber: row.seatNumber, name: row.name, slot });
                          }}
                        >
                          <span>{toAttendanceDisplayLabel(status)}</span>
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="staff-attendance-command-spacer" aria-hidden="true" />
          <div className="staff-attendance-command-bar">
            <button className="command-present" type="button" disabled={!selectedSlot?.memberId || submitting} onClick={() => updateSlotStatus('PRESENT', undefined, selectedDate, selectedSlot?.slot)}>O 출석</button>
            <button className="command-absent" type="button" disabled={!selectedSlot?.memberId || submitting} onClick={() => updateSlotStatus('ABSENT', undefined, selectedDate, selectedSlot?.slot)}>X 결석</button>
            <button className="command-other" type="button" disabled={!selectedSlot?.memberId || submitting} onClick={openOtherModal}>기타</button>
            <button className="command-undo" type="button" disabled={!selectedSlot || submitting} onClick={() => moveToNextEditableSlot()}>↵</button>
          </div>
        </div>
      ) : null}
      {otherModalOpen && (
        <div className="attendance-modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            setOtherModalOpen(false);
          }
        }}>
          <form className="attendance-modal" role="dialog" aria-modal="true" onSubmit={submitOtherReason}>
            <h2>{selectedSlot?.name} 출석 상태 선택</h2>

            <section className="attendance-modal-section">
              <strong className="attendance-modal-label">날짜</strong>
              <div className="attendance-calendar-card">
                <div className="attendance-calendar-head">
                  <button type="button" aria-label="이전 달" onClick={() => moveOtherMonth(-1)}>‹</button>
                  <strong>{otherCalendar.year}년 {otherCalendar.month}월</strong>
                  <button type="button" aria-label="다음 달" onClick={() => moveOtherMonth(1)}>›</button>
                </div>
                <div className="attendance-calendar-weekdays">
                  {['일', '월', '화', '수', '목', '금', '토'].map((weekday) => (
                    <span key={weekday}>{weekday}</span>
                  ))}
                </div>
                <div className="attendance-calendar-grid">
                  {otherCalendar.days.map((day, index) => {
                    const dateKey = day ? toDateKey(new Date(otherCalendar.year, otherCalendar.month - 1, day)) : '';
                    const selected = otherDates.includes(dateKey);

                    return day ? (
                      <button
                        className={selected ? 'selected' : ''}
                        key={`${day}-${index}`}
                        type="button"
                        onClick={() => setOtherDates((current) => current.includes(dateKey)
                          ? current.filter((date) => date !== dateKey)
                          : [...current, dateKey].sort())}
                      >
                        {day}
                      </button>
                    ) : (
                      <span key={`empty-${index}`} />
                    );
                  })}
                </div>
              </div>
              <span className="attendance-selected-count">{otherDates.length}일 선택됨</span>
            </section>

            <section className="attendance-modal-section">
              <strong className="attendance-modal-label">교시</strong>
              <div className="attendance-period-grid">
                {SLOT_LABELS.map((slot) => (
                  <button
                    className={otherSlots.includes(slot) ? 'selected' : ''}
                    key={slot}
                    type="button"
                    onClick={() => setOtherSlots((current) => current.includes(slot)
                      ? current.filter((selected) => selected !== slot)
                      : [...current, slot].sort((first, second) => first - second))}
                  >
                    {slot}
                  </button>
                ))}
              </div>
              <button
                className={`attendance-full-select${allOtherSlotsSelected ? ' selected' : ''}`}
                type="button"
                onClick={() => setOtherSlots((current) => SLOT_LABELS.every((slot) => current.includes(slot))
                  ? selectedSlot ? [selectedSlot.slot] : []
                  : [...SLOT_LABELS])}
              >
                {allOtherSlotsSelected ? '전체 선택 해제' : '전체 선택'}
              </button>
              <span className="attendance-selected-count">{otherSlots.length}교시 선택됨</span>
            </section>

            <section className="attendance-modal-section">
              <strong className="attendance-modal-label">사유 선택</strong>
              <div className="attendance-reason-grid">
                {OTHER_REASON_OPTIONS.map((reason) => (
                  <button
                    className={selectedOtherReason === reason ? 'selected' : ''}
                    key={reason}
                    type="button"
                    onClick={() => {
                      setSelectedOtherReason(reason);
                      setOtherReason('');
                    }}
                  >
                    {reason}
                  </button>
                ))}
              </div>
              <strong className="attendance-modal-label">사유 입력</strong>
              <input
                placeholder="3글자 이하 권장"
                value={otherReason}
                onChange={(event) => {
                  setOtherReason(event.target.value);
                  setSelectedOtherReason('');
                }}
              />
            </section>

            <section className="attendance-modal-section">
              <div className="attendance-leave-grid">
                {['월차', '오전반차', '오후반차'].map((reason) => (
                  <button
                    className={`${reason === '오후반차' ? 'leave-afternoon' : 'leave-red'}${selectedOtherReason === reason ? ' selected' : ''}`}
                    key={reason}
                    type="button"
                    onClick={() => {
                      setSelectedOtherReason(reason);
                      setOtherReason('');
                      setOtherSlots([...LEAVE_REASON_SLOTS[reason]]);
                    }}
                  >
                    {reason}
                  </button>
                ))}
              </div>
              <button className="attendance-clear-leave" type="button" disabled={submitting} onClick={() => updateOtherSlotStatuses('ABSENT')}>
                휴가취소
              </button>
            </section>

            <div className="attendance-modal-actions">
              <button className="attendance-submit-button" type="submit" disabled={submitting}>{submitting ? '신청 중' : '신청'}</button>
              <button className="attendance-fixed-button" type="button" disabled={submitting} onClick={createFixedLeave}>
                {submitting ? '신청 중' : '고정신청'}
              </button>
            </div>
            <button className="attendance-modal-close" type="button" onClick={() => setOtherModalOpen(false)}>닫기</button>
          </form>
        </div>
      )}
      {fixedLeaveConflictOpen && (
        <div className="attendance-modal-backdrop attendance-conflict-backdrop" role="presentation">
          <section className="attendance-fixed-conflict-alert" role="alertdialog" aria-modal="true" aria-labelledby="fixed-leave-conflict-title">
            <span className="attendance-fixed-conflict-icon" aria-hidden="true">!</span>
            <h2 id="fixed-leave-conflict-title">고정 휴무를 신청할 수 없어요</h2>
            <p>이미 고정휴무가 있는 교시입니다.</p>
            <button type="button" onClick={() => setFixedLeaveConflictOpen(false)}>확인</button>
          </section>
        </div>
      )}
      {sideDishModalOpen && (
        <div className="attendance-modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            setSideDishModalOpen(false);
          }
        }}>
          <section className="attendance-side-dish-modal" role="dialog" aria-modal="true" aria-labelledby="attendance-side-dish-title">
            <header>
              <h2 id="attendance-side-dish-title">반찬신청</h2>
              <button type="button" aria-label="닫기" onClick={() => setSideDishModalOpen(false)}>×</button>
            </header>
            <div className="attendance-side-dish-content">
              <SideDishMealSection title="점심 반찬 신청" mealType="LUNCH" sideDishes={lunchSideDishes} />
              <SideDishMealSection title="저녁 반찬 신청" mealType="DINNER" sideDishes={dinnerSideDishes} />
            </div>
          </section>
        </div>
      )}
      {suggestionModalOpen && (
        <div className="attendance-modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            setSuggestionModalOpen(false);
          }
        }}>
          <section className="attendance-suggestion-modal" role="dialog" aria-modal="true" aria-labelledby="attendance-suggestion-title">
            <header>
              <h2 id="attendance-suggestion-title">회원건의</h2>
              <button type="button" aria-label="닫기" onClick={() => setSuggestionModalOpen(false)}>×</button>
            </header>
            <div className="attendance-suggestion-list">
              {suggestionItems.map((suggestion) => (
                <article className={`attendance-suggestion-card${suggestion.isResolved ? ' resolved' : ''}`} key={suggestion.id}>
                  <button
                    className="attendance-suggestion-check"
                    type="button"
                    aria-label={`${suggestion.content} ${suggestion.isResolved ? '완료 해제' : '완료 처리'}`}
                    onClick={() => resolveSuggestion(suggestion.id)}
                  >
                    {suggestion.isResolved ? '✓' : ''}
                  </button>
                  <div className="attendance-suggestion-body">
                    <p>
                      <b>[{getSuggestionCategoryLabel(suggestion.category)}]</b>
                      <span>{suggestion.content}</span>
                    </p>
                    <small>
                      요청:{getSuggestionRequesterName(suggestion, board)} / 완료: {getSuggestionResolverName(suggestion, board)}
                    </small>
                  </div>
                </article>
              ))}
            </div>
          </section>
        </div>
      )}
      {todoModalOpen && (
        <div className="attendance-modal-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) {
            setTodoModalOpen(false);
          }
        }}>
          <section className="attendance-todo-modal" role="dialog" aria-modal="true" aria-labelledby="attendance-todo-title">
            <header>
              <h2 id="attendance-todo-title">할일목록</h2>
              <button type="button" aria-label="닫기" onClick={() => setTodoModalOpen(false)}>×</button>
            </header>
            <div className="attendance-todo-controls">
              <div className="attendance-todo-branch-select">
                <button type="button" onClick={() => setTodoBranchOpen((current) => !current)}>
                  {selectedTodoBranch.name}
                </button>
                {todoBranchOpen && (
                  <div className="attendance-todo-branch-menu">
                    {(branches.length > 0 ? branches : [selectedTodoBranch]).map((branch) => (
                      <button
                        className={String(branch.id) === String(selectedTodoBranch.id) ? 'selected' : ''}
                        key={branch.id}
                        type="button"
                        onClick={() => {
                          setTodoBranchId(String(branch.id));
                          setTodoBranchOpen(false);
                        }}
                      >
                        {branch.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <div className={`attendance-todo-input${todoUrgent ? ' urgent' : ''}`}>
                <input
                  placeholder="할 일을 입력하세요..."
                  value={todoContent}
                  onChange={(event) => setTodoContent(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault();
                      void createTodo();
                    }
                  }}
                />
                <button
                  className={`attendance-todo-priority${todoUrgent ? ' selected' : ''}`}
                  type="button"
                  onClick={() => setTodoUrgent((current) => !current)}
                >
                  긴급
                </button>
              </div>
              <button type="button" disabled={submitting || !todoContent.trim()} onClick={createTodo}>+</button>
            </div>
            <div className="attendance-todo-list">
              {todoItems.map((item) => (
                <div
                  className={`attendance-todo-card ${item.priority === 'URGENT' ? 'urgent' : 'normal'}${item.completed ? ' completed' : ''}${replyTodoId === item.id || item.replies.length > 0 ? ' has-reply-form' : ''}`}
                  key={item.id}
                >
                  <div
                    className={`attendance-todo-row${editingTodoId === item.id ? ' editing' : ''}`}
                  >
                    <button
                      className="attendance-todo-check"
                      type="button"
                      aria-label={item.completed ? '할일 완료 해제' : '할일 완료'}
                      onClick={() => void updateTodoCompletion(item, !item.completed)}
                    >
                      <span aria-hidden="true" />
                    </button>
                    {editingTodoId === item.id ? (
                      <input
                        className="attendance-todo-edit-input"
                        autoFocus
                        value={editingTodoContent}
                        onChange={(event) => setEditingTodoContent(event.target.value)}
                      />
                    ) : (
                      <p>{item.content}</p>
                    )}
                    {editingTodoId !== item.id && <small>{toTodoSourceLabel(item)}</small>}
                    {editingTodoId !== item.id && item.priority === 'URGENT' && <em>긴급</em>}
                    {item.sourceType !== 'JOIN_MEMBER' && (
                      <div className="attendance-todo-actions">
                        {editingTodoId === item.id ? (
                          <>
                            <button className="save" type="button" disabled={submitting || !editingTodoContent.trim()} onClick={() => void submitTodoEdit(item)}>저장</button>
                            <button
                              className="cancel"
                              type="button"
                              onClick={() => {
                                setEditingTodoId(null);
                                setEditingTodoContent('');
                              }}
                            >
                              취소
                            </button>
                          </>
                        ) : (
                          <>
                            <button
                              className="reply"
                              type="button"
                              onClick={() => {
                                setReplyTodoId((current) => current === item.id ? null : item.id);
                                setReplyContent('');
                                setEditingTodoId(null);
                              }}
                            >
                              답글
                            </button>
                            <button
                              type="button"
                              aria-label="수정"
                              onClick={() => {
                                setEditingTodoId(item.id);
                                setEditingTodoContent(item.content);
                                setReplyTodoId(null);
                              }}
                            >
                              <EditIcon />
                            </button>
                            <button type="button" aria-label="삭제" disabled={submitting} onClick={() => void deleteTodo(item)}>
                              <TrashIcon />
                            </button>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                  {item.replies.map((reply) => (
                    <div className="attendance-todo-reply" key={reply.id}>
                      <strong>답글 - {reply.memberName} · {formatTodoReplyDate(reply.createdAt)}</strong>
                      <p>{reply.content}</p>
                    </div>
                  ))}
                  {replyTodoId === item.id && (
                    <form
                      className="attendance-todo-reply-form"
                      onSubmit={(event) => {
                        event.preventDefault();
                        void submitTodoReply(item);
                      }}
                    >
                      <textarea
                        autoFocus
                        placeholder="답변을 입력하세요..."
                        value={replyContent}
                        onChange={(event) => setReplyContent(event.target.value)}
                      />
                      <div>
                        <button className="save" type="submit" disabled={submitting || !replyContent.trim()}>저장</button>
                        <button
                          className="cancel"
                          type="button"
                          onClick={() => {
                            setReplyTodoId(null);
                            setReplyContent('');
                          }}
                        >
                          취소
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              ))}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

type SideDishMealSectionProps = {
  title: string;
  mealType: MealType;
  sideDishes: DailySideDishResponse[];
};

function SideDishMealSection({ title, mealType, sideDishes }: SideDishMealSectionProps) {
  return (
    <section className="attendance-side-dish-section">
      <strong>{title}</strong>
      {sideDishes.length > 0 ? (
        <ol>
          {sideDishes.flatMap((sideDish) => formatSideDishItems(sideDish).map((item) => (
            <li key={`${mealType}-${sideDish.id}-${item}`}>
              {item} {toMemberSeatText(sideDish)} {sideDish.memberName}
            </li>
          )))}
        </ol>
      ) : (
        <p>신청 없음</p>
      )}
    </section>
  );
}

function formatSideDishItems(sideDish: DailySideDishResponse) {
  const items = sideDish.items
          .split(/\n|,/)
          .map((item) => item.trim())
          .filter(Boolean);

  if (items.length === 0) {
    return [`${sideDish.totalPrice.toLocaleString()}원`];
  }

  return items.map((item) => item.replace(/:\s*(\d+)/g, (_, price: string) => ` ${Number(price).toLocaleString()}원`));
}

function toMemberSeatText(sideDish: DailySideDishResponse) {
  if (sideDish.seatNumber == null) {
    return '미배정';
  }

  return `${sideDish.seatNumber}번`;
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

function formatCompactDate(date: string) {
  const parsed = new Date(`${date}T00:00:00`);

  return `${parsed.getMonth() + 1}/${parsed.getDate()}`;
}

function toDatePart(date?: string | null) {
  if (!date) {
    return '';
  }

  return date.includes('T') ? date.split('T')[0] : date.slice(0, 10);
}

function formatJoinDateText(row: DailyAttendanceBoardResponse['rows'][number]) {
  const certification = row.certificationContent?.trim();
  const certificationText = certification ? `(${certification})` : '';
  const suffix = certification ? '' : ' -';

  return `${formatCompactDate(row.joinDate || '')} ${row.name}${certificationText}${suffix}`;
}

function formatTodoReplyDate(date?: string | null) {
  if (!date) {
    return '';
  }
  const parsed = new Date(date);
  const month = String(parsed.getMonth() + 1).padStart(2, '0');
  const day = String(parsed.getDate()).padStart(2, '0');
  const hours = parsed.getHours();
  const period = hours < 12 ? '오전' : '오후';
  const hour = String(hours % 12 || 12).padStart(2, '0');
  const minute = String(parsed.getMinutes()).padStart(2, '0');

  return `${month}. ${day}. ${period} ${hour}:${minute}`;
}

function getSuggestionCategoryLabel(category: SuggestionResponse['category']) {
  const labels: Record<SuggestionResponse['category'], string> = {
    SUPPLIES: '비품',
    STUDY: '학습',
    GENERAL: '일반',
    COUNSELING: '상담',
  };

  return labels[category];
}

function getSuggestionRequesterName(suggestion: SuggestionResponse, board: DailyAttendanceBoardResponse | null) {
  return suggestion.memberName || toSuggestionMemberName(suggestion.memberId, board);
}

function getSuggestionResolverName(suggestion: SuggestionResponse, board: DailyAttendanceBoardResponse | null) {
  if (!suggestion.isResolved) {
    return '-';
  }

  if (suggestion.resolvedByMemberName) {
    return suggestion.resolvedByMemberName;
  }

  if (suggestion.resolvedByMemberId) {
    return toSuggestionMemberName(suggestion.resolvedByMemberId, board);
  }

  return '-';
}

function toSuggestionMemberName(memberId: number, board: DailyAttendanceBoardResponse | null) {
  const member = board?.rows.find((row) => row.memberId === memberId);

  return member?.name || `회원 ${memberId}`;
}

function createMonthCalendar(date: string) {
  const parsed = new Date(`${date}T00:00:00`);
  const year = parsed.getFullYear();
  const monthIndex = parsed.getMonth();
  const firstDay = new Date(year, monthIndex, 1).getDay();
  const lastDate = new Date(year, monthIndex + 1, 0).getDate();
  const days: Array<number | null> = Array.from({ length: firstDay }, () => null);

  for (let day = 1; day <= lastDate; day += 1) {
    days.push(day);
  }
  while (days.length % 7 !== 0) {
    days.push(null);
  }

  return {
    year,
    month: monthIndex + 1,
    days,
  };
}

function toStatusClassName(status: string, emptySeat: boolean, source = 'NONE') {
  if (emptySeat) {
    return 'status-empty-seat';
  }

  if (status === 'X') {
    return 'status-absent';
  }
  if (status === 'O') {
    return 'status-present';
  }
  if (source === 'MEMBER_LEAVE') {
    return 'status-member-leave';
  }
  if (status === '오전' || status === '오후') {
    return 'status-half-leave';
  }

  return 'status-leave';
}

function toAttendanceDisplayLabel(status: string) {
  if (status === '오전반차') {
    return '오전';
  }
  if (status === '오후반차') {
    return '오후';
  }

  return status;
}

function toAttendanceStatusLabel(status: SlotStatusUpdateType, reason?: string) {
  if (status === 'PRESENT') {
    return 'O';
  }
  if (status === 'ABSENT') {
    return 'X';
  }

  return reason?.trim() || '기타';
}

function getDividerClassName(seatNumber?: number | null) {
  if (!seatNumber) {
    return '';
  }
  if (ROOM_END_DIVIDER_SEATS.has(seatNumber)) {
    return 'room-end-divider-row';
  }
  if (STRONG_DIVIDER_SEATS.has(seatNumber)) {
    return 'strong-divider-row';
  }
  if (SOFT_DIVIDER_SEATS.has(seatNumber)) {
    return 'soft-divider-row';
  }

  return '';
}

function getNameCellColorClassName(seatNumber?: number | null) {
  if (!seatNumber) {
    return '';
  }
  if (ALERT_SHADED_NAME_SEATS.has(seatNumber)) {
    return 'alert-name-cell-row';
  }
  if (DARK_SHADED_NAME_SEATS.has(seatNumber)) {
    return 'dark-shaded-name-cell-row';
  }
  if (SHADED_NAME_SEATS.has(seatNumber)) {
    return 'shaded-name-cell-row';
  }

  return '';
}

function toTodoSourceLabel(todo: TodoResponse) {
  if (todo.sourceType === 'JOIN_MEMBER') {
    return '신규입사';
  }
  if (todo.sourceType === 'SUGGESTION') {
    return '건의사항';
  }

  return `작성: ${todo.createdByMemberName || '-'}`;
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="10.5" cy="10.5" r="5.5" />
      <path d="m15 15 4 4" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m4 20 4.5-1 10-10a2.1 2.1 0 0 0-3-3l-10 10L4 20Z" />
      <path d="m14 7 3 3" />
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
