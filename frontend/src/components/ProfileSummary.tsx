import { BarChart3, Clock3, Medal, Trophy } from 'lucide-react';
import type { ReactNode } from 'react';
import type { AuthResponse, ProgressSummary } from '../types/api';

function time(seconds?: number) {
  if (!seconds) return '--';
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}

export function ProfileSummary({ user, progress }: { user?: AuthResponse; progress?: ProgressSummary }) {
  const rank = progress?.rank ? `#${progress.rank}` : 'Unranked';
  const total = progress?.totalRankedUsers ? `of ${progress.totalRankedUsers}` : 'play a mate';
  return (
    <section className="rounded-md border border-black/10 bg-white/80 p-4 dark:border-white/10 dark:bg-white/10">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-semibold text-black/55 dark:text-white/55">Trainer profile</div>
          <h2 className="mt-1 text-xl font-bold">{user?.username ?? 'Guest player'}</h2>
        </div>
        <Trophy className="text-ember" />
      </div>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <Metric icon={<Medal size={16} />} label="Rank" value={rank} helper={total} />
        <Metric icon={<Clock3 size={16} />} label="Best mate" value={time(progress?.bestCheckmateSeconds)} helper="fastest finish" />
        <Metric icon={<BarChart3 size={16} />} label="Accuracy" value={`${progress?.averageAccuracy ?? 0}%`} helper="avg best moves" />
        <Metric icon={<Trophy size={16} />} label="Solved" value={String(progress?.completed ?? 0)} helper="checkmates" />
      </div>
    </section>
  );
}

function Metric({ icon, label, value, helper }: { icon: ReactNode; label: string; value: string; helper: string }) {
  return (
    <div className="rounded-md border border-black/10 bg-black/[.03] p-3 dark:border-white/10 dark:bg-black/20">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase text-black/50 dark:text-white/50">
        {icon}
        {label}
      </div>
      <div className="mt-1 text-lg font-bold">{value}</div>
      <div className="text-xs text-black/45 dark:text-white/45">{helper}</div>
    </div>
  );
}
