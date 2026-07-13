import { Chessboard } from 'react-chessboard';
import { Chess } from 'chess.js';
import type { CSSProperties } from 'react';
import { useMemo, useState } from 'react';
import { arrowFromMove, legalDestinations, uci } from '../lib/chess';
import type { HintResponse, MoveDto, SessionDto } from '../types/api';

interface Props {
  fen: string;
  session?: SessionDto;
  hint?: HintResponse;
  solutionMove?: MoveDto;
  analysisMoves?: MoveDto[];
  reviewMode?: boolean;
  onMove: (uciMove: string) => Promise<boolean>;
}

export function TrainingBoard({ fen, session, hint, solutionMove, analysisMoves = [], reviewMode = false, onMove }: Props) {
  const [selected, setSelected] = useState<string>();
  const canMove = !reviewMode && (!session || session.status === 'ACTIVE') && new Chess(fen).turn() === 'w';
  const legalSquares = useMemo(() => (selected && canMove ? legalDestinations(fen, selected) : []), [canMove, fen, selected]);
  const highlightedMoves = (session?.moves ?? []).slice(-2).map((move) => move.uci);
  // FIXED: analysis replay only drew one side's move because the board accepted a single solution move.
  const solutionArrows = analysisMoves.length > 0 ? analysisMoves.flatMap((move) => arrowFromMove(move)) : arrowFromMove(solutionMove);
  const arrows = (reviewMode ? solutionArrows : []) as never;

  const customSquareStyles = useMemo(() => {
    const styles: Record<string, CSSProperties> = {};
    if (selected) {
      styles[selected] = { boxShadow: 'inset 0 0 0 4px rgba(246,160,77,.85)' };
    }
    for (const square of legalSquares) {
      styles[square] = {
        background: 'radial-gradient(circle, rgba(21,23,19,.28) 18%, transparent 20%)',
      };
    }
    highlightedMoves.forEach((move, index) => {
      if (move.length >= 4) {
        const fromColor = index === highlightedMoves.length - 1 ? 'rgba(246,160,77,.38)' : 'rgba(124,58,237,.25)';
        const toColor = index === highlightedMoves.length - 1 ? 'rgba(246,160,77,.50)' : 'rgba(124,58,237,.34)';
        styles[move.slice(0, 2)] = { backgroundColor: fromColor };
        styles[move.slice(2, 4)] = { backgroundColor: toColor };
      }
    });
    if (reviewMode && solutionMove?.uci && solutionMove.uci.length >= 4) {
      styles[solutionMove.uci.slice(0, 2)] = { backgroundColor: 'rgba(59,130,246,.30)' };
      styles[solutionMove.uci.slice(2, 4)] = { backgroundColor: 'rgba(59,130,246,.42)' };
      // FIXED: review mode now marks initial and final squares with color instead of relying only on arrows.
    }
    const chess = new Chess(fen);
    if (chess.inCheck()) {
      for (const row of chess.board()) {
        for (const piece of row) {
          if (piece?.type === 'k' && piece.color === chess.turn()) {
            styles[piece.square] = { boxShadow: 'inset 0 0 0 5px rgba(220,38,38,.85)' };
          }
        }
      }
    }
    return styles;
  }, [fen, highlightedMoves, legalSquares, reviewMode, selected, solutionMove]);

  return (
    <div className="mx-auto w-full max-w-[min(86vh,720px)]">
      <div className="overflow-hidden rounded-forge border border-black/10 bg-[#7a4b2a] p-2 shadow-board dark:border-white/10">
        <Chessboard
          position={fen}
          boardWidth={Math.min(720, Math.max(320, window.innerWidth > 900 ? window.innerHeight * 0.74 : window.innerWidth - 32))}
          customBoardStyle={{ borderRadius: 10 }}
          customDarkSquareStyle={{ backgroundColor: '#9f6f43' }}
          customLightSquareStyle={{ backgroundColor: '#ead8b7' }}
          customSquareStyles={customSquareStyles}
          customArrows={arrows}
          arePremovesAllowed
          // FIXED: premoves were disabled on the board, so users could not queue intended moves while waiting for review/defender animations.
          animationDuration={180}
          onPieceDrop={(from, to) => {
            if (!canMove) return false;
            const chess = new Chess(fen);
            if (chess.get(from as never)?.color !== 'w') return false;
            void onMove(uci(from, to));
            return false;
            // FIXED: users could drag defender pieces during their timer turn, sending illegal black moves to the API.
          }}
          onSquareClick={async (square) => {
            if (!canMove) return;
            const chess = new Chess(fen);
            const piece = chess.get(square as never);
            if (!selected && piece?.color !== 'w') return;
            if (!selected) {
              setSelected(square);
              return;
            }
            if (selected === square) {
              setSelected(undefined);
              return;
            }
            const accepted = await onMove(uci(selected, square));
            setSelected(accepted ? undefined : square);
          }}
        />
      </div>
    </div>
  );
}
