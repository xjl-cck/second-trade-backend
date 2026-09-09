package org.example.secondhandtradingplatform.admin.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminOrderQueryRequest {

    private Integer status;
    private Long buyerId;
    private Long sellerId;
    private Long productId;
    private String orderNo;

    @Min(value = 1, message = "不能小于1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "不能小于1")
    private Integer pageSize = 10;
}
