import axios from 'axios';

/**
 * The single HTTP client for the whole app. Feature API modules (src/features/x/api.js) call the
 * helpers below; nothing else talks to the network.
 *
 * - baseURL from VITE_API_URL (default http://localhost:8080/api/v1)
 * - withCredentials: the refresh token is an httpOnly cookie scoped to /api/v1/auth
 * - the access token lives in memory only and is attached as "Authorization: Bearer ..."
 * - a 401 triggers one POST /auth/refresh (concurrent 401s wait for the same refresh), then the
 *   request is retried; if the refresh fails the session is cleared (logout)
 * - errors are normalised to ApiError { status, code, message, details }
 */
export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';

export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

/** Query strings like the API expects: arrays comma-joined, empty values dropped. */
function serializeParams(params = {}) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) {
      if (value.length) search.set(key, value.join(','));
    } else {
      search.set(key, String(value));
    }
  }
  return search.toString();
}

export const http = axios.create({
  baseURL: API_URL,
  withCredentials: true,
  headers: { Accept: 'application/json' },
  paramsSerializer: { serialize: serializeParams },
});

// ---------------------------------------------------------------- session (in-memory access token)

let accessToken = null;
let refreshTimer;
let refreshInFlight = null;
const listeners = new Set();

/** Subscribe to login/logout; the listener receives the user or null. Returns an unsubscribe fn. */
export function onSessionChange(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function decodeExp(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function setSession(token, user) {
  accessToken = token;
  clearTimeout(refreshTimer);
  if (token) {
    // Silent refresh one minute before the 15-minute access token expires.
    const exp = decodeExp(token);
    if (exp) refreshTimer = setTimeout(() => void refreshSession(), Math.max(5_000, exp - Date.now() - 60_000));
  }
  listeners.forEach((l) => l(user));
}

/**
 * Rotates the refresh cookie and obtains a new access token. Single-flight: every caller (and
 * every queued 401) awaits the same promise. Resolves to the user, or null after logging out.
 */
export function refreshSession() {
  if (!refreshInFlight) {
    refreshInFlight = http
      .post('/auth/refresh', null, { skipAuthRefresh: true })
      .then(({ data }) => {
        setSession(data.accessToken, data.user);
        return data.user;
      })
      .catch(() => {
        setSession(null, null);
        return null;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

// ---------------------------------------------------------------- interceptors

http.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

function toApiError(error) {
  if (axios.isCancel(error)) return error;
  const res = error.response;
  if (!res) return new ApiError(0, 'NETWORK_ERROR', 'Could not reach the server. Check your connection.');
  const body = res.data && typeof res.data === 'object' ? res.data.error : undefined;
  return new ApiError(res.status, body?.code ?? 'HTTP_ERROR', body?.message ?? res.statusText ?? 'Request failed', body?.details);
}

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config;
    const status = error.response?.status;
    const isAuthCall = config?.url?.startsWith('/auth/');

    // Expired/invalid access token: refresh once (shared), then retry the original request.
    if (status === 401 && config && !config.skipAuthRefresh && !config._retried && !isAuthCall) {
      config._retried = true;
      const user = await refreshSession();
      if (user) return http(config);
    }

    const apiError = toApiError(error);
    if (status === 403 && (apiError.code === 'ACCOUNT_SUSPENDED' || apiError.code === 'ACCOUNT_BANNED')) {
      setSession(null, null);
    }
    return Promise.reject(apiError);
  },
);

// ---------------------------------------------------------------- helpers used by feature API modules

/** GET; `params` become the query string, `signal` cancels (React Query). */
export const apiGet = (url, { params, signal } = {}) => http.get(url, { params, signal }).then((r) => r.data);

/** POST a JSON body, or a FormData (multipart, e.g. images). */
export const apiPost = (url, data) => http.post(url, data ?? {}).then((r) => r.data);

export const apiPatch = (url, data) => http.patch(url, data ?? {}).then((r) => r.data);

export const apiDelete = (url) => http.delete(url).then((r) => r.data);
