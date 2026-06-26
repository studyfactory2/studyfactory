export type MemberRole = 'ADMIN' | 'STAFF' | 'MEMBER';

export type Branch = {
  id: number;
  name: string;
  address?: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type Certification = {
  id: number;
  content: string;
  createdAt?: string;
  updatedAt?: string;
};

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
};

export type TokenPayload = {
  sub?: number | string;
  name?: string;
  branchId?: number | string;
  role?: MemberRole;
};

export type PreRegistrationVerifyResponse = {
  id: number;
  branchId: number;
  name: string;
  role: MemberRole;
  seatNumber?: number | null;
  expectedJoinDate?: string;
  certificationId?: number | null;
  drinkSetting?: string | null;
  drinkNote?: string | null;
  memberNote?: string | null;
};

export type PreRegistrationResponse = PreRegistrationVerifyResponse & {
  createdAt: string;
  updatedAt: string;
};

export type MemberResponse = {
  id: number;
  branchId: number;
  name: string;
  role: MemberRole;
  seatNumber?: number | null;
  joinDate?: string | null;
  expectedJoinDate?: string | null;
  certificationId?: number | null;
  memberNote?: string | null;
  preparingCertifications?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type SuggestionCategory = 'SUPPLIES' | 'STUDY' | 'GENERAL' | 'COUNSELING';

export type SuggestionResponse = {
  id: number;
  memberId: number;
  branchId: number;
  resolvedByMemberId?: number | null;
  category: SuggestionCategory;
  content: string;
  isResolved: boolean;
  createdAt: string;
  updatedAt: string;
};

export type BeveragePreferenceResponse = {
  id: number;
  memberId: number;
  branchId: number;
  drinks: string;
  notes: string;
  createdAt: string;
  updatedAt: string;
};

export type MemberBeverageResponse = {
  memberId: number;
  branchId: number;
  memberName: string;
  role: MemberRole;
  seatNumber?: number | null;
  drinks: string;
  notes?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type RoomLayoutItemType = 'SEAT' | 'DOOR';

export type RoomLayoutItemResponse = {
  id: number;
  type: RoomLayoutItemType;
  number?: number | null;
  memberId?: number | null;
  x: number;
  y: number;
};

export type RoomLayoutResponse = {
  id: number;
  branchId: number;
  name: string;
  rows: number;
  cols: number;
  items: RoomLayoutItemResponse[];
};

export type MealType = 'LUNCH' | 'DINNER';

export type SideDishResponse = {
  id: number;
  memberId: number;
  branchId: number;
  mealDate: string;
  mealType: MealType;
  items: string;
  totalPrice: number;
  createdAt: string;
  updatedAt: string;
};

export type DailySideDishResponse = {
  id: number;
  memberId: number;
  branchId: number;
  memberName: string;
  seatNumber?: number | null;
  mealDate: string;
  mealType: MealType;
  items: string;
  totalPrice: number;
};

export type LeaveType = 'FULL' | 'MORNING' | 'AFTERNOON';

export type DailyLeaveStatusResponse = {
  memberId: number;
  branchId: number;
  seatNumber?: number | null;
  name: string;
  branch: string;
  leaveType: LeaveType;
  createdAt: string;
};

export type LeaveResponse = {
  id: number;
  memberId: number;
  branchId: number;
  leaveDate: string;
  leaveType: LeaveType;
  createdAt: string;
  updatedAt: string;
};

export type MonthlyLeaveCalendarResponse = {
  leaveDate: string;
  label: string;
  source: 'LEAVE' | 'FIXED_LEAVE' | 'SPECIAL_LEAVE';
};

export type AttendanceBoardRowResponse = {
  memberId?: number | null;
  seatNumber?: number | null;
  name: string;
  joinDate?: string | null;
  certificationContent?: string | null;
  slots: string[];
};

export type DailyAttendanceBoardResponse = {
  date: string;
  rows: AttendanceBoardRowResponse[];
};

export type TodoPriority = 'NORMAL' | 'URGENT';
export type TodoSourceType = 'MANUAL' | 'JOIN_MEMBER' | 'SUGGESTION';

export type TodoReplyResponse = {
  id: number;
  todoItemId: number;
  memberId: number;
  memberName: string;
  content: string;
  createdAt: string;
  updatedAt: string;
};

export type TodoResponse = {
  id: number;
  branchId: number;
  todoDate: string;
  content: string;
  priority: TodoPriority;
  sourceType: TodoSourceType;
  sourceId?: number | null;
  targetMemberId?: number | null;
  createdByMemberId?: number | null;
  createdByMemberName?: string | null;
  completed: boolean;
  completedByMemberId?: number | null;
  completedAt?: string | null;
  replies: TodoReplyResponse[];
  createdAt: string;
  updatedAt: string;
};

export type SpecialLeaveResponse = {
  id: number;
  memberId: number;
  branchId: number;
  leaveDate: string;
  slots: string;
  reason: string;
  customReason?: string | null;
  recurring: boolean;
  createdByMemberId: number;
  createdAt: string;
  updatedAt: string;
};

export type FixedLeaveManagementResponse = {
  id: number;
  memberId: number;
  branchId: number;
  memberName: string;
  dayOfWeek: StaffScheduleDayOfWeek;
  slots: string;
  reason: string;
  createdAt: string;
  updatedAt: string;
};

export type FixedLeaveGenerationResponse = {
  startDate: string;
  endDate: string;
  createdCount: number;
};

export type StaffScheduleDayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export type StaffScheduleShift = 'MORNING' | 'AFTERNOON';
export type StaffScheduleTaskType = 'DISHWASHING' | 'SERVE';

export type StaffScheduleResponse = {
  id?: number | null;
  branchId: number;
  dayOfWeek: StaffScheduleDayOfWeek;
  shift: StaffScheduleShift;
  taskType: StaffScheduleTaskType;
  workerName: string;
};

export type StaffScheduleUpdateRequest = {
  schedules: Array<{
    dayOfWeek: StaffScheduleDayOfWeek;
    shift: StaffScheduleShift;
    taskType: StaffScheduleTaskType;
    workerName: string;
  }>;
};
