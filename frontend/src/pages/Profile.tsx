import { Play, RefreshCw, Trophy, UserRound } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { ReportPanel } from '../components/ReportPanel';
import { api } from '../lib/api';
import { difficultyLabels, modeLabels } from '../lib/fallback';
import type { FavoriteDto, PlayerProfileReportDto, ProfileDto, TrainingMode } from '../types/api';

interface Props {
  onNotice: (message: string) => void;
  onPlay: () => void;
}

export function Profile({ onNotice, onPlay }: Props) {
  const [profile, setProfile] = useState<ProfileDto>();
  const [favorites, setFavorites] = useState<FavoriteDto[]>([]);
  const [profileReport, setProfileReport] = useState<PlayerProfileReportDto>();
  const [loading, setLoading] = useState(true);
  const [reportLoading, setReportLoading] = useState(false);
  const [error, setError] = useState('');
  const [reportError, setReportError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([api.profile(), api.favorites()])
      .then(([profileResponse, favoriteResponse]) => {
        if (!active) return;
        setProfile(profileResponse);
        setFavorites(favoriteResponse);
        setError('');
      })
      .catch((failure) => {
        if (!active) return;
        const message = failure instanceof Error ? failure.message : 'Profile unavailable';
        setError(message);
        onNotice(message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [onNotice]);

  const rows = useMemo(() => {
    if (!profile) return [];
    const bestByMode = new Map<TrainingMode, number | undefined>(profile.bestTimes.map((item) => [item.mode, item.seconds]));
    return profile.breakdown.map((item) => ({ ...item, bestTime: bestByMode.get(item.mode) }));
  }, [profile]);

  async function generateReport(refresh = false) {
    setReportLoading(true);
    setReportError('');
    try {
      const response = await api.profileReport(refresh);
      setProfileReport(response);
      onNotice('Player profile report ready.');
    } catch (failure) {
      const message = failure instanceof Error && (failure.message.includes('AI') || failure.message.includes('Gemini'))
        ? 'AI reports are not available on this server right now.'
        : 'Could not generate the profile report.';
      setReportError(message);
      onNotice(message);
    } finally {
      setReportLoading(false);
    }
  }

  if (loading) {
    return (
      <section className="grid gap-4">
        <div className="h-24 animate-pulse rounded-forge bg-black/10 dark:bg-white/10" />
        <div className="h-56 animate-pulse rounded-forge bg-black/10 dark:bg-white/10" />
      </section>
    );
  }

  if (error || !profile) {
    return <div className="rounded-forge border border-ember/30 bg-ember/10 p-4 text-sm">{error || 'Profile unavailable.'}</div>;
  }

  return (
    <section className="grid gap-4">
      <div className="mf-panel rounded-forge p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-tool bg-copper/10 text-copper dark:bg-ember/15 dark:text-ember">
              <UserRound size={22} />
            </div>
            <div>
              <h1 className="font-display text-2xl font-bold">{profile.username}</h1>
              <p className="text-sm text-black/55 dark:text-white/55">Joined {formatDate(profile.joinDate)}</p>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-2 text-center text-sm">
            <Metric label="Streak" value={`${profile.currentStreak}`} />
            <Metric label="Rank" value={profile.leaderboardRank ? `#${profile.leaderboardRank}` : '-'} />
            <Metric label="Sessions" value={`${profile.totalSessions}`} />
          </div>
          <button
            type="button"
            onClick={onPlay}
            className="inline-flex items-center gap-2 rounded-tool bg-copper px-4 py-2 font-bold text-white shadow-forge transition hover:bg-copper/90 dark:bg-ember dark:text-night dark:hover:bg-ember/90"
          >
            <Play size={18} />
            Play
          </button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_360px]">
        <div className="grid gap-4">
          <div className="mf-panel rounded-forge p-4">
            <div className="mb-3 flex items-center justify-between gap-3">
              <h2 className="font-bold">Accuracy Over Time</h2>
              <span className="text-xs text-black/45 dark:text-white/45">Last {profile.accuracyTrend.length} sessions</span>
            </div>
            <Sparkline points={profile.accuracyTrend.map((point) => point.accuracy)} />
          </div>

          <div className="mf-panel overflow-hidden rounded-forge">
            <table className="w-full text-left text-sm">
              <thead className="bg-copper/10 text-xs uppercase text-black/55 dark:bg-ember/10 dark:text-white/60">
                <tr>
                  <th className="p-3">Mode</th>
                  <th className="p-3">Difficulty</th>
                  <th className="p-3">Played</th>
                  <th className="p-3">Avg Accuracy</th>
                  <th className="p-3">Best Time</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-black/5 dark:divide-white/10">
                {rows.map((row) => (
                  <tr key={`${row.mode}-${row.difficulty}`}>
                    <td className="p-3 font-semibold">{modeLabels[row.mode]}</td>
                    <td className="p-3">{difficultyLabels[row.difficulty]}</td>
                    <td className="p-3">{row.sessionsPlayed}</td>
                    <td className="p-3">{row.averageAccuracy.toFixed(1)}%</td>
                    <td className="p-3">{row.bestTime ? formatSeconds(row.bestTime) : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <aside className="grid content-start gap-4">
          <div className="mf-panel rounded-forge p-4">
            <div className="mb-3 flex items-center justify-between gap-2">
              <h2 className="font-bold">Favorites</h2>
              <span className="text-xs text-black/45 dark:text-white/45">{profile.favoritePositionsCount}</span>
            </div>
            <div className="grid gap-2">
              {favorites.length === 0 ? (
                <div className="text-sm text-black/55 dark:text-white/55">No saved positions yet.</div>
              ) : favorites.map((favorite) => (
                <div key={favorite.id} className="rounded-tool border border-black/10 bg-white/50 p-2 dark:border-white/10 dark:bg-white/5">
                  <div className="truncate text-sm font-semibold">{favorite.name}</div>
                  <div className="truncate text-xs text-black/50 dark:text-white/50">{favorite.fen}</div>
                </div>
              ))}
            </div>
          </div>

          <div className="mf-panel rounded-forge p-4">
            <div className="mb-3 flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 font-bold"><Trophy size={17} /> AI Profile</div>
              <button type="button" onClick={() => generateReport(Boolean(profileReport))} className="inline-flex items-center gap-2 rounded-tool border border-black/10 bg-white/60 px-3 py-2 text-sm font-bold transition hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember">
                <RefreshCw size={15} />
                {profileReport ? 'Refresh' : 'Generate'}
              </button>
            </div>
            <ReportPanel profileReport={profileReport} baseMoves={[]} loading={reportLoading ? 'profile' : undefined} error={reportError} />
          </div>
        </aside>
      </div>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-tool border border-black/10 bg-white/50 px-3 py-2 dark:border-white/10 dark:bg-white/5">
      <div className="font-display text-base font-bold">{value}</div>
      <div className="text-xs text-black/50 dark:text-white/50">{label}</div>
    </div>
  );
}

function Sparkline({ points }: { points: number[] }) {
  if (points.length === 0) {
    return <div className="flex h-44 items-center justify-center text-sm text-black/50 dark:text-white/50">No completed sessions yet.</div>;
  }
  const width = 640;
  const height = 180;
  const path = points.map((value, index) => {
    const x = points.length === 1 ? width / 2 : (index / (points.length - 1)) * width;
    const y = height - (Math.max(0, Math.min(100, value)) / 100) * height;
    return `${index === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
  }).join(' ');
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="h-44 w-full overflow-visible">
      <path d="M 0 45 H 640 M 0 90 H 640 M 0 135 H 640" stroke="currentColor" className="text-black/10 dark:text-white/10" />
      <path d={path} fill="none" stroke="#c9652f" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
      {points.map((value, index) => {
        const x = points.length === 1 ? width / 2 : (index / (points.length - 1)) * width;
        const y = height - (Math.max(0, Math.min(100, value)) / 100) * height;
        return <circle key={`${index}-${value}`} cx={x} cy={y} r="5" fill="#f08a3c" />;
      })}
    </svg>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value));
}

function formatSeconds(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${rest.toString().padStart(2, '0')}`;
}
