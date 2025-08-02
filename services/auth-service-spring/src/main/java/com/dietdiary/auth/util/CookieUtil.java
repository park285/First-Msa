package com.dietdiary.auth.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;
    
    @Value("${app.security.cookie.salt}")
    private String cookieSalt;

    public ResponseCookie createRefreshTokenCookie(String token) {
        // Hash the token with SHA-256 + salt before storing in cookie
        String hashedToken = DigestUtils.sha256Hex(token + cookieSalt);
        
        return ResponseCookie.from("refresh-token", hashedToken)
                .httpOnly(true)
                .secure(true) // HTTPS only
                .path("/")
                .maxAge(refreshTokenExpiration / 1000) // in seconds
                .sameSite("Strict") // CSRF protection
                .build();
    }

    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from("refresh-token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }

    public String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refresh-token".equals(cookie.getName())) {
                    return cookie.getValue(); // Returns the hashed value
                }
            }
        }
        return null;
    }
    
    /**
     * Validates if the original token matches the hashed token from the cookie.
     */
    public boolean validateRefreshToken(String originalToken, String hashedTokenFromCookie) {
        if (originalToken == null || hashedTokenFromCookie == null) {
            return false;
        }
        String computedHash = DigestUtils.sha256Hex(originalToken + cookieSalt);
        return computedHash.equals(hashedTokenFromCookie);
    }
}
