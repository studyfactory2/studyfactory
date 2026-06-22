const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export async function apiRequest(path, options = {}) {
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
    return null;
  }

  return response.json();
}

async function resolveErrorMessage(response) {
  let fallbackMessage = '요청을 처리하지 못했습니다.';
  try {
    const error = await response.json();
    return error.message || error.reason || error.error || fallbackMessage;
  } catch {
    const message = await response.text();
    if (message) {
      return message;
    }
  }

  return fallbackMessage;
}
