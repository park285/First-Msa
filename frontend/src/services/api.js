import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
  withCredentials: true, // Required for sending cookies
});

// Request interceptor to add the JWT token to headers
apiClient.interceptors.request.use(
  (config) => {
    // Do not add Authorization header for login or refresh token requests
    if (config.url?.includes('/login') || config.url?.includes('/refresh')) {
      delete config.headers['Authorization'];
    } else {
      const token = sessionStorage.getItem('accessToken');
      if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
      }
    }
    console.log(`[API] ${config.method?.toUpperCase()} ${config.url}`);
    return config;
  },
  (error) => {
    console.error('[API] Request error:', error);
    return Promise.reject(error);
  }
);

// Response interceptor for handling token refresh
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => {
    console.log(`[API] ${response.status} ${response.config.url}`);
    return response;
  },
  async (error) => {
    const originalRequest = error.config;
    
    // Do not attempt to refresh for login requests
    if (originalRequest.url?.includes('/login')) {
      console.error('[API] Login failed:', error.response?.status, error.response?.data?.message);
      return Promise.reject(error);
    }
    
    // Handle 403 Forbidden as a forced logout
    if (error.response?.status === 403) {
      console.error('[API] 403 Forbidden - Force logout detected');
      sessionStorage.removeItem('accessToken');
      window.location.href = '/login';
      return Promise.reject(error);
    }
    
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise(function(resolve, reject) {
          failedQueue.push({resolve, reject});
        }).then(token => {
          originalRequest.headers['Authorization'] = 'Bearer ' + token;
          return apiClient(originalRequest);
        }).catch(err => {
          return Promise.reject(err);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        console.log('[API] Access token expired. Attempting to refresh...');
        // Ensure no auth header is sent for the refresh request itself
        const { data } = await apiClient.post('/api/auth/refresh', {}, {
          headers: { Authorization: undefined },
          withCredentials: true
        });
        const newAccessToken = data.data.accessToken;
        
        console.log('[API] Token refreshed successfully.');
        sessionStorage.setItem('accessToken', newAccessToken);
        
        apiClient.defaults.headers.common['Authorization'] = 'Bearer ' + newAccessToken;
        originalRequest.headers['Authorization'] = 'Bearer ' + newAccessToken;
        
        processQueue(null, newAccessToken);
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        console.error('[API] Failed to refresh token. Logging out.', refreshError);
        sessionStorage.removeItem('accessToken');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }
    
    return Promise.reject(error);
  }
);

export default apiClient;
