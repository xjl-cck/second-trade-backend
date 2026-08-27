package org.example.secondhandtradingplatform.category.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.category.entity.Category;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryResponse {

    private Long id;
    private Long parentId;
    private String name;
    private String icon;
    private Integer sort;
    private List<CategoryResponse> children = new ArrayList<>();

    public static CategoryResponse from(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setParentId(category.getParentId());
        response.setName(category.getName());
        response.setIcon(category.getIcon());
        response.setSort(category.getSort());
        return response;
    }
}
