package org.example.secondhandtradingplatform.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.product.dto.ProductCreateRequest;
import org.example.secondhandtradingplatform.product.dto.ProductImageOrderRequest;
import org.example.secondhandtradingplatform.product.dto.ProductImageResponse;
import org.example.secondhandtradingplatform.product.dto.ProductQueryRequest;
import org.example.secondhandtradingplatform.product.dto.ProductResponse;
import org.example.secondhandtradingplatform.product.dto.ProductUpdateRequest;
import org.example.secondhandtradingplatform.product.service.ProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    @Operation(summary = "发布商品", description = "登录用户发布商品；默认提交审核，draft=true 时保存为草稿")
    public Result<ProductResponse> publish(@Valid @RequestBody ProductCreateRequest request) {
        return Result.success(productService.publish(request));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "编辑商品", description = "只能编辑自己发布且未售出的商品")
    public Result<ProductResponse> update(@PathVariable Long productId,
                                          @Valid @RequestBody ProductUpdateRequest request) {
        return Result.success(productService.update(productId, request));
    }

    @PutMapping("/{productId}/submit")
    @Operation(summary = "提交审核", description = "草稿或已下架商品提交管理员审核，审核通过后才会上架")
    public Result<Void> submitForReview(@PathVariable Long productId) {
        productService.submitForReview(productId);
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

    @PutMapping("/{productId}/images/order")
    @Operation(summary = "调整图片顺序", description = "按传入的 ID 顺序重排商品图片，ID 必须覆盖该商品全部图片")
    public Result<Void> reorderImages(@PathVariable Long productId,
                                      @Valid @RequestBody ProductImageOrderRequest request) {
        productService.reorderImages(productId, request.getImageIds());
        return Result.success();
    }

    @PutMapping("/{productId}/images/{imageId}/cover")
    @Operation(summary = "设置封面", description = "把指定图片设为商品封面，其余图片自动取消封面")
    public Result<ProductImageResponse> setCover(@PathVariable Long productId, @PathVariable Long imageId) {
        return Result.success(productService.setCover(productId, imageId));
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    @Operation(summary = "删除图片", description = "删除自己的商品图片，若删的是封面会自动顺延下一张")
    public Result<Void> deleteImage(@PathVariable Long productId, @PathVariable Long imageId) {
        productService.deleteImage(productId, imageId);
        return Result.success();
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
