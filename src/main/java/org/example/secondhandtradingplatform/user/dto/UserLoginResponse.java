package org.example.secondhandtradingplatform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "用户登录响应")
public class UserLoginResponse {

    @Schema(description = "JWT token")
    private String token;

    @Schema(description = "用户资料")
    private UserProfileResponse user;
}
