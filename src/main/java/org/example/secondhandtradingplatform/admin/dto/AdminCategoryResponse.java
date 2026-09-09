package org.example.secondhandtradingplatform.admin.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.category.entity.Category;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AdminCategoryResponse {

    private Long id;
    private Long parentId;
    private String name;
    private String icon;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<AdminCategoryResponse> children = new ArrayList<>();

    public static AdminCategoryResponse from(Category category) {
        AdminCategoryResponse response = new AdminCategoryResponse();
        response.setId(category.getId());
        response.setParentId(category.getParentId());
        response.setName(category.getName());
        response.setIcon(category.getIcon());
        response.setSort(category.getSort());
        response.setStatus(category.getStatus());
        response.setCreateTime(category.getCreateTime());
        response.setUpdateTime(category.getUpdateTime());
        return response;
    }
}
