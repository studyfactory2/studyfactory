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
