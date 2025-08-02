import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import apiClient from '../services/api';

// Helper to parse JWT
const parseJwt = (token) => {
  try {
    if (!token || typeof token !== 'string') {
      console.error('[AuthContext] Invalid token provided to parseJwt:', token);
      return null;
    }
    
    const parts = token.split('.');
    if (parts.length !== 3) {
      console.error('[AuthContext] JWT token does not have 3 parts:', parts.length);
      return null;
    }
    
    const payload = parts[1];
    // Base64 URL decode with proper UTF-8 handling
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const decoded = atob(base64);
    // Convert to UTF-8
    const utf8String = decodeURIComponent(escape(decoded));
    const parsed = JSON.parse(utf8String);
    
    console.log('[AuthContext] Successfully parsed JWT payload:', parsed);
    return parsed;
  } catch (e) {
    console.error('[AuthContext] Error parsing JWT:', e);
    return null;
  }
};

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const isAuthenticated = !!user;

  const logout = useCallback(async () => {
    try {
      // Prevent logout loop if already logged out
      if (!sessionStorage.getItem('accessToken')) return;
      
      console.log('[AuthContext] Logging out...');
      await apiClient.post('/api/auth/logout');
    } catch (error) {
      console.error('Logout API call failed:', error);
    } finally {
      setUser(null);
      sessionStorage.removeItem('accessToken');
      window.location.href = '/login';
    }
  }, []);

  useEffect(() => {
    setInitialLoading(true);
    try {
      const token = sessionStorage.getItem('accessToken');
      if (token) {
        const decodedJwt = parseJwt(token);
        if (decodedJwt && decodedJwt.exp * 1000 > Date.now()) {
          const userInfo = {
            userId: decodedJwt.userId,
            email: decodedJwt.sub,
            name: decodedJwt.name,
            role: decodedJwt.role || 'USER'
          };
          setUser(userInfo);
        } else {
          sessionStorage.removeItem('accessToken');
          setUser(null);
        }
      }
    } catch (error) {
      console.error("Failed to initialize auth state", error);
      setUser(null);
      sessionStorage.removeItem('accessToken');
    } finally {
      setInitialLoading(false);
    }
  }, []);

  useEffect(() => {
    let ws = null;

    if (isAuthenticated && user && (user.role === 'ADMIN' || user.role === 'SUPER_ADMIN')) {
      const token = sessionStorage.getItem('accessToken');
      
      // Dynamically construct WebSocket URL based on browser location
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = window.location.host;
      const wsUrl = `${protocol}//${host}/api/auth/ws?token=${token}`;
      
      console.log('[WebSocket] Connecting to', wsUrl);

      ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        console.log('[WebSocket] Connection established.');
      };

      ws.onmessage = (event) => {
        console.log('[WebSocket] Message received:', event.data);
        try {
          const message = JSON.parse(event.data);
          if (message.type === 'FORCED_LOGOUT_NOTICE') {
            const details = message.newLoginDetails;
            alert(`현재 세션이 비활성화되었습니다.\n사유: 새로운 기기에서 로그인\n\n- 접속 IP: ${details.ipAddress}\n- 접속 환경: ${details.userAgent}\n\n본인 활동이 아닌 경우 즉시 비밀번호를 변경하세요.`);
            logout();
          }
        } catch (e) {
          console.error('[WebSocket] Error parsing message:', e);
        }
      };

      ws.onerror = (error) => {
        console.error('[WebSocket] Error:', error);
      };

      ws.onclose = (event) => {
        console.log('[WebSocket] Connection closed:', event.reason);
      };
    }

    return () => {
      if (ws) {
        console.log('[WebSocket] Closing connection.');
        ws.close();
      }
    };
  }, [isAuthenticated, user, logout]);

  const register = async (name, email, password) => {
    setLoading(true);
    try {
      const response = await apiClient.post('/api/auth/register', { name, email, password });
      return { success: true, message: response.data.message };
    } catch (error) {
      console.error('Register error:', error.response);
      const errorMessage = error.response?.data?.message || 
                          (error.response?.status === 409 ? '이미 가입된 이메일 주소입니다.' : 
                           error.message);
      return { success: false, message: errorMessage };
    } finally {
      setLoading(false);
    }
  };

  const login = async (email, password) => {
    setLoading(true);
    try {
      const response = await apiClient.post('/api/auth/login', { email, password });
      const { accessToken } = response.data.data;
      
      sessionStorage.setItem('accessToken', accessToken);
      
      const decodedJwt = parseJwt(accessToken);
      const userInfo = {
        userId: decodedJwt.userId,
        email: decodedJwt.sub,
        name: decodedJwt.name,
        role: decodedJwt.role || 'USER'
      };
      setUser(userInfo);

      return { success: true, message: response.data.message };
    } catch (error) {
      throw error;
    } finally {
      setLoading(false);
    }
  };

  const value = {
    user,
    loading,
    initialLoading,
    register,
    login,
    logout,
    isAuthenticated,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

