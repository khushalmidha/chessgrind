import type {
  AuthResponse,
  FavoriteDto,
  HintResponse,
  GameReportDto,
  MoveResponse,
  PlayerProfileReportDto,
  ProfileDto,
  PuzzleDto,
  SessionDto,
  SolutionResponse,
  StartSessionRequest,
  ImportSessionRequest,
  CreateTournamentRequest,
  SubmitTournamentResultRequest,
  TournamentDetailDto,
  TournamentDto,
  StandingDto,
} from '../types/api';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';
const TOKEN_KEY = 'mateforge.token';
const USER_KEY = 'mateforge.user';
const AUTH_EXPIRED_EVENT = 'mateforge:auth-expired';

export class HttpError extends Error {
  constructor(
    public status: number,
    message: string,
    public body?: unknown,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

export function token() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(value: string) {
  localStorage.setItem(TOKEN_KEY, value);
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function onAuthExpired(callback: () => void) {
  window.addEventListener(AUTH_EXPIRED_EVENT, callback);
  return () => window.removeEventListener(AUTH_EXPIRED_EVENT, callback);
}

function notifyAuthExpired() {
  window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
}

export function currentUser(): AuthResponse | undefined {
  const raw = localStorage.getItem(USER_KEY);
  try {
    return raw ? JSON.parse(raw) as AuthResponse : undefined;
  } catch {
    clearAuth();
    return undefined;
    // FIXED: corrupt stored user JSON could crash app startup while leaving the JWT behind.
  }
}

export function setAuth(value: AuthResponse) {
  localStorage.setItem(TOKEN_KEY, value.token);
  localStorage.setItem(USER_KEY, JSON.stringify(value));
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Content-Type', 'application/json');
  const auth = token();
  if (auth) {
    headers.set('Authorization', `Bearer ${auth}`);
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  } catch {
    throw new Error('Cannot reach the server');
    // FIXED: network failures were surfaced as generic rejected fetches without a user-readable API error.
  }
  if (!response.ok) {
    const body = await response.json().catch(() => undefined as unknown);
    const bodyMessage = typeof body === 'object' && body && 'message' in body
      ? String((body as { message?: unknown }).message ?? '')
      : '';
    const message = bodyMessage || response.statusText || 'Request failed';
    if (response.status === 401) {
      clearAuth();
      notifyAuthExpired();
      throw new HttpError(response.status, message || 'Session expired, please sign in again', body);
      // FIXED: expired JWTs now clear the stored auth and emit a UI signal instead of failing silently.
    }
    throw new HttpError(response.status, message, body);
  }
  return response.json() as Promise<T>;
}

export const api = {
  register: (username: string, email: string, password: string) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify({ username, email, password }) }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  google: (credential: string) =>
    request<AuthResponse>('/api/auth/google', { method: 'POST', body: JSON.stringify({ credential }) }),
  puzzles: () => request<PuzzleDto[]>('/api/puzzles'),
  startSession: (payload: StartSessionRequest) =>
    request<SessionDto>('/api/sessions', { method: 'POST', body: JSON.stringify(payload) }),
  importSession: (payload: ImportSessionRequest) =>
    request<SessionDto>('/api/sessions/import', { method: 'POST', body: JSON.stringify(payload) }),
  move: (sessionId: string, uci: string) =>
    request<MoveResponse>(`/api/sessions/${sessionId}/moves`, { method: 'POST', body: JSON.stringify({ uci }) }),
  hint: (sessionId: string) => request<HintResponse>(`/api/sessions/${sessionId}/hint`, { method: 'POST' }),
  undo: (sessionId: string) => request<SessionDto>(`/api/sessions/${sessionId}/undo`, { method: 'POST' }),
  solution: (sessionId: string) => request<SolutionResponse>(`/api/sessions/${sessionId}/solution`),
  report: (sessionId: string, refresh = false) =>
    request<GameReportDto>(`/api/sessions/${sessionId}/report?refresh=${refresh ? 'true' : 'false'}`),
  profile: () => request<ProfileDto>('/api/analytics/profile'),
  profileReport: (refresh = false) =>
    request<PlayerProfileReportDto>(`/api/analytics/profile-report?refresh=${refresh ? 'true' : 'false'}`),
  favorites: () => request<FavoriteDto[]>('/api/favorites'),
  createTournament: (payload: CreateTournamentRequest) =>
    request<TournamentDetailDto>('/api/tournaments', { method: 'POST', body: JSON.stringify(payload) }),
  myTournaments: () => request<TournamentDto[]>('/api/tournaments'),
  tournament: (code: string) => request<TournamentDetailDto>(`/api/tournaments/${code}`),
  joinTournament: (code: string) => request<TournamentDetailDto>(`/api/tournaments/${code}/join`, { method: 'POST' }),
  standings: (code: string) => request<StandingDto[]>(`/api/tournaments/${code}/standings`),
  submitTournamentResult: (code: string, payload: SubmitTournamentResultRequest) =>
    request<TournamentDetailDto>(`/api/tournaments/${code}/results`, { method: 'POST', body: JSON.stringify(payload) }),
};
