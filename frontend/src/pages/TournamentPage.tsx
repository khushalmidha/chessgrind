import { Copy, Play, RefreshCw, Trophy, Users } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../lib/api';
import { difficultyLabels, modeLabels } from '../lib/fallback';
import type { Difficulty, StandingDto, TournamentDetailDto, TournamentDto, TrainingMode } from '../types/api';

interface Props {
  code?: string;
  onNotice: (message: string) => void;
}

export function TournamentPage({ code, onNotice }: Props) {
  const [detail, setDetail] = useState<TournamentDetailDto>();
  const [mine, setMine] = useState<TournamentDto[]>([]);
  const [name, setName] = useState('Friday Mate Arena');
  const [mode, setMode] = useState<TrainingMode>('KING_ROOK_VS_KING');
  const [difficulty, setDifficulty] = useState<Difficulty>('BEGINNER');
  const [rounds, setRounds] = useState(3);
  const [scheduled, setScheduled] = useState(defaultLocalDateTime());
  const [roundNumber, setRoundNumber] = useState(1);
  const [accuracy, setAccuracy] = useState(100);
  const [timeSeconds, setTimeSeconds] = useState(180);
  const [hintsUsed, setHintsUsed] = useState(0);
  const [loading, setLoading] = useState(false);

  const countdown = useCountdown(detail?.tournament.scheduledStartAt);
  const started = detail ? new Date(detail.tournament.scheduledStartAt).getTime() <= Date.now() : false;

  useEffect(() => {
    if (code) {
      void load(code);
    } else {
      void api.myTournaments().then(setMine).catch((error) => onNotice(error instanceof Error ? error.message : 'Could not load tournaments'));
    }
  }, [code, onNotice]);

  useEffect(() => {
    if (!detail) return;
    const id = window.setInterval(() => {
      void api.standings(detail.tournament.code).then((standings) => setDetail((current) => current ? { ...current, standings } : current)).catch(() => undefined);
    }, 5000);
    return () => window.clearInterval(id);
  }, [detail?.tournament.code]);

  const shareUrl = useMemo(() => detail ? `${window.location.origin}/tournament/${detail.tournament.code}` : '', [detail]);

  async function load(nextCode: string) {
    setLoading(true);
    try {
      const response = await api.tournament(nextCode.toUpperCase());
      setDetail(response);
    } catch (error) {
      onNotice(error instanceof Error ? error.message : 'Tournament unavailable');
    } finally {
      setLoading(false);
    }
  }

  async function create() {
    setLoading(true);
    try {
      const response = await api.createTournament({
        name,
        mode,
        difficulty,
        rounds,
        scheduledStartAt: new Date(scheduled).toISOString(),
      });
      setDetail(response);
      window.history.pushState({}, '', `/tournament/${response.tournament.code}`);
      onNotice('Tournament created. Share the lobby link.');
    } catch (error) {
      onNotice(error instanceof Error ? error.message : 'Could not create tournament');
    } finally {
      setLoading(false);
    }
  }

  async function join() {
    if (!detail) return;
    try {
      const response = await api.joinTournament(detail.tournament.code);
      setDetail(response);
      onNotice('Joined tournament.');
    } catch (error) {
      onNotice(error instanceof Error ? error.message : 'Could not join tournament');
    }
  }

  async function submitResult() {
    if (!detail) return;
    try {
      const response = await api.submitTournamentResult(detail.tournament.code, { roundNumber, accuracy, timeSeconds, hintsUsed });
      setDetail(response);
      onNotice('Round result submitted.');
    } catch (error) {
      onNotice(error instanceof Error ? error.message : 'Could not submit result');
    }
  }

  if (detail) {
    return (
      <section className="grid gap-4 lg:grid-cols-[1fr_360px]">
        <div className="grid gap-4">
          <div className="mf-panel rounded-forge p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="font-display text-sm font-bold uppercase text-copper dark:text-ember">Tournament Lobby</div>
                <h1 className="font-display text-3xl font-bold">{detail.tournament.name}</h1>
                <p className="text-sm text-black/55 dark:text-white/55">
                  {modeLabels[detail.tournament.mode]} · {difficultyLabels[detail.tournament.difficulty]} · {detail.tournament.rounds} rounds
                </p>
              </div>
              <button type="button" onClick={() => void navigator.clipboard.writeText(shareUrl)} className="inline-flex items-center gap-2 rounded-tool border border-black/10 bg-white/60 px-3 py-2 text-sm font-bold transition hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember">
                <Copy size={16} />
                Copy Link
              </button>
            </div>
            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              <Metric label="Start" value={new Date(detail.tournament.scheduledStartAt).toLocaleString()} />
              <Metric label="Countdown" value={started ? 'Live' : countdown} />
              <Metric label="Players" value={`${detail.tournament.participantCount}`} />
            </div>
            <div className="mt-4 rounded-tool border border-copper/20 bg-copper/10 p-3 text-sm text-copper dark:border-ember/20 dark:bg-ember/10 dark:text-ember">
              Scoring: points = accuracy x 10 - seconds - hints x 25. Everyone plays the same mode and difficulty.
            </div>
            {!detail.tournament.joined && !started && (
              <button type="button" onClick={join} className="mt-4 inline-flex items-center gap-2 rounded-tool bg-copper px-4 py-2 font-bold text-white shadow-forge dark:bg-ember dark:text-night">
                <Users size={18} />
                Join Tournament
              </button>
            )}
          </div>

          {started && detail.tournament.joined && (
            <div className="mf-panel rounded-forge p-4">
              <div className="mb-3 flex items-center gap-2 font-bold"><Play size={17} /> Submit Round Result</div>
              <div className="grid gap-2 sm:grid-cols-4">
                <input type="number" min={1} max={detail.tournament.rounds} value={roundNumber} onChange={(event) => setRoundNumber(Number(event.target.value))} className="mf-input rounded-tool p-2" aria-label="Round" />
                <input type="number" min={0} max={100} value={accuracy} onChange={(event) => setAccuracy(Number(event.target.value))} className="mf-input rounded-tool p-2" aria-label="Accuracy" />
                <input type="number" min={0} value={timeSeconds} onChange={(event) => setTimeSeconds(Number(event.target.value))} className="mf-input rounded-tool p-2" aria-label="Seconds" />
                <input type="number" min={0} value={hintsUsed} onChange={(event) => setHintsUsed(Number(event.target.value))} className="mf-input rounded-tool p-2" aria-label="Hints" />
              </div>
              <button type="button" onClick={submitResult} className="mt-3 rounded-tool bg-copper px-4 py-2 font-bold text-white shadow-forge dark:bg-ember dark:text-night">
                Save Result
              </button>
            </div>
          )}
        </div>
        <Standings standings={detail.standings} onRefresh={() => load(detail.tournament.code)} />
      </section>
    );
  }

  return (
    <section className="grid gap-4 lg:grid-cols-[1fr_360px]">
      <div className="mf-panel rounded-forge p-4">
        <h1 className="font-display text-3xl font-bold">Host Tournament</h1>
        <div className="mt-4 grid gap-3">
          <input value={name} onChange={(event) => setName(event.target.value)} className="mf-input rounded-tool p-2" />
          <select value={mode} onChange={(event) => setMode(event.target.value as TrainingMode)} className="mf-input rounded-tool p-2">
            {Object.entries(modeLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select>
          <select value={difficulty} onChange={(event) => setDifficulty(event.target.value as Difficulty)} className="mf-input rounded-tool p-2">
            {Object.entries(difficultyLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select>
          <input type="number" min={1} max={12} value={rounds} onChange={(event) => setRounds(Number(event.target.value))} className="mf-input rounded-tool p-2" />
          <input type="datetime-local" value={scheduled} onChange={(event) => setScheduled(event.target.value)} className="mf-input rounded-tool p-2" />
          <button type="button" disabled={loading} onClick={create} className="rounded-tool bg-copper px-4 py-2 font-bold text-white shadow-forge disabled:opacity-60 dark:bg-ember dark:text-night">Create Tournament</button>
        </div>
      </div>
      <div className="mf-panel rounded-forge p-4">
        <h2 className="mb-3 flex items-center gap-2 font-bold"><Trophy size={17} /> My Tournaments</h2>
        <div className="grid gap-2">
          {mine.length === 0 ? <div className="text-sm text-black/55 dark:text-white/55">No tournaments hosted yet.</div> : mine.map((item) => (
            <button key={item.code} type="button" onClick={() => { window.history.pushState({}, '', `/tournament/${item.code}`); void load(item.code); }} className="rounded-tool border border-black/10 bg-white/50 p-3 text-left text-sm transition hover:border-copper dark:border-white/10 dark:bg-white/5">
              <div className="font-semibold">{item.name}</div>
              <div className="text-xs text-black/55 dark:text-white/55">{item.code} · {new Date(item.scheduledStartAt).toLocaleString()}</div>
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}

function Standings({ standings, onRefresh }: { standings: StandingDto[]; onRefresh: () => void }) {
  return (
    <aside className="mf-panel rounded-forge p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="flex items-center gap-2 font-bold"><Trophy size={17} /> Standings</h2>
        <button type="button" onClick={onRefresh} className="rounded-tool border border-black/10 bg-white/60 p-2 dark:border-white/10 dark:bg-white/10"><RefreshCw size={15} /></button>
      </div>
      <div className="grid gap-2">
        {standings.length === 0 ? <div className="text-sm text-black/55 dark:text-white/55">No players yet.</div> : standings.map((row, index) => (
          <div key={row.username} className="rounded-tool border border-black/10 bg-white/50 p-3 text-sm dark:border-white/10 dark:bg-white/5">
            <div className="flex items-center justify-between gap-2">
              <span className="font-bold">#{index + 1} {row.username}</span>
              <span className="font-display font-bold text-copper dark:text-ember">{row.totalPoints}</span>
            </div>
            <div className="mt-1 text-xs text-black/55 dark:text-white/55">{row.completedRounds} rounds · {row.averageAccuracy.toFixed(1)}% · {formatSeconds(row.totalTimeSeconds)}</div>
          </div>
        ))}
      </div>
    </aside>
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

function useCountdown(value?: string) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);
  if (!value) return '-';
  const seconds = Math.max(0, Math.floor((new Date(value).getTime() - now) / 1000));
  return formatSeconds(seconds);
}

function formatSeconds(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${rest.toString().padStart(2, '0')}`;
}

function defaultLocalDateTime() {
  const date = new Date(Date.now() + 15 * 60 * 1000);
  date.setSeconds(0, 0);
  return date.toISOString().slice(0, 16);
}
