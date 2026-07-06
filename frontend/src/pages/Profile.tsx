import { Play, RefreshCw, Trophy, UserRound } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { ReportPanel } from '../components/ReportPanel';
import { api, currentUser, HttpError } from '../lib/api';
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
    setError('');
    setProfile(undefined);
    setFavorites([]);
    Promise.allSettled([api.profile(), api.favorites()])
      .then(([profileResult, favoritesResult]) => {
        if (!active) return;
        if (profileResult.status === 'fulfilled') {
          setProfile(profileResult.value);
          setError('');
        } else {
          console.error('Profile fetch failed:', profileResult.reason);
          const message = describeProfileFailure(profileResult.reason);
          const fallback = fallbackProfile();
          if (fallback) {
            setProfile(fallback);
            setError('');
          } else {
            setError(message);
          }
          onNotice(message);
          // FIXED: a profile API 500 should not hide the whole profile for a signed-in zero-game user.
        }
        if (favoritesResult.status === 'fulfilled') {
          setFavorites(favoritesResult.value);
        } else {
          console.error('Favorites fetch failed:', favoritesResult.reason);
        }
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
              {profile.totalSessions === 0 && (
                <div className="mt-2 rounded-tool border border-copper/20 bg-copper/10 px-3 py-2 text-sm text-copper dark:border-ember/20 dark:bg-ember/10 dark:text-ember">
                  Play your first session to unlock your profile.
                </div>
              )}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2 text-center text-sm sm:grid-cols-5">
            <Metric label="Tournament" value={`${profile.tournamentRating}`} />
            <Metric label="Regular" value={`${profile.regularChessRating}`} />
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

          <div className="mf-panel rounded-forge p-4">
            <h2 className="mb-3 font-bold">Best Checkmates</h2>
            {profile.bestCheckmateModes.length === 0 ? (
              <div className="rounded-tool border border-copper/20 bg-copper/10 p-3 text-sm text-copper dark:border-ember/20 dark:bg-ember/10 dark:text-ember">
                No completed checkmates yet. Start with king + rook to build your profile.
              </div>
            ) : (
              <div className="grid gap-2 sm:grid-cols-3">
                {profile.bestCheckmateModes.map((item) => (
                  <div key={item.mode} className="rounded-tool border border-black/10 bg-white/50 p-3 dark:border-white/10 dark:bg-white/5">
                    <div className="font-semibold">{modeLabels[item.mode]}</div>
                    <div className="mt-1 text-xs text-black/55 dark:text-white/55">
                      {item.completed} mates · {item.averageAccuracy.toFixed(1)}% avg · {item.bestSeconds ? formatSeconds(item.bestSeconds) : '-'}
                    </div>
                  </div>
                ))}
              </div>
            )}
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
            <div className="mb-3 flex items-center gap-2 font-bold"><Trophy size={17} /> Coach Suggestion</div>
            <textarea
              readOnly
              value={coachSuggestion(profile)}
              className="mf-input min-h-40 w-full resize-none rounded-tool p-3 text-sm leading-6"
              aria-label="Tournament and rating suggestion"
            />
          </div>

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

function fallbackProfile(): ProfileDto | undefined {
  const auth = currentUser();
  if (!auth) return undefined;
  return {
    username: auth.username,
    joinDate: new Date().toISOString(),
    totalSessions: 0,
    totalCompleted: 0,
    currentStreak: 0,
    tournamentRating: 1000,
    regularChessRating: 900,
    bestCheckmateModes: [],
    bestTimes: [],
    accuracyTrend: [],
    breakdown: [],
    favoritePositionsCount: 0,
    leaderboardRank: 0,
    totalRankedUsers: 0,
  };
}

function coachSuggestion(profile: ProfileDto) {
  const best = profile.bestCheckmateModes[0];
  const weak = profile.breakdown
    .slice()
    .sort((left, right) => left.averageAccuracy - right.averageAccuracy)[0];
  const targetTournament = profile.tournamentRating <= 1050
    ? 'Beginner arena around 900-1100 rating'
    : profile.tournamentRating <= 1350
      ? 'Improver arena around 1100-1350 rating'
      : 'Advanced arena within 150 rating points of your tournament rating';
  const lines = [
    `Recommended tournament: ${targetTournament}.`,
    `Your tournament rating is ${profile.tournamentRating}; choose events within +/-150 points so games are challenging but not punishing.`,
    best
      ? `Best checkmate pattern: ${modeLabels[best.mode]} (${best.averageAccuracy.toFixed(1)}% avg). Use this as your confidence round.`
      : 'Best checkmate pattern: not enough completed mates yet. Start with king + rook and queen mates.',
    weak
      ? `Rating improvement focus: drill ${modeLabels[weak.mode]} on ${difficultyLabels[weak.difficulty]} until you reach 85%+ accuracy.`
      : 'Rating improvement focus: complete 5 timed beginner sessions, then review every missed move.',
    'For regular rating: reduce hint use, keep king opposition tight, and review the final two moves after every session.',
  ];
  return lines.join('\n');
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

function describeProfileFailure(failure: unknown) {
  if (failure instanceof HttpError) {
    return `${failure.status}: ${failure.message}`;
  }
  if (failure instanceof Error) {
    return failure.message || 'Profile unavailable';
  }
  return 'Profile unavailable';
}
