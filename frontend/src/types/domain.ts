export type MemberRole = 'ADMIN' | 'STAFF' | 'MEMBER';

export type Branch = {
  id: number;
  name: string;
  address?: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type NameplateContent = {
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
  seatNumber?: number;
  expectedJoinDate?: string;
  nameplateContentId?: number | null;
  drinkSetting?: string | null;
  drinkNote?: string | null;
  memberNote?: string | null;
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
