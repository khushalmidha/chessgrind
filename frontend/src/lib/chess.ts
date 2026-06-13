import { Chess } from 'chess.js';
import type { MoveDto, SessionDto } from '../types/api';

export function uci(from: string, to: string, promotion = 'q') {
  const needsPromotion = (from[1] === '7' && to[1] === '8') || (from[1] === '2' && to[1] === '1');
  return `${from}${to}${needsPromotion ? promotion : ''}`;
}

export function legalDestinations(fen: string, square: string) {
  const chess = new Chess(fen);
  return (chess.moves({ square: square as never, verbose: true }) as Array<{ to: string }>).map((move) => move.to);
}

export function boardStateLabel(session?: SessionDto) {
  if (!session) return 'Ready';
  if (session.status === 'CHECKMATE') return 'Checkmate';
  if (session.status === 'STALEMATE') return 'Stalemate';
  if (session.status === 'DRAW') return 'Draw';
  if (session.status === 'TIMEOUT') return 'Timeout';
  const chess = new Chess(session.currentFen);
  return chess.inCheck() ? 'Check' : 'Training';
}

export function arrowFromMove(move?: string | MoveDto, color?: string): [string, string] | [string, string, string] | undefined {
  const u = typeof move === 'string' ? move : move?.uci;
  if (!u || u.length < 4) return undefined;
  return color ? [u.slice(0, 2), u.slice(2, 4), color] : [u.slice(0, 2), u.slice(2, 4)];
}
