import { useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../../api/client';
import { Dropdown } from '../common/Dropdown';
import type {
  Branch,
  StaffScheduleDayOfWeek,
  StaffScheduleResponse,
  StaffScheduleShift,
  StaffScheduleTaskType,
  StaffScheduleUpdateRequest,
} from '../../types/domain';

type StaffWorkPanelProps = {
  branches: Branch[];
  editable: boolean;
};

const DAYS: Array<{ key: StaffScheduleDayOfWeek; label: string }> = [
  { key: 'MONDAY', label: '월' },
  { key: 'TUESDAY', label: '화' },
  { key: 'WEDNESDAY', label: '수' },
  { key: 'THURSDAY', label: '목' },
  { key: 'FRIDAY', label: '금' },
  { key: 'SATURDAY', label: '토' },
  { key: 'SUNDAY', label: '일' },
];
const TASK_CELLS: Array<{ shift: StaffScheduleShift; taskType: StaffScheduleTaskType; timeLabel: string; taskLabel: string }> = [
  { shift: 'MORNING', taskType: 'DISHWASHING', timeLabel: '오전', taskLabel: '설거지' },
  { shift: 'MORNING', taskType: 'SERVE', timeLabel: '오전', taskLabel: '서브' },
  { shift: 'AFTERNOON', taskType: 'DISHWASHING', timeLabel: '오후', taskLabel: '설거지' },
  { shift: 'AFTERNOON', taskType: 'SERVE', timeLabel: '오후', taskLabel: '서브' },
];

export function StaffWorkPanel({ branches, editable }: StaffWorkPanelProps) {
  const [branchId, setBranchId] = useState(() => branches[0]?.id ? String(branches[0].id) : '');
  const [branchOpen, setBranchOpen] = useState(false);
  const [schedules, setSchedules] = useState<Record<string, string>>(() => createEmptyScheduleMap());
  const [editSchedules, setEditSchedules] = useState<Record<string, string>>(() => createEmptyScheduleMap());
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const branchOptions = useMemo(() => branches.map((branch) => ({ value: String(branch.id), label: branch.name })), [branches]);
  const selectedBranchOption = branchOptions.find((option) => option.value === branchId) || branchOptions[0] || { value: '', label: '망미점' };

  useEffect(() => {
    if (branchId || !branches[0]?.id) {
      return;
    }

    setBranchId(String(branches[0].id));
  }, [branchId, branches]);

  useEffect(() => {
    if (!branchId) {
      return;
    }

    void loadSchedules(branchId);
  }, [branchId]);

  const selectBranch = (value: string) => {
    setBranchId(value);
    setBranchOpen(false);
    setEditing(false);
    setMessage(null);
  };

  const loadSchedules = async (selectedBranchId: string) => {
    try {
      const params = new URLSearchParams({ branchId: selectedBranchId });
      const response = await apiRequest<StaffScheduleResponse[]>(`/api/staff-schedules?${params.toString()}`);
      const scheduleMap = toScheduleMap(response);
      setSchedules(scheduleMap);
      setEditSchedules(scheduleMap);
      setMessage(null);
    } catch {
      setSchedules(createEmptyScheduleMap());
      setEditSchedules(createEmptyScheduleMap());
      setMessage('근무표를 불러오지 못했습니다.');
    }
  };

  const startEdit = () => {
    setEditSchedules(schedules);
    setEditing(true);
    setMessage(null);
  };

  const cancelEdit = () => {
    setEditSchedules(schedules);
    setEditing(false);
    setMessage(null);
  };

  const changeWorkerName = (key: string, value: string) => {
    setEditSchedules((current) => ({ ...current, [key]: value }));
  };

  const saveSchedules = async () => {
    if (!branchId) {
      return;
    }

    try {
      setSaving(true);
      const params = new URLSearchParams({ branchId });
      const request: StaffScheduleUpdateRequest = {
        schedules: DAYS.flatMap((day) => TASK_CELLS.map((cell) => ({
          dayOfWeek: day.key,
          shift: cell.shift,
          taskType: cell.taskType,
          workerName: editSchedules[toScheduleKey(day.key, cell.shift, cell.taskType)] || '',
        }))),
      };
      const response = await apiRequest<StaffScheduleResponse[]>(`/api/staff-schedules?${params.toString()}`, {
        method: 'PUT',
        body: JSON.stringify(request),
      });
      const scheduleMap = toScheduleMap(response);
      setSchedules(scheduleMap);
      setEditSchedules(scheduleMap);
      setEditing(false);
      setMessage('근무표가 저장되었습니다.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '근무표 저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="staff-work-panel">
      <div className="staff-work-toolbar">
        <Dropdown
          classNamePrefix="custom-select"
          label="지점"
          open={branchOpen}
          options={branchOptions.length > 0 ? branchOptions : [{ value: '', label: '망미점' }]}
          selectedOption={selectedBranchOption}
          onSelect={selectBranch}
          onToggle={() => setBranchOpen((current) => !current)}
        />
        {editable && (
          <div className="staff-work-edit-actions">
            {editing ? (
              <>
                <button className="staff-work-cancel-button" type="button" disabled={saving} onClick={cancelEdit}>취소</button>
                <button className="staff-work-save-button" type="button" disabled={saving} onClick={saveSchedules}>
                  {saving ? '저장 중' : '저장'}
                </button>
              </>
            ) : (
              <button className="staff-work-edit-button" type="button" onClick={startEdit}>수정</button>
            )}
          </div>
        )}
      </div>
      <div className="staff-work-table-wrap">
        <table className="staff-work-table">
          <thead>
            <tr>
              <th>요일</th>
              <th><span className="staff-work-head-label">오전<span>설거지</span></span></th>
              <th><span className="staff-work-head-label">오전<span>서브</span></span></th>
              <th><span className="staff-work-head-label">오후<span>설거지</span></span></th>
              <th><span className="staff-work-head-label">오후<span>서브</span></span></th>
            </tr>
          </thead>
          <tbody>
            {DAYS.map((day) => (
              <tr key={day.key}>
                <th className="staff-work-weekday-cell">{day.label}</th>
                {TASK_CELLS.map((cell) => {
                  const key = toScheduleKey(day.key, cell.shift, cell.taskType);
                  const value = editing ? editSchedules[key] : schedules[key];

                  return (
                    <td className="staff-work-member" key={key}>
                      {editing ? (
                        <input
                          aria-label={`${day.label} ${cell.timeLabel} ${cell.taskLabel}`}
                          value={value || ''}
                          maxLength={50}
                          onChange={(event) => changeWorkerName(key, event.target.value)}
                        />
                      ) : (
                        value || '-'
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {message && <p className="staff-work-message">{message}</p>}
    </div>
  );
}

function createEmptyScheduleMap() {
  return DAYS.reduce<Record<string, string>>((acc, day) => {
    TASK_CELLS.forEach((cell) => {
      acc[toScheduleKey(day.key, cell.shift, cell.taskType)] = '';
    });

    return acc;
  }, {});
}

function toScheduleMap(responses: StaffScheduleResponse[]) {
  const scheduleMap = createEmptyScheduleMap();
  responses.forEach((schedule) => {
    scheduleMap[toScheduleKey(schedule.dayOfWeek, schedule.shift, schedule.taskType)] = schedule.workerName || '';
  });

  return scheduleMap;
}

function toScheduleKey(dayOfWeek: StaffScheduleDayOfWeek, shift: StaffScheduleShift, taskType: StaffScheduleTaskType) {
  return `${dayOfWeek}:${shift}:${taskType}`;
}
