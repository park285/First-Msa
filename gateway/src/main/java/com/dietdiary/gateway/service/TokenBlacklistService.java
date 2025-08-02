package com.dietdiary.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String USER_INVALIDATE_PREFIX = "jwt:user_invalidate:";

    private final RedisTemplate<String, String> redisTemplate;

    public TokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isTokenBlacklisted(String jwtId) {
        if (jwtId == null) return false;
        String key = BLACKLIST_PREFIX + jwtId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean isUserTokenInvalidated(Long userId, Date tokenIssuedAt) {
        if (userId == null || tokenIssuedAt == null) return false;

        String userKey = USER_INVALIDATE_PREFIX + userId;
        String invalidateData = redisTemplate.opsForValue().get(userKey);

        if (invalidateData == null) return false;

        try {
            long invalidateTimestamp;
            if (invalidateData.startsWith("{")) {
                String timestampStr = extractTimestampFromJson(invalidateData);
                invalidateTimestamp = Long.parseLong(timestampStr);
            } else {
                invalidateTimestamp = Long.parseLong(invalidateData);
            }
            boolean isInvalidated = tokenIssuedAt.getTime() < invalidateTimestamp;
            if (isInvalidated) {
                logger.warn("[Gateway] Token for user {} issued at {} is invalidated by timestamp {}", userId, tokenIssuedAt, invalidateTimestamp);
            }
            return isInvalidated;
        } catch (Exception e) {
            logger.warn("[Gateway] Invalid data format for user: {} - {}", userId, e.getMessage());
            return false;
        }
    }

    private String extractTimestampFromJson(String json) {
        String timestampPrefix = "\"timestamp\":";
        int start = json.indexOf(timestampPrefix);
        if (start == -1) throw new IllegalArgumentException("timestamp not found in JSON");

        start += timestampPrefix.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);

        return json.substring(start, end).trim();
    }
}