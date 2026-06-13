import { Brain, Target } from 'lucide-react';
import type { MoveResponse } from '../types/api';

const colors: Record<string, string> = {
  BEST: 'bg-moss text-white',
  CHECKMATE: 'bg-ember text-ink',
  INACCURACY: 'bg-yellow-400 text-ink',
  MISTAKE: 'bg-red-500 text-white',
  BLUNDER: 'bg-red-700 text-white',
};

export function CoachNote({ review }: { review?: MoveResponse }) {
  if (!review?.coachNote) return null;
  const quality = review.moveQuality ?? 'BEST';
  return (
    <section className="rounded-md border border-black/10 bg-white/85 p-4 dark:border-white/10 dark:bg-white/10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Brain className="text-moss" size={20} />
          <div className="font-bold">Coach review</div>
        </div>
        <span className={`rounded-md px-3 py-1 text-sm font-black ${colors[quality] ?? colors.BEST}`}>{quality}</span>
      </div>
      <p className="mt-3 text-sm leading-6 text-black/70 dark:text-white/75">{review.coachNote}</p>
      {review.bestMove && (
        <div className="mt-3 inline-flex items-center gap-2 rounded-md border border-ember/40 bg-ember/10 px-3 py-2 text-sm font-bold">
          <Target size={16} />
          Best move: {review.bestMoveText ?? review.bestMove}
        </div>
      )}
    </section>
  );
}
