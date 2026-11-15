# 项目架构与订单并发流程

## 1. 项目整体架构图

```mermaid
flowchart LR
    Client["Vue 3 前端<br/>商品浏览 / 发布 / 订单 / 聊天"]
    OSS["阿里云 OSS<br/>商品图片"]
    API["Spring Boot API<br/>8081"]
    Security["JWT 认证<br/>UserContext"]
    Product["商品模块<br/>商品列表 / 详情 / 分类"]
    Order["订单模块<br/>下单 / 支付 / 成交 / 完成"]
    Favorite["收藏模块"]
    Chat["聊天模块<br/>WebSocket"]
    Cache["Redis<br/>详情 / 分类 / 热门商品缓存<br/>订单锁 / 浏览量计数"]
    MySQL["MySQL<br/>用户 / 商品 / 订单 / 收藏 / 聊天"]
    Rabbit["RabbitMQ<br/>订单 / 收藏 / 聊天 / 系统通知事件<br/>失败重试 / 死信队列"]
    Flush["定时任务<br/>浏览量增量批量回写"]
    Hikari["HikariCP<br/>数据库连接池"]
    Async["异步线程池<br/>普通异步任务 / RabbitMQ 消费"]

    Client -->|HTTP / WebSocket| API
    Client -->|图片上传| API
    API --> Security
    API --> Product
    API --> Order
    API --> Favorite
    API --> Chat
    Product --> Cache
    Product --> Hikari
    Order --> Cache
    Order --> Hikari
    Favorite --> Hikari
    Chat --> Hikari
    API -->|图片存储| OSS
    Hikari --> MySQL
    Order -->|订单状态事件| Rabbit
    Favorite -->|收藏变更事件| Rabbit
    Chat -->|聊天消息事件| Rabbit
    Rabbit --> Async
    Async -->|异步消费| Order
    Async -->|异步消费| Favorite
    Async -->|异步通知| Chat
    Product -->|详情访问 INCR| Cache
    Flush -->|批量 CASE WHEN 更新| Hikari
    Flush --> Cache
```

### 架构说明

- **MySQL**：保存最终业务数据，是订单状态、商品信息和浏览量的持久化来源。
- **Redis**：缓存商品详情、分类树和热门商品列表；同时用于商品维度的订单分布式锁，以及浏览量增量计数。
- **RabbitMQ**：异步传递订单状态、聊天消息和系统通知事件，降低主请求链路耗时；生产侧用 outbox 保证提交后不丢事件，消费失败重试后转入死信队列。
- **HikariCP**：复用数据库连接，减少频繁建立连接的开销。
- **异步线程池**：普通异步任务和 RabbitMQ 消费使用独立线程池，避免消息消费占满应用线程。
- **浏览量定时任务**：详情访问只写 Redis，定时把增量批量写回 MySQL，并清理相关缓存。

## 2. 订单并发控制流程图

```mermaid
flowchart TD
    Start["客户端提交下单请求<br/>productId + quantity + addressId + idempotencyKey"]
    Precheck["查询<br/>buyerId + idempotencyKey"]
    Exists{"已存在订单？"}
    Compare["比对已存订单的<br/>商品 / 数量 / 收货地址"]
    Consistent{"参数一致？"}
    Conflict["返回 409 幂等冲突<br/>不返回旧订单"]
    ReturnOld["返回原订单<br/>幂等重放"]
    Lock["Redis SETNX<br/>lock:order:product:{productId}<br/>TTL 10 秒"]
    Locked{"获取锁成功？"}
    Retry["返回：订单正在处理中<br/>稍后重试"]
    Tx["开启 MySQL 事务"]
    RowLock["SELECT product ... FOR UPDATE<br/>锁定商品行"]
    ProductExists{"商品存在？"}
    NotFound["返回：商品不存在"]
    DoubleCheck["事务内再次查询幂等订单"]
    ExistsAgain{"已存在订单？"}
    CheckStatus["校验商品上架状态<br/>不能购买自己的商品"]
    ActiveOrder["查询进行中的订单"]
    HasActive{"已有进行中订单？"}
    Reject["返回业务错误"]
    Insert["插入订单<br/>写入唯一 idempotency_key"]
    Unique["MySQL 唯一约束<br/>(buyer_id, idempotency_key)"]
    RaceRecover["唯一键冲突<br/>加锁读回已提交订单"]
    Commit["事务提交"]
    Event["订单事件写入 outbox<br/>与订单同事务提交"]
    Unlock["finally 释放 Redis 锁"]
    Done["返回订单结果"]

    Start --> Precheck
    Precheck --> Exists
    Exists -->|是| Compare
    Compare --> Consistent
    Consistent -->|是| ReturnOld
    Consistent -->|否| Conflict
    Conflict --> Done
    ReturnOld --> Done
    Exists -->|否| Lock
    Lock --> Locked
    Locked -->|否| Retry
    Retry --> Done
    Locked -->|是| Tx
    Tx --> RowLock
    RowLock --> ProductExists
    ProductExists -->|否| NotFound
    NotFound --> Unlock
    ProductExists -->|是| DoubleCheck
    DoubleCheck --> ExistsAgain
    ExistsAgain -->|是| Compare
    ExistsAgain -->|否| CheckStatus
    CheckStatus --> ActiveOrder
    ActiveOrder --> HasActive
    HasActive -->|是| Reject
    Reject --> Unlock
    HasActive -->|否| Insert
    Insert --> Unique
    Unique -->|插入成功| Commit
    Unique -->|唯一键冲突| RaceRecover
    RaceRecover --> Compare
    Commit --> Event
    Event --> Unlock
    Unlock --> Done
```

### 订单并发控制的面试表述

> 下单接口采用三层保护。第一层是幂等请求号，客户端每次提交携带 `idempotencyKey`，重复请求直接返回原订单；命中幂等号后还会比对商品、数量、收货地址，参数不一致说明客户端把新的下单意图复用到了旧请求号上，直接返回 409 幂等冲突，而不是把旧订单当成新请求的结果。第二层是 Redis 商品粒度分布式锁，减少多个实例同时处理同一商品的竞争。第三层是在事务中对商品行执行 `SELECT FOR UPDATE`，由数据库保证最终串行化；同时订单表对 `(buyer_id, idempotency_key)` 建立唯一约束，作为最终兜底，防止重复订单落库，唯一键冲突时加锁读回已提交订单再走一次参数比对。

### 关键边界

- 幂等号只在请求参数完全一致时才可复用。只按 `buyerId + idempotencyKey` 命中就返回旧订单，会让“同一个 key 换成商品 B 再提交”拿回商品 A 的订单，这是静默的错误结果，必须显式拒绝。
- Redis 锁用于削峰和减少竞争，**不能替代数据库事务和唯一约束**。
- 商品行锁必须在事务中使用，否则事务结束后锁立即释放，无法保护后续更新。
- 订单事件不直接发 MQ，而是与订单数据同事务写入 outbox，提交后由投递器异步发送，详见第 3 节。
- 支付入账、确认成交和完成等需要读取完整上下文的操作通过订单行锁串行处理；取消与超时关单落终态时额外使用带来源状态的 CAS，防止重复回补库存。

## 3. 消息可靠投递（事务性 Outbox）

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Service as 业务服务
    participant DB as MySQL
    participant Relay as OutboxRelay
    participant MQ as RabbitMQ
    participant Consumer as 消费者

    Client->>Service: 下单 / 发消息 / 状态变更
    Service->>DB: 本地事务：业务数据 + outbox_event
    DB-->>Service: commit
    Service-->>Client: 直接返回，不等 MQ
    Service->>Relay: afterCommit 异步触发
    Relay->>DB: 查询待投递事件 + CAS 抢占
    Relay->>MQ: 发送（messageId = event_id，mandatory）
    MQ-->>Relay: publisher confirm（ack / nack）
    alt ack 且未被退回
        Relay->>DB: status = 已投递
    else nack / 确认超时 / 路由失败 / 连接异常
        Relay->>DB: attempts+1，指数退避后重试
    end
    MQ->>Consumer: 队列投递
    Consumer-->>MQ: ack，失败按重试策略进入死信队列
```

### 消息可靠投递的面试表述

> 事务提交后再发 MQ 有一个窗口：数据库已经提交、消息还没发出去，进程崩溃或 broker 不可用，这次事件就永久丢了；`afterCommit` 只能避免事务回滚后发出脏消息，不保证提交成功后一定发得出去。补法是事务性 outbox：业务数据和事件行在同一个本地事务里落库，提交之后由独立的投递器异步发送，并等待 broker 的 publisher confirm，收到 ack 才标记已投递；nack、确认超时、路由失败或连接异常按指数退避重试，超过次数置为投递失败留在表里待排查。这样把“业务与消息的原子性”降级为“业务与本地事件表的原子性”，再靠可重试的投递把事件推到 MQ，代价是至少一次投递和秒级延迟。

### 关键边界

- outbox 只保证**至少一次**投递，不保证 exactly once，也不保证严格顺序：重试的事件会被后续事件超过。消费者需要幂等；关单消费者通过订单状态 CAS 保证重复消费不会重复取消或回补库存，广播类消费者重复执行也不改变核心业务数据。
- 消费端的重试和死信队列处理的是**已经进队列**的消息，解决不了“还没进队列”的丢失，两者是互补关系而不是替代关系。
- 投递器用 `update ... where status = 0` 做 CAS 抢占，多实例同时扫描也不会重复投递；实例在“投递中”宕机会把记录留在该状态，超时后自动放回待投递。
- 失败超过上限的记录停在 `status = 3`（默认 30 次、最长间隔 5 分钟，约 2 小时重试窗口），需要人工排查原因后重放：`update outbox_event set status = 0, next_attempt_at = now() where id = ?`。
- 投递依赖 `publisher-confirm-type: correlated`、`publisher-returns: true` 和模板 `mandatory`；缺了确认配置，“发出去”就不等于“broker 收到了”，这个方案会退化成原来的样子。

## 4. 超时关单与支付成功竞态

```mermaid
sequenceDiagram
    participant Job as 超时扫描任务
    participant DB as MySQL
    participant Outbox as Outbox Relay
    participant MQ as RabbitMQ
    participant Close as 关单消费者
    participant Alipay as 支付宝
    participant Notify as 支付回调

    Job->>DB: 短事务 CAS 0待确认 → 4关单中
    Job->>DB: 同事务写 OrderCloseRequestedEvent
    DB-->>Job: commit
    Outbox->>MQ: 投递关单命令
    MQ->>Close: 消费命令
    Close->>Alipay: alipay.trade.close（事务外）

    alt 支付宝确认已关闭
        Close->>DB: 短事务 CAS 4关单中 → 2已取消
        DB->>DB: CAS 成功才回补库存
    else 支付宝提示交易已支付
        Close->>Alipay: alipay.trade.query
        Close->>DB: 支付入账 4关单中 → 1已成交
    else 网络异常或结果未知
        Close-->>MQ: 抛异常触发重试 / 死信
        Note over Job,DB: 定时扫描陈旧的 status=4 并重新投递
    end

    par 支付回调可能同时到达
        Notify->>DB: 锁订单并校验金额
        Notify->>DB: 0或4 → 1已成交
    end
```

### 竞态处理的面试表述

> 超时关单不能把支付宝 RPC 放在数据库事务里，否则网络抖动会让订单行锁长时间不释放，支付回调也会被阻塞。我的实现先用一个短事务把订单从待确认 CAS 到关单中，并把关单命令写入 Outbox；事务提交后由 RabbitMQ 消费者在事务外调用支付宝。支付宝确认关闭后，再用第二个短事务执行 `关单中 → 已取消`，只有条件更新影响一行时才恢复库存。如果支付宝提示已经支付，就主动查询并把订单从关单中推进到已成交；如果结果未知，订单保持关单中，由 MQ 重试和定时扫描继续收敛。支付回调也允许 `待确认/关单中 → 已成交`，因此支付与关单谁先完成由数据库状态条件裁决，不会出现重复取消、重复回补库存或长事务包 RPC。

### 关键边界

- `0 → 4` 与 Outbox 事件必须在同一个本地事务中，否则可能出现订单已经进入关单中但命令永久丢失。
- 支付宝 RPC 必须在事务外执行，数据库行锁无法锁住支付宝侧的支付动作。
- 最终取消必须是 `update ... where status = 4`；影响行数为 0 表示支付或其他流程已经抢先，绝不能恢复库存。
- 关单命令是至少一次投递，消费者必须允许重复执行；订单状态 CAS 与支付宝关单接口的幂等语义共同兜底。
- 已取消后仍收到支付成功通知时不能重新发货，系统使用稳定的退款请求号自动原路退款。
