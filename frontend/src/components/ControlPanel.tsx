import { BookOpen, Brain, FileText, Lightbulb, Play, RotateCcw, Save, StepBack, Target, UserRound } from 'lucide-react';
import { IconButton } from './IconButton';
import { MoveList } from './MoveList';
import { ReportPanel } from './ReportPanel';
import { difficultyLabels, modeLabels } from '../lib/fallback';
import type { Difficulty, GameReportDto, HintResponse, PlayerProfileReportDto, PuzzleDto, ReviewMoveDto, SessionDto, TimerMode, TrainingMode } from '../types/api';

interface Props {
  puzzles: PuzzleDto[];
  mode: TrainingMode;
  difficulty: Difficulty;
  timerMode: TimerMode;
  timeLimit: number;
  session?: SessionDto;
  hint?: HintResponse;
  gameReport?: GameReportDto;
  profileReport?: PlayerProfileReportDto;
  reportLoading?: 'session' | 'profile';
  reportError?: string;
  reportsUnavailable?: boolean;
  reviews?: ReviewMoveDto[];
  reviewIndex?: number;
  busy: boolean;
  customFen: string;
  onMode: (mode: TrainingMode) => void;
  onDifficulty: (difficulty: Difficulty) => void;
  onTimerMode: (mode: TimerMode) => void;
  onTimeLimit: (seconds: number) => void;
  onCustomFen: (fen: string) => void;
  onStart: () => void;
  onReset: () => void;
  onHint: () => void;
  onUndo: () => void;
  onAnalyze: () => void;
  onReviewIndex?: (index: number) => void;
  onReport: (refresh?: boolean) => void;
  onProfileReport: (refresh?: boolean) => void;
}

const modes: TrainingMode[] = [
  'KING_ROOK_VS_KING',
  'TWO_ROOKS_VS_KING',
  'QUEEN_VS_KING',
  'TWO_BISHOPS_VS_KING',
  'BISHOP_KNIGHT_VS_KING',
  'TWO_PAWNS_VS_KING',
  'CUSTOM',
  'RANDOM',
];

export function ControlPanel(props: Props) {
  const sessionEnded = props.session && props.session.status !== 'ACTIVE';

  return (
    <aside className="mf-panel flex h-full flex-col gap-4 rounded-forge p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="mf-wordmark-mark flex h-10 w-10 items-center justify-center rounded-tool font-display text-lg font-bold text-white">M</div>
          <div>
            <h1 className="mf-wordmark text-2xl font-bold">Mateforge</h1>
            <p className="text-sm text-black/55 dark:text-white/55">Pattern drills against best defense.</p>
          </div>
        </div>
        <Target className="text-copper dark:text-ember" />
      </div>

      <div className="grid gap-3">
        <label className="font-display text-sm font-bold" htmlFor="mode">Mode</label>
        <select id="mode" value={props.mode} onChange={(event) => props.onMode(event.target.value as TrainingMode)} className="mf-input rounded-tool p-2">
          {modes.map((mode) => (
            <option key={mode} value={mode}>{modeLabels[mode]}</option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-3 gap-2" role="tablist" aria-label="Difficulty">
        {(['BEGINNER', 'INTERMEDIATE', 'ADVANCED'] as Difficulty[]).map((difficulty) => (
          <button
            key={difficulty}
            type="button"
            onClick={() => props.onDifficulty(difficulty)}
            className={`rounded-tool border px-2 py-2 text-sm font-bold transition ${props.difficulty === difficulty ? 'border-copper bg-copper text-white shadow-forge' : 'border-black/10 bg-white/70 hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember'}`}
          >
            {difficultyLabels[difficulty]}
          </button>
        ))}
      </div>

      {props.mode === 'CUSTOM' && (
        <textarea
          value={props.customFen}
          onChange={(event) => props.onCustomFen(event.target.value)}
          placeholder="Paste FEN"
          className="mf-input min-h-20 rounded-tool p-2 text-sm"
        />
      )}

      <div className="grid grid-cols-[1fr_auto] gap-3">
        <select value={props.timerMode} onChange={(event) => props.onTimerMode(event.target.value as TimerMode)} className="mf-input rounded-tool p-2">
          <option value="FIXED_COUNTDOWN">Countdown</option>
          <option value="INCREMENTAL">Increment</option>
          <option value="NONE">No timer</option>
        </select>
        <input
          type="number"
          min={0}
          step={30}
          value={props.timeLimit}
          onChange={(event) => props.onTimeLimit(Number(event.target.value))}
          className="mf-input w-24 rounded-tool p-2"
          aria-label="Time limit seconds"
        />
      </div>

      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={props.busy} onClick={props.onStart} className="inline-flex items-center gap-2 rounded-tool bg-copper px-4 py-2 font-bold text-white shadow-forge transition hover:bg-copper/90 disabled:opacity-60 dark:bg-ember dark:text-night dark:hover:bg-ember/90">
          <Play size={18} />
          Start
        </button>
        <IconButton icon={<RotateCcw size={18} />} label="Reset" onClick={props.onReset} />
        <IconButton icon={<Lightbulb size={18} />} label="Hint" onClick={props.onHint} />
        <IconButton icon={<StepBack size={18} />} label="Undo" onClick={props.onUndo} />
        <IconButton icon={<Brain size={18} />} label="Analyze" onClick={props.onAnalyze} />
        <IconButton icon={<Save size={18} />} label="Save position" />
      </div>

      {!props.reportsUnavailable && (
        <div className="flex flex-wrap gap-2">
          {sessionEnded && (
            <button type="button" onClick={() => props.onReport()} className="inline-flex items-center gap-2 rounded-tool border border-black/10 bg-white/60 px-3 py-2 text-sm font-bold transition hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember">
              <FileText size={16} />
              Report
            </button>
          )}
          <button type="button" onClick={() => props.onProfileReport()} className="inline-flex items-center gap-2 rounded-tool border border-black/10 bg-white/60 px-3 py-2 text-sm font-bold transition hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember">
            <UserRound size={16} />
            Profile
          </button>
          {(props.gameReport || props.profileReport) && (
            <button type="button" onClick={() => (props.profileReport ? props.onProfileReport(true) : props.onReport(true))} className="rounded-tool border border-black/10 bg-white/60 px-3 py-2 text-sm font-semibold transition hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember">
              Refresh
            </button>
          )}
        </div>
      )}

      {props.hint && (
        <div className="rounded-forge border border-ember/40 bg-ember/10 p-3 text-sm">
          <div className="font-semibold">Hint: {props.hint.bestMove}</div>
          <p className="mt-1 text-black/65 dark:text-white/70">{props.hint.reason}</p>
        </div>
      )}

      <div className="flex items-center gap-2 font-display text-sm font-bold">
        <BookOpen size={17} />
        Move history
      </div>
      <MoveList moves={props.session?.moves ?? []} />

      {props.reviews && props.reviews.length > 0 && (
        <div className="grid gap-2 rounded-forge border border-copper/25 bg-copper/5 p-3 dark:border-ember/25 dark:bg-ember/10">
          <div className="flex items-center justify-between gap-2">
            <div className="font-display text-sm font-bold">Move-by-move review</div>
            <div className="text-xs font-semibold text-black/55 dark:text-white/55">{(props.reviewIndex ?? 0) + 1}/{props.reviews.length}</div>
          </div>
          <div className="grid max-h-56 gap-2 overflow-auto pr-1">
            {props.reviews.map((review, index) => (
              <button
                key={`${review.ply}-${review.bestMove}-${index}`}
                type="button"
                onClick={() => props.onReviewIndex?.(index)}
                className={`rounded-tool border p-2 text-left text-sm transition ${props.reviewIndex === index ? 'border-copper bg-white/80 shadow-forge dark:border-ember dark:bg-white/10' : 'border-black/10 bg-white/50 hover:border-copper dark:border-white/10 dark:bg-white/5 dark:hover:border-ember'}`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-display font-bold">Ply {review.ply}</span>
                  <span className={`rounded-full px-2 py-0.5 text-xs font-bold ${review.optimal ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300' : 'bg-ember/15 text-copper dark:text-ember'}`}>
                    {review.optimal ? 'Good' : 'Learn'}
                  </span>
                </div>
                <div className="mt-1 grid grid-cols-2 gap-2 text-xs">
                  <span>Played: <strong>{review.playedSan || review.playedMove || '-'}</strong></span>
                  <span>Best: <strong>{review.bestSan || review.bestMove}</strong></span>
                </div>
                <p className="mt-1 text-xs leading-5 text-black/60 dark:text-white/60">{review.comment}</p>
              </button>
            ))}
          </div>
        </div>
      )}

      <ReportPanel
        gameReport={props.gameReport}
        profileReport={props.profileReport}
        baseMoves={props.session?.moves ?? []}
        loading={props.reportLoading}
        error={props.reportError}
      />

      <div className="text-xs leading-5 text-black/50 dark:text-white/50">
        {props.puzzles.length} prepared positions loaded. Engine defense and optimal-line replay use the Java UCI service when connected.
      </div>
    </aside>
  );
}
