package org.example.secondhandtradingplatform.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.admin.dto.AdminCategoryCreateRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminCategoryResponse;
import org.example.secondhandtradingplatform.admin.dto.AdminCategoryUpdateRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminOrderQueryRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminProductAuditRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminUserQueryRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminUserUpdateRequest;
import org.example.secondhandtradingplatform.admin.service.AdminService;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.order.dto.OrderResponse;
import org.example.secondhandtradingplatform.product.dto.ProductQueryRequest;
import org.example.secondhandtradingplatform.product.dto.ProductResponse;
import org.example.secondhandtradingplatform.report.dto.ReportHandleRequest;
import org.example.secondhandtradingplatform.report.dto.ReportListRequest;
import org.example.secondhandtradingplatform.report.dto.ReportResponse;
import org.example.secondhandtradingplatform.report.service.ReportService;
import org.example.secondhandtradingplatform.user.dto.UserProfileResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理后台", description = "商品审核、举报处理、用户管理、分类管理、订单查看")
public class AdminController {

    private final AdminService adminService;
    private final ReportService reportService;

    public AdminController(AdminService adminService, ReportService reportService) {
        this.adminService = adminService;
        this.reportService = reportService;
    }

    @GetMapping("/products")
    @Operation(summary = "后台商品列表", description = "管理员按状态、分类、发布人、关键词等条件查看商品")
    public Result<Page<ProductResponse>> listProducts(@Valid ProductQueryRequest request) {
        return Result.success(adminService.listProducts(request));
    }

    @PutMapping("/products/{productId}/audit")
    @Operation(summary = "商品审核", description = "status=2 表示审核通过上架，status=3 表示驳回/下架")
    public Result<ProductResponse> auditProduct(@PathVariable Long productId,
                                                @Valid @RequestBody AdminProductAuditRequest request) {
        return Result.success(adminService.auditProduct(productId, request));
    }

    @GetMapping("/reports")
    @Operation(summary = "后台举报列表", description = "管理员按状态和举报对象类型筛选举报")
    public Result<Page<ReportResponse>> listReports(@Valid ReportListRequest request) {
        return Result.success(reportService.listForAdmin(request));
    }

    @PutMapping("/reports/{reportId}")
    @Operation(summary = "处理举报", description = "管理员将待处理举报更新为已受理、已驳回或已关闭")
    public Result<ReportResponse> handleReport(@PathVariable Long reportId,
                                               @Valid @RequestBody ReportHandleRequest request) {
        return Result.success(reportService.handle(reportId, request));
    }

    @GetMapping("/users")
    @Operation(summary = "后台用户列表", description = "管理员按关键词、角色和状态查询用户")
    public Result<Page<UserProfileResponse>> listUsers(@Valid AdminUserQueryRequest request) {
        return Result.success(adminService.listUsers(request));
    }

    @PutMapping("/users/{userId}")
    @Operation(summary = "更新用户", description = "管理员修改用户资料、角色和状态")
    public Result<UserProfileResponse> updateUser(@PathVariable Long userId,
                                                  @Valid @RequestBody AdminUserUpdateRequest request) {
        return Result.success(adminService.updateUser(userId, request));
    }

    @GetMapping("/categories")
    @Operation(summary = "后台分类树", description = "管理员查看全部分类，包含禁用分类")
    public Result<List<AdminCategoryResponse>> listCategories() {
        return Result.success(adminService.listCategories());
    }

    @PostMapping("/categories")
    @Operation(summary = "新增分类")
    public Result<AdminCategoryResponse> createCategory(@Valid @RequestBody AdminCategoryCreateRequest request) {
        return Result.success(adminService.createCategory(request));
    }

    @PutMapping("/categories/{categoryId}")
    @Operation(summary = "更新分类")
    public Result<AdminCategoryResponse> updateCategory(@PathVariable Long categoryId,
                                                        @Valid @RequestBody AdminCategoryUpdateRequest request) {
        return Result.success(adminService.updateCategory(categoryId, request));
    }

    @DeleteMapping("/categories/{categoryId}")
    @Operation(summary = "删除分类", description = "存在子分类或商品时不能删除")
    public Result<Void> deleteCategory(@PathVariable Long categoryId) {
        adminService.deleteCategory(categoryId);
        return Result.success();
    }

    @GetMapping("/orders")
    @Operation(summary = "后台订单列表", description = "管理员按订单号、状态、买家、卖家和商品筛选订单")
    public Result<Page<OrderResponse>> listOrders(@Valid AdminOrderQueryRequest request) {
        return Result.success(adminService.listOrders(request));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "后台订单详情")
    public Result<OrderResponse> getOrder(@PathVariable Long orderId) {
        return Result.success(adminService.getOrder(orderId));
    }
}
