package org.example.secondhandtradingplatform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.example.secondhandtradingplatform.user.entity.SysUser;

import java.time.LocalDateTime;

@Data
@Schema(description = "用户资料响应")
public class UserProfileResponse {

    @Schema(description = "用户ID")
    private Long id;
    @Schema(description = "登录账号")
    private String username;
    @Schema(description = "昵称")
    private String nickname;
    @Schema(description = "手机号")
    private String phone;
    @Schema(description = "头像")
    private String avatar;
    @Schema(description = "学号")
    private String studentNo;
    @Schema(description = "性别 0未知 1男 2女")
    private Integer gender;
    @Schema(description = "专业")
    private String major;
    @Schema(description = "年级")
    private String grade;
    @Schema(description = "角色 0学生 1管理员")
    private Integer role;
    @Schema(description = "状态 0禁用 1正常 2封禁")
    private Integer status;
    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public static UserProfileResponse from(SysUser user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setPhone(user.getPhone());
        response.setAvatar(user.getAvatar());
        response.setStudentNo(user.getStudentNo());
        response.setGender(user.getGender());
        response.setMajor(user.getMajor());
        response.setGrade(user.getGrade());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setLastLoginTime(user.getLastLoginTime());
        response.setCreateTime(user.getCreateTime());
        return response;
    }
}
