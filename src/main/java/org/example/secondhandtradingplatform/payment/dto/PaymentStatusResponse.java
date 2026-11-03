package org.example.secondhandtradingplatform.payment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付状态。paid=true 表示钱已经到账（异步通知或主动查询已确认）。
 */
@Data
public class PaymentStatusResponse {

    private String orderNo;
    private String tradeNo;
    private String channel;
    private BigDecimal amount;
    private boolean paid;
    private Integer orderStatus;
    private String orderStatusText;
    private LocalDateTime paidTime;
    private LocalDateTime dealTime;
}
