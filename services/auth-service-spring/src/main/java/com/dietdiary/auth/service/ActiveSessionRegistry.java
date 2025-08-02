package com.dietdiary.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActiveSessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public WebSocketSession get(String userId) {
        return sessions.get(userId);
    }

    public void unregister(String userId) {
        sessions.remove(userId);
    }
}
