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
      <div className="rounded-md border border-black/10 bg-white/70 p-3 dark:border-white/10 dark:bg-white/10">
        <div className="flex items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2">
            <UserRound size={18} className="text-moss" />
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold">{user.username}</div>
              <div className="truncate text-xs text-black/50 dark:text-white/50">{user.email}</div>
            </div>
          </div>
          <button
            type="button"
            className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-black/10 dark:border-white/10"
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
    <div className="rounded-md border border-black/10 bg-white/70 p-3 dark:border-white/10 dark:bg-white/10">
      <div className="mb-3 flex gap-2">
        {(['login', 'register'] as const).map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => setMode(item)}
            className={`rounded-md px-3 py-2 text-sm font-semibold ${mode === item ? 'bg-moss text-white' : 'bg-black/5 dark:bg-white/10'}`}
          >
            {item === 'login' ? 'Sign in' : 'Create account'}
          </button>
        ))}
      </div>
      {mode === 'register' && (
        <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Username" className="mb-2 w-full rounded-md border border-black/10 bg-white p-2 text-sm dark:border-white/10 dark:bg-white/10" />
      )}
      <input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="Email" className="mb-2 w-full rounded-md border border-black/10 bg-white p-2 text-sm dark:border-white/10 dark:bg-white/10" />
      <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" placeholder="Password" className="mb-3 w-full rounded-md border border-black/10 bg-white p-2 text-sm dark:border-white/10 dark:bg-white/10" />
      <button type="button" onClick={submit} className="mb-3 w-full rounded-md bg-moss px-3 py-2 text-sm font-semibold text-white">
        {mode === 'login' ? 'Sign in' : 'Create account'}
      </button>
      {googleClientId ? <div ref={googleRef} className="min-h-10" /> : <div className="text-xs text-black/50 dark:text-white/50">Add VITE_GOOGLE_CLIENT_ID to enable Google sign-in.</div>}
    </div>
  );
}
