import { Activity, Clock3, Flame, Gauge } from 'lucide-react';
import type { SessionDto } from '../types/api';

function format(seconds: number) {
  if (!seconds) return 'No limit';
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}

export function StatStrip({ session }: { session?: SessionDto }) {
  const stats = [
    { icon: Clock3, label: 'Timer', value: session ? format(session.remainingSeconds) : '5:00' },
    { icon: Gauge, label: 'Accuracy', value: session ? `${session.accuracy}%` : '100%' },
    { icon: Activity, label: 'Mistakes', value: String(session?.mistakes ?? 0) },
    { icon: Flame, label: 'Streak', value: 'Daily' },
  ];

  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
      {stats.map((stat) => (
        <div key={stat.label} className="rounded-md border border-black/10 bg-white/75 p-3 dark:border-white/10 dark:bg-white/10">
          <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-black/55 dark:text-white/55">
            <stat.icon size={15} />
            {stat.label}
          </div>
          <div className="mt-1 text-lg font-semibold">{stat.value}</div>
        </div>
      ))}
    </div>
  );
}
