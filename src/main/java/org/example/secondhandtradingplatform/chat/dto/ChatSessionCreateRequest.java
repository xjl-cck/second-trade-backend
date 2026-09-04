package org.example.secondhandtradingplatform.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatSessionCreateRequest {

    @NotNull(message = "商品不能为空")
    private Long productId;
}
