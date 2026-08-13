import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/auth';
import { setSessionLostHandler, tokenStore } from '../api/client';

const AuthContext = createContext(null);

/**
 * Any one of these opens a page in the administration. Mirrors the menu rules in Sidebar — whoever holds
 * none of them sees nothing there and belongs in the game.
 */
const ADMIN_ENTRY_PERMISSIONS = [
  'USER_READ',
  'ROLE_READ',
  'POI_READ_ALL',
  'POI_PUBLISH',
  'AUDIT_READ',
  'AUDIT_READ_CONTENT',
];

/**
 * Everything the interface needs to know about the signed-in account: who it is, which roles it holds
 * and — the part that actually decides what is visible — which permissions.
 * <p>
 * The permission list comes from the server with every login and every refresh. It is never derived
 * from the role name in the client, because that would be a second, silently diverging copy of the
 * matrix.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(Boolean(tokenStore.access()));

  const clearSession = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    setSessionLostHandler(clearSession);
  }, [clearSession]);

  // A reload must not require signing in again: the token is in localStorage, so ask the server who it
  // belongs to. A rejected token lands in the client's refresh path and, if that fails too, here.
  useEffect(() => {
    if (!tokenStore.access()) {
      return;
    }
    authApi
      .me()
      .then(setUser)
      .catch(() => clearSession())
      .finally(() => setLoading(false));
  }, [clearSession]);

  const login = useCallback(async (username, password) => {
    const session = await authApi.login(username, password);
    tokenStore.set(session.accessToken, session.refreshToken);
    setUser(session.user);
    return session.user;
  }, []);

  /** Registration answers with a session, so it ends in the same state as a login. */
  const register = useCallback(async (account) => {
    const session = await authApi.register(account);
    tokenStore.set(session.accessToken, session.refreshToken);
    setUser(session.user);
    return session.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // The session ends locally either way; a failed call must not strand the user in the app.
    }
    clearSession();
  }, [clearSession]);

  const logoutEverywhere = useCallback(async () => {
    try {
      await authApi.logoutEverywhere();
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo(() => {
    const permissions = user?.permissions ?? [];
    const roles = user?.roles ?? [];
    return {
      user,
      roles,
      permissions,
      loading,
      login,
      register,
      logout,
      logoutEverywhere,
      setUser,
      isAuthenticated: Boolean(user),
      hasPermission: (permission) => permissions.includes(permission),
      hasAnyPermission: (...wanted) => wanted.flat().some((p) => permissions.includes(p)),
      hasRole: (role) => roles.includes(role),
      /**
       * Whether this account has anything to do in the administration. A registered student holds only
       * the three reading permissions of EXTERNE_PERSON and would face an empty interface — the answer
       * decides where the login sends them.
       */
      canAdminister: ADMIN_ENTRY_PERMISSIONS.some((p) => permissions.includes(p)),
    };
  }, [user, loading, login, register, logout, logoutEverywhere]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth muss innerhalb von AuthProvider verwendet werden');
  }
  return context;
}
