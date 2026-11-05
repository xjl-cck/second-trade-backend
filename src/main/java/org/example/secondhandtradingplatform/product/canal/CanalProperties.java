package org.example.secondhandtradingplatform.product.canal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.search.canal")
public class CanalProperties {

    /** 关掉后不会连 canal，完全退回定时对账 */
    private boolean enabled = true;
    private String host = "127.0.0.1";
    private int port = 11111;
    /** canal-server 里的 instance 名字，默认 example */
    private String destination = "example";
    /** 订阅过滤，格式是 schema\\.table 的正则 */
    private String filter = "trading\\.product";
    private int batchSize = 100;
}
