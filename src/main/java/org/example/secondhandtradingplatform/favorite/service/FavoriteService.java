package org.example.secondhandtradingplatform.favorite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.favorite.dto.FavoriteListRequest;
import org.example.secondhandtradingplatform.favorite.dto.FavoriteProductResponse;

public interface FavoriteService {

    void favorite(Long productId);

    void unfavorite(Long productId);

    Page<FavoriteProductResponse> list(FavoriteListRequest request);
}
