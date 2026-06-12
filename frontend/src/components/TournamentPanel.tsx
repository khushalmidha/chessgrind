import { Copy, Plus, Trophy, UsersRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { difficultyLabels, modeLabels } from '../lib/fallback';
import type { Difficulty, Tournament, TrainingMode } from '../types/api';

interface Props {
  signedIn: boolean;
  onNotice: (message: string) => void;
}

export function TournamentPanel({ signedIn, onNotice }: Props) {
  const [name, setName] = useState('Friday Mate Race');
  const [mode, setMode] = useState<TrainingMode>('KING_ROOK_VS_KING');
  const [difficulty, setDifficulty] = useState<Difficulty>('BEGINNER');
  const [timeLimitSeconds, setTimeLimitSeconds] = useState(300);
  const [maxPlayers, setMaxPlayers] = useState(16);
  const [joinCode, setJoinCode] = useState('');
  const [tournament, setTournament] = useState<Tournament>();
  const [mine, setMine] = useState<Tournament[]>([]);

  useEffect(() => {
    if (signedIn) {
      api.myTournaments().then(setMine).catch(() => setMine([]));
    }
  }, [signedIn]);

  async function create() {
    if (!signedIn) {
      onNotice('Sign in before creating a tournament.');
      return;
    }
    try {
      const created = await api.createTournament({ name, mode, difficulty, timeLimitSeconds, maxPlayers });
      setTournament(created);
      setMine((items) => [created, ...items.filter((item) => item.id !== created.id)]);
      onNotice('Tournament created. Share the link with players.');
    } catch (error) {
      onNotice(error instanceof Error ? error.message : 'Could not create tournament');
    }
  }

  async function join(code = joinCode) {
    if (!signedIn) {
      onNotice('Sign in before joining a tournament.');
      return;
    }
    try {
      const joined = await api.joinTournament(code.trim().toUpperCase());
      setTournament(joined);
      onNotice(`Joined ${joined.name}.`);
    } catch (error) {
      onNotice(error instanceof Error ? error.message : 'Could not join tournament');
    }
  }

  async function copy(link: string) {
    await navigator.clipboard.writeText(link);
    onNotice('Tournament link copied.');
  }

  return (
    <section className="rounded-md border border-black/10 bg-white/80 p-4 dark:border-white/10 dark:bg-white/10">
      <div className="flex items-center justify-between gap-3">
        <div>
          <div className="text-sm font-semibold uppercase text-moss">Tournament</div>
          <h2 className="text-xl font-bold">Host a mate race</h2>
        </div>
        <Trophy className="text-ember" />
      </div>

      <div className="mt-4 grid gap-2">
        <input value={name} onChange={(event) => setName(event.target.value)} className="rounded-md border border-black/10 bg-white p-2 text-sm dark:border-white/10 dark:bg-white/10" />
        <div className="grid grid-cols-2 gap-2">
          <select value={mode} onChange={(event) => setMode(event.target.value as TrainingMode)} className="rounded-md border border-black/10 bg-white p-2 text-sm dark:border-white/10 dark:bg-white/10">
            {Object.entries(modeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <select value={difficulty} onChange={(event) => setDifficulty(event.target.value as Difficulty)} className="rounded-md border border-black/10 bg-white p-2 text-sm dark:border-white/10 dark:bg-white/10">
            {Object.entries(difficultyLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <input type="number" min={30} step={30} value={timeLimitSeconds} onChange={(event) => setTimeLimitSeconds(Number(event.target.value))} className="rounded-md border border-black/10 bg-white p-2 text-sm dark:border-white/10 dark:bg-white/10" aria-label="Tournament seconds" />
          <input type="number" min={2} max={200} value={maxPlayers} onChange={(event) => setMaxPlayers(Number(event.target.value))} className="rounded-md border border-black/10 bg-white p-2 text-sm dark:border-white/10 dark:bg-white/10" aria-label="Max players" />
        </div>
        <button type="button" onClick={create} className="inline-flex items-center justify-center gap-2 rounded-md bg-moss px-4 py-2 font-bold text-white">
          <Plus size={18} />
          Create tournament
        </button>
      </div>

      <div className="mt-4 grid grid-cols-[1fr_auto] gap-2">
        <input value={joinCode} onChange={(event) => setJoinCode(event.target.value)} placeholder="Join code" className="rounded-md border border-black/10 bg-white p-2 text-sm uppercase dark:border-white/10 dark:bg-white/10" />
        <button type="button" onClick={() => join()} className="rounded-md border border-black/10 px-3 py-2 text-sm font-bold dark:border-white/10">Join</button>
      </div>

      {tournament && (
        <div className="mt-4 rounded-md border border-moss/35 bg-moss/10 p-3">
          <div className="flex items-center justify-between gap-2">
            <div>
              <div className="font-bold">{tournament.name}</div>
              <div className="text-xs text-black/55 dark:text-white/55">{tournament.playerCount}/{tournament.maxPlayers} players · code {tournament.joinCode}</div>
            </div>
            <button type="button" onClick={() => copy(tournament.shareUrl)} className="inline-flex items-center gap-2 rounded-md border border-black/10 bg-white px-3 py-2 text-sm font-bold dark:border-white/10 dark:bg-white/10">
              <Copy size={15} />
              Copy
            </button>
          </div>
          <Standings tournament={tournament} />
        </div>
      )}

      {mine.length > 0 && (
        <div className="mt-4 grid gap-2">
          {mine.slice(0, 3).map((item) => (
            <button key={item.id} type="button" onClick={() => setTournament(item)} className="flex items-center justify-between rounded-md border border-black/10 px-3 py-2 text-left text-sm dark:border-white/10">
              <span>{item.name}</span>
              <span className="flex items-center gap-1 text-black/50 dark:text-white/50"><UsersRound size={14} />{item.playerCount}</span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

function Standings({ tournament }: { tournament: Tournament }) {
  return (
    <ol className="mt-3 divide-y divide-black/10 text-sm dark:divide-white/10">
      {tournament.participants.length === 0 ? (
        <li className="py-2 text-black/50 dark:text-white/50">No players yet.</li>
      ) : tournament.participants.map((player, index) => (
        <li key={player.userId} className="flex items-center justify-between py-2">
          <span>{index + 1}. {player.username}</span>
          <span className="text-black/50 dark:text-white/50">{player.score} pts</span>
        </li>
      ))}
    </ol>
  );
}
