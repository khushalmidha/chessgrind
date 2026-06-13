import { ArrowRight, CheckCircle2, CircleAlert, Crown } from 'lucide-react';
import type { MoveDto, SessionDto } from '../types/api';

interface Props {
  solution: MoveDto[];
  session?: SessionDto;
  index: number;
  onIndex: (index: number) => void;
}

export function AnalysisReview({ solution, session, index, onIndex }: Props) {
  if (solution.length === 0) return null;
  const current = solution[index];
  const played = session?.moves.find((move) => !move.engineMove && move.ply === current.ply);
  const same = played?.uci === current.uci;

  return (
    <section className="rounded-md border border-black/10 bg-white/85 p-4 dark:border-white/10 dark:bg-white/10">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 text-xs font-black uppercase tracking-wide text-moss">
            <Crown size={16} />
            Analysis line
          </div>
          <h2 className="mt-1 text-2xl font-black">Best move now: {current.san || current.uci}</h2>
          <p className="mt-1 text-sm text-black/60 dark:text-white/65">
            Green arrow shows the best move. Yellow highlights what was played around this moment.
          </p>
        </div>
        <div className={`rounded-md px-3 py-2 text-sm font-black ${same ? 'bg-moss text-white' : played ? 'bg-yellow-400 text-ink' : 'bg-black/10 dark:bg-white/10'}`}>
          {same ? 'Your move matched' : played ? 'Different from best' : 'No matching user move'}
        </div>
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <MoveCard label="Best move" move={current.uci} color="green" note={current.reason} />
        <MoveCard label="Your move at this ply" move={played?.uci ?? '--'} color={same ? 'green' : played ? 'yellow' : 'neutral'} note={played?.reason ?? 'You did not play a move at this exact step.'} />
      </div>

      <div className="mt-4 max-h-52 overflow-auto rounded-md border border-black/10 dark:border-white/10">
        {solution.map((move, moveIndex) => {
          const userMove = session?.moves.find((candidate) => !candidate.engineMove && candidate.ply === move.ply);
          const isSame = userMove?.uci === move.uci;
          return (
            <button
              key={`${move.ply}-${move.uci}`}
              type="button"
              onClick={() => onIndex(moveIndex)}
              className={`grid w-full grid-cols-[3rem_1fr_auto] items-center gap-3 border-b border-black/5 px-3 py-2 text-left text-sm last:border-b-0 dark:border-white/10 ${moveIndex === index ? 'bg-moss/15' : ''}`}
            >
              <span className="text-black/45 dark:text-white/45">{moveIndex + 1}</span>
              <span className="font-semibold">
                {move.engineMove ? 'Defense' : 'Attack'}: {move.uci}
              </span>
              <span className={`rounded px-2 py-1 text-xs font-bold ${isSame ? 'bg-moss text-white' : userMove ? 'bg-yellow-400 text-ink' : 'bg-black/10 dark:bg-white/10'}`}>
                {isSame ? 'GOOD' : userMove ? 'CHECK' : 'BEST'}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

function MoveCard({ label, move, color, note }: { label: string; move: string; color: 'green' | 'yellow' | 'neutral'; note: string }) {
  const tone = color === 'green' ? 'border-moss/50 bg-moss/10' : color === 'yellow' ? 'border-yellow-400/60 bg-yellow-400/10' : 'border-black/10 bg-black/[.03] dark:border-white/10 dark:bg-white/5';
  const Icon = color === 'green' ? CheckCircle2 : color === 'yellow' ? CircleAlert : ArrowRight;
  return (
    <div className={`rounded-md border p-3 ${tone}`}>
      <div className="flex items-center gap-2 text-xs font-black uppercase tracking-wide text-black/55 dark:text-white/55">
        <Icon size={16} />
        {label}
      </div>
      <div className="mt-2 text-2xl font-black">{move}</div>
      <p className="mt-2 text-sm leading-6 text-black/65 dark:text-white/70">{note}</p>
    </div>
  );
}
