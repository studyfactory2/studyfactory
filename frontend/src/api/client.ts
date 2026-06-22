const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export async function apiRequest<TResponse>(path: string, options: RequestInit = {}): Promise<TResponse> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  });

  if (!response.ok) {
    throw new Error(await resolveErrorMessage(response));
  }

  if (response.status === 204) {
    return null as TResponse;
  }

  return response.json() as Promise<TResponse>;
}

async function resolveErrorMessage(response: Response): Promise<string> {
  const fallbackMessage = '요청을 처리하지 못했습니다.';
  try {
    const error = (await response.json()) as { message?: string; reason?: string; error?: string };
    return error.message || error.reason || error.error || fallbackMessage;
  } catch {
    const message = await response.text();
    if (message) {
      return message;
    }
  }

  return fallbackMessage;
}
