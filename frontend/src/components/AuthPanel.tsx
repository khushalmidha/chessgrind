import { LogOut, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { api, clearAuth, setAuth } from '../lib/api';
import type { AuthResponse } from '../types/api';

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: { client_id: string; callback: (response: { credential: string }) => void }) => void;
          renderButton: (element: HTMLElement, options: Record<string, string | number | boolean>) => void;
        };
      };
    };
  }
}

interface Props {
  user?: AuthResponse;
  onUser: (user?: AuthResponse) => void;
  onNotice: (message: string) => void;
}

const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

export function AuthPanel({ user, onUser, onNotice }: Props) {
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const googleRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!googleClientId || !googleRef.current) return;
    const scriptId = 'google-identity-script';
    const render = () => {
      if (!window.google || !googleRef.current) return;
      googleRef.current.innerHTML = '';
      window.google.accounts.id.initialize({
        client_id: googleClientId,
        callback: async ({ credential }) => {
          try {
            const auth = await api.google(credential);
            setAuth(auth);
            onUser(auth);
            onNotice(`Signed in as ${auth.username}`);
          } catch (error) {
            onNotice(error instanceof Error ? error.message : 'Google sign-in failed');
          }
        },
      });
      window.google.accounts.id.renderButton(googleRef.current, { theme: 'outline', size: 'large', width: 320 });
    };
    if (!document.getElementById(scriptId)) {
      const script = document.createElement('script');
      script.id = scriptId;
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = render;
      document.head.appendChild(script);
    } else {
      render();
    }
  }, [onNotice, onUser]);

  async function submit() {
    try {
      const auth = mode === 'login'
        ? await api.login(email, password)
        : await api.register(username || email.split('@')[0], email, password);
      setAuth(auth);
      onUser(auth);
      onNotice(`Signed in as ${auth.username}`);
    } catch (error) {
      onNotice(error instanceof Error ? error.message : 'Sign-in failed');
    }
  }

  if (user) {
    return (
      <div className="mf-panel rounded-forge p-3">
        <div className="flex items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2">
            <UserRound size={18} className="text-copper dark:text-ember" />
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold">{user.username}</div>
              <div className="truncate text-xs text-black/50 dark:text-white/50">{user.email}</div>
            </div>
          </div>
          <button
            type="button"
            className="inline-flex h-9 w-9 items-center justify-center rounded-tool border border-black/10 bg-white/60 transition hover:border-copper hover:text-copper dark:border-white/10 dark:bg-white/10 dark:hover:border-ember dark:hover:text-ember"
            title="Sign out"
            aria-label="Sign out"
            onClick={() => {
              clearAuth();
              onUser(undefined);
              onNotice('Signed out.');
            }}
          >
            <LogOut size={16} />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="mf-panel rounded-forge p-3">
      <div className="mb-3 flex gap-2">
        {(['login', 'register'] as const).map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => setMode(item)}
            className={`rounded-tool px-3 py-2 text-sm font-bold transition ${mode === item ? 'bg-copper text-white shadow-forge dark:bg-ember dark:text-night' : 'bg-black/5 hover:text-copper dark:bg-white/10 dark:hover:text-ember'}`}
          >
            {item === 'login' ? 'Sign in' : 'Create account'}
          </button>
        ))}
      </div>
      {mode === 'register' && (
        <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Username" className="mf-input mb-2 w-full rounded-tool p-2 text-sm" />
      )}
      <input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="Email" className="mf-input mb-2 w-full rounded-tool p-2 text-sm" />
      <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" placeholder="Password" className="mf-input mb-3 w-full rounded-tool p-2 text-sm" />
      <button type="button" onClick={submit} className="mb-3 w-full rounded-tool bg-copper px-3 py-2 text-sm font-bold text-white shadow-forge transition hover:bg-copper/90 dark:bg-ember dark:text-night dark:hover:bg-ember/90">
        {mode === 'login' ? 'Sign in' : 'Create account'}
      </button>
      {googleClientId ? <div ref={googleRef} className="min-h-10" /> : <div className="text-xs text-black/50 dark:text-white/50">Add VITE_GOOGLE_CLIENT_ID to enable Google sign-in.</div>}
    </div>
  );
}
