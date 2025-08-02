package com.dietdiary.auth.service;

import com.dietdiary.auth.dto.LoginRequest;
import com.dietdiary.auth.dto.RegisterRequest;
import com.dietdiary.auth.dto.UserResponse;
import com.dietdiary.auth.entity.User;
import com.dietdiary.auth.entity.UserRole;
import com.dietdiary.auth.repository.UserRepository;
import com.dietdiary.auth.security.JwtUtil;
import com.dietdiary.auth.websocket.service.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                       RefreshTokenService refreshTokenService, TokenBlacklistService tokenBlacklistService,
                       WebSocketSessionManager sessionManager,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    public UserResponse register(RegisterRequest request) {
        logger.info("[auth-service] Registering user: {}", request.getEmail());
        try {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already exists.");
            }
            String hashedPassword = passwordEncoder.encode(request.getPassword());
            User user = new User(request.getEmail(), hashedPassword, request.getName());
            User savedUser = userRepository.save(user);
            logger.info("[auth-service] User registered successfully: {}", savedUser.getEmail());
            return new UserResponse(savedUser);
        } catch (DataIntegrityViolationException e) {
            logger.error("[auth-service] Registration error - duplicate email: {}", request.getEmail());
            throw new RuntimeException("Email already exists.");
        }
    }

    public Map<String, Object> login(LoginRequest request, String ipAddress, String userAgent) {
        logger.info("[auth-service] Attempting to login user: {}", request.getEmail());
        logger.info("[auth-service] Login details - IP: '{}', UserAgent: '{}'", ipAddress, userAgent);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            User user = (User) authentication.getPrincipal();
            
            // Admin/Super Admin 중복 로그인 처리
            if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
                logger.warn("[auth-service] Admin user {} is logging in. Invalidating all previous sessions.", user.getEmail());

                // 1. 웹소켓으로 기존 세션에 강제 로그아웃 알림 전송
                try {
                    Map<String, Object> newLoginDetails = new HashMap<>();
                    newLoginDetails.put("ipAddress", ipAddress);
                    newLoginDetails.put("userAgent", userAgent);
                    newLoginDetails.put("message", "새로운 기기에서의 로그인으로 인해 현재 세션이 비활성화되었습니다.");

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("type", "FORCED_LOGOUT_NOTICE");
                    payload.put("timestamp", Instant.now().toString());
                    payload.put("newLoginDetails", newLoginDetails);

                    String payloadJson = objectMapper.writeValueAsString(payload);
                    sessionManager.sendToUser(user.getEmail(), payloadJson);
                } catch (Exception e) {
                    logger.error("[auth-service] Failed to send WebSocket notification for user {}: {}", user.getId(), e.getMessage());
                }

                // 2. 기존 액세스 토큰들을 무효화하기 위해 타임스탬프 기록
                tokenBlacklistService.blacklistAllUserTokens(user.getId(), new Date());
                logger.info("[auth-service] Invalidated all previous tokens for admin user: {}", user.getEmail());
            }
            
            logger.info("[auth-service] User object - ID: {}, Email: {}, Name: '{}'", 
                       user.getId(), user.getEmail(), user.getName());
            
            String accessToken = jwtUtil.generateAccessToken(user);
            String refreshToken = refreshTokenService.createRefreshToken(user.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("user", new UserResponse(user));

            logger.info("[auth-service] User logged in successfully: {}", user.getEmail());
            return response;
        } catch (Exception e) {
            logger.error("[auth-service] Login failed for user: {}. Reason: {}", request.getEmail(), e.getMessage());
            throw new RuntimeException("Invalid email or password.");
        }
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        logger.info("[auth-service] Fetching all users from the database");
        return userRepository.findAll().stream()
                .map(UserResponse::new)
                .collect(Collectors.toList());
    }
}
