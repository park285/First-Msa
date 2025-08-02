package com.dietdiary.gateway.util;

import com.dietdiary.gateway.service.TokenBlacklistService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    public String getEmailFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getLongClaim("userId");
        } catch (ParseException e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            JWSVerifier verifier = new MACVerifier(jwtSecret.getBytes());
            
            // Basic validation (signature, expiration)
            if (!signedJWT.verify(verifier) || isTokenExpired(signedJWT)) {
                return false;
            }
            
            // Blacklist validation (individual token)
            String jwtId = claims.getJWTID();
            if (jwtId != null && tokenBlacklistService.isTokenBlacklisted(jwtId)) {
                logger.info("[Gateway] Token is blacklisted: {}", jwtId);
                return false;
            }
            
            // User-wide token invalidation check
            Long userId = claims.getLongClaim("userId");
            Date issuedAt = claims.getIssueTime();
            logger.info("[Gateway] Checking user token invalidation - userId: {}, issuedAt: {}", userId, issuedAt);
            if (userId != null && tokenBlacklistService.isUserTokenInvalidated(userId, issuedAt)) {
                logger.warn("[Gateway] User tokens invalidated for userId: {}", userId);
                return false;
            }
            logger.info("[Gateway] Token validation passed for userId: {}", userId);
            
            return true;
        } catch (ParseException | JOSEException e) {
            logger.warn("[Gateway] JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(SignedJWT signedJWT) {
        try {
            return signedJWT.getJWTClaimsSet().getExpirationTime().before(new Date());
        } catch (ParseException e) {
            return true;
        }
    }
}
