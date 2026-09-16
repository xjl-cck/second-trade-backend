package org.example.secondhandtradingplatform.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderCreateRequest {

    @NotNull(message = "不能为空")
    private Long productId;

    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    @NotBlank(message = "幂等请求号不能为空")
    @Size(max = 64, message = "幂等请求号不能超过64个字符")
    private String idempotencyKey;
}
