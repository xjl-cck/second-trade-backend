package org.example.secondhandtradingplatform.category.service;

import org.example.secondhandtradingplatform.category.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {

    String ENABLED_TREE_CACHE_KEY = "category:tree:enabled";

    List<CategoryResponse> listTree();
}
