package org.example.secondhandtradingplatform.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝开放平台配置。
 *
 * <p>默认指向沙箱网关；上正式环境时把 gateway-url 换成
 * https://openapi.alipay.com/gateway.do，并替换 app-id 与三把密钥。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 是否启用支付宝支付；关闭时支付接口会直接报错，原有 /api/orders/{id}/pay 模拟支付仍可用 */
    private boolean enabled = false;

    /** 网关地址，沙箱与正式环境不同 */
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /** 应用 ID */
    private String appId;

    /** 应用私钥（PKCS8，去掉 PEM 头尾的单行或带换行内容均可） */
    private String privateKey;

    /** 应用私钥文件路径，与 private-key 二选一 */
    private String privateKeyPath;

    /** 支付宝公钥，用于校验异步通知与主动查询结果的签名 */
    private String alipayPublicKey;

    /** 签名算法，固定 RSA2 */
    private String signType = "RSA2";

    /** 字符集 */
    private String charset = "utf-8";

    /** 报文格式 */
    private String format = "json";

    /** 异步通知地址，必须是公网可访问的地址 */
    private String notifyUrl;

    /** 同步跳回地址 */
    private String returnUrl;

    /** 支付完成后前端结果页，留空则由后端返回一个简易结果页 */
    private String returnPage;

    /** 商品标题前缀 */
    private String subjectPrefix = "二手交易平台-";

    /** 订单支付超时时间，超过后支付宝会关闭交易 */
    private String timeoutExpress = "30m";
}
