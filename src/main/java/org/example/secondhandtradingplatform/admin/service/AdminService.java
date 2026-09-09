package org.example.secondhandtradingplatform.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.admin.dto.AdminCategoryCreateRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminCategoryResponse;
import org.example.secondhandtradingplatform.admin.dto.AdminCategoryUpdateRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminOrderQueryRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminProductAuditRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminUserQueryRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminUserUpdateRequest;
import org.example.secondhandtradingplatform.order.dto.OrderResponse;
import org.example.secondhandtradingplatform.product.dto.ProductQueryRequest;
import org.example.secondhandtradingplatform.product.dto.ProductResponse;
import org.example.secondhandtradingplatform.user.dto.UserProfileResponse;

import java.util.List;

public interface AdminService {

    Page<ProductResponse> listProducts(ProductQueryRequest request);

    ProductResponse auditProduct(Long productId, AdminProductAuditRequest request);

    Page<UserProfileResponse> listUsers(AdminUserQueryRequest request);

    UserProfileResponse updateUser(Long userId, AdminUserUpdateRequest request);

    List<AdminCategoryResponse> listCategories();

    AdminCategoryResponse createCategory(AdminCategoryCreateRequest request);

    AdminCategoryResponse updateCategory(Long categoryId, AdminCategoryUpdateRequest request);

    void deleteCategory(Long categoryId);

    Page<OrderResponse> listOrders(AdminOrderQueryRequest request);

    OrderResponse getOrder(Long orderId);
}
