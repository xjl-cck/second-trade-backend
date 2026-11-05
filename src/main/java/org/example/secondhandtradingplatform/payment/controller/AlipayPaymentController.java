package org.example.secondhandtradingplatform.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.payment.config.AlipayProperties;
import org.example.secondhandtradingplatform.payment.dto.PaymentCreateResponse;
import org.example.secondhandtradingplatform.payment.dto.PaymentRefundResponse;
import org.example.secondhandtradingplatform.payment.dto.PaymentStatusResponse;
import org.example.secondhandtradingplatform.payment.service.AlipayPaymentService;
import org.example.secondhandtradingplatform.payment.service.AlipayRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/alipay")
@Tag(name = "支付宝支付", description = "电脑网站支付：下单拿收银台表单、异步通知、同步跳回与主动查询")
public class AlipayPaymentController {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaymentController.class);

    private final AlipayPaymentService alipayPaymentService;
    private final AlipayRefundService alipayRefundService;
    private final AlipayProperties alipayProperties;

    public AlipayPaymentController(AlipayPaymentService alipayPaymentService,
                                   AlipayRefundService alipayRefundService,
                                   AlipayProperties alipayProperties) {
        this.alipayPaymentService = alipayPaymentService;
        this.alipayRefundService = alipayRefundService;
        this.alipayProperties = alipayProperties;
    }

    @PostMapping("/pay/{orderId}")
    @Operation(summary = "发起支付宝支付",
            description = "返回 formHtml，前端把它写入页面即可自动跳转支付宝收银台（沙箱用沙箱买家账号付款）")
    public Result<PaymentCreateResponse> pay(@PathVariable Long orderId) {
        return Result.success(alipayPaymentService.createPagePay(orderId));
    }

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "支付宝异步通知",
            description = "支付宝服务器回调，验签通过并处理成功返回 success，否则返回 failure 让其重试")
    public ResponseEntity<String> notify(@RequestParam Map<String, String> params) {
        try {
            alipayPaymentService.handleNotify(params);
            return ResponseEntity.ok("success");
        } catch (BizException e) {
            // 验签失败、app_id 不匹配、金额不一致这类是预期内的校验失败，只记一行，不打堆栈
            log.warn("支付宝异步通知被拒绝，outTradeNo={}，tradeNo={}，原因={}",
                    params.get("out_trade_no"), params.get("trade_no"), e.getMessage());
            return ResponseEntity.ok("failure");
        } catch (Exception e) {
            log.error("处理支付宝异步通知异常，outTradeNo={}", params.get("out_trade_no"), e);
            return ResponseEntity.ok("failure");
        }
    }

    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "支付宝同步跳回",
            description = "用户付款后浏览器跳回的地址；这里会主动查询一次交易状态作为异步通知的兜底")
    public ResponseEntity<String> syncReturn(@RequestParam Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        PaymentStatusResponse status = null;
        String error = null;
        if (StringUtils.hasText(outTradeNo)) {
            try {
                status = alipayPaymentService.syncByOutTradeNo(outTradeNo);
            } catch (Exception e) {
                log.warn("同步跳回时查询支付宝交易失败，outTradeNo={}", outTradeNo, e);
                error = e.getMessage();
            }
        }
        if (StringUtils.hasText(alipayProperties.getReturnPage())) {
            String target = alipayProperties.getReturnPage()
                    + (alipayProperties.getReturnPage().contains("?") ? "&" : "?")
                    + "outTradeNo=" + URLEncoder.encode(outTradeNo == null ? "" : outTradeNo, StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, target)
                    .body(null);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(renderResultPage(outTradeNo, status, error));
    }

    @GetMapping("/status/{orderNo}")
    @Operation(summary = "查询并同步支付状态",
            description = "前端轮询用：会主动向支付宝查询一次，若已支付则同步推进本地订单状态")
    public Result<PaymentStatusResponse> status(@PathVariable String orderNo) {
        return Result.success(alipayPaymentService.queryAndSync(orderNo));
    }

    @GetMapping("/refund/{returnRequestId}")
    @Operation(summary = "查询退款结果",
            description = "退货单确认退款后，前端可用它轮询；会主动向支付宝查询退款单并同步本地记录")
    public Result<PaymentRefundResponse> refundStatus(@PathVariable Long returnRequestId) {
        return Result.success(alipayRefundService.queryAndSync(returnRequestId));
    }

    private String renderResultPage(String outTradeNo, PaymentStatusResponse status, String error) {
        String state;
        if (error != null) {
            state = "查询支付结果失败：" + error;
        } else if (status != null && status.isPaid()) {
            state = "支付成功，订单已进入" + (status.getOrderStatusText() == null ? "已成交" : status.getOrderStatusText()) + "状态";
        } else {
            state = "暂未查询到支付成功记录，请稍后在订单列表确认";
        }
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head><meta charset="utf-8"><title>支付结果</title></head>
                <body style="font-family:system-ui,-apple-system,'Segoe UI',sans-serif;padding:48px;line-height:1.8">
                  <h2>支付宝支付结果</h2>
                  <p>订单号：%s</p>
                  <p>状态：%s</p>
                  <p style="color:#666">可以关闭本页面，回到平台查看订单详情。</p>
                </body>
                </html>
                """.formatted(escape(outTradeNo == null ? "-" : outTradeNo), escape(state));
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
