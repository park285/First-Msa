package com.dietdiary.auth.security;

import com.dietdiary.auth.entity.User;
import com.dietdiary.auth.service.TokenBlacklistService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    private JWSSigner signer;
    private JWSVerifier verifier;
    
    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @PostConstruct
    public void init() throws KeyLengthException, JOSEException {
        signer = new MACSigner(secret.getBytes());
        verifier = new MACVerifier(secret.getBytes());
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);
        String jwtId = UUID.randomUUID().toString();

        logger.debug("Creating JWT for user: {}, name: {}, ID: {}, JWT ID: {}", 
                    user.getEmail(), user.getName(), user.getId(), jwtId);
        
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issueTime(now)
                .expirationTime(expiryDate)
                .jwtID(jwtId)
                .claim("userId", user.getId())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .build();
        
        String token = createToken(claimsSet);
        logger.debug("Generated JWT claims: {}", claimsSet.toJSONObject());
        return token;
    }

    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issueTime(now)
                .expirationTime(expiryDate)
                .claim("userId", user.getId())
                .build();

        return createToken(claimsSet);
    }

    public String createToken(JWTClaimsSet claimsSet) {
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        try {
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Could not create JWT", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            
            boolean signatureValid = signedJWT.verify(verifier);
            boolean tokenExpired = isTokenExpired(signedJWT);
            
            if (!signatureValid || tokenExpired) {
                logger.warn("Token validation failed - signature valid: {}, token expired: {}", signatureValid, tokenExpired);
                return false;
            }
            
            String jwtId = claims.getJWTID();
            if (jwtId != null && tokenBlacklistService.isTokenBlacklisted(jwtId)) {
                logger.warn("Token is blacklisted: {}", jwtId);
                return false;
            }
            
            Long userId = claims.getLongClaim("userId");
            Date issuedAt = claims.getIssueTime();
            if (userId != null && tokenBlacklistService.isUserTokenInvalidated(userId, issuedAt)) {
                logger.warn("User tokens invalidated for userId: {}", userId);
                return false;
            }
            
            logger.debug("Token validation successful");
            return true;
        } catch (ParseException | JOSEException e) {
            logger.error("Token validation exception: {}", e.getMessage());
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
    
    /**
     * Extracts all claims from a JWT to avoid DB I/O.
     */
    public JWTClaimsSet getClaimsFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
    
    public String getNameFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getStringClaim("name");
        } catch (ParseException e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
    
    public String getRolesFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getStringClaim("roles");
        } catch (ParseException e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
}
