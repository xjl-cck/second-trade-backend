# second-handTradingPlatform
一个二手交易平台的后端项目

## 项目文档

- [项目架构与订单并发流程](docs/interview-diagrams.md)
- [支付宝接入说明（沙箱 / 电脑网站支付）](docs/alipay-integration.md)

## 环境变量

复制查看 `env.example`，在 IDEA 的 Spring Boot Run Configuration 中配置环境变量后启动项目。数据库、Redis、RabbitMQ 密码和 JWT 密钥没有代码默认值，未配置时应用会启动失败。
