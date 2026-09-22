package org.example.secondhandtradingplatform.favorite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.favorite.dto.FavoriteListRequest;
import org.example.secondhandtradingplatform.favorite.dto.FavoriteProductResponse;
import org.example.secondhandtradingplatform.favorite.service.FavoriteRedisService;
import org.example.secondhandtradingplatform.favorite.service.FavoriteService;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final int PRODUCT_ON_SALE = 2;

    private final ProductMapper productMapper;
    private final SysUserMapper sysUserMapper;
    private final FavoriteRedisService favoriteRedisService;

    public FavoriteServiceImpl(ProductMapper productMapper,
                               SysUserMapper sysUserMapper,
                               FavoriteRedisService favoriteRedisService) {
        this.productMapper = productMapper;
        this.sysUserMapper = sysUserMapper;
        this.favoriteRedisService = favoriteRedisService;
    }

    @Override
    public void favorite(Long productId) {
        Long userId = requireLoginUserId();
        Product product = getProduct(productId);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (!Integer.valueOf(PRODUCT_ON_SALE).equals(product.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "只能收藏上架中的商品");
        }
        favoriteRedisService.change(userId, productId, true);
    }

    @Override
    public void unfavorite(Long productId) {
        Long userId = requireLoginUserId();
        favoriteRedisService.change(userId, productId, false);
    }

    @Override
    public Page<FavoriteProductResponse> list(FavoriteListRequest request) {
        Long userId = requireLoginUserId();
        if (request == null) {
            request = new FavoriteListRequest();
        }
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        long total = favoriteRedisService.userFavoriteCount(userId);
        List<Long> productIds = favoriteRedisService.userProductIds(userId, (pageNo - 1) * pageSize, pageSize)
                .stream()
                .map(Long::valueOf)
                .toList();
        if (productIds.isEmpty()) {
            Page<FavoriteProductResponse> emptyPage = Page.of(pageNo, pageSize, total);
            emptyPage.setRecords(List.of());
            return emptyPage;
        }

        List<Product> products = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .in(Product::getId, productIds));
        Map<Long, Product> productMap = new LinkedHashMap<>();
        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        List<FavoriteProductResponse> records = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = productMap.get(productId);
            if (product == null) {
                continue;
            }
            FavoriteProductResponse response = FavoriteProductResponse.from(product);
            SysUser seller = sysUserMapper.selectById(product.getSellerId());
            if (seller != null) {
                response.setSellerNickname(seller.getNickname());
                response.setSellerAvatar(seller.getAvatar());
            }
            records.add(response);
        }

        Page<FavoriteProductResponse> responsePage = Page.of(pageNo, pageSize, total);
        responsePage.setRecords(records);
        return responsePage;
    }

    private Product getProduct(Long productId) {
        return productMapper.selectById(productId);
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
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
        return Math.min(pageSize, 50);
    }
}
