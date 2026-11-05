# second-handTradingPlatform
一个二手交易平台的后端项目

## 项目文档

- [项目架构与订单并发流程](docs/interview-diagrams.md)
- [支付宝接入说明（沙箱 / 电脑网站支付）](docs/alipay-integration.md)
- [商品索引同步：Canal 监听 binlog + 定时对账](docs/canal-search-sync.md)

## 本地依赖

除 MySQL / Redis / RabbitMQ 外，商品搜索的实时索引依赖 canal-server，用 Docker 启动：

```powershell
docker compose -f docker/canal/docker-compose.yml up -d
```

不启动它也能跑（搜索会退回 MySQL，索引由定时任务兜底），把 `CANAL_ENABLED` 设为 `false` 即可关掉重连。

## 环境变量

复制查看 `env.example`，在 IDEA 的 Spring Boot Run Configuration 中配置环境变量后启动项目。数据库、Redis、RabbitMQ 密码和 JWT 密钥没有代码默认值，未配置时应用会启动失败。
