import { Activity, Clock3, Flame, Gauge } from 'lucide-react';
import type { SessionDto } from '../types/api';

function format(seconds: number) {
  if (!seconds) return 'No limit';
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}

export function StatStrip({ session }: { session?: SessionDto }) {
  const accuracy = session?.accuracy ?? 100;
  const stats = [
    { icon: Clock3, label: 'Timer', value: session ? format(session.remainingSeconds) : '5:00' },
    { icon: Gauge, label: 'Accuracy', value: `${accuracy}%`, ring: true },
    { icon: Activity, label: 'Mistakes', value: String(session?.mistakes ?? 0) },
    { icon: Flame, label: 'Streak', value: 'Daily' },
  ];

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {stats.map((stat) => (
        <div key={stat.label} className="mf-panel rounded-forge p-3">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 text-xs font-bold uppercase text-black/55 dark:text-white/60">
              <span className="flex h-7 w-7 items-center justify-center rounded-tool bg-copper/10 text-copper dark:bg-ember/15 dark:text-ember">
                <stat.icon size={15} />
              </span>
              {stat.label}
            </div>
            {stat.ring && (
              <div
                className="h-9 w-9 rounded-full"
                style={{ background: `conic-gradient(#c9652f ${Math.max(0, Math.min(100, accuracy))}%, rgba(0,0,0,.10) 0)` }}
              >
                <div className="m-1 h-7 w-7 rounded-full bg-[#fffaf2] dark:bg-[#211a2e]" />
              </div>
            )}
          </div>
          <div className="mt-2 font-display text-xl font-bold">{stat.value}</div>
        </div>
      ))}
    </div>
  );
}
