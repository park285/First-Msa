package com.dietdiary.auth.service;

import com.dietdiary.auth.entity.User;
import com.dietdiary.auth.repository.UserRepository;
import com.dietdiary.auth.security.JwtUtil;
import com.dietdiary.auth.util.CookieUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RefreshTokenService {

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    @Value("${app.security.cookie.salt}")
    private String cookieSalt;

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String USER_REFRESH_TOKEN_PREFIX = "user_refresh:";
    private static final String HASH_TO_TOKEN_PREFIX = "hash_to_token:";

    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate, UserRepository userRepository, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public String createRefreshToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        deleteByUserId(user.getId());

        String refreshToken = jwtUtil.generateRefreshToken(user);
        
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("userId", user.getId());
        tokenData.put("email", user.getEmail());
        tokenData.put("name", user.getName());
        tokenData.put("issuedAt", Instant.now().toString());
        tokenData.put("expiryDate", Instant.now().plusMillis(refreshTokenExpiration).toString());

        Duration expiration = Duration.ofMillis(refreshTokenExpiration);
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken, tokenData, expiration);
        redisTemplate.opsForValue().set(USER_REFRESH_TOKEN_PREFIX + user.getId(), refreshToken, expiration);
        
        String hashedToken = org.apache.commons.codec.digest.DigestUtils.sha256Hex(refreshToken + cookieSalt);
        redisTemplate.opsForValue().set(HASH_TO_TOKEN_PREFIX + hashedToken, refreshToken, expiration);

        return refreshToken;
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> findByToken(String token) {
        Object tokenData = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + token);
        if (tokenData instanceof Map) {
            return Optional.of((Map<String, Object>) tokenData);
        }
        return Optional.empty();
    }

    public void deleteByUserId(Long userId) {
        String existingToken = (String) redisTemplate.opsForValue().get(USER_REFRESH_TOKEN_PREFIX + userId);
        if (existingToken != null) {
            String hashedToken = org.apache.commons.codec.digest.DigestUtils.sha256Hex(existingToken + System.getenv("COOKIE_SALT"));
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + existingToken);
            redisTemplate.delete(USER_REFRESH_TOKEN_PREFIX + userId);
            redisTemplate.delete(HASH_TO_TOKEN_PREFIX + hashedToken);
        }
    }

    public boolean deleteByHashedToken(String hashedToken) {
        String originalToken = (String) redisTemplate.opsForValue().get(HASH_TO_TOKEN_PREFIX + hashedToken);
        if (originalToken != null) {
            Optional<Map<String, Object>> tokenDataOpt = findByToken(originalToken);
            if (tokenDataOpt.isPresent()) {
                Long userId = ((Number) tokenDataOpt.get().get("userId")).longValue();
                redisTemplate.delete(USER_REFRESH_TOKEN_PREFIX + userId);
            }
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + originalToken);
            redisTemplate.delete(HASH_TO_TOKEN_PREFIX + hashedToken);
            return true;
        }
        return false;
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> findValidTokenByHash(String hashedToken, CookieUtil cookieUtil) {
        String originalToken = (String) redisTemplate.opsForValue().get(HASH_TO_TOKEN_PREFIX + hashedToken);
        if (originalToken == null) return null;
        
        Object tokenData = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + originalToken);
        if (tokenData instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) tokenData;
            Instant expiryDate = Instant.parse((String) data.get("expiryDate"));
            if (expiryDate.isAfter(Instant.now())) {
                return data;
            } else {
                deleteByHashedToken(hashedToken);
            }
        }
        return null;
    }

    public List<Map<String, Object>> findSessionsByUserId(Long userId) {
        String token = (String) redisTemplate.opsForValue().get(USER_REFRESH_TOKEN_PREFIX + userId);
        if (token == null) {
            return Collections.emptyList();
        }

        Optional<Map<String, Object>> tokenDataOpt = findByToken(token);
        if (tokenDataOpt.isPresent()) {
            Map<String, Object> tokenData = tokenDataOpt.get();
            Map<String, Object> sessionInfo = new HashMap<>();
            String hashedToken = org.apache.commons.codec.digest.DigestUtils.sha256Hex(token + System.getenv("COOKIE_SALT"));
            
            sessionInfo.put("tokenHash", hashedToken);
            sessionInfo.put("issuedAt", tokenData.get("issuedAt"));
            sessionInfo.put("expiryDate", tokenData.get("expiryDate"));
            
            List<Map<String, Object>> sessions = new ArrayList<>();
            sessions.add(sessionInfo);
            return sessions;
        }
        return Collections.emptyList();
    }

    public long countActiveRefreshTokens() {
        Set<String> keys = redisTemplate.keys(REFRESH_TOKEN_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }
}
