package org.example.secondhandtradingplatform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "修改用户资料请求")
public class UserUpdateRequest {

    @Schema(description = "用户昵称", example = "校园好物达人")
    @Size(max = 64, message = "长度不能超过64")
    private String nickname;

    @Schema(description = "手机号", example = "13800138000")
    @Size(max = 20, message = "长度不能超过20")
    private String phone;

    @Schema(description = "头像地址", example = "https://example.com/avatar.png")
    @Size(max = 255, message = "长度不能超过255")
    private String avatar;

    @Schema(description = "学号", example = "20260001")
    @Size(max = 32, message = "长度不能超过32")
    private String studentNo;

    @Schema(description = "性别 0未知 1男 2女", example = "0")
    @Min(value = 0, message = "只能为0、1、2")
    @Max(value = 2, message = "只能为0、1、2")
    private Integer gender;

    @Schema(description = "专业", example = "软件工程")
    @Size(max = 64, message = "长度不能超过64")
    private String major;

    @Schema(description = "年级", example = "2026级")
    @Size(max = 32, message = "长度不能超过32")
    private String grade;

}
