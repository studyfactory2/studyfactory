import { Fragment, useEffect, useMemo, useRef, useState } from 'react';
import { apiRequest } from '../../api/client';
import type { Branch, MemberResponse } from '../../types/domain';

type WeeklyPlanItem = {
  id: number | null;
  periodIndex: number;
  dayIndex: number;
  content: string;
  done: boolean;
  sortOrder: number;
};

type WeeklyPlanResponse = {
  weekStartDate: string;
  goal: string;
  items: WeeklyPlanItem[];
};

type WeekSlideDirection = 'previous' | 'next' | null;

const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일'];
const STUDY_PERIODS = [
  { label: '1교시', duration: '90분', index: 0 },
  { label: '2교시', duration: '80분', index: 1 },
  { label: '3교시', duration: '70분', index: 2 },
  { label: '4교시', duration: '90분', index: 3 },
  { label: '5교시', duration: '80분', index: 4 },
  { label: '6교시', duration: '80분', index: 5 },
  { label: '7교시', duration: '80분', index: 6 },
];

type MemberWeeklyPlanViewerProps = {
  branches: Branch[];
  member: MemberResponse;
  onBack: () => void;
};

export function MemberWeeklyPlanViewer({ member, onBack }: MemberWeeklyPlanViewerProps) {
  const [weekStart, setWeekStart] = useState(() => getMonday(new Date()));
  const [displayedWeekStart, setDisplayedWeekStart] = useState(() => getMonday(new Date()));
  const [plan, setPlan] = useState<WeeklyPlanResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [weekSlideDirection, setWeekSlideDirection] = useState<WeekSlideDirection>(null);
  const pendingSlideDirection = useRef<WeekSlideDirection>(null);
  const weekStartKey = toDateKey(weekStart);
  const weekDays = useMemo(() => WEEKDAYS.map((label, index) => ({ label, date: addDays(displayedWeekStart, index) })), [displayedWeekStart]);
  const itemsByCell = useMemo(() => {
    return (plan?.items || []).reduce<Record<string, WeeklyPlanItem[]>>((result, item) => {
      const key = `${item.periodIndex}-${item.dayIndex}`;
      result[key] = [...(result[key] || []), item];
      return result;
    }, {});
  }, [plan]);

  useEffect(() => {
    let active = true;

    const loadPlan = async () => {
      setLoading(true);
      setMessage('');
      try {
        const response = await apiRequest<WeeklyPlanResponse>(`/api/weekly-plans/members/${member.id}?weekStartDate=${weekStartKey}`);
        if (!active) return;
        setPlan(response);
        setDisplayedWeekStart(weekStart);
        setWeekSlideDirection(pendingSlideDirection.current);
        pendingSlideDirection.current = null;
      } catch (error) {
        if (!active) return;
        setMessage(error instanceof Error ? error.message : '주간학습장을 불러오지 못했습니다.');
      } finally {
        if (active) setLoading(false);
      }
    };

    void loadPlan();

    return () => {
      active = false;
    };
  }, [member.id, weekStartKey]);

  const moveWeek = (amount: number) => {
    if (loading) return;
    pendingSlideDirection.current = amount < 0 ? 'previous' : 'next';
    setWeekStart((current) => addDays(current, amount));
  };

  return (
    <div className="vacation-history-panel member-weekly-plan-viewer">
      <header className="vacation-detail-header">
        <button type="button" aria-label="뒤로가기" onClick={onBack}>‹</button>
        <div>
          <h2>{member.name}</h2>
          <span>주간 학습장</span>
        </div>
      </header>

      <section className="member-weekly-plan-card" aria-label={`${member.name} 주간 학습장`}>
        <div className="member-weekly-plan-week-control">
          <button type="button" aria-label="지난주" onClick={() => moveWeek(-7)} disabled={loading}>‹</button>
          <strong>{toWeekRangeLabel(displayedWeekStart)}</strong>
          <button type="button" aria-label="다음주" onClick={() => moveWeek(7)} disabled={loading}>›</button>
        </div>

        {message && !plan ? (
          <p className="vacation-history-empty">{message}</p>
        ) : !plan ? (
          <p className="vacation-history-empty">주간학습장을 불러오는 중입니다.</p>
        ) : (
          <div
            className={`member-weekly-plan-content${weekSlideDirection ? ` week-slide-${weekSlideDirection}` : ''}`}
            onAnimationEnd={() => setWeekSlideDirection(null)}
          >
            <section className="weekly-goal-field weekly-goal-readonly">
              <span>이번주 목표</span>
              <p>{plan.goal || '등록된 이번주 목표가 없습니다.'}</p>
            </section>

            <section className="weekly-board-card member-weekly-plan-board">
              <div className="weekly-board-scroll">
                <div className="weekly-board-grid">
                  <div className="weekly-board-corner">교시</div>
                  {weekDays.map((day) => (
                    <div className="weekly-board-day" key={day.label}>
                      <span>{day.label}</span>
                      <strong>{toDateLabel(day.date)}</strong>
                    </div>
                  ))}

                  {STUDY_PERIODS.map((period) => (
                    <Fragment key={period.index}>
                      <PlanRowLabel label={period.label} duration={period.duration} />
                      {weekDays.map((day, dayIndex) => <PlanCell items={itemsByCell[`${period.index}-${dayIndex}`] || []} key={`${period.index}-${day.label}`} />)}
                      {period.index === 1 && <BreakRow label="점심시간" weekDays={weekDays} itemsByCell={itemsByCell} periodIndex={100} />}
                      {period.index === 4 && <BreakRow label="저녁시간" weekDays={weekDays} itemsByCell={itemsByCell} periodIndex={101} />}
                    </Fragment>
                  ))}
                </div>
              </div>
            </section>

            {message && <p className="vacation-history-empty">{message}</p>}
          </div>
        )}
      </section>
    </div>
  );
}

function PlanRowLabel({ label, duration }: { label: string; duration: string }) {
  return <div className="weekly-period-cell"><strong>{label}</strong><span>{duration}</span></div>;
}

function BreakRow({ label, weekDays, itemsByCell, periodIndex }: { label: string; weekDays: Array<{ label: string; date: Date }>; itemsByCell: Record<string, WeeklyPlanItem[]>; periodIndex: number }) {
  return (
    <>
      <div className="weekly-break-label">{label} <span>75분</span></div>
      {weekDays.map((day, dayIndex) => <PlanCell className="weekly-break-cell" items={itemsByCell[`${periodIndex}-${dayIndex}`] || []} key={`${periodIndex}-${day.label}`} />)}
    </>
  );
}

function PlanCell({ items, className = '' }: { items: WeeklyPlanItem[]; className?: string }) {
  return (
    <div className={`weekly-plan-cell weekly-plan-readonly-cell${className ? ` ${className}` : ''}`}>
      <ol>
        {items.map((item) => <li className={item.done ? 'done' : ''} key={item.id ?? `${item.content}-${item.sortOrder}`}><span>{item.content}</span></li>)}
      </ol>
    </div>
  );
}

function getMonday(date: Date) {
  const nextDate = new Date(date);
  const day = nextDate.getDay();
  nextDate.setDate(nextDate.getDate() + (day === 0 ? -6 : 1 - day));
  nextDate.setHours(0, 0, 0, 0);
  return nextDate;
}

function addDays(date: Date, amount: number) {
  const nextDate = new Date(date);
  nextDate.setDate(nextDate.getDate() + amount);
  return nextDate;
}

function toDateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function toDateLabel(date: Date) {
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

function toWeekRangeLabel(startDate: Date) {
  return `${toDateKey(startDate)} - ${toDateKey(addDays(startDate, 6))}`;
}
