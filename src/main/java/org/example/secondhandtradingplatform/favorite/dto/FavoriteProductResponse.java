package org.example.secondhandtradingplatform.favorite.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.product.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FavoriteProductResponse {

    private Long id;
    private Long sellerId;
    private String sellerNickname;
    private String sellerAvatar;
    private Long categoryId;
    private String title;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String description;
    private String coverImage;
    private Integer exchangeType;
    private Integer deliveryMethod;
    private String location;
    private Integer status;
    private Integer browseCount;
    private Integer favoriteCount;
    private String contactPhone;
    private LocalDateTime publishedTime;
    private LocalDateTime createTime;

    public static FavoriteProductResponse from(Product product) {
        FavoriteProductResponse response = new FavoriteProductResponse();
        response.setId(product.getId());
        response.setSellerId(product.getSellerId());
        response.setCategoryId(product.getCategoryId());
        response.setTitle(product.getTitle());
        response.setPrice(product.getPrice());
        response.setOriginalPrice(product.getOriginalPrice());
        response.setDescription(product.getDescription());
        response.setCoverImage(product.getCoverImage());
        response.setExchangeType(product.getExchangeType());
        response.setDeliveryMethod(product.getDeliveryMethod());
        response.setLocation(product.getLocation());
        response.setStatus(product.getStatus());
        response.setBrowseCount(product.getBrowseCount());
        response.setFavoriteCount(product.getFavoriteCount());
        response.setContactPhone(product.getContactPhone());
        response.setPublishedTime(product.getPublishedTime());
        response.setCreateTime(product.getCreateTime());
        return response;
    }
}
