package com.dietdiary.auth.websocket.interceptor;

import com.dietdiary.auth.security.JwtUtil;
import com.dietdiary.auth.service.TokenBlacklistService;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AuthHandshakeInterceptor.class);
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthHandshakeInterceptor(JwtUtil jwtUtil, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String query = request.getURI().getQuery();
        logger.debug("WebSocket handshake request query: {}", query);

        if (query != null && query.startsWith("token=")) {
            String token = query.substring("token=".length());
            try {
                if (tokenBlacklistService.isTokenBlacklisted(token)) {
                    logger.warn("WebSocket connection attempt with blacklisted token.");
                    return false;
                }

                JWTClaimsSet claims = jwtUtil.getClaimsFromToken(token);
                String role = claims.getStringClaim("role");
                String userEmail = claims.getSubject();

                if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
                    attributes.put("userEmail", userEmail);
                    attributes.put("role", role);
                    logger.info("Admin user {} authenticated for WebSocket.", userEmail);
                    return true;
                } else {
                    logger.warn("Non-admin user {} attempted to connect to admin WebSocket.", userEmail);
                    return false;
                }
            } catch (Exception e) {
                logger.error("Invalid token for WebSocket connection.", e);
                return false;
            }
        }
        logger.warn("WebSocket connection attempt without token.");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // Do nothing
    }
}