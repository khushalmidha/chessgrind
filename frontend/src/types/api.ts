export type TrainingMode =
  | 'KING_ROOK_VS_KING'
  | 'TWO_ROOKS_VS_KING'
  | 'QUEEN_VS_KING'
  | 'TWO_BISHOPS_VS_KING'
  | 'BISHOP_KNIGHT_VS_KING'
  | 'TWO_PAWNS_VS_KING'
  | 'CUSTOM'
  | 'RANDOM';

export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type TimerMode = 'FIXED_COUNTDOWN' | 'INCREMENTAL' | 'NONE';
export type SessionStatus = 'ACTIVE' | 'CHECKMATE' | 'STALEMATE' | 'DRAW' | 'TIMEOUT' | 'RESIGNED' | 'ABANDONED';

export interface PuzzleDto {
  id: string;
  mode: TrainingMode;
  difficulty: Difficulty;
  title: string;
  fen: string;
  targetMateMoves: number;
  dailyChallenge: boolean;
}

export interface MoveDto {
  ply: number;
  uci: string;
  san: string;
  fenAfter: string;
  engineMove: boolean;
  optimal: boolean;
  reason: string;
}

export interface SessionDto {
  id: string;
  mode: TrainingMode;
  difficulty: Difficulty;
  timerMode: TimerMode;
  status: SessionStatus;
  startFen: string;
  currentFen: string;
  remainingSeconds: number;
  hintsUsed: number;
  mistakes: number;
  accuracy: number;
  startedAt: string;
  endedAt?: string;
  moves: MoveDto[];
}

export interface StartSessionRequest {
  mode: TrainingMode;
  difficulty: Difficulty;
  timerMode: TimerMode;
  timeLimitSeconds: number;
  incrementSeconds: number;
  hintsEnabled: boolean;
  takebacksEnabled: boolean;
  puzzleId?: string;
  customFen?: string;
}

export interface MoveResponse {
  session: SessionDto;
  userMove: string;
  engineMove?: string;
  gameState: string;
  check: boolean;
  checkmate: boolean;
  stalemate: boolean;
  moveQuality?: 'BEST' | 'CHECKMATE' | 'INACCURACY' | 'MISTAKE' | 'BLUNDER';
  bestMove?: string;
  coachNote?: string;
  message: string;
}

export interface HintResponse {
  bestMove: string;
  san: string;
  reason: string;
  arrows: string[];
}

export interface SolutionResponse {
  line: MoveDto[];
  summary: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
  email: string;
}

export interface ProgressSummary {
  completed: number;
  activeSessions: number;
  streakDays: number;
  averageAccuracy: number;
  rank: number;
  totalRankedUsers: number;
  bestCheckmateSeconds?: number;
  recentMistakes: string[];
}

export interface LeaderboardEntry {
  username: string;
  mode: TrainingMode;
  difficulty: Difficulty;
  seconds: number;
  accuracy: number;
}

export interface TournamentParticipant {
  userId: string;
  username: string;
  score: number;
  bestTimeSeconds: number;
  bestAccuracy: number;
}

export interface Tournament {
  id: string;
  name: string;
  joinCode: string;
  shareUrl: string;
  status: 'DRAFT' | 'OPEN' | 'ACTIVE' | 'FINISHED';
  mode: TrainingMode;
  difficulty: Difficulty;
  timeLimitSeconds: number;
  maxPlayers: number;
  playerCount: number;
  createdAt: string;
  participants: TournamentParticipant[];
}
