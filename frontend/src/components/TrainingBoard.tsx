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
  onMove: (uciMove: string) => Promise<boolean>;
}

export function TrainingBoard({ fen, session, hint, solutionMove, analysisMoves = [], onMove }: Props) {
  const [selected, setSelected] = useState<string>();
  const legalSquares = useMemo(() => (selected ? legalDestinations(fen, selected) : []), [fen, selected]);
  const lastMove = session?.moves[session.moves.length - 1]?.uci;
  // FIXED: analysis replay only drew one side's move because the board accepted a single solution move.
  const solutionArrows = analysisMoves.length > 0 ? analysisMoves.flatMap((move) => arrowFromMove(move)) : arrowFromMove(solutionMove);
  const arrows = [...arrowFromMove(lastMove), ...arrowFromMove(hint?.bestMove), ...solutionArrows] as never;

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
    if (lastMove && lastMove.length >= 4) {
      styles[lastMove.slice(0, 2)] = { backgroundColor: 'rgba(246,160,77,.38)' };
      styles[lastMove.slice(2, 4)] = { backgroundColor: 'rgba(246,160,77,.48)' };
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
  }, [fen, legalSquares, lastMove, selected]);

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
          animationDuration={180}
          onPieceDrop={(from, to) => {
            void onMove(uci(from, to));
            return false;
          }}
          onSquareClick={async (square) => {
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
