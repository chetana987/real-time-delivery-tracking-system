import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  api,
  persistAuth,
  getStoredUser,
  clearAuth,
  setUnauthorizedHandler,
  clearUnauthorizedHandler,
} from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const [user, setUser] = useState(() => getStoredUser());

  // Any protected API that returns 401 (expired/invalid JWT) clears the stale
  // session and routes to login. No refresh-token architecture — just a clean
  // logout when the token can no longer authenticate.
  useEffect(() => {
    const handleUnauthorized = () => {
      setUser(null);
      navigate('/login', { replace: true });
    };
    setUnauthorizedHandler(handleUnauthorized);
    return () => clearUnauthorizedHandler();
  }, [navigate]);

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
