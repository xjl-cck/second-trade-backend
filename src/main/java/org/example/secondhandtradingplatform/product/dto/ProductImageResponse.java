package org.example.secondhandtradingplatform.product.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.product.entity.ProductImage;

@Data
public class ProductImageResponse {

    private Long id;
    private Long productId;
    private String url;
    private Integer isCover;
    private Integer sort;

    public static ProductImageResponse from(ProductImage image) {
        ProductImageResponse response = new ProductImageResponse();
        response.setId(image.getId());
        response.setProductId(image.getProductId());
        response.setUrl(image.getUrl());
        response.setIsCover(image.getIsCover());
        response.setSort(image.getSort());
        return response;
    }
}
