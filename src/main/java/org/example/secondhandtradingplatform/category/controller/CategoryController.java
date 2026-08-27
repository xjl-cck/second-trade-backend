package org.example.secondhandtradingplatform.category.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.secondhandtradingplatform.category.dto.CategoryResponse;
import org.example.secondhandtradingplatform.category.service.CategoryService;
import org.example.secondhandtradingplatform.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "商品分类模块", description = "商品分类查询")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "查询分类树", description = "返回启用状态的商品分类树")
    public Result<List<CategoryResponse>> listTree() {
        return Result.success(categoryService.listTree());
    }
}
