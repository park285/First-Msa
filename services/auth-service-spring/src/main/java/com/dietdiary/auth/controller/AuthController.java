package com.dietdiary.auth.controller;

import com.dietdiary.auth.dto.*;
import com.dietdiary.auth.entity.User;
import com.dietdiary.auth.repository.UserRepository;
import com.dietdiary.auth.security.JwtUtil;
import com.dietdiary.auth.service.AuthService;
import com.dietdiary.auth.service.RefreshTokenService;
import com.dietdiary.auth.service.TokenBlacklistService;
import com.dietdiary.auth.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService,
                         CookieUtil cookieUtil, JwtUtil jwtUtil, UserRepository userRepository,
                         TokenBlacklistService tokenBlacklistService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.cookieUtil = cookieUtil;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            BindingResult bindingResult) {

        logger.info("[auth-service] Received /register request");

        if (bindingResult.hasErrors()) {
            String error = bindingResult.getFieldErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(ApiResponse.error(error));
        }

        try {
            UserResponse user = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("회원가입이 성공적으로 완료되었습니다.", user));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("이미 존재하는 이메일")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("이미 존재하는 이메일입니다."));
            }
            logger.error("Registration error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("회원가입 처리 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            BindingResult bindingResult,
            HttpServletRequest httpRequest) {

        logger.info("[auth-service] Received /login request");

        if (bindingResult.hasErrors()) {
            String error = bindingResult.getFieldErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(ApiResponse.error(error));
        }

        try {
            // IP 주소 및 User-Agent 추출
            String ipAddress = getClientIP(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            
            logger.info("[auth-service] Login IP extraction - X-Forwarded-For: {}, X-Real-IP: {}, Remote Addr: {}, Final IP: {}", 
                       httpRequest.getHeader("X-Forwarded-For"), httpRequest.getHeader("X-Real-IP"), 
                       httpRequest.getRemoteAddr(), ipAddress);

            // AuthService에 추가 정보 전달
            Map<String, Object> authResponse = authService.login(request, ipAddress, userAgent);
            String accessToken = (String) authResponse.get("accessToken");
            String refreshToken = (String) authResponse.get("refreshToken");
            UserResponse userResponse = (UserResponse) authResponse.get("user");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(refreshToken).toString());

            LoginResponse loginResponse = new LoginResponse(accessToken, userResponse);

            return ResponseEntity.ok().headers(headers)
                    .body(ApiResponse.success("로그인 성공", loginResponse));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("이메일 또는 비밀번호가 올바르지 않습니다")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("이메일 또는 비밀번호가 올바르지 않습니다."));
            }
            logger.error("Login error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그인 처리 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request) {
        logger.info("[auth-service] Received /refresh request");

        String hashedTokenFromCookie = cookieUtil.getRefreshTokenFromCookie(request);
        logger.debug("[auth-service] Refresh token validation attempt");

        if (hashedTokenFromCookie == null) {
            logger.warn("[auth-service] Refresh token is missing");
            return ResponseEntity.badRequest().body(ApiResponse.error("Refresh token is missing"));
        }

        try {
            logger.info("[auth-service] Processing refresh token");

            Map<String, Object> validTokenData = refreshTokenService.findValidTokenByHash(hashedTokenFromCookie, cookieUtil);

            if (validTokenData == null) {
                logger.warn("[auth-service] Invalid refresh token hash");
                return ResponseEntity.status(401).body(ApiResponse.error("유효하지 않은 리프레쉬 토큰입니다."));
            }

            logger.info("[auth-service] Refresh token verified successfully");

            Long userId = ((Number) validTokenData.get("userId")).longValue();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            logger.info("[auth-service] Generating new access token for user: {}", user.getEmail());
            String newAccessToken = jwtUtil.generateAccessToken(user);
            Map<String, String> response = new HashMap<>();
            response.put("accessToken", newAccessToken);
            logger.info("[auth-service] Successfully refreshed access token");

            return ResponseEntity.ok(ApiResponse.success("Access token refreshed", response));
        } catch (RuntimeException e) {
            logger.error("[auth-service] Refresh token verification failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(ApiResponse.error("토큰 갱신에 실패했습니다."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        logger.info("[auth-service] Received /logout request");
        String refreshToken = cookieUtil.getRefreshTokenFromCookie(request);
        logger.debug("[auth-service] Logout process initiated");

        if (refreshToken != null) {
            try {
                refreshTokenService.deleteByHashedToken(refreshToken);
                logger.info("[auth-service] Refresh token deleted successfully");
            } catch (Exception e) {
                logger.warn("[auth-service] Failed to delete refresh token: {}", e.getMessage());
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookieUtil.deleteRefreshTokenCookie().toString());
        logger.info("[auth-service] Logout completed successfully");

        return ResponseEntity.ok().headers(headers).body(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/admin/users")
    public ResponseEntity<?> getAllUsers() {
        logger.info("[auth-service] Admin: Get all users requested");
        try {
            List<UserResponse> users = authService.getAllUsers();
            return ResponseEntity.ok(ApiResponse.success("사용자 목록이 성공적으로 조회되었습니다.", users));
        } catch (Exception e) {
            logger.error("[auth-service] Admin: Get all users failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("사용자 목록 조회에 실패했습니다."));
        }
    }

    @GetMapping("/admin/users/{userId}/sessions")
    public ResponseEntity<?> getUserSessions(@PathVariable Long userId) {
        logger.info("[auth-service] Admin: Get sessions for user: {}", userId);
        try {
            List<Map<String, Object>> sessions = refreshTokenService.findSessionsByUserId(userId);
            return ResponseEntity.ok(ApiResponse.success("사용자 세션 정보가 성공적으로 조회되었습니다.", sessions));
        } catch (Exception e) {
            logger.error("[auth-service] Admin: Get user sessions failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("세션 정보 조회에 실패했습니다."));
        }
    }

    @PostMapping("/admin/force-logout/{userId}")
    public ResponseEntity<?> forceLogoutUser(@PathVariable Long userId, @AuthenticationPrincipal UserDetails adminDetails) {
        logger.info("[auth-service] Admin: Force logout requested for user: {}", userId);
        try {
            String adminEmail = adminDetails.getUsername();
            User admin = userRepository.findByEmail(adminEmail)
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));
            Long adminUserId = admin.getId();
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("해당 사용자를 찾을 수 없습니다."));
            
            tokenBlacklistService.blacklistAllUserTokens(userId, new Date(), adminUserId, adminEmail);
            refreshTokenService.deleteByUserId(userId);
            
            logger.info("[auth-service] Admin: Force logout completed for user: {} ({}) by admin: {} ({})", 
                       user.getEmail(), userId, adminEmail, adminUserId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("email", user.getEmail());
            response.put("forceLogoutTime", new Date());
            response.put("adminUserId", adminUserId);
            response.put("adminEmail", adminEmail);
            return ResponseEntity.ok(ApiResponse.success("사용자 강제 로그아웃이 성공적으로 처리되었습니다.", response));
        } catch (RuntimeException e) {
            logger.error("[auth-service] Admin: Force logout failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("강제 로그아웃에 실패했습니다: " + e.getMessage()));
        }
    }

    @PostMapping("/admin/blacklist-token")
    public ResponseEntity<?> blacklistToken(@RequestBody Map<String, String> requestBody) {
        logger.info("[auth-service] Admin: Blacklist token requested");
        String tokenToBlacklist = requestBody.get("token");

        if (tokenToBlacklist == null || tokenToBlacklist.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("무효화할 토큰을 제공해야 합니다."));
        }

        try {
            if (refreshTokenService.deleteByHashedToken(tokenToBlacklist)) {
                logger.info("[auth-service] Admin: Refresh token with hash blacklisted by deletion: {}", tokenToBlacklist);
                return ResponseEntity.ok(ApiResponse.success("리프레시 토큰이 성공적으로 무효화되었습니다.", Map.of("tokenHash", tokenToBlacklist)));
            }

            com.nimbusds.jwt.JWTClaimsSet tokenClaims;
            try {
                tokenClaims = jwtUtil.getClaimsFromToken(tokenToBlacklist);
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(ApiResponse.error("유효하지 않은 토큰 형식입니다."));
            }

            String jwtId = tokenClaims.getJWTID();
            Date expiration = tokenClaims.getExpirationTime();

            if (jwtId == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("유효하지 않은 토큰입니다 (JWT ID 없음)."));
            }

            tokenBlacklistService.blacklistToken(jwtId, expiration);
            logger.info("[auth-service] Admin: Access token blacklisted - JWT ID: {}", jwtId);
            Map<String, Object> response = new HashMap<>();
            response.put("jwtId", jwtId);
            response.put("blacklistedAt", new Date());
            return ResponseEntity.ok(ApiResponse.success("액세스 토큰이 성공적으로 무효화되었습니다.", response));

        } catch (Exception e) {
            logger.error("[auth-service] Admin: Token blacklist failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("토큰 무효화에 실패했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/session-stats")
    public ResponseEntity<?> getSessionStats() {
        logger.info("[auth-service] Admin: Session stats requested");
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("timestamp", new Date());
            
            long activeTokens = refreshTokenService.countActiveRefreshTokens();
            stats.put("activeSessionsApprox", activeTokens);
            
            long blacklistedTokens = tokenBlacklistService.countBlacklistedTokens();
            stats.put("blacklistedTokens", blacklistedTokens);
            
            stats.put("note", "활성 세션은 현재 유효한 리프레시 토큰의 수를 기반으로 추정됩니다.");
            return ResponseEntity.ok(ApiResponse.success("세션 통계가 성공적으로 조회되었습니다.", stats));
        } catch (Exception e) {
            logger.error("[auth-service] Admin: Session stats failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("세션 통계 조회에 실패했습니다."));
        }
    }

    @GetMapping("/admin/test-endpoint")
    public ResponseEntity<?> testEndpoint() {
        logger.info("[auth-service] TEST ENDPOINT CALLED!");
        return ResponseEntity.ok(ApiResponse.success("Test endpoint works", null));
    }

    @GetMapping("/admin/force-logout-history")
    public ResponseEntity<?> getForceLogoutHistory() {
        logger.info("[auth-service] Admin: Force logout history requested");
        try {
            List<Map<String, Object>> history = tokenBlacklistService.getForceLogoutHistory();
            
            // 사용자 정보 보강
            for (Map<String, Object> entry : history) {
                Long userId = (Long) entry.get("userId");
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    entry.put("userEmail", user.getEmail());
                    entry.put("userName", user.getName());
                } else {
                    entry.put("userEmail", "삭제된 사용자");
                    entry.put("userName", "알 수 없음");
                }
                
                // 타임스탬프를 Date 객체로 변환
                Object timestampObj = entry.get("timestamp");
                if (timestampObj != null) {
                    Long timestamp = timestampObj instanceof String ? 
                        Long.parseLong((String) timestampObj) : (Long) timestampObj;
                    entry.put("logoutTime", new Date(timestamp));
                }
            }
            
            logger.info("[auth-service] Admin: Force logout history retrieved - {} entries", history.size());
            return ResponseEntity.ok(ApiResponse.success("강제로그아웃 히스토리가 성공적으로 조회되었습니다.", history));
        } catch (Exception e) {
            logger.error("[auth-service] Admin: Force logout history failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("강제로그아웃 히스토리 조회에 실패했습니다."));
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyToken(HttpServletRequest request) {
        // JwtAuthenticationFilter validates the token, so reaching this means the token is valid.
        return ResponseEntity.ok(ApiResponse.success("Token is valid"));
    }

    /**
     * 프록시 환경에서 실제 클라이언트 IP 주소를 추출하는 유틸리티 메서드
     * Spring Boot의 RemoteIpValve와 함께 사용하여 정확한 IP 주소를 얻습니다.
     */
    private String getClientIP(HttpServletRequest request) {
        // Spring Boot RemoteIpValve가 설정된 경우, request.getRemoteAddr()이 실제 클라이언트 IP를 반환합니다
        String clientIP = request.getRemoteAddr();
        
        // 추가 검증을 위해 다양한 헤더 확인
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIP = request.getHeader("X-Real-IP");
        String xOriginalForwardedFor = request.getHeader("X-Original-Forwarded-For");
        
        logger.debug("[auth-service] IP Headers - RemoteAddr: {}, X-Forwarded-For: {}, X-Real-IP: {}, X-Original-Forwarded-For: {}", 
                    clientIP, xForwardedFor, xRealIP, xOriginalForwardedFor);
        
        // RemoteIpValve가 제대로 작동하면 request.getRemoteAddr()이 가장 신뢰할 수 있는 값입니다
        if (clientIP != null && !clientIP.isEmpty() && !"unknown".equalsIgnoreCase(clientIP)) {
            // 내부 프록시 IP가 아닌 경우에만 사용
            if (!isInternalIP(clientIP)) {
                return clientIP;
            }
        }
        
        // Fallback: X-Forwarded-For 헤더에서 첫 번째 IP 추출
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // X-Forwarded-For는 "client, proxy1, proxy2" 형태일 수 있으므로 첫 번째 IP만 추출
            String firstIP = xForwardedFor.split(",")[0].trim();
            if (!isInternalIP(firstIP)) {
                return firstIP;
            }
        }
        
        // Fallback: X-Real-IP 헤더 확인
        if (xRealIP != null && !xRealIP.isEmpty() && !"unknown".equalsIgnoreCase(xRealIP)) {
            if (!isInternalIP(xRealIP)) {
                return xRealIP;
            }
        }
        
        // 모든 방법이 실패하면 RemoteAddr 반환 (최소한의 정보라도 제공)
        return clientIP != null ? clientIP : "unknown";
    }
    
    /**
     * 내부 네트워크 IP인지 확인하는 헬퍼 메서드
     */
    private boolean isInternalIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        
        // 로컬호스트 및 내부 네트워크 대역 확인
        return ip.equals("127.0.0.1") || 
               ip.equals("0:0:0:0:0:0:0:1") ||
               ip.startsWith("192.168.") ||
               ip.startsWith("10.") ||
               ip.startsWith("172.16.") ||
               ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") ||
               ip.startsWith("172.19.") ||
               ip.startsWith("172.20.") ||
               ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") ||
               ip.startsWith("172.23.") ||
               ip.startsWith("172.24.") ||
               ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") ||
               ip.startsWith("172.27.") ||
               ip.startsWith("172.28.") ||
               ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") ||
               ip.startsWith("172.31.");
    }
}
