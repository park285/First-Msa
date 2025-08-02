import React from 'react';
import { BrowserRouter as Router, Routes, Route, Link, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import Dashboard from './pages/Dashboard';
import AdminDashboard from './pages/AdminDashboard';

// Handles protected routes
const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, initialLoading } = useAuth();
  
  if (initialLoading) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        fontSize: '1.2rem'
      }}>
        Loading...
      </div>
    );
  }
  
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  
  return children;
};

function App() {
  return (
    <AuthProvider>
      <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes>
          <Route path="/login" element={<LoginPageWithRedirect />} />
          <Route path="/register" element={<RegisterPageWithRedirect />} />
          <Route 
            path="/" 
            element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/admin" 
            element={
              <ProtectedRoute>
                <AdminDashboard />
              </ProtectedRoute>
            } 
          />
        </Routes>
      </Router>
    </AuthProvider>
  );
}

// Wrappers for auth pages to handle redirection
const LoginPageWithRedirect = () => {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }
  return (
    <>
      <LoginPage />
      <p style={{textAlign: 'center', marginTop: '1rem'}}>
        Don't have an account? <Link to="/register">Register</Link>
      </p>
    </>
  );
};

const RegisterPageWithRedirect = () => {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }
  return (
    <>
      <RegisterPage />
      <p style={{textAlign: 'center', marginTop: '1rem'}}>
        Already have an account? <Link to="/login">Login</Link>
      </p>
    </>
  );
};

export default App;
