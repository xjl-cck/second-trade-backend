package org.example.secondhandtradingplatform.order.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderCancelRequest {

    @Size(max = 255, message = "不能超过255个字符")
    private String reason;
}
