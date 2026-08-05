import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { api, persistAuth, getStoredUser, clearAuth } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => getStoredUser());

  const login = useCallback(async (email, password) => {
    const data = await api.login({ email, password });
    persistAuth(data);
    setUser({
      userId: data.userId,
      name: data.name,
      email: data.email,
      role: data.role,
    });
  }, []);

  const register = useCallback(async (payload) => {
    const data = await api.register(payload);
    persistAuth(data);
    setUser({
      userId: data.userId,
      name: data.name,
      email: data.email,
      role: data.role,
    });
  }, []);

  const logout = useCallback(() => {
    clearAuth();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, login, register, logout }),
    [user, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
