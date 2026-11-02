package org.example.secondhandtradingplatform.outbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    /** 每轮扫描最多取多少条待投递事件 */
    private int batchSize = 50;
    /** 最大尝试次数，超过后置为投递失败并停止自动重试 */
    private int maxAttempts = 30;
    /** 首次重试间隔（秒），之后指数退避 */
    private long initialBackoffSeconds = 2;
    /** 重试间隔上限（秒） */
    private long maxBackoffSeconds = 300;
    /** 等待 broker 确认的超时时间（毫秒） */
    private long confirmTimeoutMillis = 5000;
    /** “投递中”状态的超时时间（秒），超时视为实例宕机并放回待投递 */
    private long stuckTimeoutSeconds = 120;
}
