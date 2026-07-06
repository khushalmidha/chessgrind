import { clsx } from 'clsx';
import type { MoveDto } from '../types/api';

export function MoveList({ moves }: { moves: MoveDto[] }) {
  return (
    <div className="h-48 overflow-auto rounded-forge border border-black/10 bg-[#fffaf2]/70 dark:border-white/10 dark:bg-white/5">
      {moves.length === 0 ? (
        <div className="p-4 text-sm text-black/55 dark:text-white/55">No moves yet.</div>
      ) : (
        <ol className="relative space-y-1 p-2 before:absolute before:left-7 before:top-4 before:h-[calc(100%-2rem)] before:w-px before:bg-copper/20 dark:before:bg-ember/25">
          {moves.map((move) => (
            <li key={`${move.ply}-${move.uci}`} className="relative grid grid-cols-[2.75rem_1fr] gap-2 rounded-tool p-2 text-sm transition hover:bg-copper/5 dark:hover:bg-ember/10">
              <span className="z-10 flex h-8 w-8 items-center justify-center rounded-full border border-copper/30 bg-[#fffaf2] font-display text-xs font-bold text-copper dark:border-ember/40 dark:bg-[#211a2e] dark:text-ember">{move.ply}</span>
              <div className="min-w-0">
                <div className="flex items-center justify-between gap-2">
                  <span className={clsx('font-display font-bold', move.engineMove ? 'text-violet dark:text-forge' : move.optimal ? 'text-copper dark:text-ember' : 'text-ember')}>{move.san}</span>
                  <span className="rounded-full bg-black/5 px-2 py-0.5 text-xs font-semibold text-black/50 dark:bg-white/10 dark:text-white/55">{move.engineMove ? 'Defense' : move.optimal ? 'Best' : 'Inexact'}</span>
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
