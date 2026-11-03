package org.example.secondhandtradingplatform.payment.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 仅当 alipay.enabled=true 时才创建客户端，避免本地没有密钥时启动失败。
 */
@Configuration
@ConditionalOnProperty(prefix = "alipay", name = "enabled", havingValue = "true")
public class AlipayClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AlipayClientConfig.class);

    @Bean
    public AlipayClient alipayClient(AlipayProperties properties) {
        try {
            String appId = require(properties.getAppId(), "alipay.app-id（对应环境变量 ALIPAY_APP_ID）");
            String publicKey = require(properties.getAlipayPublicKey(), "alipay.alipay-public-key（对应环境变量 ALIPAY_PUBLIC_KEY）");
            String privateKey = resolvePrivateKey(properties);
            AlipayClient client = new DefaultAlipayClient(
                    properties.getGatewayUrl(),
                    appId,
                    privateKey,
                    properties.getFormat(),
                    properties.getCharset(),
                    publicKey,
                    properties.getSignType());
            log.info("支付宝支付已启用，网关={}，appId={}，签名算法={}",
                    properties.getGatewayUrl(), appId, properties.getSignType());
            return client;
        } catch (RuntimeException e) {
            // 密钥没配好不应该阻塞整个应用启动：支付接口会在调用时明确报错，其它功能照常可用
            log.error("支付宝客户端初始化失败，支付接口将不可用（其它功能不受影响）：{}", e.getMessage());
            return null;
        }
    }

    private String resolvePrivateKey(AlipayProperties properties) {
        String pem = properties.getPrivateKey();
        if (!StringUtils.hasText(pem) && StringUtils.hasText(properties.getPrivateKeyPath())) {
            Path path = Path.of(properties.getPrivateKeyPath().trim());
            if (!Files.isReadable(path)) {
                throw new IllegalStateException("支付宝应用私钥文件不存在或不可读：" + path.toAbsolutePath()
                        + "。请把沙箱「系统默认密钥 → 查看」里的应用私钥保存到该路径，"
                        + "或删掉 ALIPAY_APP_PRIVATE_KEY_PATH、改用 ALIPAY_APP_PRIVATE_KEY 直接内联私钥内容");
            }
            try {
                pem = Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("读取支付宝应用私钥文件失败：" + path.toAbsolutePath(), e);
            }
        }
        if (!StringUtils.hasText(pem)) {
            throw new IllegalStateException("未配置支付宝应用私钥：请设置 ALIPAY_APP_PRIVATE_KEY 或 ALIPAY_APP_PRIVATE_KEY_PATH");
        }
        return pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }

    private String require(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("缺少支付宝配置项：" + name);
        }
        return value.trim();
    }
}
