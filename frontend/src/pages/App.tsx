import { Chess } from 'chess.js';
import { AnimatePresence, motion } from 'framer-motion';
import { Dumbbell, LogOut, Moon, Sun, UserRound } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { AuthPanel } from '../components/AuthPanel';
import { ControlPanel } from '../components/ControlPanel';
import { IconButton } from '../components/IconButton';
import { StatStrip } from '../components/StatStrip';
import { TrainingBoard } from '../components/TrainingBoard';
import { Profile } from './Profile';
import { api, currentUser, onAuthExpired, token } from '../lib/api';
import { boardStateLabel } from '../lib/chess';
import { fallbackPuzzles } from '../lib/fallback';
import type { AuthResponse, Difficulty, GameReportDto, HintResponse, MoveDto, PlayerProfileReportDto, PuzzleDto, SessionDto, SessionStatus, TimerMode, TrainingMode } from '../types/api';

export function App() {
  const [dark, setDark] = useState(true);
  const [route, setRoute] = useState<'profile' | 'train'>(() => window.location.pathname === '/play' ? 'train' : 'profile');
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
  const [gameReport, setGameReport] = useState<GameReportDto>();
  const [profileReport, setProfileReport] = useState<PlayerProfileReportDto>();
  const [reportLoading, setReportLoading] = useState<'session' | 'profile'>();
  const [reportError, setReportError] = useState('');
  const [reportsUnavailable, setReportsUnavailable] = useState(false);
  const [solutionIndex, setSolutionIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('Ready for a clean mate.');

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
  }, [dark]);

  useEffect(() => {
    const onPopState = () => setRoute(window.location.pathname === '/play' ? 'train' : 'profile');
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  useEffect(() => {
    api.puzzles().then(setPuzzles).catch((error) => {
      setPuzzles(fallbackPuzzles);
      setNotice(error instanceof Error ? `${error.message}. Loaded local fallback puzzles.` : 'Loaded local fallback puzzles.');
      // FIXED: puzzle API failures were silently swallowed into fallback data with no user-visible error.
    });
  }, []);

  useEffect(() => onAuthExpired(() => {
    setUser(undefined);
    setSession(undefined);
    setHint(undefined);
    setSolution([]);
    setGameReport(undefined);
    setProfileReport(undefined);
    setReportError('');
    setRoute('profile');
    window.history.replaceState({}, '', '/profile');
    setNotice('Session expired, please sign in again.');
    // FIXED: a 401 from the API now clears the in-memory user state so the UI returns to sign-in mode.
  }), []);

  useEffect(() => {
    if (!session || session.status !== 'ACTIVE' || session.timerMode === 'NONE') return;
    setDisplaySeconds(session.remainingSeconds);
    const id = window.setInterval(() => {
      setDisplaySeconds((seconds) => {
        if (seconds <= 1) {
          window.clearInterval(id);
          setNotice('Time expired. Analyze the optimal mating line.');
          setSession((current) => current ? { ...current, status: 'TIMEOUT', remainingSeconds: 0 } : current);
          // FIXED: local timeout now stops the active session immediately so stale UI cannot keep submitting moves.
          return 0;
        }
        return seconds - 1;
      });
    }, 1000);
    return () => window.clearInterval(id);
  }, [session?.id, session?.status, session?.timerMode, session?.remainingSeconds]);

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
  const analysisArrows = solution.slice(solutionIndex, solutionIndex + 2);
  const reviewMode = solution.length > 0;

  async function start() {
    if (!token() || !user) {
      startLocalSession('Guest practice started locally. Sign in and connect the API to save progress.');
      return;
    }
    setBusy(true);
    setHint(undefined);
    setSolution([]);
    setGameReport(undefined);
    setProfileReport(undefined);
    setReportError('');
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
      const message = error instanceof Error ? error.message : 'Could not start session';
      startLocalSession(`${message}. Started timed local practice; backend session did not start.`);
      // FIXED: Start left the app in a non-session board mode after API failures, so the timer never started.
    } finally {
      setBusy(false);
    }
  }

  async function move(uciMove: string) {
    setHint(undefined);
    setGameReport(undefined);
    setProfileReport(undefined);
    setReportError('');
    if (session) {
      if (session.status !== 'ACTIVE' || (session.timerMode !== 'NONE' && displaySeconds <= 0)) {
        setNotice('Time expired. Analyze the optimal mating line.');
        setSession((current) => current ? { ...current, status: 'TIMEOUT', remainingSeconds: 0 } : current);
        return false;
        // FIXED: moves could still be submitted after the client countdown reached zero.
      }
      if (session.id.startsWith('local-')) {
        return moveLocal(uciMove);
      }
      try {
        const response = await api.move(session.id, uciMove);
        setSession(response.session);
        setDisplaySeconds(response.session.remainingSeconds);
        // FIXED: server-confirmed remaining time was overwritten by a stale local timer value after each move.
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

  function startLocalSession(message: string) {
    const startFen = mode === 'CUSTOM' && customFen ? customFen : selectedPuzzle.fen;
    const now = new Date().toISOString();
    const localSession: SessionDto = {
      id: `local-${Date.now()}`,
      mode,
      difficulty,
      timerMode,
      status: 'ACTIVE',
      startFen,
      currentFen: startFen,
      remainingSeconds: timerMode === 'NONE' ? 0 : timeLimit,
      hintsUsed: 0,
      mistakes: 0,
      accuracy: 100,
      startedAt: now,
      moves: [],
    };
    setHint(undefined);
    setSolution([]);
    setGameReport(undefined);
    setProfileReport(undefined);
    setReportError('');
    setSolutionIndex(0);
    setSession(localSession);
    setDisplaySeconds(localSession.remainingSeconds);
    setLocalFen(startFen);
    setNotice(message);
  }

  async function moveLocal(uciMove: string) {
    if (!session) return false;
    try {
      const chess = new Chess(session.currentFen);
      if (chess.turn() !== 'w') {
        setNotice('The defender is thinking; make the attacking move after black replies.');
        return false;
      }
      const result = chess.move({ from: uciMove.slice(0, 2), to: uciMove.slice(2, 4), promotion: uciMove[4] ?? 'q' });
      if (!result) return false;
      let status: SessionStatus = chess.isCheckmate() ? 'CHECKMATE' : chess.isStalemate() ? 'STALEMATE' : chess.isDraw() ? 'DRAW' : 'ACTIVE';
      const moveDto: MoveDto = {
        ply: session.moves.length + 1,
        uci: uciMove,
        san: result.san,
        fenAfter: chess.fen(),
        engineMove: false,
        optimal: true,
        reason: 'Local practice move; engine scoring is available when the backend is connected.',
      };
      const nextMoves = [...session.moves, moveDto];
      if (status === 'ACTIVE' && chess.turn() === 'b') {
        const defense = chess.moves({ verbose: true })[0];
        if (defense) {
          const defenseUci = `${defense.from}${defense.to}${defense.promotion ?? ''}`;
          chess.move(defense);
          status = chess.isCheckmate() ? 'CHECKMATE' : chess.isStalemate() ? 'STALEMATE' : chess.isDraw() ? 'DRAW' : 'ACTIVE';
          nextMoves.push({
            ply: nextMoves.length + 1,
            uci: defenseUci,
            san: defense.san,
            fenAfter: chess.fen(),
            engineMove: true,
            optimal: true,
            reason: 'Local defender reply. Connected sessions use Stockfish for the strongest defense.',
          });
          // FIXED: local fallback let the user move both sides; black now auto-replies during timed practice.
        }
      }
      setSession({
        ...session,
        currentFen: chess.fen(),
        status,
        remainingSeconds: displaySeconds,
        endedAt: status === 'ACTIVE' ? undefined : new Date().toISOString(),
        moves: nextMoves,
      });
      setLocalFen(chess.fen());
      setNotice(status === 'CHECKMATE' ? 'Checkmate.' : status === 'STALEMATE' ? 'Stalemate.' : status === 'DRAW' ? 'Draw.' : 'Move accepted locally. Black replied.');
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

  async function requestReport(refresh = false) {
    if (!session) return;
    setReportLoading('session');
    setReportError('');
    setProfileReport(undefined);
    try {
      const response = await api.report(session.id, refresh);
      setGameReport(response);
      setNotice('Performance report ready.');
    } catch (error) {
      handleReportError(error);
    } finally {
      setReportLoading(undefined);
    }
  }

  async function requestProfileReport(refresh = false) {
    if (!token() || !user) {
      setNotice('Sign in first to generate a profile report.');
      return;
    }
    setReportLoading('profile');
    setReportError('');
    setGameReport(undefined);
    try {
      const response = await api.profileReport(refresh);
      setProfileReport(response);
      setNotice('Player profile ready.');
    } catch (error) {
      handleReportError(error);
    } finally {
      setReportLoading(undefined);
    }
  }

  function handleReportError(error: unknown) {
    const message = error instanceof Error ? error.message : '';
    const friendly = message.includes('Gemini') || message.includes('AI reports') || message.includes('AI report')
      ? 'AI reports are not available on this server right now.'
      : 'Could not generate the report. Training still works normally.';
    if (friendly.includes('not available')) {
      setReportsUnavailable(true);
    }
    setReportError(friendly);
    setNotice(friendly);
  }

  function reset() {
    setSession(undefined);
    setDisplaySeconds(timerMode === 'NONE' ? 0 : timeLimit);
    setHint(undefined);
    setSolution([]);
    setGameReport(undefined);
    setProfileReport(undefined);
    setReportError('');
    setSolutionIndex(0);
    setLocalFen(mode === 'CUSTOM' && customFen ? customFen : selectedPuzzle.fen);
    setNotice('Board reset.');
  }

  function navigate(nextRoute: 'train' | 'profile') {
    const path = nextRoute === 'profile' ? '/profile' : '/play';
    window.history.pushState({}, '', path);
    setRoute(nextRoute);
  }

  function playFromProfile() {
    if (!token() || !user) {
      setNotice('Sign in first, then press Play.');
      return;
    }
    navigate('train');
    setNotice('Choose your endgame type, then press Start to begin.');
  }

  function accountChip() {
    if (!user) {
      return (
        <button
          type="button"
          onClick={() => navigate('profile')}
          className="inline-flex items-center gap-2 rounded-tool border border-black/10 bg-white/70 px-3 py-2 text-sm font-bold transition hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember"
        >
          <UserRound size={17} />
          Account
        </button>
      );
    }
    return (
      <div className="flex items-center gap-2 rounded-tool border border-black/10 bg-white/70 px-2 py-1.5 shadow-insetGlow dark:border-white/10 dark:bg-white/10">
        <button type="button" onClick={() => navigate('profile')} className="flex min-w-0 items-center gap-2 pr-1 text-left">
          <span className="mf-wordmark-mark flex h-8 w-8 shrink-0 items-center justify-center rounded-tool font-display text-sm font-bold text-white">
            {user.username.slice(0, 1).toUpperCase()}
          </span>
          <span className="hidden min-w-0 sm:block">
            <span className="block truncate text-sm font-bold">{user.username}</span>
            <span className="block truncate text-xs text-black/50 dark:text-white/50">{user.email}</span>
          </span>
        </button>
        <button
          type="button"
          title="Sign out"
          aria-label="Sign out"
          onClick={() => {
            localStorage.removeItem('mateforge.token');
            localStorage.removeItem('mateforge.user');
            setUser(undefined);
            setSession(undefined);
            navigate('profile');
            setNotice('Signed out.');
          }}
          className="inline-flex h-8 w-8 items-center justify-center rounded-tool border border-black/10 transition hover:border-copper hover:text-copper dark:border-white/10 dark:hover:border-ember dark:hover:text-ember"
        >
          <LogOut size={15} />
        </button>
      </div>
    );
  }

  if (route === 'profile') {
    return (
      <main className="min-h-screen text-ink transition dark:text-white">
        <div className="mx-auto grid max-w-7xl gap-5 px-4 py-4">
          <div className="mf-panel flex flex-wrap items-center justify-between gap-3 rounded-forge p-3">
            <div className="flex items-center gap-3">
              <div className="mf-wordmark-mark flex h-10 w-10 items-center justify-center rounded-tool font-display text-lg font-bold text-white">M</div>
              <div>
                <div className="font-display text-sm font-bold uppercase text-copper dark:text-ember">Profile</div>
                <div className="text-sm text-black/55 dark:text-white/55">{notice}</div>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {accountChip()}
              <IconButton icon={<Dumbbell size={18} />} label="Training" onClick={() => navigate('train')} />
              <IconButton icon={dark ? <Sun size={18} /> : <Moon size={18} />} label="Toggle theme" onClick={() => setDark((value) => !value)} />
            </div>
          </div>
          {!token() || !user ? (
            <div className="mx-auto w-full max-w-md">
              <div className="mf-panel mb-3 rounded-forge p-4 text-sm">
                Sign in to view your profile.
              </div>
              <AuthPanel user={user} onUser={setUser} onNotice={setNotice} />
            </div>
          ) : (
            <Profile onNotice={setNotice} onPlay={playFromProfile} />
          )}
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen text-ink transition dark:text-white">
      <div className="mx-auto grid min-h-screen max-w-7xl gap-5 px-4 py-4 lg:grid-cols-[minmax(320px,1fr)_390px]">
        <section className="flex flex-col gap-4">
          <div className="mf-panel flex flex-wrap items-center justify-between gap-3 rounded-forge p-3">
            <div className="flex min-w-0 items-center gap-3">
              <div className="mf-wordmark-mark flex h-11 w-11 shrink-0 items-center justify-center rounded-tool font-display text-lg font-bold text-white">M</div>
              <div className="min-w-0">
                <div className="mf-wordmark text-xl font-bold">Mateforge</div>
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="font-display font-bold uppercase text-copper dark:text-ember">{state}</span>
                  <span className="text-black/50 dark:text-white/55">{notice}</span>
                </div>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {solution.length > 0 && (
                <div className="flex items-center gap-2">
                  <button type="button" className="rounded-tool border border-black/10 bg-white/70 px-3 py-2 text-sm font-semibold transition hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember" onClick={() => setSolutionIndex(Math.max(0, solutionIndex - 1))}>Prev</button>
                  <button type="button" className="rounded-tool border border-black/10 bg-white/70 px-3 py-2 text-sm font-semibold transition hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember" onClick={() => setSolutionIndex(Math.min(solution.length - 1, solutionIndex + 1))}>Next</button>
                </div>
              )}
              {accountChip()}
              <IconButton icon={dark ? <Sun size={18} /> : <Moon size={18} />} label="Toggle theme" onClick={() => setDark((value) => !value)} />
            </div>
          </div>

          <StatStrip session={session ? { ...session, remainingSeconds: displaySeconds } : undefined} />
          <TrainingBoard fen={solutionMove?.fenAfter ?? fen} session={session} hint={hint} solutionMove={solutionMove} analysisMoves={analysisArrows} reviewMode={reviewMode} onMove={move} />
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
              gameReport={gameReport}
              profileReport={profileReport}
              reportLoading={reportLoading}
              reportError={reportError}
              reportsUnavailable={reportsUnavailable}
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
              onReport={requestReport}
              onProfileReport={requestProfileReport}
            />
            {!user && (
              <div className="mt-4">
                <AuthPanel user={user} onUser={setUser} onNotice={setNotice} />
              </div>
            )}
          </motion.div>
        </AnimatePresence>
      </div>
    </main>
  );
}
