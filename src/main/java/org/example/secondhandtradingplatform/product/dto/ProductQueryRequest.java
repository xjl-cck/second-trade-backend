package org.example.secondhandtradingplatform.product.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductQueryRequest {

    private Long campusId;
    private Long categoryId;
    private String keyword;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    /**
     * latest = 最新, hot = 最热
     */
    private String sortType;
    private Integer status;
    private Long sellerId;

    @Min(value = 1, message = "不能小于1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "不能小于1")
    private Integer pageSize = 10;
}
