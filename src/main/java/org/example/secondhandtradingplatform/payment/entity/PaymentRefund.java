package org.example.secondhandtradingplatform.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款流水。一张退货单对应一条记录，out_request_no 用于支付宝幂等。
 */
@Data
@TableName("payment_refund")
public class PaymentRefund {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderNo;
    private String outTradeNo;
    private String tradeNo;
    private Long returnRequestId;
    private String outRequestNo;
    private BigDecimal amount;
    private BigDecimal refundFee;
    private String fundChange;
    private Integer status;
    private String error;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
