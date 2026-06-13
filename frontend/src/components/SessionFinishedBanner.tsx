import { RotateCcw, Search, Trophy } from 'lucide-react';
import type { SessionDto } from '../types/api';

interface Props {
  session?: SessionDto;
  onPracticeAgain: () => void;
  onAnalyze: () => void;
}

export function SessionFinishedBanner({ session, onPracticeAgain, onAnalyze }: Props) {
  if (!session || session.status === 'ACTIVE') return null;
  const title = session.status === 'CHECKMATE' ? 'Session finished: checkmate!' : `Session finished: ${session.status.toLowerCase()}`;
  return (
    <section className="rounded-md border border-ember/45 bg-ember/12 p-4 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="rounded-md bg-ember/20 p-2">
            <Trophy className="text-ember" size={22} />
          </div>
          <div>
            <div className="text-lg font-black">{title}</div>
            <div className="text-sm text-black/60 dark:text-white/65">
              Accuracy {session.accuracy}% · Mistakes {session.mistakes} · You can restart this exact position.
            </div>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={onPracticeAgain} className="inline-flex items-center gap-2 rounded-md bg-moss px-4 py-2 text-sm font-bold text-white">
            <RotateCcw size={17} />
            Practice again
          </button>
          <button type="button" onClick={onAnalyze} className="inline-flex items-center gap-2 rounded-md border border-black/10 bg-white px-4 py-2 text-sm font-bold dark:border-white/10 dark:bg-white/10">
            <Search size={17} />
            Analyze
          </button>
        </div>
      </div>
    </section>
  );
}
