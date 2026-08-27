package org.example.secondhandtradingplatform.favorite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.favorite.dto.FavoriteListRequest;
import org.example.secondhandtradingplatform.favorite.dto.FavoriteProductResponse;
import org.example.secondhandtradingplatform.favorite.service.FavoriteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
@Tag(name = "收藏模块", description = "收藏、取消收藏、收藏列表")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{productId}")
    @Operation(summary = "收藏商品", description = "登录后收藏指定商品，重复收藏会直接成功返回")
    public Result<Void> favorite(@PathVariable Long productId) {
        favoriteService.favorite(productId);
        return Result.success();
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "取消收藏", description = "登录后取消收藏指定商品，不存在时直接成功返回")
    public Result<Void> unfavorite(@PathVariable Long productId) {
        favoriteService.unfavorite(productId);
        return Result.success();
    }

    @GetMapping
    @Operation(summary = "收藏列表", description = "分页返回当前登录用户收藏的商品列表")
    public Result<Page<FavoriteProductResponse>> list(@Valid FavoriteListRequest request) {
        return Result.success(favoriteService.list(request));
    }
}
