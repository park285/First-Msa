import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import apiClient from '../services/api';
import './AdminDashboard.css';

// Tooltip Component
const Tooltip = ({ text, children }) => (
    <div className="tooltip-container">
        {children}
        <span className="tooltip-text">{text}</span>
    </div>
);

// Session Modal Component
const SessionModal = ({ user, onClose, onInvalidate }) => {
    const [sessions, setSessions] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchSessions = async () => {
            try {
                const response = await apiClient.get(`/api/auth/admin/users/${user.id}/sessions`);
                if (response.data.success) {
                    setSessions(response.data.data);
                }
            } catch (error) {
                console.error("Failed to fetch user sessions:", error);
                alert("세션 정보를 불러오는 데 실패했습니다.");
            } finally {
                setLoading(false);
            }
        };
        fetchSessions();
    }, [user.id]);

    return (
        <div className="modal-backdrop" onClick={onClose}>
            <div className="modal-content" onClick={e => e.stopPropagation()}>
                <h2>{user.name}님의 활성 세션</h2>
                {loading ? <p>세션 정보를 불러오는 중...</p> : (
                    sessions.length > 0 ? (
                        <ul className="session-list">
                            {sessions.map(session => (
                                <li key={session.tokenHash}>
                                    <div className="session-info">
                                        <strong>토큰 해시:</strong>
                                        <input type="text" readOnly value={session.tokenHash} />
                                        <span>만료: {new Date(session.expiryDate).toLocaleString()}</span>
                                    </div>
                                    <button 
                                        className="btn-warning"
                                        onClick={() => onInvalidate(session.tokenHash)}
                                    >
                                        이 세션 무효화
                                    </button>
                                </li>
                            ))}
                        </ul>
                    ) : <p>활성 세션이 없습니다.</p>
                )}
                <button className="btn-secondary" onClick={onClose}>닫기</button>
            </div>
        </div>
    );
};


const AdminDashboard = () => {
    const { user } = useAuth();
    const [users, setUsers] = useState([]);
    const [sessionStats, setSessionStats] = useState(null);
    const [forceLogoutHistory, setForceLogoutHistory] = useState([]);
    const [tokenToBlacklist, setTokenToBlacklist] = useState('');
    const [selectedUser, setSelectedUser] = useState(null); // For modal
    const [loading, setLoading] = useState({
        users: false,
        stats: false,
        history: false,
        logout: null, // userId
        blacklist: false,
    });

    const fetchUsers = useCallback(async () => {
        setLoading(prev => ({ ...prev, users: true }));
        try {
            const response = await apiClient.get('/api/auth/admin/users');
            if (response.data.success) {
                setUsers(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch users:', error);
            alert('사용자 목록을 불러오는 데 실패했습니다.');
        } finally {
            setLoading(prev => ({ ...prev, users: false }));
        }
    }, []);

    const loadSessionStats = useCallback(async () => {
        setLoading(prev => ({ ...prev, stats: true }));
        try {
            const response = await apiClient.get('/api/auth/admin/session-stats');
            if (response.data.success) {
                setSessionStats(response.data.data);
            }
        } catch (error) {
            console.error('Session stats load failed:', error);
        } finally {
            setLoading(prev => ({ ...prev, stats: false }));
        }
    }, []);

    const loadForceLogoutHistory = useCallback(async () => {
        setLoading(prev => ({ ...prev, history: true }));
        try {
            const response = await apiClient.get('/api/auth/admin/force-logout-history');
            if (response.data.success) {
                setForceLogoutHistory(response.data.data);
            }
        } catch (error) {
            console.error('Force logout history load failed:', error);
            alert('강제 로그아웃 히스토리를 불러오는 데 실패했습니다.');
        } finally {
            setLoading(prev => ({ ...prev, history: false }));
        }
    }, []);

    useEffect(() => {
        const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
        if (user && !isAdmin) {
            window.location.href = '/';
        } else if (isAdmin) {
            fetchUsers();
            loadSessionStats();
            loadForceLogoutHistory();
        }
    }, [user, fetchUsers, loadSessionStats, loadForceLogoutHistory]);

    const forceLogoutUser = async (userId, userName) => {
        if (!window.confirm(`사용자 '${userName}'(ID: ${userId})님을 강제 로그아웃하시겠습니까? 모든 기기에서 즉시 로그아웃됩니다.`)) {
            return;
        }
        setLoading(prev => ({ ...prev, logout: userId }));
        try {
            const response = await apiClient.post(`/api/auth/admin/force-logout/${userId}`);
            if (response.data.success) {
                alert(`사용자(ID: ${userId})가 강제 로그아웃되었습니다.`);
                loadSessionStats();
                loadForceLogoutHistory(); // 히스토리도 갱신
            }
        } catch (error) {
            console.error('Force logout failed:', error);
            alert('강제 로그아웃에 실패했습니다: ' + (error.response?.data?.message || error.message));
        } finally {
            setLoading(prev => ({ ...prev, logout: null }));
        }
    };

    const blacklistToken = async (token) => {
        if (!token.trim()) {
            alert('블랙리스트에 추가할 토큰(또는 해시)을 입력해주세요.');
            return;
        }
        setLoading(prev => ({ ...prev, blacklist: true }));
        try {
            const response = await apiClient.post('/api/auth/admin/blacklist-token', { token });
            if (response.data.success) {
                alert('토큰이 성공적으로 무효화되었습니다.');
                setTokenToBlacklist('');
                loadSessionStats();
                if (selectedUser) {
                    setSelectedUser(null); // Close modal on success
                }
            }
        } catch (error) {
            console.error('Token blacklist failed:', error);
            alert('토큰 무효화에 실패했습니다: ' + (error.response?.data?.message || error.message));
        } finally {
            setLoading(prev => ({ ...prev, blacklist: false }));
        }
    };

    if (!user) {
        return <div>로딩 중...</div>;
    }
    if (user.role !== 'ADMIN' && user.role !== 'SUPER_ADMIN') {
        return <div>관리자 권한이 필요합니다.</div>;
    }

    return (
        <div className="admin-dashboard">
            {selectedUser && 
                <SessionModal 
                    user={selectedUser} 
                    onClose={() => setSelectedUser(null)}
                    onInvalidate={(tokenHash) => blacklistToken(tokenHash)}
                />
            }

            <h1>🛡️ 관리자 대시보드</h1>
            
            <div className="admin-grid">
                <div className="grid-span-2">
                    <div className="user-management-section">
                        <div className="section-header">
                            <h2>
                                사용자 관리 ({users.length}명)
                                <Tooltip text="전체 사용자 목록입니다. '세션 보기'로 특정 사용자의 활성 세션을 확인하고 무효화하거나, '강제 로그아웃'으로 모든 세션을 즉시 종료시킬 수 있습니다.">
                                    <span className="info-icon">?</span>
                                </Tooltip>
                            </h2>
                            <button onClick={fetchUsers} disabled={loading.users} className="btn-secondary">
                                {loading.users ? '...' : '🔄'}
                            </button>
                        </div>
                        <div className="user-table-container">
                            <table className="user-table">
                                <thead>
                                    <tr>
                                        <th>ID</th>
                                        <th>이름</th>
                                        <th>이메일</th>
                                        <th>가입일</th>
                                        <th>작업</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {users.map(u => (
                                        <tr key={u.id}>
                                            <td>{u.id}</td>
                                            <td>{u.name}</td>
                                            <td>{u.email}</td>
                                            <td>{new Date(u.createdAt).toLocaleDateString()}</td>
                                            <td className="action-buttons">
                                                <button className="btn-secondary" onClick={() => setSelectedUser(u)}>세션 보기</button>
                                                <button 
                                                    onClick={() => forceLogoutUser(u.id, u.name)}
                                                    className="btn-danger"
                                                    disabled={loading.logout === u.id}
                                                >
                                                    {loading.logout === u.id ? '...' : '강제 로그아웃'}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>

                    {/* 강제 로그아웃 히스토리 섹션 */}
                    <div className="force-logout-history-section">
                        <div className="section-header">
                            <h2>
                                강제 로그아웃 히스토리
                                <Tooltip text="관리자가 수행한 강제 로그아웃 기록입니다. 언제, 누가, 어떤 사용자를 강제 로그아웃시켰는지 확인할 수 있습니다.">
                                    <span className="info-icon">?</span>
                                </Tooltip>
                            </h2>
                            <button onClick={loadForceLogoutHistory} disabled={loading.history} className="btn-secondary">
                                {loading.history ? '...' : '🔄'}
                            </button>
                        </div>
                        
                        {loading.history ? (
                            <p>히스토리를 불러오는 중...</p>
                        ) : forceLogoutHistory.length > 0 ? (
                            <div className="history-table-container">
                                <table className="history-table">
                                    <thead>
                                        <tr>
                                            <th>대상 사용자</th>
                                            <th>관리자</th>
                                            <th>강제 로그아웃 시간</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {forceLogoutHistory.map((entry, index) => (
                                            <tr key={`${entry.userId}-${entry.timestamp}-${index}`}>
                                                <td>
                                                    <div className="user-info">
                                                        <strong>{entry.userName || '알 수 없음'}</strong>
                                                        <br />
                                                        <small>{entry.userEmail || '이메일 없음'}</small>
                                                        <br />
                                                        <small className="user-id">ID: {entry.userId}</small>
                                                    </div>
                                                </td>
                                                <td>
                                                    <div className="admin-info">
                                                        {entry.adminEmail && entry.adminEmail !== 'system' ? (
                                                            <>
                                                                <strong>{entry.adminEmail}</strong>
                                                                {entry.adminUserId && (
                                                                    <>
                                                                        <br />
                                                                        <small className="admin-id">ID: {entry.adminUserId}</small>
                                                                    </>
                                                                )}
                                                            </>
                                                        ) : (
                                                            <span className="system-action">시스템</span>
                                                        )}
                                                    </div>
                                                </td>
                                                <td>
                                                    {entry.logoutTime ? 
                                                        new Date(entry.logoutTime).toLocaleString('ko-KR', {
                                                            year: 'numeric',
                                                            month: '2-digit',
                                                            day: '2-digit',
                                                            hour: '2-digit',
                                                            minute: '2-digit',
                                                            second: '2-digit'
                                                        }) : 
                                                        '시간 정보 없음'
                                                    }
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <p>강제 로그아웃 기록이 없습니다.</p>
                        )}
                    </div>
                </div>

                <div>
                    <div className="token-management-section">
                        <div className="section-header">
                            <h2>
                                토큰 관리
                                <Tooltip text="유출이 의심되는 특정 액세스 토큰(JWT)이나 리프레시 토큰의 해시를 직접 입력하여 무효화합니다. 일반적인 경우 사용자 목록의 '강제 로그아웃' 기능 사용을 권장합니다.">
                                    <span className="info-icon">?</span>
                                </Tooltip>
                            </h2>
                        </div>
                        <div className="blacklist-token">
                            <h3>토큰 직접 무효화</h3>
                            <div className="form-group">
                                <textarea
                                    value={tokenToBlacklist}
                                    onChange={(e) => setTokenToBlacklist(e.target.value)}
                                    placeholder="무효화할 액세스 토큰(JWT) 또는 리프레시 토큰 해시를 여기에 붙여넣으세요."
                                    rows={4}
                                    disabled={loading.blacklist}
                                />
                                <button 
                                    onClick={() => blacklistToken(tokenToBlacklist)}
                                    className="btn-warning"
                                    disabled={loading.blacklist}
                                >
                                    {loading.blacklist ? '처리 중...' : '토큰 무효화'}
                                </button>
                            </div>
                        </div>
                    </div>

                    <div className="session-stats-section">
                        <div className="section-header">
                            <h2>
                                세션 통계
                                <Tooltip text="현재 시스템의 활성 세션 및 보안 상태에 대한 개요입니다. '활성 세션'은 유효한 리프레시 토큰의 수를 의미합니다.">
                                    <span className="info-icon">?</span>
                                </Tooltip>
                            </h2>
                            <button onClick={loadSessionStats} disabled={loading.stats} className="btn-secondary">
                                {loading.stats ? '...' : '🔄'}
                            </button>
                        </div>
                        
                        {sessionStats ? (
                            <div className="stats-display">
                                <div className="stat-item">
                                    <strong>활성 세션 (추정):</strong> {sessionStats.activeSessionsApprox}
                                </div>
                                <div className="stat-item">
                                    <strong>무효화된 토큰:</strong> {sessionStats.blacklistedTokens}
                                </div>
                                <div className="stat-item">
                                    <strong>마지막 업데이트:</strong> {new Date(sessionStats.timestamp).toLocaleString()}
                                </div>
                                <small>{sessionStats.note}</small>
                            </div>
                        ) : <p>통계 정보를 불러오는 중...</p>}
                    </div>
                </div>
            </div>

        </div>
    );
};

export default AdminDashboard;