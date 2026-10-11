import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { onSessionChange, refreshSession, setSession } from '@/lib/api';
import { authApi } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const queryClient = useQueryClient();
  const [user, setUserState] = useState(null);
  const [status, setStatus] = useState('loading');

  useEffect(() => {
    const off = onSessionChange((u) => {
      setUserState(u);
      setStatus(u ? 'authenticated' : 'guest');
      if (!u) queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== 'categories' && q.queryKey[0] !== 'items' });
    });
    // Restore the session from the httpOnly refresh cookie on page load.
    void refreshSession().then((u) => {
      if (!u) setStatus('guest');
    });
    return () => {
      off();
    };
  }, [queryClient]);

  const login = useCallback(async (email, password) => {
    const res = await authApi.login(email, password);
    setSession(res.accessToken, res.user);
    return res.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setSession(null, null);
      queryClient.clear();
    }
  }, [queryClient]);

  const value = useMemo(
    () => ({ user, status, isAdmin: user?.role === 'ADMIN', login, logout, setUser: setUserState }),
    [user, status, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
