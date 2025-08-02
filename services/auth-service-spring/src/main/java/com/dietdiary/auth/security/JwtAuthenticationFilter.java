package com.dietdiary.auth.security;

import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        logger.info("[JwtFilter] Processing request: {} {}", request.getMethod(), requestURI);
        
        try {
            String jwt = getJwtFromRequest(request);
            logger.info("[JwtFilter] JWT present: {}", jwt != null);

            if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt)) {
                JWTClaimsSet claims = jwtUtil.getClaimsFromToken(jwt);
                String email = claims.getSubject();
                
                // Read role information from the 'role' claim
                String role = (String) claims.getClaim("role");
                logger.info("[JwtFilter] User: {}, Role: {}", email, role);
                
                Collection<SimpleGrantedAuthority> authorities = java.util.Collections.emptyList();
                if (role != null) {
                    authorities = java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
                }
                    
                UserDetails userDetails = User.builder()
                    .username(email)
                    .password("") // Password is not needed for JWT auth
                    .authorities(authorities)
                    .build();
                    
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.info("[JwtFilter] Authentication set successfully for user: {}", email);
            } else {
                logger.warn("[JwtFilter] JWT validation failed or no JWT provided");
            }
        } catch (Exception ex) {
            // If JWT parsing/validation fails, just proceed without setting authentication.
            // Access control will be handled later by the SecurityFilterChain for anonymous users.
            logger.error("Error processing JWT", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
