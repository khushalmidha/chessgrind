import { Chess } from 'chess.js';
import { AnimatePresence, motion } from 'framer-motion';
import { ChevronLeft, Moon, Play, Sun } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { AuthPanel } from '../components/AuthPanel';
import { CoachNote } from '../components/CoachNote';
import { ControlPanel } from '../components/ControlPanel';
import { IconButton } from '../components/IconButton';
import { ProfileSummary } from '../components/ProfileSummary';
import { StatStrip } from '../components/StatStrip';
import { TrainingBoard } from '../components/TrainingBoard';
import { TournamentPanel } from '../components/TournamentPanel';
import { api, currentUser, token } from '../lib/api';
import { boardStateLabel } from '../lib/chess';
import { fallbackPuzzles } from '../lib/fallback';
import type { AuthResponse, Difficulty, HintResponse, MoveDto, MoveResponse, ProgressSummary, PuzzleDto, SessionDto, TimerMode, TrainingMode } from '../types/api';

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
  const [progress, setProgress] = useState<ProgressSummary>();
  const [view, setView] = useState<'home' | 'practice'>('home');
  const [displaySeconds, setDisplaySeconds] = useState(timeLimit);
  const [localFen, setLocalFen] = useState(fallbackPuzzles[0].fen);
  const [hint, setHint] = useState<HintResponse>();
  const [solution, setSolution] = useState<MoveDto[]>([]);
  const [coachReview, setCoachReview] = useState<MoveResponse>();
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
    const match = window.location.pathname.match(/^\/tournament\/([A-Z0-9]+)/i);
    if (match) {
      setView('home');
      setNotice(`Tournament invite detected: ${match[1].toUpperCase()}. Sign in and join from the tournament panel.`);
    }
  }, []);

  useEffect(() => {
    if (!user || !token()) {
      setProgress(undefined);
      return;
    }
    api.progress().then(setProgress).catch(() => setProgress(undefined));
  }, [user]);

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
  const solutionStep = solution.length > 0 ? `${solutionIndex + 1} / ${solution.length}` : '';

  async function start() {
    if (!token() || !user) {
      setNotice('Sign in first, then start a training session.');
      return;
    }
    setBusy(true);
    setHint(undefined);
    setSolution([]);
    setCoachReview(undefined);
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
        setCoachReview(response);
        setNotice(response.message);
        if (response.session.status !== 'ACTIVE') {
          api.progress().then(setProgress).catch(() => undefined);
        }
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
    setCoachReview(undefined);
    setSolutionIndex(0);
    setLocalFen(mode === 'CUSTOM' && customFen ? customFen : selectedPuzzle.fen);
    setNotice('Board reset.');
  }

  const topBar = (
    <header className="sticky top-0 z-20 border-b border-black/10 bg-[#f4f6f0]/95 px-4 py-3 backdrop-blur dark:border-white/10 dark:bg-[#10130f]/95">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-3">
        <button type="button" onClick={() => setView('home')} className="text-left">
          <div className="text-xl font-black">ChessGrind</div>
          <div className="text-xs text-black/50 dark:text-white/50">MateForge training</div>
        </button>
        <div className="flex items-center gap-2">
          <IconButton icon={dark ? <Sun size={18} /> : <Moon size={18} />} label="Toggle theme" onClick={() => setDark((value) => !value)} />
        </div>
      </div>
    </header>
  );

  if (view === 'home') {
    return (
      <main className="min-h-screen bg-[#f4f6f0] text-ink transition dark:bg-[#10130f] dark:text-white">
        {topBar}
        <div className="mx-auto grid max-w-7xl gap-5 px-4 py-6 lg:grid-cols-[1fr_380px]">
          <section className="grid content-start gap-5">
            <div className="rounded-md border border-black/10 bg-white/80 p-5 dark:border-white/10 dark:bg-white/10">
              <div className="text-sm font-semibold uppercase text-moss">Checkmate practice</div>
              <h1 className="mt-2 text-4xl font-black">Train technical mates against best defense.</h1>
              <p className="mt-3 max-w-2xl text-black/60 dark:text-white/60">
                Pick an endgame, beat the clock, and review the optimal mating line after each attempt.
              </p>
              <button
                type="button"
                onClick={() => setView('practice')}
                className="mt-5 inline-flex items-center gap-2 rounded-md bg-moss px-5 py-3 font-bold text-white"
              >
                <Play size={20} />
                Open Checkmate Practice
              </button>
            </div>
            <div className="grid gap-3 md:grid-cols-3">
              {['King + rook basics', 'Queen net speed', 'Bishop + knight control'].map((title) => (
                <button key={title} type="button" onClick={() => setView('practice')} className="rounded-md border border-black/10 bg-white/75 p-4 text-left transition hover:border-moss dark:border-white/10 dark:bg-white/10">
                  <div className="font-bold">{title}</div>
                  <div className="mt-2 text-sm text-black/55 dark:text-white/55">Start a focused mating drill.</div>
                </button>
              ))}
            </div>
          </section>
          <aside className="grid content-start gap-4">
            <AuthPanel user={user} onUser={setUser} onNotice={setNotice} />
            <ProfileSummary user={user} progress={progress} />
            <TournamentPanel signedIn={Boolean(user && token())} onNotice={setNotice} />
          </aside>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f4f6f0] text-ink transition dark:bg-[#10130f] dark:text-white">
      {topBar}
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
                  <span className="text-sm font-bold text-ember">{solutionStep}</span>
                  <button type="button" className="rounded-md border border-black/10 px-3 py-2 text-sm font-semibold dark:border-white/10" onClick={() => setSolutionIndex(Math.max(0, solutionIndex - 1))}>Prev</button>
                  <button type="button" className="rounded-md border border-black/10 px-3 py-2 text-sm font-semibold dark:border-white/10" onClick={() => setSolutionIndex(Math.min(solution.length - 1, solutionIndex + 1))}>Next</button>
                </div>
              )}
              <button type="button" onClick={() => setView('home')} className="inline-flex items-center gap-2 rounded-md border border-black/10 px-3 py-2 text-sm font-semibold dark:border-white/10">
                <ChevronLeft size={17} />
                Dashboard
              </button>
            </div>
          </div>

          <StatStrip session={session ? { ...session, remainingSeconds: displaySeconds } : undefined} />
          <CoachNote review={coachReview} />
          {solutionMove && (
            <section className="rounded-md border border-ember/40 bg-ember/10 p-4 shadow-sm">
              <div className="text-xs font-black uppercase tracking-wide text-ember">Best Move</div>
              <div className="mt-1 flex flex-wrap items-end justify-between gap-3">
                <div>
                  <div className="text-4xl font-black text-ink dark:text-white">{solutionMove.san || solutionMove.uci}</div>
                  <div className="mt-1 text-sm font-semibold text-black/60 dark:text-white/65">
                    Move {solutionStep}: {solutionMove.uci.slice(0, 2)} to {solutionMove.uci.slice(2, 4)}
                  </div>
                </div>
                <div className="rounded-md bg-white/75 px-3 py-2 text-sm font-bold text-ember dark:bg-black/25">
                  Follow the arrow on the board
                </div>
              </div>
              <p className="mt-3 max-w-3xl text-sm leading-6 text-black/65 dark:text-white/70">{solutionMove.reason}</p>
            </section>
          )}
          <TrainingBoard fen={solutionMove?.fenAfter ?? fen} session={session} hint={hint} solutionMove={solutionMove} onMove={move} />
        </section>

        <AnimatePresence mode="wait">
          <motion.div initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 12 }}>
            <div className="mb-4">
              <AuthPanel user={user} onUser={setUser} onNotice={setNotice} />
            </div>
            <div className="mb-4">
              <ProfileSummary user={user} progress={progress} />
            </div>
            <div className="mb-4">
              <TournamentPanel signedIn={Boolean(user && token())} onNotice={setNotice} />
            </div>
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
          </motion.div>
        </AnimatePresence>
      </div>
    </main>
  );
}
