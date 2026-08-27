package org.example.secondhandtradingplatform.category.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.secondhandtradingplatform.category.dto.CategoryResponse;
import org.example.secondhandtradingplatform.category.entity.Category;
import org.example.secondhandtradingplatform.category.mapper.CategoryMapper;
import org.example.secondhandtradingplatform.category.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryServiceImpl implements CategoryService {

    private static final int STATUS_ENABLED = 1;

    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<CategoryResponse> listTree() {
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getStatus, STATUS_ENABLED)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, CategoryResponse> responseMap = new LinkedHashMap<>();
        for (Category category : categories) {
            responseMap.put(category.getId(), CategoryResponse.from(category));
        }
        List<CategoryResponse> roots = new ArrayList<>();
        for (CategoryResponse response : responseMap.values()) {
            if (response.getParentId() == null || response.getParentId() == 0) {
                roots.add(response);
                continue;
            }
            CategoryResponse parent = responseMap.get(response.getParentId());
            if (parent == null) {
                roots.add(response);
            } else {
                parent.getChildren().add(response);
            }
        }
        return roots;
    }
}
