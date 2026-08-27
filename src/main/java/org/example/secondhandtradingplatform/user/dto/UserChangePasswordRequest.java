package org.example.secondhandtradingplatform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "修改密码请求")
public class UserChangePasswordRequest {

    @Schema(description = "原密码", example = "123456")
    @NotBlank(message = "不能为空")
    private String oldPassword;

    @Schema(description = "新密码", example = "654321")
    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 32, message = "长度必须在6到32之间")
    private String newPassword;
}
