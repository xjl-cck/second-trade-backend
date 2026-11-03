package org.example.secondhandtradingplatform.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建支付后返回给前端的数据。formHtml 是支付宝返回的自动提交表单，
 * 前端直接 document.write 或放入 iframe 就会跳到收银台。
 */
@Data
public class PaymentCreateResponse {

    private String orderNo;
    private String outTradeNo;
    private String channel;
    private String payMethod;
    private BigDecimal amount;
    private String formHtml;
}
