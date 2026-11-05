package org.example.secondhandtradingplatform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "用户注册请求")
public class UserRegisterRequest {

    @Schema(description = "登录账号", example = "student001")
    @NotBlank(message = "不能为空")
    @Size(min = 3, max = 64, message = "长度必须在3到64之间")
    private String username;

    @Schema(description = "登录密码", example = "123456")
    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 32, message = "长度必须在6到32之间")
    private String password;

    @Schema(description = "用户昵称", example = "小明")
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过64")
    private String nickname;

    @Schema(description = "手机号", example = "13800138000")
    @Size(max = 20, message = "长度不能超过20")
    private String phone;

    @Schema(description = "学号", example = "20260001")
    @Size(max = 32, message = "长度不能超过32")
    private String studentNo;

}
