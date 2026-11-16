package org.example.secondhandtradingplatform.config;

import org.example.secondhandtradingplatform.security.AccountStatusChecker;
import org.example.secondhandtradingplatform.security.JwtUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class ChatWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final AccountStatusChecker accountStatusChecker;

    public ChatWebSocketHandshakeInterceptor(JwtUtil jwtUtil, AccountStatusChecker accountStatusChecker) {
        this.jwtUtil = jwtUtil;
        this.accountStatusChecker = accountStatusChecker;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams();
        String token = queryParams.getFirst("token");
        if (token == null || token.isBlank()) {
            token = request.getHeaders().getFirst("Authorization");
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length());
        }
        try {
            Long userId = jwtUtil.parseUserId(token);
            accountStatusChecker.requireActive(userId);
            attributes.put("userId", userId);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
