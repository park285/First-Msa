import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';

const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');

  const { login, loading } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');
    try {
      await login(email, password);
      // Redirection is handled by the wrapper component in App.js
      setMessage('로그인 성공! 잠시 후 대시보드로 이동합니다...');
    } catch (error) {
      const errorText = error.response?.data?.error || error.message;
      setMessage(`오류: ${errorText}`);
    }
  };

  return (
    <div className="auth-layout">
      <div className="auth-container">
        <div className="card">
          <div className="auth-header">
            <h1 className="auth-title">🥗 로그인</h1>
            <p className="auth-subtitle">다이어트 다이어리에 다시 오신 것을 환영합니다.</p>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="email">이메일 주소</label>
              <div className="input-wrapper">
                <input type="email" id="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" required disabled={loading} />
              </div>
            </div>
            <div className="form-group">
              <label htmlFor="password">비밀번호</label>
              <div className="input-wrapper">
                <input type="password" id="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" required disabled={loading} />
              </div>
            </div>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? '로그인 중...' : '로그인'}
            </button>
          </form>
          
          {message && <p style={{ textAlign: 'center', marginTop: '1rem', wordBreak: 'break-all' }}>{message}</p>}
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
