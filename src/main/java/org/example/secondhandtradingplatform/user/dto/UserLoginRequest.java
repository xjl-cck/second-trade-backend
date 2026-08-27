package org.example.secondhandtradingplatform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "用户登录请求")
public class UserLoginRequest {

    @Schema(description = "登录账号", example = "student001")
    @NotBlank(message = "不能为空")
    private String username;

    @Schema(description = "登录密码", example = "123456")
    @NotBlank(message = "不能为空")
    private String password;
}
