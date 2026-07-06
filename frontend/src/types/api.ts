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

export interface MoveHighlightDto {
  ply: number;
  comment: string;
}

export interface GameReportDto {
  summary: string;
  strengths: string[];
  weaknesses: string[];
  recurringMistakes: string[];
  moveHighlights: MoveHighlightDto[];
  nextFocusAreas: string[];
  overallRatingBand: string;
}

export interface PlayerProfileReportDto {
  playerLevel: string;
  styleSummary: string;
  strongModes: string[];
  weakModes: string[];
  trendNotes: string;
  recommendedDrills: string[];
  consistencyScore: number;
}

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
  email: string;
}

export interface FavoriteDto {
  id: string;
  name: string;
  fen: string;
}

export interface AccuracyPointDto {
  date: string;
  accuracy: number;
}

export interface ModeBestTimeDto {
  mode: TrainingMode;
  seconds?: number;
}

export interface ModeDifficultyBreakdownDto {
  mode: TrainingMode;
  difficulty: Difficulty;
  sessionsPlayed: number;
  averageAccuracy: number;
}

export interface BestCheckmateModeDto {
  mode: TrainingMode;
  completed: number;
  averageAccuracy: number;
  bestSeconds?: number;
}

export interface ProfileDto {
  username: string;
  joinDate: string;
  totalSessions: number;
  totalCompleted: number;
  currentStreak: number;
  tournamentRating: number;
  regularChessRating: number;
  bestCheckmateModes: BestCheckmateModeDto[];
  bestTimes: ModeBestTimeDto[];
  accuracyTrend: AccuracyPointDto[];
  breakdown: ModeDifficultyBreakdownDto[];
  favoritePositionsCount: number;
  leaderboardRank: number;
  totalRankedUsers: number;
}

export interface CreateTournamentRequest {
  name: string;
  mode: TrainingMode;
  difficulty: Difficulty;
  rounds: number;
  scheduledStartAt: string;
}

export interface SubmitTournamentResultRequest {
  roundNumber: number;
  accuracy: number;
  timeSeconds: number;
  hintsUsed: number;
}

export interface TournamentDto {
  code: string;
  name: string;
  hostUsername: string;
  mode: TrainingMode;
  difficulty: Difficulty;
  rounds: number;
  scheduledStartAt: string;
  status: 'LOBBY' | 'LIVE' | string;
  joined: boolean;
  joinUrl: string;
  participantCount: number;
}

export interface StandingDto {
  username: string;
  completedRounds: number;
  totalPoints: number;
  averageAccuracy: number;
  totalTimeSeconds: number;
}

export interface TournamentDetailDto {
  tournament: TournamentDto;
  standings: StandingDto[];
}
