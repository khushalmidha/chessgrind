import type { Difficulty, PuzzleDto, TrainingMode } from '../types/api';

export const modeLabels: Record<TrainingMode, string> = {
  KING_ROOK_VS_KING: 'King + rook',
  TWO_ROOKS_VS_KING: 'Two rooks',
  QUEEN_VS_KING: 'Queen',
  TWO_BISHOPS_VS_KING: 'Two bishops',
  BISHOP_KNIGHT_VS_KING: 'Bishop + knight',
  TWO_PAWNS_VS_KING: 'Two pawns',
  CUSTOM: 'Custom',
  RANDOM: 'Random',
};

export const difficultyLabels: Record<Difficulty, string> = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
};

export const fallbackPuzzles: PuzzleDto[] = [
  {
    id: 'local-krk',
    mode: 'KING_ROOK_VS_KING',
    difficulty: 'BEGINNER',
    title: 'Box the king',
    fen: '8/8/8/8/8/4k3/8/4K2R w K - 0 1',
    targetMateMoves: 8,
    dailyChallenge: true,
  },
  {
    id: 'local-rrk',
    mode: 'TWO_ROOKS_VS_KING',
    difficulty: 'BEGINNER',
    title: 'Ladder mate',
    fen: '8/8/8/8/8/4k3/8/R3K2R w KQ - 0 1',
    targetMateMoves: 5,
    dailyChallenge: false,
  },
  {
    id: 'local-qk',
    mode: 'QUEEN_VS_KING',
    difficulty: 'BEGINNER',
    title: 'Queen shoulder',
    fen: '8/8/8/8/8/3k4/8/4K2Q w - - 0 1',
    targetMateMoves: 7,
    dailyChallenge: false,
  },
  {
    id: 'local-bbk',
    mode: 'TWO_BISHOPS_VS_KING',
    difficulty: 'INTERMEDIATE',
    title: 'Drive to the corner',
    fen: '8/8/8/8/3k4/8/8/2B1KB2 w - - 0 1',
    targetMateMoves: 16,
    dailyChallenge: false,
  },
  {
    id: 'local-bnk',
    mode: 'BISHOP_KNIGHT_VS_KING',
    difficulty: 'ADVANCED',
    title: 'Wrong corner no more',
    fen: '8/8/8/8/3k4/8/8/2B1KN2 w - - 0 1',
    targetMateMoves: 24,
    dailyChallenge: false,
  },
];
