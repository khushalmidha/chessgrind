import { BookOpen, Brain, Lightbulb, Play, RotateCcw, Save, StepBack, Target } from 'lucide-react';
import type { ReactNode } from 'react';
import { MoveList } from './MoveList';
import { difficultyLabels, modeLabels } from '../lib/fallback';
import type { Difficulty, HintResponse, PuzzleDto, SessionDto, TimerMode, TrainingMode } from '../types/api';

interface Props {
  puzzles: PuzzleDto[];
  mode: TrainingMode;
  difficulty: Difficulty;
  timerMode: TimerMode;
  timeLimit: number;
  session?: SessionDto;
  hint?: HintResponse;
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
  return (
    <aside className="flex h-full flex-col gap-4 rounded-md border border-black/10 bg-white/82 p-4 backdrop-blur dark:border-white/10 dark:bg-[#171b15]/88">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">MateForge</h1>
          <p className="text-sm text-black/55 dark:text-white/55">Checkmate drills against best defense.</p>
        </div>
        <Target className="text-moss" />
      </div>

      <div className="grid gap-2">
        <div className="text-sm font-semibold">Mode</div>
        <div className="grid grid-cols-2 gap-2">
          {modes.map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => props.onMode(mode)}
              className={`min-h-11 rounded-md border px-3 py-2 text-left text-sm font-semibold transition ${props.mode === mode ? 'border-moss bg-moss text-white' : 'border-black/10 bg-white hover:border-moss dark:border-white/10 dark:bg-white/10'}`}
            >
              {modeLabels[mode]}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2" role="tablist" aria-label="Difficulty">
        {(['BEGINNER', 'INTERMEDIATE', 'ADVANCED'] as Difficulty[]).map((difficulty) => (
          <button
            key={difficulty}
            type="button"
            onClick={() => props.onDifficulty(difficulty)}
            className={`rounded-md border px-2 py-2 text-sm font-semibold ${props.difficulty === difficulty ? 'border-moss bg-moss text-white' : 'border-black/10 bg-white dark:border-white/10 dark:bg-white/10'}`}
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
          className="min-h-20 rounded-md border border-black/10 bg-white p-2 text-sm dark:border-white/10 dark:bg-white/10"
        />
      )}

      <div className="grid grid-cols-[1fr_auto] gap-3">
        <select value={props.timerMode} onChange={(event) => props.onTimerMode(event.target.value as TimerMode)} className="rounded-md border border-black/10 bg-white p-2 dark:border-white/10 dark:bg-white/10">
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
          className="w-24 rounded-md border border-black/10 bg-white p-2 dark:border-white/10 dark:bg-white/10"
          aria-label="Time limit seconds"
        />
      </div>

      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={props.busy} onClick={props.onStart} className="inline-flex items-center gap-2 rounded-md bg-moss px-4 py-2 font-semibold text-white disabled:opacity-60">
          <Play size={18} />
          Start
        </button>
        <Action icon={<RotateCcw size={17} />} label="Reset" onClick={props.onReset} />
        <Action icon={<Lightbulb size={17} />} label="Hint" onClick={props.onHint} />
        <Action icon={<StepBack size={17} />} label="Undo" onClick={props.onUndo} />
        <Action icon={<Brain size={17} />} label="Analyze" onClick={props.onAnalyze} />
        <Action icon={<Save size={17} />} label="Save" />
      </div>

      {props.hint && (
        <div className="rounded-md border border-ember/40 bg-ember/10 p-3 text-sm">
          <div className="font-semibold">Hint: {props.hint.bestMove}</div>
          <p className="mt-1 text-black/65 dark:text-white/70">{props.hint.reason}</p>
        </div>
      )}

      <div className="flex items-center gap-2 text-sm font-semibold">
        <BookOpen size={17} />
        Move history
      </div>
      <MoveList moves={props.session?.moves ?? []} />

      <div className="text-xs leading-5 text-black/50 dark:text-white/50">
        {props.puzzles.length} prepared positions loaded. Engine defense and optimal-line replay use the Java UCI service when connected.
      </div>
    </aside>
  );
}

function Action({ icon, label, onClick }: { icon: ReactNode; label: string; onClick?: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex items-center gap-2 rounded-md border border-black/10 bg-white/85 px-3 py-2 text-sm font-semibold transition hover:border-moss dark:border-white/10 dark:bg-white/10"
    >
      {icon}
      {label}
    </button>
  );
}
