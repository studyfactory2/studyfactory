const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export class ApiRequestError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

export async function apiRequest<TResponse>(path: string, options: RequestInit = {}): Promise<TResponse> {
  const accessToken = localStorage.getItem('accessToken');
  const response = await fetch(`${API_BASE_URL}${path}`, {
    // 출석·음료 현황처럼 즉시 변경되는 화면은 이전 GET 응답을 재사용하지 않는다.
    cache: 'no-store',
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...options.headers,
    },
    ...options,
  });

  if (!response.ok) {
    throw new ApiRequestError(await resolveErrorMessage(response), response.status);
  }

  if (response.status === 204) {
    return null as TResponse;
  }

  return response.json() as Promise<TResponse>;
}

async function resolveErrorMessage(response: Response): Promise<string> {
  const fallbackMessage = '요청을 처리하지 못했습니다.';
  const message = await response.text();
  if (!message) {
    return fallbackMessage;
  }

  try {
    const error = JSON.parse(message) as { message?: string; reason?: string; detail?: string; error?: string };
    return error.message || error.reason || error.detail || error.error || fallbackMessage;
  } catch {
    return message;
  }
}
