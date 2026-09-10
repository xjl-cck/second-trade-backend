package org.example.secondhandtradingplatform.favorite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.favorite.dto.FavoriteListRequest;
import org.example.secondhandtradingplatform.favorite.dto.FavoriteProductResponse;
import org.example.secondhandtradingplatform.favorite.entity.Favorite;
import org.example.secondhandtradingplatform.favorite.mapper.FavoriteMapper;
import org.example.secondhandtradingplatform.favorite.message.FavoriteEventPublisher;
import org.example.secondhandtradingplatform.favorite.service.FavoriteService;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final int PRODUCT_ON_SALE = 2;

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;
    private final SysUserMapper sysUserMapper;
    private final FavoriteEventPublisher favoriteEventPublisher;

    public FavoriteServiceImpl(FavoriteMapper favoriteMapper,
                               ProductMapper productMapper,
                               SysUserMapper sysUserMapper,
                               FavoriteEventPublisher favoriteEventPublisher) {
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
        this.sysUserMapper = sysUserMapper;
        this.favoriteEventPublisher = favoriteEventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favorite(Long productId) {
        Long userId = requireLoginUserId();
        Product product = getProduct(productId);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (!Integer.valueOf(PRODUCT_ON_SALE).equals(product.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "只能收藏上架中的商品");
        }
        if (existsFavorite(userId, productId)) {
            return;
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favorite.setIsDeleted(0);
        favoriteMapper.insert(favorite);
        favoriteEventPublisher.publishAfterCommit(productId, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavorite(Long productId) {
        Long userId = requireLoginUserId();
        Favorite favorite = favoriteMapper.selectOne(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        if (favorite == null) {
            return;
        }
        favoriteMapper.deleteById(favorite.getId());
        favoriteEventPublisher.publishAfterCommit(productId, -1);
    }

    @Override
    public Page<FavoriteProductResponse> list(FavoriteListRequest request) {
        Long userId = requireLoginUserId();
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());

        Page<Favorite> favoritePage = favoriteMapper.selectPage(Page.of(pageNo, pageSize), Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .orderByDesc(Favorite::getId));

        List<Long> productIds = favoritePage.getRecords().stream()
                .map(Favorite::getProductId)
                .toList();
        if (productIds.isEmpty()) {
            Page<FavoriteProductResponse> emptyPage = Page.of(pageNo, pageSize, favoritePage.getTotal());
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

        Page<FavoriteProductResponse> responsePage = Page.of(pageNo, pageSize, favoritePage.getTotal());
        responsePage.setRecords(records);
        return responsePage;
    }

    private boolean existsFavorite(Long userId, Long productId) {
        Long count = favoriteMapper.selectCount(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        return count != null && count > 0;
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
