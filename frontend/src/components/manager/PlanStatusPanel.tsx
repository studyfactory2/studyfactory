import { useState } from 'react';
import type { Branch, MemberResponse } from '../../types/domain';
import { MemberWeeklyPlanViewer } from './MemberWeeklyPlanViewer';
import { VacationHistoryPanel } from './VacationHistoryPanel';

type PlanStatusPanelProps = {
  branches: Branch[];
};

export function PlanStatusPanel({ branches }: PlanStatusPanelProps) {
  const [selectedMember, setSelectedMember] = useState<MemberResponse | null>(null);

  if (selectedMember) {
    return <MemberWeeklyPlanViewer branches={branches} member={selectedMember} onBack={() => setSelectedMember(null)} />;
  }

  return <VacationHistoryPanel branches={branches} title="사원별 계획 현황" onMemberSelect={setSelectedMember} />;
}
