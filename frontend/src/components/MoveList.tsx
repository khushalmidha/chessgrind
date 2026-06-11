import { clsx } from 'clsx';
import type { MoveDto } from '../types/api';

export function MoveList({ moves }: { moves: MoveDto[] }) {
  return (
    <div className="h-48 overflow-auto rounded-md border border-black/10 bg-white/80 dark:border-white/10 dark:bg-white/10">
      {moves.length === 0 ? (
        <div className="p-4 text-sm text-black/55 dark:text-white/55">No moves yet.</div>
      ) : (
        <ol className="divide-y divide-black/5 dark:divide-white/10">
          {moves.map((move) => (
            <li key={`${move.ply}-${move.uci}`} className="grid grid-cols-[3rem_1fr] gap-2 p-3 text-sm">
              <span className="text-black/45 dark:text-white/45">{move.ply}.</span>
              <div>
                <div className="flex items-center justify-between gap-2">
                  <span className={clsx('font-semibold', move.engineMove ? 'text-ember' : 'text-moss')}>{move.san}</span>
                  <span className="text-xs text-black/45 dark:text-white/45">{move.engineMove ? 'Defense' : move.optimal ? 'Best' : 'Inexact'}</span>
                </div>
                <p className="mt-1 text-xs leading-5 text-black/55 dark:text-white/55">{move.reason}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
