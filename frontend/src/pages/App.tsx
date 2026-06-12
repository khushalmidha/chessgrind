import { Chess } from 'chess.js';
import { AnimatePresence, motion } from 'framer-motion';
import { Moon, Sun } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { AuthPanel } from '../components/AuthPanel';
import { ControlPanel } from '../components/ControlPanel';
import { IconButton } from '../components/IconButton';
import { StatStrip } from '../components/StatStrip';
import { TrainingBoard } from '../components/TrainingBoard';
import { api, currentUser, token } from '../lib/api';
import { boardStateLabel } from '../lib/chess';
import { fallbackPuzzles } from '../lib/fallback';
import type { AuthResponse, Difficulty, HintResponse, MoveDto, PuzzleDto, SessionDto, TimerMode, TrainingMode } from '../types/api';

export function App() {
  const [dark, setDark] = useState(true);
  const [puzzles, setPuzzles] = useState<PuzzleDto[]>(fallbackPuzzles);
  const [mode, setMode] = useState<TrainingMode>('KING_ROOK_VS_KING');
  const [difficulty, setDifficulty] = useState<Difficulty>('BEGINNER');
  const [timerMode, setTimerMode] = useState<TimerMode>('FIXED_COUNTDOWN');
  const [timeLimit, setTimeLimit] = useState(300);
  const [customFen, setCustomFen] = useState('');
  const [session, setSession] = useState<SessionDto>();
  const [user, setUser] = useState<AuthResponse | undefined>(() => currentUser());
  const [displaySeconds, setDisplaySeconds] = useState(timeLimit);
  const [localFen, setLocalFen] = useState(fallbackPuzzles[0].fen);
  const [hint, setHint] = useState<HintResponse>();
  const [solution, setSolution] = useState<MoveDto[]>([]);
  const [solutionIndex, setSolutionIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('Ready for a clean mate.');

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
  }, [dark]);

  useEffect(() => {
    api.puzzles().then(setPuzzles).catch(() => setPuzzles(fallbackPuzzles));
  }, []);

  useEffect(() => {
    if (!session || session.status !== 'ACTIVE' || timerMode === 'NONE') return;
    setDisplaySeconds(session.remainingSeconds);
    const id = window.setInterval(() => {
      setDisplaySeconds((seconds) => {
        if (seconds <= 1) {
          window.clearInterval(id);
          setNotice('Time expired. Analyze the optimal mating line.');
          setSession((current) => current ? { ...current, status: 'TIMEOUT', remainingSeconds: 0 } : current);
          return 0;
        }
        return seconds - 1;
      });
    }, 1000);
    return () => window.clearInterval(id);
  }, [session?.id, session?.status, timerMode]);

  useEffect(() => {
    if (!session) {
      setDisplaySeconds(timerMode === 'NONE' ? 0 : timeLimit);
    }
  }, [session, timeLimit, timerMode]);

  const selectedPuzzle = useMemo(() => {
    if (mode === 'RANDOM') {
      const filtered = puzzles.filter((puzzle) => puzzle.difficulty === difficulty);
      return filtered[Math.floor(Math.random() * Math.max(1, filtered.length))] ?? fallbackPuzzles[0];
    }
    return puzzles.find((puzzle) => puzzle.mode === mode && puzzle.difficulty === difficulty)
      ?? puzzles.find((puzzle) => puzzle.mode === mode)
      ?? fallbackPuzzles[0];
  }, [difficulty, mode, puzzles]);

  const fen = session?.currentFen ?? (mode === 'CUSTOM' && customFen ? customFen : localFen);
  const state = boardStateLabel(session);
  const solutionMove = solution[solutionIndex];

  async function start() {
    if (!token() || !user) {
      setNotice('Sign in first, then start a training session.');
      return;
    }
    setBusy(true);
    setHint(undefined);
    setSolution([]);
    setSolutionIndex(0);
    try {
      const currentPuzzle = mode === 'CUSTOM' ? undefined : selectedPuzzle;
      const started = await api.startSession({
        mode,
        difficulty,
        timerMode,
        timeLimitSeconds: timerMode === 'NONE' ? 0 : timeLimit,
        incrementSeconds: timerMode === 'INCREMENTAL' ? 5 : 0,
        hintsEnabled: true,
        takebacksEnabled: true,
        puzzleId: currentPuzzle?.id.startsWith('local-') ? undefined : currentPuzzle?.id,
        customFen: mode === 'CUSTOM' ? customFen : currentPuzzle?.id.startsWith('local-') ? currentPuzzle.fen : undefined,
      });
      setSession(started);
      setDisplaySeconds(started.remainingSeconds);
      setLocalFen(started.currentFen);
      setNotice('Session started. Make the attacking move; the king will defend.');
    } catch (error) {
      setSession(undefined);
      const startFen = mode === 'CUSTOM' && customFen ? customFen : selectedPuzzle.fen;
      setLocalFen(startFen);
      setNotice(error instanceof Error ? `${error.message}. Running local board mode.` : 'Running local board mode.');
    } finally {
      setBusy(false);
    }
  }

  async function move(uciMove: string) {
    setHint(undefined);
    if (session) {
      try {
        const response = await api.move(session.id, uciMove);
        setSession({ ...response.session, remainingSeconds: displaySeconds });
        setNotice(response.message);
        return true;
      } catch (error) {
        setNotice(error instanceof Error ? error.message : 'Illegal move');
        return false;
      }
    }
    try {
      const chess = new Chess(localFen);
      const result = chess.move({ from: uciMove.slice(0, 2), to: uciMove.slice(2, 4), promotion: uciMove[4] ?? 'q' });
      if (!result) return false;
      setLocalFen(chess.fen());
      setNotice(chess.isCheckmate() ? 'Checkmate.' : chess.inCheck() ? 'Check.' : 'Move accepted locally.');
      return true;
    } catch {
      setNotice('Illegal move in this position.');
      return false;
    }
  }

  async function requestHint() {
    if (!session) {
      setNotice('Start a connected session to use engine hints.');
      return;
    }
    try {
      const response = await api.hint(session.id);
      setHint(response);
      setNotice('Hint loaded.');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Hint unavailable');
    }
  }

  async function undo() {
    if (!session) return;
    try {
      const updated = await api.undo(session.id);
      setSession(updated);
      setNotice('Takeback applied.');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Undo unavailable');
    }
  }

  async function analyze() {
    if (!session) {
      setNotice('Start a connected session before analysis.');
      return;
    }
    try {
      const response = await api.solution(session.id);
      setSolution(response.line);
      setSolutionIndex(0);
      setNotice(response.summary);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Solution unavailable');
    }
  }

  function reset() {
    setSession(undefined);
    setDisplaySeconds(timerMode === 'NONE' ? 0 : timeLimit);
    setHint(undefined);
    setSolution([]);
    setSolutionIndex(0);
    setLocalFen(mode === 'CUSTOM' && customFen ? customFen : selectedPuzzle.fen);
    setNotice('Board reset.');
  }

  return (
    <main className="min-h-screen bg-[#f4f6f0] text-ink transition dark:bg-[#10130f] dark:text-white">
      <div className="mx-auto grid min-h-screen max-w-7xl gap-5 px-4 py-4 lg:grid-cols-[minmax(320px,1fr)_380px]">
        <section className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-black/10 bg-white/80 p-3 dark:border-white/10 dark:bg-white/10">
            <div>
              <div className="text-sm font-semibold uppercase tracking-wide text-moss">{state}</div>
              <div className="text-sm text-black/55 dark:text-white/55">{notice}</div>
            </div>
            <div className="flex items-center gap-2">
              {solution.length > 0 && (
                <div className="flex items-center gap-2">
                  <button type="button" className="rounded-md border border-black/10 px-3 py-2 text-sm dark:border-white/10" onClick={() => setSolutionIndex(Math.max(0, solutionIndex - 1))}>Prev</button>
                  <button type="button" className="rounded-md border border-black/10 px-3 py-2 text-sm dark:border-white/10" onClick={() => setSolutionIndex(Math.min(solution.length - 1, solutionIndex + 1))}>Next</button>
                </div>
              )}
              <IconButton icon={dark ? <Sun size={18} /> : <Moon size={18} />} label="Toggle theme" onClick={() => setDark((value) => !value)} />
            </div>
          </div>

          <StatStrip session={session ? { ...session, remainingSeconds: displaySeconds } : undefined} />
          <TrainingBoard fen={solutionMove?.fenAfter ?? fen} session={session} hint={hint} solutionMove={solutionMove} onMove={move} />
        </section>

        <AnimatePresence mode="wait">
          <motion.div initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 12 }}>
            <ControlPanel
              puzzles={puzzles}
              mode={mode}
              difficulty={difficulty}
              timerMode={timerMode}
              timeLimit={timeLimit}
              session={session ? { ...session, remainingSeconds: displaySeconds } : undefined}
              hint={hint}
              busy={busy}
              customFen={customFen}
              onMode={setMode}
              onDifficulty={setDifficulty}
              onTimerMode={setTimerMode}
              onTimeLimit={setTimeLimit}
              onCustomFen={setCustomFen}
              onStart={start}
              onReset={reset}
              onHint={requestHint}
              onUndo={undo}
              onAnalyze={analyze}
            />
            <div className="mt-4">
              <AuthPanel user={user} onUser={setUser} onNotice={setNotice} />
            </div>
          </motion.div>
        </AnimatePresence>
      </div>
    </main>
  );
}
