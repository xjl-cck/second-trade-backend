package org.example.secondhandtradingplatform.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.category.entity.Category;
import org.example.secondhandtradingplatform.category.mapper.CategoryMapper;
import org.example.secondhandtradingplatform.common.cache.RedisCacheService;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.product.dto.ProductCreateRequest;
import org.example.secondhandtradingplatform.product.dto.ProductImageResponse;
import org.example.secondhandtradingplatform.product.dto.ProductQueryRequest;
import org.example.secondhandtradingplatform.product.dto.ProductResponse;
import org.example.secondhandtradingplatform.product.dto.ProductUpdateRequest;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.entity.ProductImage;
import org.example.secondhandtradingplatform.product.mapper.ProductImageMapper;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.product.service.ProductService;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final int STATUS_ENABLED = 1;
    private static final int PRODUCT_ON_SALE = 2;
    private static final int PRODUCT_OFF_SALE = 3;
    private static final int PRODUCT_SOLD = 4;
    private static final int YES = 1;
    private static final int NO = 0;
    private static final String PRODUCT_DETAIL_CACHE_PREFIX = "product:detail:";
    private static final String PRODUCT_BROWSE_COUNT_PREFIX = "product:browse:count:";

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final SysUserMapper sysUserMapper;
    private final Path uploadDir;
    private final OssStorageService ossStorageService;
    private final RedisCacheService redisCacheService;
    private final Duration cacheTtl;

    public ProductServiceImpl(ProductMapper productMapper,
                              ProductImageMapper productImageMapper,
                              CategoryMapper categoryMapper,
                              SysUserMapper sysUserMapper,
                              ObjectProvider<OssStorageService> ossStorageServiceProvider,
                              RedisCacheService redisCacheService,
                              @Value("${app.cache.product-detail-ttl-seconds:300}") long cacheTtlSeconds,
                              @Value("${app.upload.path:uploads}") String uploadPath) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.categoryMapper = categoryMapper;
        this.sysUserMapper = sysUserMapper;
        this.uploadDir = Path.of(uploadPath).toAbsolutePath().normalize();
        this.ossStorageService = ossStorageServiceProvider.getIfAvailable();
        this.redisCacheService = redisCacheService;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductResponse publish(ProductCreateRequest request) {
        Long userId = requireLoginUserId();
        validateCategory(request.getCategoryId());

        Product product = new Product();
        product.setSellerId(userId);
        product.setCampusId(request.getCampusId());
        product.setCategoryId(request.getCategoryId());
        product.setTitle(request.getTitle().trim());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setDescription(trimToNull(request.getDescription()));
        product.setCoverImage(trimToNull(request.getCoverImage()));
        product.setExchangeType(defaultInt(request.getExchangeType(), 0));
        product.setDeliveryMethod(defaultInt(request.getDeliveryMethod(), 0));
        product.setLocation(trimToNull(request.getLocation()));
        product.setStatus(PRODUCT_ON_SALE);
        product.setBrowseCount(0);
        product.setFavoriteCount(0);
        product.setContactPhone(trimToNull(request.getContactPhone()));
        product.setPublishedTime(LocalDateTime.now());
        product.setIsDeleted(0);
        productMapper.insert(product);
        return buildResponse(product, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductResponse update(Long productId, ProductUpdateRequest request) {
        Product product = getOwnedProduct(productId);
        if (Integer.valueOf(PRODUCT_SOLD).equals(product.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "已售出的商品不能编辑");
        }
        if (request.getCampusId() != null) {
            product.setCampusId(request.getCampusId());
        }
        if (request.getCategoryId() != null) {
            validateCategory(request.getCategoryId());
            product.setCategoryId(request.getCategoryId());
        }
        if (StringUtils.hasText(request.getTitle())) {
            product.setTitle(request.getTitle().trim());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getOriginalPrice() != null) {
            product.setOriginalPrice(request.getOriginalPrice());
        }
        if (request.getDescription() != null) {
            product.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getCoverImage() != null) {
            product.setCoverImage(trimToNull(request.getCoverImage()));
        }
        if (request.getExchangeType() != null) {
            product.setExchangeType(request.getExchangeType());
        }
        if (request.getDeliveryMethod() != null) {
            product.setDeliveryMethod(request.getDeliveryMethod());
        }
        if (request.getLocation() != null) {
            product.setLocation(trimToNull(request.getLocation()));
        }
        if (request.getContactPhone() != null) {
            product.setContactPhone(trimToNull(request.getContactPhone()));
        }
        productMapper.updateById(product);
        evictDetailCache(productId);
        return buildResponse(product, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void putOnSale(Long productId) {
        Product product = getOwnedProduct(productId);
        if (Integer.valueOf(PRODUCT_SOLD).equals(product.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "已售出的商品不能上架");
        }
        product.setStatus(PRODUCT_ON_SALE);
        if (product.getPublishedTime() == null) {
            product.setPublishedTime(LocalDateTime.now());
        }
        productMapper.updateById(product);
        evictDetailCache(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void takeOffSale(Long productId) {
        Product product = getOwnedProduct(productId);
        if (Integer.valueOf(PRODUCT_SOLD).equals(product.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "已售出的商品不能下架");
        }
        product.setStatus(PRODUCT_OFF_SALE);
        productMapper.updateById(product);
        evictDetailCache(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductResponse getDetail(Long productId) {
        String cacheKey = productDetailCacheKey(productId);
        ProductResponse cached = redisCacheService.get(cacheKey, ProductResponse.class);
        if (cached != null) {
            recordBrowse(productId);
            cached.setBrowseCount(defaultInt(cached.getBrowseCount(), 0)
                    + (int) redisCacheService.getLong(browseCountKey(productId)));
            return cached;
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        ProductResponse response = buildResponse(product, true);
        redisCacheService.set(cacheKey, response, cacheTtl);
        recordBrowse(productId);
        response.setBrowseCount(defaultInt(product.getBrowseCount(), 0)
                + (int) redisCacheService.getLong(browseCountKey(productId)));
        return response;
    }

    @Override
    public Page<ProductResponse> page(ProductQueryRequest request) {
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        Integer status = request.getStatus();
        if (status == null && request.getSellerId() == null) {
            status = PRODUCT_ON_SALE;
        }
        LambdaQueryWrapper<Product> query = Wrappers.lambdaQuery(Product.class)
                .eq(request.getCampusId() != null, Product::getCampusId, request.getCampusId())
                .eq(request.getSellerId() != null, Product::getSellerId, request.getSellerId())
                .eq(status != null, Product::getStatus, status)
                .like(StringUtils.hasText(request.getKeyword()), Product::getTitle, trimToNull(request.getKeyword()));

        if (request.getCategoryId() != null) {
            List<Long> categoryIds = resolveCategoryIds(request.getCategoryId());
            if (categoryIds.isEmpty()) {
                return emptyPage(pageNo, pageSize);
            }
            query.in(Product::getCategoryId, categoryIds);
        }
        if (request.getMinPrice() != null) {
            query.ge(Product::getPrice, request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            query.le(Product::getPrice, request.getMaxPrice());
        }

        applySort(query, request.getSortType());
        Page<Product> productPage = productMapper.selectPage(Page.of(pageNo, pageSize), query);
        Page<ProductResponse> responsePage = Page.of(pageNo, pageSize, productPage.getTotal());
        Map<Long, SysUser> sellers = sysUserMapper.selectBatchIds(productPage.getRecords().stream()
                        .map(Product::getSellerId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<ProductResponse> records = productPage.getRecords().stream()
                .map(product -> buildResponse(product, false, sellers.get(product.getSellerId())))
                .toList();
        responsePage.setRecords(records);
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductImageResponse uploadImage(Long productId, MultipartFile file, Boolean cover, Integer sort) {
        Product product = getOwnedProduct(productId);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能为空");
        }
        String url = saveFile(file);
        boolean coverImage = Boolean.TRUE.equals(cover) || !StringUtils.hasText(product.getCoverImage());
        if (coverImage) {
            productImageMapper.update(null, Wrappers.<ProductImage>lambdaUpdate()
                    .set(ProductImage::getIsCover, NO)
                    .eq(ProductImage::getProductId, productId));
        }
        ProductImage image = new ProductImage();
        image.setProductId(productId);
        image.setUrl(url);
        image.setIsCover(coverImage ? YES : NO);
        image.setSort(defaultInt(sort, 0));
        image.setIsDeleted(0);
        productImageMapper.insert(image);
        if (coverImage) {
            product.setCoverImage(url);
            productMapper.updateById(product);
        }
        evictDetailCache(productId);
        return ProductImageResponse.from(image);
    }

    private Product getOwnedProduct(Long productId) {
        Long userId = requireLoginUserId();
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (!userId.equals(product.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        return product;
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private void validateCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || !Integer.valueOf(STATUS_ENABLED).equals(category.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "商品分类不存在或已禁用");
        }
    }

    private List<Long> resolveCategoryIds(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || !Integer.valueOf(STATUS_ENABLED).equals(category.getStatus())) {
            return List.of();
        }
        List<Long> categoryIds = new ArrayList<>();
        categoryIds.add(categoryId);
        List<Category> children = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getParentId, categoryId)
                .eq(Category::getStatus, STATUS_ENABLED)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        for (Category child : children) {
            categoryIds.add(child.getId());
        }
        return categoryIds;
    }

    private void applySort(LambdaQueryWrapper<Product> query, String sortType) {
        if ("hot".equalsIgnoreCase(sortType)) {
            query.orderByDesc(Product::getFavoriteCount)
                    .orderByDesc(Product::getBrowseCount)
                    .orderByDesc(Product::getPublishedTime)
                    .orderByDesc(Product::getCreateTime);
            return;
        }
        query.orderByDesc(Product::getPublishedTime)
                .orderByDesc(Product::getCreateTime);
    }

    private Page<ProductResponse> emptyPage(int pageNo, int pageSize) {
        Page<ProductResponse> responsePage = Page.of(pageNo, pageSize, 0);
        responsePage.setRecords(List.of());
        return responsePage;
    }

    private ProductResponse buildResponse(Product product, boolean withImages) {
        return buildResponse(product, withImages, sysUserMapper.selectById(product.getSellerId()));
    }

    private ProductResponse buildResponse(Product product, boolean withImages, SysUser seller) {
        ProductResponse response = ProductResponse.from(product);
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

    private String saveFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String suffix = "";
        if (StringUtils.hasText(originalName) && originalName.contains(".")) {
            suffix = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        if (!List.of(".jpg", ".jpeg", ".png", ".webp").contains(suffix)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 jpg、jpeg、png、webp 图片");
        }
        if (ossStorageService != null) {
            return ossStorageService.upload(file, suffix);
        }
        try {
            Files.createDirectories(uploadDir);
            String fileName = UUID.randomUUID() + suffix;
            Files.copy(file.getInputStream(), uploadDir.resolve(fileName));
            return "/uploads/" + fileName;
        } catch (IOException exception) {
            log.error("Save uploaded image failed. uploadDir={}", uploadDir, exception);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片保存失败");
        }
    }

    private void evictDetailCache(Long productId) {
        redisCacheService.delete(productDetailCacheKey(productId));
    }

    private void recordBrowse(Long productId) {
        redisCacheService.increment(browseCountKey(productId));
    }

    private String productDetailCacheKey(Long productId) {
        return PRODUCT_DETAIL_CACHE_PREFIX + productId;
    }

    private String browseCountKey(Long productId) {
        return PRODUCT_BROWSE_COUNT_PREFIX + productId;
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
