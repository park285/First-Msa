package com.dietdiary.auth.handler;

import com.dietdiary.auth.security.JwtUtil;
import com.dietdiary.auth.service.ActiveSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

@Component
public class AuthWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthWebSocketHandler.class);
    private final ActiveSessionRegistry sessionRegistry;
    private final JwtUtil jwtUtil;

    public AuthWebSocketHandler(ActiveSessionRegistry sessionRegistry, JwtUtil jwtUtil) {
        this.sessionRegistry = sessionRegistry;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractTokenFromSession(session);
        if (token != null && jwtUtil.validateToken(token)) {
            String userId = jwtUtil.getUserIdFromToken(token).toString();
            sessionRegistry.register(userId, session);
            logger.info("[WebSocket] Session established and registered for user: {}", userId);
            String message = String.format("{\"type\": \"CONNECTION_ESTABLISHED\", \"userId\": \"%s\"}", userId);
            session.sendMessage(new TextMessage(message));
        } else {
            logger.warn("[WebSocket] Invalid or missing token. Closing connection.");
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid or missing JWT token"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String token = extractTokenFromSession(session);
        if (token != null && jwtUtil.validateToken(token)) {
            String userId = jwtUtil.getUserIdFromToken(token).toString();
            sessionRegistry.unregister(userId);
            logger.info("[WebSocket] Session closed for user: {}. Status: {}", userId, status);
        }
    }

    private String extractTokenFromSession(WebSocketSession session) {
        // 예시: ws://localhost:8080/ws/auth-status?token=...
        String query = session.getUri().getQuery();
        if (query != null && query.startsWith("token=")) {
            return query.substring(6);
        }
        return null;
    }

    public void sendForcedLogoutNotification(String userId, String payload) {
        WebSocketSession session = sessionRegistry.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(payload));
                logger.info("[WebSocket] Sent forced logout notification to user: {}", userId);
            } catch (IOException e) {
                logger.error("[WebSocket] Failed to send message to user: {}. Reason: {}", userId, e.getMessage());
            }
        } else {
            logger.warn("[WebSocket] Could not find active session for user {} to send notification.", userId);
        }
    }
}
