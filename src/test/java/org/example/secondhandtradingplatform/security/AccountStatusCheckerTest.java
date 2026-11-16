package org.example.secondhandtradingplatform.security;

import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountStatusCheckerTest {

    private SysUserMapper sysUserMapper;
    private AccountStatusChecker checker;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        checker = new AccountStatusChecker(sysUserMapper);
    }

    @Test
    @DisplayName("正常账号通过校验并返回用户实体")
    void acceptsNormalUser() {
        SysUser user = user(1);
        when(sysUserMapper.selectById(1L)).thenReturn(user);

        assertSame(user, checker.requireActive(1L));
    }

    @Test
    @DisplayName("禁用账号被拒绝")
    void rejectsDisabledUser() {
        when(sysUserMapper.selectById(1L)).thenReturn(user(0));

        BizException exception = assertThrows(BizException.class, () -> checker.requireActive(1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        assertEquals("账号已被禁用", exception.getMessage());
    }

    @Test
    @DisplayName("封禁账号被拒绝")
    void rejectsBannedUser() {
        when(sysUserMapper.selectById(1L)).thenReturn(user(2));

        BizException exception = assertThrows(BizException.class, () -> checker.requireActive(1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        assertEquals("账号已被封禁", exception.getMessage());
    }

    @Test
    @DisplayName("用户不存在时按未登录处理")
    void rejectsMissingUser() {
        when(sysUserMapper.selectById(1L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> checker.requireActive(1L));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), exception.getCode());
    }

    private SysUser user(int status) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(status);
        return user;
    }
}
