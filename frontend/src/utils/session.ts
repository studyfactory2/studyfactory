import type { AccessTokenResponse, MemberRole, TokenPayload } from '../types/domain';
import { decodeTokenPayload } from './token';

const SESSION_KEYS = ['accessToken', 'refreshToken', 'memberName', 'memberId', 'branchId', 'memberRole'] as const;

export function saveSession(accessToken: string, refreshToken?: string) {
  const payload = decodeTokenPayload(accessToken);

  localStorage.setItem('accessToken', accessToken);
  if (refreshToken) {
    localStorage.setItem('refreshToken', refreshToken);
  }
  localStorage.setItem('memberName', payload.name || '');
  localStorage.setItem('memberId', payload.sub ? String(payload.sub) : '');
  localStorage.setItem('branchId', payload.branchId ? String(payload.branchId) : '');
  localStorage.setItem('memberRole', payload.role || '');

  return payload;
}

export function clearSession() {
  SESSION_KEYS.forEach((key) => localStorage.removeItem(key));
}

export function dashboardPathForRole(role?: MemberRole) {
  return role === 'ADMIN' || role === 'STAFF'
    ? '/managerdashboard?view=attendance'
    : '/memberdashboard';
}

export function hasUsableAccessToken() {
  const accessToken = localStorage.getItem('accessToken');
  if (!accessToken) {
    return false;
  }

  const payload = decodeTokenPayload(accessToken);
  return Boolean(payload.role && payload.exp && payload.exp * 1000 > Date.now());
}

export async function restoreSession(requestAccessToken: (refreshToken: string) => Promise<AccessTokenResponse>) {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) {
    return null;
  }

  const tokens = await requestAccessToken(refreshToken);
  return saveSession(tokens.accessToken);
}
