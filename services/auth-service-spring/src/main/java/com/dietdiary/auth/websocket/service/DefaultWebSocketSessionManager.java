package com.dietdiary.auth.websocket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DefaultWebSocketSessionManager implements WebSocketSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultWebSocketSessionManager.class);
    private final ConcurrentHashMap<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToUser = new ConcurrentHashMap<>();

    @Override
    public void registerSession(String userEmail, WebSocketSession session) {
        // If a user logs in from a new session, close the old one.
        if (userSessions.containsKey(userEmail)) {
            logger.warn("User {} already has an active session. Closing the old one.", userEmail);
            WebSocketSession oldSession = userSessions.get(userEmail);
            try {
                oldSession.close();
            } catch (IOException e) {
                logger.error("Error closing old session for user {}", userEmail, e);
            }
        }
        userSessions.put(userEmail, session);
        sessionToUser.put(session.getId(), userEmail);
        logger.info("WebSocket session registered for user: {}", userEmail);
    }

    @Override
    public void removeSession(WebSocketSession session) {
        String userEmail = sessionToUser.remove(session.getId());
        if (userEmail != null) {
            userSessions.remove(userEmail);
            logger.info("WebSocket session removed for user: {}", userEmail);
        }
    }

    @Override
    public WebSocketSession getSession(String userEmail) {
        return userSessions.get(userEmail);
    }

    @Override
    public void sendToUser(String userEmail, String message) {
        WebSocketSession session = getSession(userEmail);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                logger.info("Sent message to {}: {}", userEmail, message);
            } catch (IOException e) {
                logger.error("Failed to send message to user {}", userEmail, e);
            }
        } else {
            logger.warn("No active session found for user {}", userEmail);
        }
    }

    @Override
    public void broadcast(String message) {
        logger.info("Broadcasting message to all admin users: {}", message);
        userSessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                logger.error("Failed to broadcast message to session {}", session.getId(), e);
            }
        });
    }
}
