package org.example.secondhandtradingplatform.security;

import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthInterceptorTest {

    private JwtUtil jwtUtil;
    private AccountStatusChecker accountStatusChecker;
    private JwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        accountStatusChecker = mock(AccountStatusChecker.class);
        interceptor = new JwtAuthInterceptor(jwtUtil, accountStatusChecker);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("公开 GET 携带有效 token 时仍然识别登录用户")
    void resolvesUserOnPublicGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products/1");
        request.addHeader("Authorization", "Bearer good-token");
        when(jwtUtil.parseUserId("good-token")).thenReturn(7L);
        when(accountStatusChecker.requireActive(7L)).thenReturn(new SysUser());

        assertEquals(true, interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(7L, UserContext.getUserId());
    }

    @Test
    @DisplayName("公开 GET 未携带 token 不拦截，按匿名处理")
    void allowsAnonymousPublicGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products/1");

        assertDoesNotThrow(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertNull(UserContext.getUserId());
    }

    @Test
    @DisplayName("公开 GET 携带失效 token 时降级为匿名，不影响浏览")
    void ignoresInvalidTokenOnPublicGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products/1");
        request.addHeader("Authorization", "Bearer bad-token");
        when(jwtUtil.parseUserId("bad-token")).thenThrow(new BizException(ErrorCode.UNAUTHORIZED));

        assertDoesNotThrow(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertNull(UserContext.getUserId());
    }

    @Test
    @DisplayName("非公开接口缺少 token 时按未登录拦截")
    void rejectsMissingTokenOnProtectedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/products");

        assertThrows(BizException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
