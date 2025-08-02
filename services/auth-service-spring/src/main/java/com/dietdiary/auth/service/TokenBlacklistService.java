package com.dietdiary.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Manages JWT token blacklisting.
 * Provides O(1) performance for token invalidation during forced logout.
 */
@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String USER_INVALIDATE_PREFIX = "jwt:user_invalidate:";
    
    private final RedisTemplate<String, String> redisTemplate;

    public TokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Adds a token to the blacklist.
     * @param jwtId The jti (JWT ID) claim of the JWT.
     * @param expirationTime The token's expiration time.
     */
    public void blacklistToken(String jwtId, Date expirationTime) {
        if (jwtId == null || expirationTime == null) {
            logger.warn("[TokenBlacklist] Invalid parameters - jwtId: {}, expiration: {}", jwtId, expirationTime);
            return;
        }

        long ttlSeconds = (expirationTime.getTime() - System.currentTimeMillis()) / 1000;
        
        if (ttlSeconds > 0) {
            String key = BLACKLIST_PREFIX + jwtId;
            redisTemplate.opsForValue().set(key, "blacklisted", Duration.ofSeconds(ttlSeconds));
            logger.info("[TokenBlacklist] Token blacklisted - jwtId: {}, TTL: {}s", jwtId, ttlSeconds);
        } else {
            logger.debug("[TokenBlacklist] Token already expired - jwtId: {}", jwtId);
        }
    }

    /**
     * Blacklists all tokens for a user (force logout).
     * @param userId User ID
     * @param currentTime Current time
     */
    public void blacklistAllUserTokens(Long userId, Date currentTime) {
        blacklistAllUserTokens(userId, currentTime, null, null);
    }
    
    /**
     * Blacklists all tokens for a user, including admin info.
     * @param userId Target User ID
     * @param currentTime Current time
     * @param adminUserId Admin User ID
     * @param adminEmail Admin Email
     */
    public void blacklistAllUserTokens(Long userId, Date currentTime, Long adminUserId, String adminEmail) {
        String userKey = USER_INVALIDATE_PREFIX + userId;
        long timestamp = currentTime.getTime();
        
        // Store metadata as a JSON string
        String metadata = String.format(
            "{\"timestamp\":%d,\"adminUserId\":%s,\"adminEmail\":\"%s\",\"reason\":\"FORCE_LOGOUT\"}", 
            timestamp, 
            adminUserId != null ? adminUserId : "null",
            adminEmail != null ? adminEmail : "system"
        );
        
        // Set TTL for 24 hours (longer than max access token expiration)
        redisTemplate.opsForValue().set(userKey, metadata, Duration.ofHours(24));
        logger.info("[TokenBlacklist] All tokens invalidated for user: {} by admin: {} ({}) at {}", 
                   userId, adminUserId, adminEmail, timestamp);
    }

    /**
     * Checks if a token is blacklisted (O(1) performance).
     * @param jwtId JWT ID
     * @return true if blacklisted
     */
    public boolean isTokenBlacklisted(String jwtId) {
        if (jwtId == null) return false;
        
        String key = BLACKLIST_PREFIX + jwtId;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Checks if a user's tokens have been invalidated.
     * @param userId User ID
     * @param tokenIssuedAt Token issuance time
     * @return true if invalidated
     */
    public boolean isUserTokenInvalidated(Long userId, Date tokenIssuedAt) {
        if (userId == null || tokenIssuedAt == null) return false;
        
        String userKey = USER_INVALIDATE_PREFIX + userId;
        String invalidateData = redisTemplate.opsForValue().get(userKey);
        
        if (invalidateData == null) return false;
        
        try {
            long invalidateTimestamp;
            
            if (invalidateData.startsWith("{")) {
                // New JSON format
                String timestampStr = extractTimestampFromJson(invalidateData);
                invalidateTimestamp = Long.parseLong(timestampStr);
            } else {
                // Legacy format (numeric only)
                invalidateTimestamp = Long.parseLong(invalidateData);
            }
            
            return tokenIssuedAt.getTime() < invalidateTimestamp;
        } catch (Exception e) {
            logger.warn("[TokenBlacklist] Invalid data format for user: {} - {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts timestamp value from JSON string.
     * Uses simple string parsing to avoid Jackson dependency.
     */
    private String extractTimestampFromJson(String json) {
        String timestampPrefix = "\"timestamp\":";
        int start = json.indexOf(timestampPrefix);
        if (start == -1) throw new IllegalArgumentException("timestamp not found in JSON");
        
        start += timestampPrefix.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        
        return json.substring(start, end).trim();
    }

    /**
     * Cleans up blacklist entries for a specific user.
     * @param userId User ID
     */
    public void cleanupUserBlacklist(Long userId) {
        String userKey = USER_INVALIDATE_PREFIX + userId;
        redisTemplate.delete(userKey);
        logger.info("[TokenBlacklist] Cleaned up blacklist for user: {}", userId);
    }

    /**
     * Returns the number of blacklisted tokens (using SCAN).
     * @return Count of blacklisted tokens.
     */
    public long countBlacklistedTokens() {
        try {
            logger.info("[TokenBlacklist] Starting count operation using SCAN...");
            long individualCount = scanKeys(BLACKLIST_PREFIX + "*").size();
            long userInvalidateCount = scanKeys(USER_INVALIDATE_PREFIX + "*").size();
            
            logger.info("[TokenBlacklist] Count - Individual tokens: {}, User invalidate keys: {}, Total: {}", 
                       individualCount, userInvalidateCount, individualCount + userInvalidateCount);
            
            return individualCount + userInvalidateCount;
        } catch (Exception e) {
            logger.error("[TokenBlacklist] Error counting blacklisted tokens with SCAN: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Retrieves the force logout history for all users (using SCAN).
     * @return List of force logout history entries.
     */
    public java.util.List<java.util.Map<String, Object>> getForceLogoutHistory() {
        logger.info("[TokenBlacklist] Starting getForceLogoutHistory using SCAN...");
        java.util.List<java.util.Map<String, Object>> history = new java.util.ArrayList<>();
        
        try {
            Set<String> userInvalidateKeys = scanKeys(USER_INVALIDATE_PREFIX + "*");
            logger.info("[TokenBlacklist] Found {} keys with SCAN", userInvalidateKeys.size());
            
            for (String key : userInvalidateKeys) {
                String data = redisTemplate.opsForValue().get(key);
                if (data == null) continue;
                
                java.util.Map<String, Object> entry = new java.util.HashMap<>();
                
                String userIdStr = key.substring(USER_INVALIDATE_PREFIX.length());
                entry.put("userId", Long.parseLong(userIdStr));
                
                if (data.startsWith("{")) {
                    // JSON parsing
                    entry.put("timestamp", Long.parseLong(Objects.requireNonNull(extractValueFromJson(data, "timestamp"))));
                    entry.put("adminUserId", extractValueFromJson(data, "adminUserId"));
                    entry.put("adminEmail", extractValueFromJson(data, "adminEmail"));
                    entry.put("reason", extractValueFromJson(data, "reason"));
                } else {
                    // Legacy data
                    entry.put("timestamp", Long.parseLong(data));
                    entry.put("adminUserId", null);
                    entry.put("adminEmail", "system");
                    entry.put("reason", "FORCE_LOGOUT");
                }
                
                history.add(entry);
            }
            
            history.sort((a, b) -> Long.compare((Long) b.get("timestamp"), (Long) a.get("timestamp")));
            
        } catch (Exception e) {
            logger.error("[TokenBlacklist] Error getting force logout history with SCAN: {}", e.getMessage(), e);
        }
        
        return history;
    }
    
    /**
     * Safely gets a set of keys matching a pattern using Redis SCAN.
     * @param pattern The key pattern to scan for.
     * @return A set of matching keys.
     */
    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
            }
            return keys;
        });
    }
    
    /**
     * Extracts a specific field value from a JSON string.
     */
    private String extractValueFromJson(String json, String fieldName) {
        String fieldPrefix = "\"" + fieldName + "\":";
        int start = json.indexOf(fieldPrefix);
        if (start == -1) return null;
        
        start += fieldPrefix.length();
        
        char firstChar = json.charAt(start);
        if (firstChar == '"') {
            start++;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            String value = json.substring(start, end).trim();
            return "null".equals(value) ? null : value;
        }
    }
}
