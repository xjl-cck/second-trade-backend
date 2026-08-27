package org.example.secondhandtradingplatform.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.user.dto.UserChangePasswordRequest;
import org.example.secondhandtradingplatform.user.dto.UserLoginRequest;
import org.example.secondhandtradingplatform.user.dto.UserLoginResponse;
import org.example.secondhandtradingplatform.user.dto.UserProfileResponse;
import org.example.secondhandtradingplatform.user.dto.UserRegisterRequest;
import org.example.secondhandtradingplatform.user.dto.UserUpdateRequest;
import org.example.secondhandtradingplatform.user.service.SysUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "用户模块", description = "注册、登录、当前用户资料和密码管理")
public class UserController {

    private final SysUserService sysUserService;

    public UserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建学生用户，账号、手机号、学号不能重复")
    public Result<UserProfileResponse> register(@Valid @RequestBody UserRegisterRequest request) {
        return Result.success(sysUserService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "账号密码校验成功后返回 JWT token")
    public Result<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return Result.success(sysUserService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户", description = "需要在请求头 Authorization 中携带 Bearer token")
    public Result<UserProfileResponse> getCurrentUser() {
        return Result.success(sysUserService.getCurrentUser());
    }

    @PutMapping("/me")
    @Operation(summary = "修改当前用户资料", description = "修改昵称、手机号、头像、学号、性别、专业、年级和校园信息")
    public Result<UserProfileResponse> updateCurrentUser(@Valid @RequestBody UserUpdateRequest request) {
        return Result.success(sysUserService.updateCurrentUser(request));
    }

    @PutMapping("/me/password")
    @Operation(summary = "修改当前用户密码", description = "校验原密码后更新为新密码")
    public Result<Void> changeCurrentPassword(@Valid @RequestBody UserChangePasswordRequest request) {
        sysUserService.changeCurrentPassword(request);
        return Result.success();
    }
}
