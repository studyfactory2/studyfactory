import { Fragment, useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../../api/client';

type WeeklyPlanItem = {
  text: string;
  done: boolean;
};

type WeeklyPlans = Record<string, WeeklyPlanItem[]>;
type StoredWeeklyPlan = {
  goal: string;
  plans: WeeklyPlans;
};

type WeeklyPlanMode = 'board' | 'work';

type WeeklyPlanApiItem = {
  id: number | null;
  periodIndex: number;
  dayIndex: number;
  content: string;
  done: boolean;
  sortOrder: number;
};

type WeeklyPlanApiResponse = {
  goalId: number | null;
  memberId: number;
  branchId: number;
  weekStartDate: string;
  goal: string;
  items: WeeklyPlanApiItem[];
};

type MonthlyPlanGoalApiResponse = {
  id: number | null;
  memberId: number;
  branchId: number;
  month: string;
  goal: string;
};

const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일'];
const STUDY_PERIODS = [
  { label: '1교시', duration: '90분' },
  { label: '2교시', duration: '80분' },
  { label: '3교시', duration: '70분' },
  { label: '4교시', duration: '90분' },
  { label: '5교시', duration: '80분' },
  { label: '6교시', duration: '80분' },
  { label: '7교시', duration: '80분' },
];
const BREAK_PERIODS = {
  lunch: 100,
  dinner: 101,
} as const;

function getMonday(date: Date) {
  const nextDate = new Date(date);
  const day = nextDate.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  nextDate.setDate(nextDate.getDate() + diff);
  nextDate.setHours(0, 0, 0, 0);
  return nextDate;
}

function addDays(date: Date, amount: number) {
  const nextDate = new Date(date);
  nextDate.setDate(nextDate.getDate() + amount);
  return nextDate;
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function toDateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function toMonthKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function toWeekRangeLabel(startDate: Date) {
  const endDate = addDays(startDate, 6);
  return `${toDateKey(startDate)} - ${toDateKey(endDate)}`;
}

function getMonthCalendarDays(monthStart: Date) {
  const firstDate = startOfMonth(monthStart);
  const startOffset = firstDate.getDay() === 0 ? -6 : 1 - firstDate.getDay();
  const calendarStart = addDays(firstDate, startOffset);

  return Array.from({ length: 42 }, (_, index) => addDays(calendarStart, index));
}

function toDateLabel(date: Date) {
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

function toStoredPlan(response: WeeklyPlanApiResponse): StoredWeeklyPlan {
  return {
    goal: response.goal || '',
    plans: response.items.reduce<WeeklyPlans>((result, item) => {
      const cellKey = `${item.periodIndex}-${item.dayIndex}`;
      result[cellKey] = [...(result[cellKey] || []), { text: item.content, done: item.done }];
      return result;
    }, {}),
  };
}

function toApiItems(plan: StoredWeeklyPlan) {
  return Object.entries(plan.plans).flatMap(([cellKey, items]) => {
    const [periodIndex, dayIndex] = cellKey.split('-').map(Number);

    return items.map((item, index) => ({
      periodIndex,
      dayIndex,
      content: item.text,
      done: item.done,
      sortOrder: index,
    }));
  });
}

export function WeeklyPlanPanel() {
  const [weekStart, setWeekStart] = useState(() => getMonday(new Date()));
  const [mode, setMode] = useState<WeeklyPlanMode>('work');
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(new Date()));
  const [monthlyGoal, setMonthlyGoal] = useState('');
  const weekStartKey = toDateKey(weekStart);
  const [storedPlan, setStoredPlan] = useState<StoredWeeklyPlan>({ goal: '', plans: {} });
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const weekDays = useMemo(() => WEEKDAYS.map((label, index) => ({ label, date: addDays(weekStart, index) })), [weekStart]);
  const monthKey = toMonthKey(visibleMonth);
  const monthDays = useMemo(() => getMonthCalendarDays(visibleMonth), [visibleMonth]);
  const flatItems = useMemo(() => Object.values(storedPlan.plans).flat(), [storedPlan.plans]);
  const completedCount = flatItems.filter((item) => item.done).length;

  useEffect(() => {
    void loadPlan(weekStartKey);
  }, [weekStartKey]);

  useEffect(() => {
    void loadMonthlyGoal(monthKey);
  }, [monthKey]);

  const loadPlan = async (nextWeekStartKey: string) => {
    setLoading(true);
    try {
      const response = await apiRequest<WeeklyPlanApiResponse>(`/api/weekly-plans/me?weekStartDate=${nextWeekStartKey}`);
      setStoredPlan(toStoredPlan(response));
      setDrafts({});
      setMessage('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '주간학습장을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const loadMonthlyGoal = async (nextMonthKey: string) => {
    try {
      const response = await apiRequest<MonthlyPlanGoalApiResponse>(`/api/weekly-plans/monthly-goal/me?month=${nextMonthKey}`);
      setMonthlyGoal(response.goal || '');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '이번달 목표를 불러오지 못했습니다.');
    }
  };

  const saveMonthlyGoal = async (goal: string, showMessage = true) => {
    try {
      await apiRequest<MonthlyPlanGoalApiResponse>('/api/weekly-plans/monthly-goal/me', {
        method: 'PUT',
        body: JSON.stringify({
          month: monthKey,
          goal,
        }),
      });
      if (showMessage) {
        setMessage('이번달 목표가 저장되었습니다.');
      }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '이번달 목표를 저장하지 못했습니다.');
    }
  };

  const moveWeek = (amount: number) => {
    const nextWeekStart = addDays(weekStart, amount * 7);
    setWeekStart(nextWeekStart);
    setDrafts({});
    setMessage('');
  };

  const moveMonth = (amount: number) => {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1));
  };

  const selectCalendarDate = (date: Date) => {
    setWeekStart(getMonday(date));
    setVisibleMonth(startOfMonth(date));
    setDrafts({});
    setMessage('');
  };

  const changeMonthlyGoal = (value: string) => {
    setMonthlyGoal(value);
    setMessage('');
  };

  const savePlan = async (nextPlan = storedPlan, successMessage = '이번 주 계획이 저장되었습니다.') => {
    try {
      const response = await apiRequest<WeeklyPlanApiResponse>('/api/weekly-plans/me', {
        method: 'PUT',
        body: JSON.stringify({
          weekStartDate: weekStartKey,
          goal: nextPlan.goal,
          items: toApiItems(nextPlan),
        }),
      });
      setStoredPlan(toStoredPlan(response));
      setMessage(successMessage);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '주간학습장을 저장하지 못했습니다.');
    }
  };

  const changeGoal = (goal: string) => {
    setStoredPlan((current) => ({ ...current, goal }));
    setMessage('');
  };

  const changeDraft = (cellKey: string, value: string) => {
    setDrafts((current) => ({ ...current, [cellKey]: value }));
    setMessage('');
  };

  const addPlan = async (cellKey: string) => {
    const value = (drafts[cellKey] || '').trim();
    if (!value) {
      return;
    }

    const nextPlan = {
      ...storedPlan,
      plans: {
        ...storedPlan.plans,
        [cellKey]: [...(storedPlan.plans[cellKey] || []), { text: value, done: false }],
      },
    };
    setStoredPlan(nextPlan);
    setDrafts((current) => ({ ...current, [cellKey]: '' }));
    await savePlan(nextPlan, '할 일이 추가되었습니다.');
  };

  const removePlan = async (cellKey: string, index: number) => {
    const nextItems = (storedPlan.plans[cellKey] || []).filter((_, itemIndex) => itemIndex !== index);
    const nextPlans = { ...storedPlan.plans };

    if (nextItems.length === 0) {
      delete nextPlans[cellKey];
    } else {
      nextPlans[cellKey] = nextItems;
    }

    const nextPlan = { ...storedPlan, plans: nextPlans };
    setStoredPlan(nextPlan);
    await savePlan(nextPlan, '할 일이 삭제되었습니다.');
  };

  const togglePlanDone = async (cellKey: string, index: number) => {
    const nextItems = (storedPlan.plans[cellKey] || []).map((item, itemIndex) => (
      itemIndex === index ? { ...item, done: !item.done } : item
    ));
    const nextPlan = {
      ...storedPlan,
      plans: {
        ...storedPlan.plans,
        [cellKey]: nextItems,
      },
    };

    setStoredPlan(nextPlan);
    setMessage('');
    await savePlan(nextPlan, '');
  };

  const saveCellItems = async (cellKey: string, items: WeeklyPlanItem[]) => {
    const nextPlans = { ...storedPlan.plans };
    if (items.length === 0) {
      delete nextPlans[cellKey];
    } else {
      nextPlans[cellKey] = items;
    }

    const nextPlan = { ...storedPlan, plans: nextPlans };
    setStoredPlan(nextPlan);
    await savePlan(nextPlan, '할 일을 수정했습니다.');
  };

  return (
    <div className="member-panel weekly-plan-panel">
      <div className="weekly-plan-mode-tabs" aria-label="작업계획 보기 전환">
        <button className={mode === 'work' ? 'active' : ''} type="button" onClick={() => setMode('work')}>
          작업계획
        </button>
        <button className={mode === 'board' ? 'active' : ''} type="button" onClick={() => setMode('board')}>
          주간 학습장
        </button>
      </div>
      {loading && <p className="weekly-plan-message">주간학습장을 불러오는 중입니다.</p>}

      {mode === 'work' ? (
        <WorkPlanView
          completedCount={completedCount}
          monthlyGoal={monthlyGoal}
          monthDays={monthDays}
          selectedWeekStart={weekStart}
          totalCount={flatItems.length}
          visibleMonth={visibleMonth}
          weeklyGoal={storedPlan.goal}
          onChangeMonthlyGoal={changeMonthlyGoal}
          onMoveMonth={moveMonth}
          onSaveMonthlyGoal={() => void saveMonthlyGoal(monthlyGoal)}
          onSaveWeeklyGoal={() => void savePlan()}
          onSelectDate={selectCalendarDate}
          onWeeklyGoalChange={changeGoal}
        />
      ) : (
        <>
          <WeeklyGoalField goal={storedPlan.goal} onChange={changeGoal} />
          <WeeklyBoard
            drafts={drafts}
            plan={storedPlan}
            weekDays={weekDays}
            onAddPlan={addPlan}
            onChangeDraft={changeDraft}
            onRemovePlan={removePlan}
            onSaveCellItems={saveCellItems}
            onTogglePlanDone={togglePlanDone}
          />
          <div className="weekly-plan-actions">
            <button className="member-secondary-action" type="button" onClick={() => moveWeek(-1)}>
              지난주
            </button>
            <button className="member-secondary-action" type="button" onClick={() => moveWeek(1)}>
              다음주
            </button>
          </div>
          <button className="member-primary-action" type="button" onClick={() => void savePlan()}>
            이번 주 계획 저장하기
          </button>
        </>
      )}
      {message && <p className="weekly-plan-message">{message}</p>}
    </div>
  );
}

function WeeklyGoalField({ goal, onChange }: { goal: string; onChange: (goal: string) => void }) {
  return (
    <label className="weekly-goal-field">
      <span>이번주 목표</span>
      <textarea
        placeholder="예) 오전에는 기출, 오후에는 오답 정리"
        rows={3}
        wrap="soft"
        value={goal}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

type WeeklyBoardProps = {
  drafts: Record<string, string>;
  plan: StoredWeeklyPlan;
  weekDays: Array<{ label: string; date: Date }>;
  onAddPlan: (cellKey: string) => Promise<void>;
  onChangeDraft: (cellKey: string, value: string) => void;
  onRemovePlan: (cellKey: string, index: number) => Promise<void>;
  onSaveCellItems: (cellKey: string, items: WeeklyPlanItem[]) => Promise<void>;
  onTogglePlanDone: (cellKey: string, index: number) => Promise<void>;
};

function WeeklyBoard({ drafts, onAddPlan, onChangeDraft, onRemovePlan, onSaveCellItems, onTogglePlanDone, plan, weekDays }: WeeklyBoardProps) {
  return (
    <section className="weekly-board-card" aria-label="주간 학습 계획표">
        <div className="weekly-board-scroll">
          <div className="weekly-board-grid">
            <div className="weekly-board-corner">교시</div>
            {weekDays.map((day) => (
              <div className="weekly-board-day" key={day.label}>
                <span>{day.label}</span>
                <strong>{toDateLabel(day.date)}</strong>
              </div>
            ))}

            {STUDY_PERIODS.map((period, periodIndex) => (
              <Fragment key={period.label}>
                <div className="weekly-period-cell">
                  <strong>{period.label}</strong>
                  <span>{period.duration}</span>
                </div>
                {weekDays.map((day, dayIndex) => (
                  <WeeklyPlanCell
                    ariaLabel={`${day.label} ${period.label} 할 일`}
                    cellKey={`${periodIndex}-${dayIndex}`}
                    drafts={drafts}
                    key={`${periodIndex}-${dayIndex}`}
                    plan={plan}
                    onAddPlan={onAddPlan}
                    onChangeDraft={onChangeDraft}
                    onRemovePlan={onRemovePlan}
                    onSaveCellItems={onSaveCellItems}
                    onTogglePlanDone={onTogglePlanDone}
                  />
                ))}
                {periodIndex === 1 && (
                  <WeeklyBreakRow
                    duration="75분"
                    label="점심시간"
                    periodIndex={BREAK_PERIODS.lunch}
                    drafts={drafts}
                    plan={plan}
                    weekDays={weekDays}
                    onAddPlan={onAddPlan}
                    onChangeDraft={onChangeDraft}
                    onRemovePlan={onRemovePlan}
                    onSaveCellItems={onSaveCellItems}
                    onTogglePlanDone={onTogglePlanDone}
                  />
                )}
                {periodIndex === 4 && (
                  <WeeklyBreakRow
                    duration="75분"
                    label="저녁시간"
                    periodIndex={BREAK_PERIODS.dinner}
                    drafts={drafts}
                    plan={plan}
                    weekDays={weekDays}
                    onAddPlan={onAddPlan}
                    onChangeDraft={onChangeDraft}
                    onRemovePlan={onRemovePlan}
                    onSaveCellItems={onSaveCellItems}
                    onTogglePlanDone={onTogglePlanDone}
                  />
                )}
              </Fragment>
            ))}
          </div>
        </div>
      </section>
  );
}

type WeeklyPlanCellProps = {
  ariaLabel: string;
  cellKey: string;
  className?: string;
  drafts: Record<string, string>;
  plan: StoredWeeklyPlan;
  onAddPlan: (cellKey: string) => Promise<void>;
  onChangeDraft: (cellKey: string, value: string) => void;
  onRemovePlan: (cellKey: string, index: number) => Promise<void>;
  onSaveCellItems: (cellKey: string, items: WeeklyPlanItem[]) => Promise<void>;
  onTogglePlanDone: (cellKey: string, index: number) => Promise<void>;
};

function WeeklyPlanCell({
  ariaLabel,
  cellKey,
  className = '',
  drafts,
  onAddPlan,
  onChangeDraft,
  onRemovePlan,
  onSaveCellItems,
  onTogglePlanDone,
  plan,
}: WeeklyPlanCellProps) {
  const items = plan.plans[cellKey] || [];
  const [editing, setEditing] = useState(false);
  const [editingItems, setEditingItems] = useState<WeeklyPlanItem[]>(items);

  useEffect(() => {
    if (!editing) {
      setEditingItems(items);
    }
  }, [editing, items]);

  const startEditing = () => {
    setEditingItems(items.map((item) => ({ ...item })));
    setEditing(true);
  };

  const updateEditingItem = (index: number, text: string) => {
    setEditingItems((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, text } : item));
  };

  const removeEditingItem = (index: number) => {
    setEditingItems((current) => current.filter((_, itemIndex) => itemIndex !== index));
  };

  const addEditingItem = () => {
    const value = (drafts[cellKey] || '').trim();
    if (!value) {
      return;
    }
    setEditingItems((current) => [...current, { text: value, done: false }]);
    onChangeDraft(cellKey, '');
  };

  const saveEditing = async () => {
    const cleanedItems = editingItems
      .map((item) => ({ ...item, text: item.text.trim() }))
      .filter((item) => item.text);
    await onSaveCellItems(cellKey, cleanedItems);
    setEditing(false);
  };

  return (
    <div className={`weekly-plan-cell${editing ? ' is-editing' : ''}${className ? ` ${className}` : ''}`}>
      <ol>
        {(editing ? editingItems : items).map((item, itemIndex) => editing ? (
          <li className="editing" key={`${item.text}-${itemIndex}`}>
            <input
              aria-label={`${itemIndex + 1}번째 할 일 수정`}
              value={item.text}
              onChange={(event) => updateEditingItem(itemIndex, event.target.value)}
            />
            <button type="button" aria-label={`${item.text} 삭제`} onClick={() => removeEditingItem(itemIndex)}>×</button>
          </li>
        ) : (
          <li className={item.done ? 'done' : ''} key={`${item.text}-${itemIndex}`}>
            <button
              className="weekly-plan-check"
              type="button"
              aria-label={`${item.text} 완료 표시`}
              aria-pressed={item.done}
              onClick={() => void onTogglePlanDone(cellKey, itemIndex)}
            />
            <span>{item.text}</span>
            <button type="button" aria-label={`${item.text} 삭제`} onClick={() => void onRemovePlan(cellKey, itemIndex)}>
              ×
            </button>
          </li>
        ))}
      </ol>
      <div className="weekly-plan-draft-row">
        <input
          aria-label={ariaLabel}
          placeholder="+ 할 일"
          value={drafts[cellKey] || ''}
          onChange={(event) => onChangeDraft(cellKey, event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              if (editing) {
                addEditingItem();
              } else {
                void onAddPlan(cellKey);
              }
            }
          }}
        />
      </div>
      <div className="weekly-plan-cell-actions">
        <button type="button" onClick={() => editing ? void saveEditing() : startEditing()}>
          {editing ? '저장' : '수정'}
        </button>
        <button type="button" onClick={() => editing ? addEditingItem() : void onAddPlan(cellKey)}>
          {editing ? '+ 할 일 추가' : '+ 추가'}
        </button>
      </div>
    </div>
  );
}

type WorkPlanViewProps = {
  completedCount: number;
  monthlyGoal: string;
  monthDays: Date[];
  selectedWeekStart: Date;
  totalCount: number;
  visibleMonth: Date;
  weeklyGoal: string;
  onChangeMonthlyGoal: (value: string) => void;
  onMoveMonth: (amount: number) => void;
  onSaveMonthlyGoal: () => void;
  onSaveWeeklyGoal: () => void;
  onSelectDate: (date: Date) => void;
  onWeeklyGoalChange: (goal: string) => void;
};

function WorkPlanView({
  completedCount,
  monthlyGoal,
  monthDays,
  onChangeMonthlyGoal,
  onMoveMonth,
  onSaveMonthlyGoal,
  onSaveWeeklyGoal,
  onSelectDate,
  onWeeklyGoalChange,
  selectedWeekStart,
  totalCount,
  visibleMonth,
  weeklyGoal,
}: WorkPlanViewProps) {
  const selectedWeekEnd = addDays(selectedWeekStart, 6);
  const selectedWeekStartKey = toDateKey(selectedWeekStart);
  const selectedWeekEndKey = toDateKey(selectedWeekEnd);

  return (
    <div className="work-plan-view">
      <label className="weekly-goal-field">
        <span>이번달 목표</span>
        <textarea
          placeholder="예) 재무회계 2회독 + 기출 300문제 완료"
          rows={2}
          value={monthlyGoal}
          onChange={(event) => onChangeMonthlyGoal(event.target.value)}
          onBlur={onSaveMonthlyGoal}
        />
      </label>

      <section className="work-calendar-card">
        <div className="work-calendar-header">
          <button type="button" aria-label="이전 달" onClick={() => onMoveMonth(-1)}>
            ‹
          </button>
          <strong>{visibleMonth.getFullYear()}년 {String(visibleMonth.getMonth() + 1).padStart(2, '0')}월</strong>
          <button type="button" aria-label="다음 달" onClick={() => onMoveMonth(1)}>
            ›
          </button>
        </div>
        <div className="work-calendar-grid">
          {WEEKDAYS.map((weekday) => (
            <span className="work-calendar-weekday" key={weekday}>{weekday}</span>
          ))}
          {monthDays.map((date) => {
            const dateKey = toDateKey(date);
            const inMonth = date.getMonth() === visibleMonth.getMonth();
            const inSelectedWeek = dateKey >= selectedWeekStartKey && dateKey <= selectedWeekEndKey;

            return (
              <button
                className={`${inMonth ? '' : 'muted'}${inSelectedWeek ? ' selected-week' : ''}`}
                type="button"
                key={dateKey}
                onClick={() => onSelectDate(date)}
              >
                {date.getDate()}
              </button>
            );
          })}
        </div>
      </section>

      <p className="work-selected-week">선택된 주: {toWeekRangeLabel(selectedWeekStart)}</p>
      <WeeklyGoalField goal={weeklyGoal} onChange={onWeeklyGoalChange} />
      <div className="work-weekly-goal-actions">
        <button className="member-secondary-action" type="button" onClick={onSaveWeeklyGoal}>
          저장
        </button>
      </div>
      <section className="work-plan-summary">
        <div>
          <span>이번주 할 일</span>
          <strong>{totalCount}개</strong>
        </div>
        <div>
          <span>완료</span>
          <strong>{completedCount}개</strong>
        </div>
      </section>
    </div>
  );
}

type WeeklyBreakRowProps = {
  duration: string;
  label: string;
  periodIndex: number;
  drafts: Record<string, string>;
  plan: StoredWeeklyPlan;
  weekDays: Array<{ label: string; date: Date }>;
  onAddPlan: (cellKey: string) => Promise<void>;
  onChangeDraft: (cellKey: string, value: string) => void;
  onRemovePlan: (cellKey: string, index: number) => Promise<void>;
  onSaveCellItems: (cellKey: string, items: WeeklyPlanItem[]) => Promise<void>;
  onTogglePlanDone: (cellKey: string, index: number) => Promise<void>;
};

function WeeklyBreakRow({
  drafts,
  duration,
  label,
  onAddPlan,
  onChangeDraft,
  onRemovePlan,
  onSaveCellItems,
  onTogglePlanDone,
  periodIndex,
  plan,
  weekDays,
}: WeeklyBreakRowProps) {
  return (
    <>
      <div className="weekly-break-label">
        {label} <span>{duration}</span>
      </div>
      {weekDays.map((day, dayIndex) => (
        <WeeklyPlanCell
          ariaLabel={`${day.label} ${label} 할 일`}
          cellKey={`${periodIndex}-${dayIndex}`}
          className="weekly-break-cell"
          drafts={drafts}
          key={`${periodIndex}-${dayIndex}`}
          plan={plan}
          onAddPlan={onAddPlan}
          onChangeDraft={onChangeDraft}
          onRemovePlan={onRemovePlan}
          onSaveCellItems={onSaveCellItems}
          onTogglePlanDone={onTogglePlanDone}
        />
      ))}
    </>
  );
}
