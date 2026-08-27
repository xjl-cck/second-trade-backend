package org.example.secondhandtradingplatform.user.service;

import org.example.secondhandtradingplatform.user.dto.UserLoginRequest;
import org.example.secondhandtradingplatform.user.dto.UserLoginResponse;
import org.example.secondhandtradingplatform.user.dto.UserChangePasswordRequest;
import org.example.secondhandtradingplatform.user.dto.UserProfileResponse;
import org.example.secondhandtradingplatform.user.dto.UserRegisterRequest;
import org.example.secondhandtradingplatform.user.dto.UserUpdateRequest;

public interface SysUserService {

    UserProfileResponse register(UserRegisterRequest request);

    UserLoginResponse login(UserLoginRequest request);

    UserProfileResponse getCurrentUser();

    UserProfileResponse updateCurrentUser(UserUpdateRequest request);

    void changeCurrentPassword(UserChangePasswordRequest request);
}
