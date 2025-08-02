package com.dietdiary.auth.websocket.service;

import org.springframework.web.socket.WebSocketSession;

public interface WebSocketSessionManager {
    void registerSession(String userEmail, WebSocketSession session);
    void removeSession(WebSocketSession session);
    WebSocketSession getSession(String userEmail);
    void sendToUser(String userEmail, String message);
    void broadcast(String message);
}
