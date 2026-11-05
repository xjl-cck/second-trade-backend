package org.example.secondhandtradingplatform.product.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.product.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductResponse {

    private Long id;
    private Long sellerId;
    private String sellerNickname;
    private String sellerAvatar;
    private Long categoryId;
    private String title;
    private BigDecimal price;
    private Integer stock;
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
    private List<ProductImageResponse> images = new ArrayList<>();

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSellerId(product.getSellerId());
        response.setCategoryId(product.getCategoryId());
        response.setTitle(product.getTitle());
        response.setPrice(product.getPrice());
        response.setStock(product.getStock());
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
