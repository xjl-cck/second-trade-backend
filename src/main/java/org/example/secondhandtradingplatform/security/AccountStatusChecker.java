package org.example.secondhandtradingplatform.security;

import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

/**
 * 账号状态守卫。
 * <p>
 * 统一校验用户当前是否仍处于“正常”状态。管理员把账号改为禁用/封禁后立即生效，
 * 不再依赖 JWT 自然过期，避免违规账号拿着旧 Token 继续操作。
 */
@Component
public class AccountStatusChecker {

    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_BANNED = 2;

    private final SysUserMapper sysUserMapper;

    public AccountStatusChecker(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 校验账号可用并返回用户实体；账号不存在、被禁用或被封禁时抛出异常。
     */
    public SysUser requireActive(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录用户不存在");
        }
        Integer status = user.getStatus();
        if (Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        if (Integer.valueOf(STATUS_BANNED).equals(status)) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被封禁");
        }
        if (!Integer.valueOf(STATUS_NORMAL).equals(status)) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号状态异常");
        }
        return user;
    }
}
