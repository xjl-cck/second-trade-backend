package org.example.secondhandtradingplatform.order.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OrderStatusEvent implements Serializable {

    private Long orderId;
    private String orderNo;
    private Long productId;
    private String productTitle;
    private Long buyerId;
    private Long sellerId;
    private Integer status;
    private String statusText;
    private LocalDateTime eventTime;
}
