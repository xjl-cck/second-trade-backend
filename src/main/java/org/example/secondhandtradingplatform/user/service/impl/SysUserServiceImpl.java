package org.example.secondhandtradingplatform.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.security.JwtUtil;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.dto.UserChangePasswordRequest;
import org.example.secondhandtradingplatform.user.dto.UserLoginRequest;
import org.example.secondhandtradingplatform.user.dto.UserLoginResponse;
import org.example.secondhandtradingplatform.user.dto.UserProfileResponse;
import org.example.secondhandtradingplatform.user.dto.UserRegisterRequest;
import org.example.secondhandtradingplatform.user.dto.UserUpdateRequest;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.example.secondhandtradingplatform.user.service.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysUserServiceImpl implements SysUserService {

    private static final int ROLE_STUDENT = 0;
    private static final int STATUS_NORMAL = 1;

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public SysUserServiceImpl(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse register(UserRegisterRequest request) {
        validateUnique(null, request.getUsername(), request.getPhone(), request.getStudentNo());

        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname().trim());
        user.setPhone(trimToNull(request.getPhone()));
        user.setStudentNo(trimToNull(request.getStudentNo()));
        user.setGender(0);
        user.setRole(ROLE_STUDENT);
        user.setStatus(STATUS_NORMAL);
        user.setIsDeleted(0);
        sysUserMapper.insert(user);
        return UserProfileResponse.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserLoginResponse login(UserLoginRequest request) {
        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername().trim()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "账号或密码错误");
        }
        if (!Integer.valueOf(STATUS_NORMAL).equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用或封禁");
        }

        user.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new UserLoginResponse(token, UserProfileResponse.from(user));
    }

    @Override
    public UserProfileResponse getCurrentUser() {
        SysUser user = getLoginUser();
        return UserProfileResponse.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateCurrentUser(UserUpdateRequest request) {
        SysUser user = getLoginUser();
        validateUnique(user.getId(), null, request.getPhone(), request.getStudentNo());

        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(trimToNull(request.getPhone()));
        }
        if (request.getAvatar() != null) {
            user.setAvatar(trimToNull(request.getAvatar()));
        }
        if (request.getStudentNo() != null) {
            user.setStudentNo(trimToNull(request.getStudentNo()));
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getMajor() != null) {
            user.setMajor(trimToNull(request.getMajor()));
        }
        if (request.getGrade() != null) {
            user.setGrade(trimToNull(request.getGrade()));
        }
        sysUserMapper.updateById(user);
        return UserProfileResponse.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeCurrentPassword(UserChangePasswordRequest request) {
        SysUser user = getLoginUser();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "原密码错误");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserMapper.updateById(user);
    }

    private SysUser getLoginUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录用户不存在");
        }
        if (!Integer.valueOf(STATUS_NORMAL).equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用或封禁");
        }
        return user;
    }

    private void validateUnique(Long currentUserId, String username, String phone, String studentNo) {
        if (StringUtils.hasText(username) && existsOther(currentUserId, "username", username.trim())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "账号已存在");
        }
        if (StringUtils.hasText(phone) && existsOther(currentUserId, "phone", phone.trim())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "手机号已被使用");
        }
        if (StringUtils.hasText(studentNo) && existsOther(currentUserId, "student_no", studentNo.trim())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "学号已被使用");
        }
    }

    private boolean existsOther(Long currentUserId, String column, String value) {
        LambdaQueryWrapper<SysUser> queryWrapper = Wrappers.lambdaQuery(SysUser.class)
                .ne(currentUserId != null, SysUser::getId, currentUserId);
        switch (column) {
            case "username" -> queryWrapper.eq(SysUser::getUsername, value);
            case "phone" -> queryWrapper.eq(SysUser::getPhone, value);
            case "student_no" -> queryWrapper.eq(SysUser::getStudentNo, value);
            default -> throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        Long count = sysUserMapper.selectCount(queryWrapper);
        return count != null && count > 0;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
