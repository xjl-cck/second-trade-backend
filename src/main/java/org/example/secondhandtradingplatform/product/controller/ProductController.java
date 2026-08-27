package org.example.secondhandtradingplatform.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.product.dto.ProductCreateRequest;
import org.example.secondhandtradingplatform.product.dto.ProductImageResponse;
import org.example.secondhandtradingplatform.product.dto.ProductQueryRequest;
import org.example.secondhandtradingplatform.product.dto.ProductResponse;
import org.example.secondhandtradingplatform.product.dto.ProductUpdateRequest;
import org.example.secondhandtradingplatform.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
@Tag(name = "商品模块", description = "商品发布、编辑、上下架、图片上传、检索和详情")
public class ProductController {

    private final ProductService productService;
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "发布商品", description = "登录用户发布商品，当前版本发布后直接上架")
    public Result<ProductResponse> publish(@Valid @RequestBody ProductCreateRequest request) {
        return Result.success(productService.publish(request));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "编辑商品", description = "只能编辑自己发布且未售出的商品")
    public Result<ProductResponse> update(@PathVariable Long productId,
                                          @Valid @RequestBody ProductUpdateRequest request) {
        return Result.success(productService.update(productId, request));
    }

    @PutMapping("/{productId}/on-sale")
    @Operation(summary = "上架商品", description = "只能上架自己发布且未售出的商品")
    public Result<Void> putOnSale(@PathVariable Long productId) {
        productService.putOnSale(productId);
        return Result.success();
    }

    @PutMapping("/{productId}/off-sale")
    @Operation(summary = "下架商品", description = "只能下架自己发布且未售出的商品")
    public Result<Void> takeOffSale(@PathVariable Long productId) {
        productService.takeOffSale(productId);
        return Result.success();
    }

    @PostMapping("/{productId}/images")
    @Operation(summary = "上传商品图片", description = "上传 jpg、jpeg、png、webp 图片，返回可访问图片地址")
    public Result<ProductImageResponse> uploadImage(@PathVariable Long productId,
                                                    @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
                                                    @RequestParam(required = false) Boolean cover,
                                                    @RequestParam(required = false) Integer sort) {
        return Result.success(productService.uploadImage(productId, file, cover, sort));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "商品详情", description = "返回商品基础信息、卖家信息和图片列表")
    public Result<ProductResponse> getDetail(@PathVariable Long productId) {
        return Result.success(productService.getDetail(productId));
    }

    @GetMapping
    @Operation(summary = "商品检索", description = "支持关键词搜索、分类筛选、价格区间筛选，以及 latest/hot 排序")
    public Result<Page<ProductResponse>> page(@Valid ProductQueryRequest request) {
        return Result.success(productService.page(request));
    }
}
