package org.example.secondhandtradingplatform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AccountStatusChecker accountStatusChecker;

    public JwtAuthInterceptor(JwtUtil jwtUtil, AccountStatusChecker accountStatusChecker) {
        this.jwtUtil = jwtUtil;
        this.accountStatusChecker = accountStatusChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (isPublicGetRequest(request)) {
            return true;
        }

        String token = request.getHeader(AUTHORIZATION_HEADER);
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }
        Long userId = jwtUtil.parseUserId(token);
        // 先校验账号状态，再写入上下文：被禁用/封禁的账号立即失效，不依赖 Token 过期
        accountStatusChecker.requireActive(userId);
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean isPublicGetRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri.equals("/api/categories")
                || uri.equals("/api/products")
                || uri.matches("/api/products/\\d+");
    }
}

