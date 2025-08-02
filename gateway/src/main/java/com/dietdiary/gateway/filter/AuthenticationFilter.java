package com.dietdiary.gateway.filter;

import com.dietdiary.gateway.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();
            
            logger.debug("Processing request for path: {}", path);

            // Bypass auth for token refresh
            if (path.endsWith("/refresh")) {
                logger.debug("Skipping auth for refresh endpoint");
                return chain.filter(exchange);
            }

            // Check for WebSocket connection
            HttpHeaders headers = request.getHeaders();
            boolean isWebSocket = "websocket".equalsIgnoreCase(headers.getUpgrade());

            String token = null;

            if (isWebSocket) {
                logger.debug("WebSocket connection detected for path: {}", path);
                List<String> tokens = request.getQueryParams().get("token");
                if (tokens != null && !tokens.isEmpty()) {
                    token = tokens.get(0);
                    logger.debug("Token found in query params for WebSocket");
                } else {
                    logger.warn("Missing token in query params for WebSocket connection");
                    return onError(exchange, "Missing token for WebSocket", HttpStatus.UNAUTHORIZED);
                }
            } else {
                // Standard HTTP request authorization
                if (!headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                    return onError(exchange, "Missing authorization header", HttpStatus.UNAUTHORIZED);
                }

                String authHeader = headers.get(HttpHeaders.AUTHORIZATION).get(0);
                if (!authHeader.startsWith("Bearer ")) {
                    return onError(exchange, "Authorization header is not Bearer type", HttpStatus.UNAUTHORIZED);
                }
                token = authHeader.substring(7);
            }

            if (token == null) {
                return onError(exchange, "Token could not be extracted", HttpStatus.UNAUTHORIZED);
            }
            
            logger.debug("Validating token for path: {}", path);
            boolean isValid = jwtUtil.validateToken(token);
            logger.debug("Token validation result: {}", isValid);

            if (!isValid) {
                logger.warn("Returning UNAUTHORIZED for invalid token");
                return onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
            }

            String email = jwtUtil.getEmailFromToken(token);
            Long userId = jwtUtil.getUserIdFromToken(token);

            ServerHttpRequest newRequest = request.mutate()
                    .header("X-User-Email", email)
                    .header("X-User-Id", String.valueOf(userId))
                    .build();

            return chain.filter(exchange.mutate().request(newRequest).build());
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        logger.error("Auth Error: {}, Status: {}", err, httpStatus);
        return response.setComplete();
    }

    public static class Config {
    }
}
