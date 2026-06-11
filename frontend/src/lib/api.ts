import type {
  AuthResponse,
  HintResponse,
  MoveResponse,
  PuzzleDto,
  SessionDto,
  SolutionResponse,
  StartSessionRequest,
} from '../types/api';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';
const TOKEN_KEY = 'mateforge.token';

export function token() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(value: string) {
  localStorage.setItem(TOKEN_KEY, value);
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
  puzzles: () => request<PuzzleDto[]>('/api/puzzles'),
  startSession: (payload: StartSessionRequest) =>
    request<SessionDto>('/api/sessions', { method: 'POST', body: JSON.stringify(payload) }),
  move: (sessionId: string, uci: string) =>
    request<MoveResponse>(`/api/sessions/${sessionId}/moves`, { method: 'POST', body: JSON.stringify({ uci }) }),
  hint: (sessionId: string) => request<HintResponse>(`/api/sessions/${sessionId}/hint`, { method: 'POST' }),
  undo: (sessionId: string) => request<SessionDto>(`/api/sessions/${sessionId}/undo`, { method: 'POST' }),
  solution: (sessionId: string) => request<SolutionResponse>(`/api/sessions/${sessionId}/solution`),
};
