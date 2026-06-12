import type {
  AuthResponse,
  HintResponse,
  LeaderboardEntry,
  MoveResponse,
  PuzzleDto,
  ProgressSummary,
  SessionDto,
  SolutionResponse,
  StartSessionRequest,
  Tournament,
} from '../types/api';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';
const TOKEN_KEY = 'mateforge.token';
const USER_KEY = 'mateforge.user';

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

export function currentUser(): AuthResponse | undefined {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? JSON.parse(raw) as AuthResponse : undefined;
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
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(body.message ?? 'Request failed');
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
  move: (sessionId: string, uci: string) =>
    request<MoveResponse>(`/api/sessions/${sessionId}/moves`, { method: 'POST', body: JSON.stringify({ uci }) }),
  hint: (sessionId: string) => request<HintResponse>(`/api/sessions/${sessionId}/hint`, { method: 'POST' }),
  undo: (sessionId: string) => request<SessionDto>(`/api/sessions/${sessionId}/undo`, { method: 'POST' }),
  solution: (sessionId: string) => request<SolutionResponse>(`/api/sessions/${sessionId}/solution`),
  progress: () => request<ProgressSummary>('/api/analytics/progress'),
  leaderboard: () => request<LeaderboardEntry[]>('/api/leaderboard'),
  createTournament: (payload: { name: string; mode: string; difficulty: string; timeLimitSeconds: number; maxPlayers: number }) =>
    request<Tournament>('/api/tournaments', { method: 'POST', body: JSON.stringify(payload) }),
  myTournaments: () => request<Tournament[]>('/api/tournaments/mine'),
  tournament: (joinCode: string) => request<Tournament>(`/api/tournaments/${joinCode}`),
  joinTournament: (joinCode: string) => request<Tournament>(`/api/tournaments/${joinCode}/join`, { method: 'POST' }),
};
