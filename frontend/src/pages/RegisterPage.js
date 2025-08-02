import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const RegisterPage = () => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  
  const { register, loading, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  // Redirect to dashboard if already logged in
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');
    const result = await register(name, email, password);
    if (result.success) {
      setMessage('회원가입 성공! 로그인 페이지로 이동합니다...');
      // Redirect to login page after successful registration
      setTimeout(() => {
        navigate('/login', { replace: true });
      }, 2000);
    } else {
      setMessage(`오류: ${result.message}`);
    }
  };

  return (
    <div className="auth-layout">
      <div className="auth-container">
        <div className="card">
          <div className="auth-header">
            <h1 className="auth-title">🥗 회원가입</h1>
            <p className="auth-subtitle">새로운 계정을 만들어 건강한 습관을 시작하세요.</p>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="name">이름</label>
              <div className="input-wrapper">
                <input type="text" id="name" value={name} onChange={(e) => setName(e.target.value)} placeholder="홍길동" required disabled={loading} />
              </div>
            </div>
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
              {loading ? '계정 생성 중...' : '회원가입'}
            </button>
          </form>

          {message && <p style={{ textAlign: 'center', marginTop: '1rem', wordBreak: 'break-all' }}>{message}</p>}
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;