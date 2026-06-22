import type { TokenPayload } from '../types/domain';

export function decodeTokenPayload(token: string): TokenPayload {
  try {
    const payload = token.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(normalized)
        .split('')
        .map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join(''),
    );

    return JSON.parse(json) as TokenPayload;
  } catch {
    return {};
  }
}
