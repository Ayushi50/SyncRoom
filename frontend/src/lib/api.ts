import type { AuthResponse } from './types';

export class ApiError extends Error {
  status: number;
  title: string;
  extras: Record<string, unknown>;
  constructor(status: number, title: string, detail: string, extras: Record<string, unknown> = {}) {
    super(detail);
    this.status = status;
    this.title = title;
    this.extras = extras;
  }
}

let accessToken: string | null = null;
let refreshing: Promise<AuthResponse | null> | null = null;
let onSessionLost: (() => void) | null = null;

export const setAccessToken = (t: string | null) => {
  accessToken = t;
};
export const setSessionLostHandler = (fn: (() => void) | null) => {
  onSessionLost = fn;
};

async function parseError(res: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {};
  try {
    body = await res.json();
  } catch {
    /* empty body */
  }
  const { title, detail, status: _s, type: _t, ...extras } = body as Record<string, unknown>;
  void _s;
  void _t;
  return new ApiError(
    res.status,
    typeof title === 'string' ? title : 'Request failed',
    typeof detail === 'string'
      ? detail
      : 'Something went wrong. Check your connection and try again.',
    extras,
  );
}

export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshing) {
    refreshing = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(async (res) => {
        if (!res.ok) return null;
        const data = (await res.json()) as AuthResponse;
        accessToken = data.accessToken;
        return data;
      })
      .catch(() => null)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

interface Options {
  method?: string;
  body?: unknown;
  auth?: boolean;
}

export async function api<T>(path: string, opts: Options = {}, retried = false): Promise<T> {
  const { method = 'GET', body, auth = true } = opts;
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;
  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(
      0,
      'Network error',
      'Could not reach the server. Check your connection and try again.',
    );
  }
  if (res.status === 401 && auth && !retried) {
    const renewed = await refreshSession();
    if (renewed) return api<T>(path, opts, true);
    accessToken = null;
    onSessionLost?.();
  }
  if (!res.ok) throw await parseError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
