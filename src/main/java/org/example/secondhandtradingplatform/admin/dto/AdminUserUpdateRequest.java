package org.example.secondhandtradingplatform.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {

    @Size(max = 64, message = "长度不能超过64")
    private String nickname;

    @Size(max = 20, message = "长度不能超过20")
    private String phone;

    @Size(max = 255, message = "长度不能超过255")
    private String avatar;

    @Size(max = 32, message = "长度不能超过32")
    private String studentNo;

    @Min(value = 0, message = "只能为0、1、2")
    @Max(value = 2, message = "只能为0、1、2")
    private Integer gender;

    @Size(max = 64, message = "长度不能超过64")
    private String major;

    @Size(max = 32, message = "长度不能超过32")
    private String grade;

    private Long campusId;

    @Min(value = 0, message = "角色只能为0或1")
    @Max(value = 1, message = "角色只能为0或1")
    private Integer role;

    @Min(value = 0, message = "状态只能为0、1、2")
    @Max(value = 2, message = "状态只能为0、1、2")
    private Integer status;
}
