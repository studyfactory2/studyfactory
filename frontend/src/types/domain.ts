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

export type LeaveType = 'FULL' | 'MORNING' | 'AFTERNOON';

export type DailyLeaveStatusResponse = {
  name: string;
  branch: string;
  leaveType: LeaveType;
  createdAt: string;
};

export type MonthlyLeaveCalendarResponse = {
  leaveDate: string;
  label: string;
  source: 'LEAVE' | 'FIXED_LEAVE' | 'SPECIAL_LEAVE';
};

export type AttendanceBoardRowResponse = {
  seatNumber?: number | null;
  name: string;
  slots: string[];
};

export type DailyAttendanceBoardResponse = {
  date: string;
  rows: AttendanceBoardRowResponse[];
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
