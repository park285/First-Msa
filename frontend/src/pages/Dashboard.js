import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import apiClient from '../services/api';
import AnalysisDisplay from '../components/AnalysisDisplay';
import Trends from '../components/Trends';

const Dashboard = () => {
  const { user, logout, initialLoading } = useAuth();
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);
  const [diaryText, setDiaryText] = useState('');
  const [analysisResult, setAnalysisResult] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setDiaryText('');
    setAnalysisResult('');
    setError('');
  }, [selectedDate]);


  const handleSave = async () => {
    if (!diaryText.trim()) {
      setError('일기 내용을 입력해주세요.');
      return;
    }
    
    setLoading(true);
    setError('');

    try {
      const response = await apiClient.post('/api/diary/freestyle', {
        date: selectedDate,
        text: diaryText,
      });
      
      alert('일기가 성공적으로 저장되었습니다!');

    } catch (err) {
      const errorMessage = err.response?.data?.error || '일기 저장에 실패했습니다. 다시 시도해주세요.';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleAnalysis = async () => {
    if (!diaryText.trim()) {
      setError('일기 내용을 입력해주세요.');
      return;
    }
    
    setLoading(true);
    setError('');
    setAnalysisResult('');

    try {
      const response = await apiClient.post('/api/analysis/freestyle', {
        date: selectedDate,
        text: diaryText,
      });
      
      setAnalysisResult(response.data.analysis);

    } catch (err) {
      const errorMessage = err.response?.data?.error || 'AI 분석에 실패했습니다. 다시 시도해주세요.';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  // Show loading indicator if initialLoading or no user
  if (initialLoading) {
    return (
      <div style={{ minHeight: '100vh', backgroundColor: 'var(--background-color)', padding: '1rem', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <div>Loading...</div>
      </div>
    );
  }

  if (!user) {
    return (
      <div style={{ minHeight: '100vh', backgroundColor: 'var(--background-color)', padding: '1rem', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <div>사용자 정보를 불러올 수 없습니다.</div>
      </div>
    );
  }

  return (
    <div style={{ minHeight: '100vh', backgroundColor: 'var(--background-color)', padding: '1rem' }}>
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        {/* Header */}
        <div style={{
          backgroundColor: 'var(--card-background)',
          borderRadius: '12px',
          padding: '1.5rem',
          marginBottom: '1.5rem',
          boxShadow: 'var(--shadow)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem'
        }}>
          <div>
            <h1 style={{ margin: 0, fontSize: '1.5rem', fontWeight: '700', color: 'var(--text-primary)' }}>
              대시보드
            </h1>
            <p style={{ margin: '0.25rem 0 0 0', color: 'var(--text-secondary)' }}>
              {user.name || user.email || '사용자'}님, 다시 오신 것을 환영합니다! 👋
            </p>
          </div>
          
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            {/* Admin page link */}
            {user && (user.role === 'ADMIN' || user.role === 'SUPER_ADMIN') && (
              <Link to="/admin" className="btn btn-danger btn-sm btn-auto">
                🛡️ 관리자
              </Link>
            )}
            
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              style={{
                padding: '0.5rem',
                border: '1px solid var(--border-color)',
                borderRadius: '8px',
                fontSize: '0.875rem',
                backgroundColor: 'var(--card-background)',
                color: 'var(--text-primary)'
              }}
            />
            <button 
              onClick={() => {
                console.log('[Dashboard] Logout button clicked!');
                console.log('[Dashboard] logout function:', typeof logout);
                logout();
              }}
              className="btn btn-secondary btn-auto"
            >
              로그아웃
            </button>
          </div>
        </div>


        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
            {/* Left Column */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
                {/* Diary Input Area */}
                <div className="card">
                  <h2 style={{ marginTop: 0 }}>오늘의 일기</h2>
                  <textarea
                    value={diaryText}
                    onChange={(e) => setDiaryText(e.target.value)}
                    placeholder="오늘 하루는 어떠셨나요? 식단, 운동, 기분 등 자유롭게 기록해주세요."
                    style={{
                      width: '100%',
                      minHeight: '150px',
                      padding: '1rem',
                      border: '1px solid var(--border-color)',
                      borderRadius: '8px',
                      fontSize: '1rem',
                      resize: 'vertical',
                    }}
                    disabled={loading}
                  />
                  <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                    <button 
                      onClick={handleSave}
                      disabled={loading}
                      className="btn btn-secondary"
                    >
                      {loading ? '저장 중...' : '일기 저장'}
                    </button>
                    <button 
                      onClick={handleAnalysis}
                      disabled={loading}
                      className="btn btn-primary"
                    >
                      {loading ? '분석 중...' : 'AI 분석 요청'}
                    </button>
                  </div>
                </div>
                <Trends />
            </div>

            {/* Right Column */}
            <div>
                {error && <div className="alert alert-danger">{error}</div>}
                <AnalysisDisplay analysis={analysisResult} />
            </div>
        </div>

      </div>
    </div>
  );
};

export default Dashboard;