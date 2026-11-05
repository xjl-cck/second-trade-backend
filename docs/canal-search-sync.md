# 商品索引同步（Canal + 定时对账）

ES 里的 `products` 索引只存三个字段：`title`、`description`、`location`，文档 `_id` 就是 `product.id`。
检索时 ES 只负责"找出匹配的 id"，商品数据仍然从 MySQL 读，所以索引字段少是刻意的。

## 三条写入链路

| 链路 | 触发 | 延迟 | 作用 |
|---|---|---|---|
| 同步双写 | `ProductServiceImpl` / `AdminServiceImpl` 里写完库立刻调 `index()` | 0 | 让搜索立刻可见 |
| **Canal** | 订阅 MySQL binlog，`CanalProductIndexListener` 消费 | 秒级 | 覆盖**所有**写路径，包括订单把商品置为已售、逻辑删除 |
| 定时对账 | `ProductSearchSyncJob` 每 60s 扫 `update_time` 最近 10 分钟的商品 | ≤ 窗口 | 兜底：canal 挂过、ES 抖过、双写失败过，都能被窗口重放纠正 |

链路图：

```
MySQL (binlog ROW)
   │  dump
   ▼
canal-server (Docker, 伪装从库, 端口 11111)
   │  TCP 订阅
   ▼
CanalProductIndexListener ──► ProductSearchIndex ──► Elasticsearch (products)
                                   ▲
ProductSearchSyncJob ──────────────┘   (定时窗口重放，兜底)
```

## 为什么用 TCP 而不是投递到 RabbitMQ

`canal/canal-server:1.1.8` 的官方镜像里**没有** Kafka / RocketMQ / RabbitMQ 的 producer 类（`lib` 下也没有对应客户端 jar），
只有 TCP 模式可用。所以这里用 `canal.client` 直连 11111 端口，而不是走 MQ。

顺带的好处：少一个 MQ 配置面，消息也不会因为队列堆积而被丢弃。

## 启动

前提（本机已满足）：

- MySQL `log_bin=ON`、`binlog_format=ROW`、`binlog_row_image=FULL`
- 复制账号：`canal` / `canal`，权限 `SELECT, REPLICATION SLAVE, REPLICATION CLIENT`

```powershell
# 1. 起 canal-server（首次会自动拉镜像）
docker compose -f docker/canal/docker-compose.yml up -d
```

> 如果 `docker pull` 因为镜像源不可用而失败，可以换镜像站再打回本地 tag：
> ```powershell
> docker pull docker.m.daocloud.io/canal/canal-server:v1.1.8
> docker tag docker.m.daocloud.io/canal/canal-server:v1.1.8 canal/canal-server:v1.1.8
> ```

```powershell
# 2. 确认启动成功（看到 start successful / find start position successfully）
docker exec canal-server tail -20 /home/admin/canal-server/logs/example/example.log

# 3. 启动后端，日志里应出现：
#    Canal connected, 127.0.0.1:11111, destination=example, filter=trading\.product
```

验证：改一条商品的标题，1 秒内 ES 就能查到新标题：

```powershell
curl "http://localhost:9200/products/_doc/1"
```

## 配置

`application.yaml` → `app.search.canal`：

| 配置 | 环境变量 | 默认值 |
|---|---|---|
| enabled | `CANAL_ENABLED` | `true` |
| host | `CANAL_HOST` | `127.0.0.1` |
| port | `CANAL_PORT` | `11111` |
| destination | `CANAL_DESTINATION` | `example` |
| filter | `CANAL_FILTER` | `trading\.product` |
| batch-size | `CANAL_BATCH_SIZE` | `100` |

不跑 canal 容器时把 `CANAL_ENABLED=false`，否则启动后会按 1s→30s 退避一直重连并打告警。

## 失败语义

- **at-least-once**：处理完一批就 ack，进程崩在中间会重放，重复写是幂等的（`_id` 固定、全量覆盖）。
- **写 ES 失败不阻塞**：个别文档写失败只记 warn 并继续 ack，靠定时任务在窗口内补。
- **有损窗口**：如果 ES 连续不可用超过 10 分钟，窗口滚动过去后就补不回来了，需要重启应用触发
  `ProductSearchIndex.rebuildFromDatabase()` 全量重建。要更强的保证就得换成 outbox 那种"事件不丢"的模型。

## 运维

- **位点文件**：`docker/canal/conf/example/meta.dat`（已挂载到宿主机），容器重启会从上次位点续传。
- **表结构变更**：canal 会缓存表元数据，加字段后需要 `docker restart canal-server`。
- **重置位点**：删掉 `meta.dat` 和 `h2.mv.db` 后重启，canal 会从当前 binlog 位点重新开始（会漏掉中间的数据，
  靠应用重启时的全量重建补齐）。
- **binlog 保留期**：本机 `binlog_expire_logs_seconds=2592000`（30 天），canal 停机超过这个时间位点就失效了。
