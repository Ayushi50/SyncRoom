import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, refreshSession, setAccessToken, setSessionLostHandler } from '../lib/api';
import type { AuthResponse, User } from '../lib/types';

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (name: string, email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    refreshSession().then((res) => {
      if (!alive) return;
      setUser(res?.user ?? null);
      setLoading(false);
    });
    setSessionLostHandler(() => setUser(null));
    return () => {
      alive = false;
      setSessionLostHandler(null);
    };
  }, []);

  const accept = useCallback((res: AuthResponse) => {
    setAccessToken(res.accessToken);
    setUser(res.user);
    return res.user;
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      login: async (email, password) =>
        accept(
          await api<AuthResponse>('/auth/login', {
            method: 'POST',
            body: { email, password },
            auth: false,
          }),
        ),
      register: async (name, email, password) =>
        accept(
          await api<AuthResponse>('/auth/register', {
            method: 'POST',
            body: { name, email, password },
            auth: false,
          }),
        ),
      logout: async () => {
        try {
          await api<void>('/auth/logout', { method: 'POST', auth: false });
        } finally {
          setAccessToken(null);
          setUser(null);
        }
      },
    }),
    [user, loading, accept],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
