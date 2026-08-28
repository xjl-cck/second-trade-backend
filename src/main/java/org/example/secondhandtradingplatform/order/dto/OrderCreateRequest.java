package org.example.secondhandtradingplatform.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderCreateRequest {

    @NotNull(message = "不能为空")
    private Long productId;
}
