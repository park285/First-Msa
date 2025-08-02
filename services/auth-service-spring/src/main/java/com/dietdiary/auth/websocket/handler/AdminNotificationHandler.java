package com.dietdiary.auth.websocket.handler;

import com.dietdiary.auth.websocket.service.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AdminNotificationHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AdminNotificationHandler.class);
    private final WebSocketSessionManager sessionManager;

    public AdminNotificationHandler(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userEmail = (String) session.getAttributes().get("userEmail");
        if (userEmail != null) {
            sessionManager.registerSession(userEmail, session);
            logger.info("WebSocket connection established for user: {}", userEmail);
            session.sendMessage(new TextMessage("{\"type\":\"CONNECTION_SUCCESS\", \"message\":\"Admin notification channel connected.\"}"));
        } else {
            logger.warn("WebSocket connection established without user email. Closing session.");
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // For now, we just log messages from admins.
        // This could be extended to handle specific commands from admins.
        String userEmail = (String) session.getAttributes().get("userEmail");
        logger.info("Received message from admin {}: {}", userEmail, message.getPayload());
        // Example of echoing back a confirmation
        session.sendMessage(new TextMessage("{\"type\":\"MESSAGE_RECEIVED\", \"payload\":" + message.getPayload() + "}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session);
        logger.info("WebSocket connection closed: {}", status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error", exception);
    }
}
