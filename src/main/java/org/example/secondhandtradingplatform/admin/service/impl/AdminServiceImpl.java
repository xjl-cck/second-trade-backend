package org.example.secondhandtradingplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.admin.dto.AdminCategoryCreateRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminCategoryResponse;
import org.example.secondhandtradingplatform.admin.dto.AdminCategoryUpdateRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminOrderQueryRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminProductAuditRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminUserQueryRequest;
import org.example.secondhandtradingplatform.admin.dto.AdminUserUpdateRequest;
import org.example.secondhandtradingplatform.admin.service.AdminService;
import org.example.secondhandtradingplatform.category.entity.Category;
import org.example.secondhandtradingplatform.category.mapper.CategoryMapper;
import org.example.secondhandtradingplatform.category.service.CategoryService;
import org.example.secondhandtradingplatform.common.cache.RedisCacheService;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.order.dto.OrderResponse;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.mapper.TradeOrderMapper;
import org.example.secondhandtradingplatform.product.dto.ProductImageResponse;
import org.example.secondhandtradingplatform.product.dto.ProductQueryRequest;
import org.example.secondhandtradingplatform.product.dto.ProductResponse;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.entity.ProductImage;
import org.example.secondhandtradingplatform.product.mapper.ProductImageMapper;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.product.service.impl.ProductSearchIndex;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.dto.UserProfileResponse;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminServiceImpl implements AdminService {

    private static final int ADMIN_ROLE = 1;
    private static final int CATEGORY_ENABLED = 1;
    private static final int PRODUCT_PENDING = 1;
    private static final int PRODUCT_ON_SALE = 2;
    private static final int PRODUCT_OFF_SALE = 3;

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final SysUserMapper sysUserMapper;
    private final CategoryMapper categoryMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final RedisCacheService redisCacheService;
    private final ProductSearchIndex productSearchIndex;

    public AdminServiceImpl(ProductMapper productMapper,
                            ProductImageMapper productImageMapper,
                            SysUserMapper sysUserMapper,
                            CategoryMapper categoryMapper,
                            TradeOrderMapper tradeOrderMapper,
                            RedisCacheService redisCacheService,
                            ProductSearchIndex productSearchIndex) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.sysUserMapper = sysUserMapper;
        this.categoryMapper = categoryMapper;
        this.tradeOrderMapper = tradeOrderMapper;
        this.redisCacheService = redisCacheService;
        this.productSearchIndex = productSearchIndex;
    }

    @Override
    public Page<ProductResponse> listProducts(ProductQueryRequest request) {
        requireAdmin();
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        LambdaQueryWrapper<Product> query = Wrappers.lambdaQuery(Product.class)
                .eq(request.getCategoryId() != null, Product::getCategoryId, request.getCategoryId())
                .eq(request.getSellerId() != null, Product::getSellerId, request.getSellerId())
                .eq(request.getStatus() != null, Product::getStatus, request.getStatus())
                .ge(request.getMinPrice() != null, Product::getPrice, request.getMinPrice())
                .le(request.getMaxPrice() != null, Product::getPrice, request.getMaxPrice())
                .like(StringUtils.hasText(request.getKeyword()), Product::getTitle, trimToNull(request.getKeyword()))
                .orderByDesc(Product::getCreateTime)
                .orderByDesc(Product::getId);
        Page<Product> productPage = productMapper.selectPage(Page.of(pageNo, pageSize), query);
        Page<ProductResponse> responsePage = Page.of(pageNo, pageSize, productPage.getTotal());
        responsePage.setRecords(productPage.getRecords().stream()
                .map(product -> buildProductResponse(product, false))
                .toList());
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductResponse auditProduct(Long productId, AdminProductAuditRequest request) {
        requireAdmin();
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (!List.of(PRODUCT_PENDING, PRODUCT_ON_SALE, PRODUCT_OFF_SALE).contains(product.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "当前商品状态不能审核");
        }
        product.setStatus(request.getStatus());
        product.setAuditReason(trimToNull(request.getAuditReason()));
        if (Integer.valueOf(PRODUCT_ON_SALE).equals(request.getStatus()) && product.getPublishedTime() == null) {
            product.setPublishedTime(LocalDateTime.now());
        }
        productMapper.updateById(product);
        productSearchIndex.index(product);
        redisCacheService.delete("product:detail:" + productId);
        redisCacheService.deleteByPattern("product:hot:*");
        return buildProductResponse(product, true);
    }

    @Override
    public Page<UserProfileResponse> listUsers(AdminUserQueryRequest request) {
        requireAdmin();
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        String keyword = trimToNull(request.getKeyword());
        LambdaQueryWrapper<SysUser> query = Wrappers.lambdaQuery(SysUser.class)
                .eq(request.getRole() != null, SysUser::getRole, request.getRole())
                .eq(request.getStatus() != null, SysUser::getStatus, request.getStatus())
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(SysUser::getUsername, keyword)
                        .or()
                        .like(SysUser::getNickname, keyword)
                        .or()
                        .like(SysUser::getPhone, keyword)
                        .or()
                        .like(SysUser::getStudentNo, keyword))
                .orderByDesc(SysUser::getCreateTime)
                .orderByDesc(SysUser::getId);
        Page<SysUser> userPage = sysUserMapper.selectPage(Page.of(pageNo, pageSize), query);
        Page<UserProfileResponse> responsePage = Page.of(pageNo, pageSize, userPage.getTotal());
        responsePage.setRecords(userPage.getRecords().stream()
                .map(UserProfileResponse::from)
                .toList());
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateUser(Long userId, AdminUserUpdateRequest request) {
        requireAdmin();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (request.getNickname() != null) {
            if (!StringUtils.hasText(request.getNickname())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "昵称不能为空");
            }
            user.setNickname(request.getNickname().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(trimToNull(request.getPhone()));
        }
        if (request.getAvatar() != null) {
            user.setAvatar(trimToNull(request.getAvatar()));
        }
        if (request.getStudentNo() != null) {
            user.setStudentNo(trimToNull(request.getStudentNo()));
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getMajor() != null) {
            user.setMajor(trimToNull(request.getMajor()));
        }
        if (request.getGrade() != null) {
            user.setGrade(trimToNull(request.getGrade()));
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        sysUserMapper.updateById(user);
        return UserProfileResponse.from(user);
    }

    @Override
    public List<AdminCategoryResponse> listCategories() {
        requireAdmin();
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        return buildCategoryTree(categories);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCategoryResponse createCategory(AdminCategoryCreateRequest request) {
        requireAdmin();
        validateParent(request.getParentId(), null);
        Category category = new Category();
        category.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        category.setName(request.getName().trim());
        category.setIcon(trimToNull(request.getIcon()));
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        category.setStatus(request.getStatus() == null ? CATEGORY_ENABLED : request.getStatus());
        category.setIsDeleted(0);
        categoryMapper.insert(category);
        evictCategoryCache();
        return AdminCategoryResponse.from(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminCategoryResponse updateCategory(Long categoryId, AdminCategoryUpdateRequest request) {
        requireAdmin();
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        if (request.getParentId() != null) {
            validateParent(request.getParentId(), categoryId);
            category.setParentId(request.getParentId());
        }
        if (StringUtils.hasText(request.getName())) {
            category.setName(request.getName().trim());
        }
        if (request.getIcon() != null) {
            category.setIcon(trimToNull(request.getIcon()));
        }
        if (request.getSort() != null) {
            category.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }
        categoryMapper.updateById(category);
        evictCategoryCache();
        return AdminCategoryResponse.from(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long categoryId) {
        requireAdmin();
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        Long childCount = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery()
                .eq(Category::getParentId, categoryId));
        if (childCount != null && childCount > 0) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "存在子分类，不能删除");
        }
        Long productCount = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getCategoryId, categoryId));
        if (productCount != null && productCount > 0) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "分类下存在商品，不能删除");
        }
        categoryMapper.deleteById(categoryId);
        evictCategoryCache();
    }

    private void evictCategoryCache() {
        redisCacheService.delete(CategoryService.ENABLED_TREE_CACHE_KEY);
    }

    @Override
    public Page<OrderResponse> listOrders(AdminOrderQueryRequest request) {
        requireAdmin();
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        LambdaQueryWrapper<TradeOrder> query = Wrappers.lambdaQuery(TradeOrder.class)
                .eq(request.getStatus() != null, TradeOrder::getStatus, request.getStatus())
                .eq(request.getBuyerId() != null, TradeOrder::getBuyerId, request.getBuyerId())
                .eq(request.getSellerId() != null, TradeOrder::getSellerId, request.getSellerId())
                .eq(request.getProductId() != null, TradeOrder::getProductId, request.getProductId())
                .like(StringUtils.hasText(request.getOrderNo()), TradeOrder::getOrderNo, trimToNull(request.getOrderNo()))
                .orderByDesc(TradeOrder::getCreateTime)
                .orderByDesc(TradeOrder::getId);
        Page<TradeOrder> orderPage = tradeOrderMapper.selectPage(Page.of(pageNo, pageSize), query);
        Page<OrderResponse> responsePage = Page.of(pageNo, pageSize, orderPage.getTotal());
        responsePage.setRecords(orderPage.getRecords().stream()
                .map(this::buildOrderResponse)
                .toList());
        return responsePage;
    }

    @Override
    public OrderResponse getOrder(Long orderId) {
        requireAdmin();
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return buildOrderResponse(order);
    }

    private ProductResponse buildProductResponse(Product product, boolean withImages) {
        ProductResponse response = ProductResponse.from(product);
        SysUser seller = sysUserMapper.selectById(product.getSellerId());
        if (seller != null) {
            response.setSellerNickname(seller.getNickname());
            response.setSellerAvatar(seller.getAvatar());
        }
        if (withImages) {
            List<ProductImageResponse> images = productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                            .eq(ProductImage::getProductId, product.getId())
                            .orderByDesc(ProductImage::getIsCover)
                            .orderByAsc(ProductImage::getSort)
                            .orderByAsc(ProductImage::getId))
                    .stream()
                    .map(ProductImageResponse::from)
                    .toList();
            response.setImages(images);
        }
        return response;
    }

    private OrderResponse buildOrderResponse(TradeOrder order) {
        OrderResponse response = OrderResponse.from(order);
        Product product = productMapper.selectById(order.getProductId());
        if (product != null) {
            response.setProductTitle(product.getTitle());
            response.setProductCoverImage(product.getCoverImage());
        }
        SysUser buyer = sysUserMapper.selectById(order.getBuyerId());
        if (buyer != null) {
            response.setBuyerNickname(buyer.getNickname());
        }
        SysUser seller = sysUserMapper.selectById(order.getSellerId());
        if (seller != null) {
            response.setSellerNickname(seller.getNickname());
        }
        return response;
    }

    private List<AdminCategoryResponse> buildCategoryTree(List<Category> categories) {
        Map<Long, AdminCategoryResponse> categoryMap = new LinkedHashMap<>();
        List<AdminCategoryResponse> roots = new ArrayList<>();
        for (Category category : categories) {
            categoryMap.put(category.getId(), AdminCategoryResponse.from(category));
        }
        for (AdminCategoryResponse category : categoryMap.values()) {
            AdminCategoryResponse parent = categoryMap.get(category.getParentId());
            if (parent == null) {
                roots.add(category);
            } else {
                parent.getChildren().add(category);
            }
        }
        return roots;
    }

    private void validateParent(Long parentId, Long categoryId) {
        if (parentId == null || parentId == 0) {
            return;
        }
        if (parentId.equals(categoryId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "父级分类不能是自己");
        }
        if (categoryMapper.selectById(parentId) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "父级分类不存在");
        }
    }

    private Long requireAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !Integer.valueOf(ADMIN_ROLE).equals(user.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可以操作");
        }
        return userId;
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
