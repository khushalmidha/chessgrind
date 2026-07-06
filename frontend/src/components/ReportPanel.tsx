import { MoveList } from './MoveList';
import type { GameReportDto, MoveDto, PlayerProfileReportDto } from '../types/api';

interface Props {
  gameReport?: GameReportDto;
  profileReport?: PlayerProfileReportDto;
  baseMoves: MoveDto[];
  loading?: 'session' | 'profile';
  error?: string;
}

export function ReportPanel({ gameReport, profileReport, baseMoves, loading, error }: Props) {
  if (loading) {
    return (
      <div className="mf-panel grid gap-3 rounded-forge p-3">
        <div className="h-4 w-1/2 animate-pulse rounded-tool bg-black/10 dark:bg-white/15" />
        <div className="h-16 animate-pulse rounded-tool bg-black/10 dark:bg-white/15" />
        <div className="grid grid-cols-2 gap-2">
          <div className="h-9 animate-pulse rounded-tool bg-black/10 dark:bg-white/15" />
          <div className="h-9 animate-pulse rounded-tool bg-black/10 dark:bg-white/15" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-forge border border-ember/30 bg-ember/10 p-3 text-sm text-black/70 dark:text-white/75">
        {error}
      </div>
    );
  }

  if (profileReport) {
    return (
      <div className="mf-panel grid gap-3 rounded-forge p-3 text-sm">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-display text-base font-bold">Player Profile</h2>
          <span className="rounded-tool bg-copper px-2 py-1 text-xs font-bold text-white dark:bg-ember dark:text-night">{profileReport.consistencyScore}/100</span>
        </div>
        <p className="leading-6 text-black/65 dark:text-white/70">{profileReport.styleSummary}</p>
        <div className="grid gap-2 sm:grid-cols-2">
          <ChipBlock title="Strong Modes" values={profileReport.strongModes} />
          <ChipBlock title="Weak Modes" values={profileReport.weakModes} tone="violet" />
        </div>
        <div>
          <div className="font-semibold">{profileReport.playerLevel}</div>
          <p className="mt-1 leading-6 text-black/60 dark:text-white/65">{profileReport.trendNotes}</p>
        </div>
        <ListBlock title="Recommended Drills" values={profileReport.recommendedDrills} />
      </div>
    );
  }

  if (!gameReport) return null;

  const highlightedMoves = gameReport.moveHighlights
    .map((highlight) => {
      const move = baseMoves.find((item) => item.ply === highlight.ply);
      return move ? { ...move, reason: highlight.comment } : undefined;
    })
    .filter(Boolean) as MoveDto[];

  return (
    <div className="mf-panel grid gap-3 rounded-forge p-3 text-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-display text-base font-bold">Session Report</h2>
        <span className="rounded-tool bg-copper px-2 py-1 text-xs font-bold text-white dark:bg-ember dark:text-night">{gameReport.overallRatingBand}</span>
      </div>
      <p className="leading-6 text-black/65 dark:text-white/70">{gameReport.summary}</p>
      <div className="grid gap-2 sm:grid-cols-2">
        <ChipBlock title="Strengths" values={gameReport.strengths} />
        <ChipBlock title="Weaknesses" values={gameReport.weaknesses} tone="violet" />
      </div>
      <ListBlock title="Recurring Mistakes" values={gameReport.recurringMistakes} />
      <ListBlock title="Next Focus" values={gameReport.nextFocusAreas} />
      {highlightedMoves.length > 0 && (
        <div className="grid gap-2">
          <div className="font-semibold">Move Highlights</div>
          <MoveList moves={highlightedMoves} />
        </div>
      )}
    </div>
  );
}

function ChipBlock({ title, values, tone = 'copper' }: { title: string; values: string[]; tone?: 'copper' | 'violet' }) {
  const color = tone === 'copper' ? 'bg-copper/10 text-copper dark:bg-ember/15 dark:text-ember' : 'bg-violet/10 text-violet dark:bg-forge/10 dark:text-forge';
  return (
    <div>
      <div className="mb-2 font-semibold">{title}</div>
      <div className="flex flex-wrap gap-2">
        {values.map((value) => (
          <span key={value} className={`rounded-tool px-2 py-1 text-xs font-bold ${color}`}>{value}</span>
        ))}
      </div>
    </div>
  );
}

function ListBlock({ title, values }: { title: string; values: string[] }) {
  if (values.length === 0) return null;
  return (
    <div>
      <div className="mb-1 font-semibold">{title}</div>
      <ul className="grid gap-1 text-black/60 dark:text-white/65">
        {values.map((value) => <li key={value}>{value}</li>)}
      </ul>
    </div>
  );
}
