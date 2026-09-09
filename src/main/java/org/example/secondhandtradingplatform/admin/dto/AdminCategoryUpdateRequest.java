package org.example.secondhandtradingplatform.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCategoryUpdateRequest {

    private Long parentId;

    @Size(max = 64, message = "分类名称不能超过64个字符")
    private String name;

    @Size(max = 255, message = "分类图标不能超过255个字符")
    private String icon;

    private Integer sort;

    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status;
}
