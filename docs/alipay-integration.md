# 支付宝接入说明（电脑网站支付 / 沙箱）

本文对应 `payment` 包：把原来的「点击即支付」换成了真实的支付宝电脑网站支付（`alipay.trade.page.pay`），
支持沙箱联调，正式环境只需换网关和三把密钥。

## 1. 代码结构

| 文件 | 作用 |
| --- | --- |
| `payment/config/AlipayProperties.java` | 支付宝配置项（`alipay.*`） |
| `payment/config/AlipayClientConfig.java` | 仅当 `alipay.enabled=true` 时创建 `AlipayClient`，本地无密钥也能正常启动 |
| `payment/entity/PaymentRecord.java` | 支付流水（`payment_record` 表） |
| `payment/service/impl/AlipayPaymentServiceImpl.java` | 下单、验签、异步通知处理、主动查询、幂等入账 |
| `payment/controller/AlipayPaymentController.java` | 4 个对外的 HTTP 接口 |
| `sql/migration/V9__alipay_payment.sql` | 支付流水表 DDL |

## 2. 接口

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/payments/alipay/pay/{orderId}` | 买家 JWT | 返回 `formHtml`，前端写入页面即跳转收银台 |
| POST | `/api/payments/alipay/notify` | 无 | 支付宝异步通知，验签后处理，返回 `success` / `failure` |
| GET | `/api/payments/alipay/return` | 无 | 用户付款后浏览器跳回，主动查询一次作为兜底 |
| GET | `/api/payments/alipay/status/{orderNo}` | 买家/卖家 JWT | 前端轮询，会主动向支付宝查询并同步本地状态 |

`/notify` 和 `/return` 已在 `WebConfig` 里排除 JWT 拦截器。

### 下单示例

```bash
curl -X POST http://localhost:8081/api/payments/alipay/pay/123 \
  -H "Authorization: Bearer <买家 token>"
```

```json
{
  "code": 200,
  "data": {
    "orderNo": "SH202611022130001234",
    "outTradeNo": "SH202611022130001234",
    "channel": "alipay",
    "payMethod": "page",
    "amount": 199.00,
    "formHtml": "<form name=\"punchout_form\" method=\"post\" action=\"https://openapi-sandbox.dl.alipaydev.com/gateway.do?...\">..."
  }
}
```

前端只要：

```js
const { data } = await api.post(`/api/payments/alipay/pay/${orderId}`);
document.open();
document.write(data.formHtml); // 表单里有自动提交脚本，会跳到收银台
document.close();
```

## 3. 沙箱开通步骤

1. 登录 [支付宝开放平台](https://open.alipay.com/)，进入 **控制台 → 沙箱环境**，记下 **APPID**。
2. 生成密钥对（也可以用平台自带的「密钥工具」）：

   ```bash
   openssl genrsa -out alipay_private_key.pem 2048
   openssl rsa -in alipay_private_key.pem -pubout -out alipay_public_key.pem
   ```

3. 把 **应用公钥**（`alipay_public_key.pem` 的内容）填进沙箱应用的「接口加签方式 → 公钥」，
   平台会生成 **支付宝公钥** —— 这个才是 `ALIPAY_PUBLIC_KEY`，**不是**你自己生成的应用公钥。
4. 沙箱页面下载「沙箱账号」，里面有买家账号，付款时用它登录。

## 4. 配置

`application.yaml` 里已有默认值，实际用环境变量覆盖：

```properties
ALIPAY_ENABLED=true
ALIPAY_APP_ID=9021000xxxxxxxxx
ALIPAY_APP_PRIVATE_KEY_PATH=D:\keys\alipay_private_key.txt   # 或 ALIPAY_APP_PRIVATE_KEY= 直接贴内容
ALIPAY_PUBLIC_KEY=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCg...
ALIPAY_GATEWAY_URL=https://openapi-sandbox.dl.alipaydev.com/gateway.do
ALIPAY_NOTIFY_URL=https://你的域名/api/payments/alipay/notify
ALIPAY_RETURN_URL=https://你的域名/api/payments/alipay/return
```

- `ALIPAY_ENABLED=false` 时，`payment` 接口返回「支付宝支付未启用」的业务异常，
  原来的 `POST /api/orders/{id}/pay` 模拟支付继续可用，便于没有密钥时演示。
- 私钥文件里带 `-----BEGIN PRIVATE KEY-----` 头尾也能识别，会自动去掉并压缩空白。

## 5. 本地联调（关键）

支付宝的 `notify_url` 必须是**公网可达**的地址，`localhost` 收不到异步通知。本地开发有两条路：

**推荐：内网穿透**

```bash
# 任选其一：cpolar / natapp / frp / cloudflared
cpolar http 8081
# 拿到 https://xxxx.cpolar.cn 后，把 notify/return 换成该域名
```

**兜底：主动查询**

即使通知收不到，前端在用户从支付宝跳回后调用
`GET /api/payments/alipay/status/{orderNo}`，服务端会调用 `alipay.trade.query` 补偿，
效果和异步通知一致（`/return` 本身也会做一次）。

## 6. 状态流转与可靠性

```
创建订单(status=0 待确认)
   └─ POST /pay/{orderId} → 写 payment_record(status=0 待支付) → 返回收银台表单
        └─ 支付宝付款
             ├─ notify(TRADE_SUCCESS) → 验签 → 校验金额 → order.paySuccess() → 订单 status=1 已成交、商品置为已售出
             └─ TRADE_CLOSED → payment_record.status=2
```

已经处理好的点：

- **验签**：`AlipaySignature.rsaCheckV1`（RSA2 + utf-8），同时校验 `app_id` 是否为本应用。
- **金额校验**：通知里的 `total_amount` 必须与订单金额完全相等，避免改价攻击。
- **幂等**：`payment_record` 已支付时直接返回；`trade_order` 已成交时 `paySuccess` 也直接返回。
  重复通知、通知与主动查询并发调用都不会重复推进订单。
- **并发**：入账前先 `select ... for update` 锁订单行，与 `pay()` 共用同一条状态机。
- **异常留痕**：金额不一致、订单已取消却收到支付成功，都会把流水置为 `status=3 支付异常待退款`
  并把原因写进 `last_error`、原文写进 `raw_notify`，便于人工对账退款。
- **重复下单**：历史支付单已关闭时，新的 `out_trade_no` 会追加 `R+时间戳`，避免支付宝返回 `TRADE_HAS_CLOSE`。
- **支付超时**：`alipay.timeout-express`（默认 30m）到期后支付宝自动关闭交易，`syncFromAlipay` 会把流水置为已关闭。

## 7. 上线正式环境

1. `ALIPAY_GATEWAY_URL` 换成 `https://openapi.alipay.com/gateway.do`。
2. 换成正式应用的 APPID、应用私钥、支付宝公钥（正式环境用的是「支付宝公钥」证书模式或公钥模式，注意别混用）。
3. `notify_url` / `return_url` 换成 HTTPS 域名，且不能被网关鉴权拦截。
4. 电脑网站支付需要正式应用签约「电脑网站支付」产品。

## 8. 退款（退货流程）

退货单走到「退货中」后，卖家点「确认退款完成」会**先真的调支付宝退款**，成功才把状态置为「退款完成」。

```
POST /api/return-requests/{id}/complete
   └─ AlipayRefundService.refund()   ← 独立事务（REQUIRES_NEW）
        ├─ 成功：写 payment_refund(status=1) → 退货单 status=3
        └─ 失败：写 payment_refund(status=2) → 抛异常 → 退货单保持 status=1，可重试
```

关键点：

- **幂等**：`out_request_no = RF + 退货单ID`，重复点确认不会退两次；同一退货单复用同一条退款流水（`return_request_id` 唯一索引）。
- **独立事务**：退款是资金操作，结果必须先落库。如果用外层事务，失败信息会跟着回滚，事后无从排查。
- **失败可重试**：失败时退货单停在「退货中」，卖家可以再点一次，`out_request_no` 不变，支付宝侧不会重复扣。
- **非支付宝订单**：`payment_record` 没有已支付流水（比如用的模拟支付 `/api/orders/{id}/pay`），直接跳过线上退款，行为与以前一致。
- **对账补偿**：`GET /api/payments/alipay/refund/{returnRequestId}` 会调 `alipay.trade.fastpay.refund.query` 主动核对，前端可轮询。

退款表 `payment_refund` 记录 `out_request_no`、`refund_fee`、`fund_change`、失败原因，可用于对账。

## 9. 后台补偿任务

两个定时任务，风格与 `OutboxRelay` / `FavoriteFlushJob` 一致，都可配置开关与间隔。

### 9.1 超时未支付自动关单 `OrderTimeoutCloseJob`

```
每 60s 扫描 trade_order(status=0 且 create_time < now-30min) → 最多 50 笔
   └─ 逐笔独立事务：
        1. alipay.trade.close(out_trade_no)   ← 幂等；ACQ.TRADE_NOT_EXIST 也算已关
        2. 回滚库存 stock += quantity，商品若已售出则恢复上架
        3. 订单 status=2 已取消，close_reason=「超时未支付，系统自动关闭」
        4. 走 outbox 发「订单已取消」事件
```

关不掉支付宝交易时抛异常回滚，下一轮重试，不会出现“本地关了、支付宝还挂着能付款”的情况。

### 9.2 退款对账 `RefundReconcileJob`

```
每 300s 扫描 payment_refund(status in (0待退款, 2失败) 且 24h 内 且 update_time 超过 2 分钟)
   └─ alipay.trade.fastpay.refund.query(out_trade_no, out_request_no)
        └─ REFUND_SUCCESS → 本地补正为 status=1，回填 refund_fee / refund_time
```

典型场景：退款请求超时，本地记了失败，但支付宝其实退成功了。这个任务把「钱退了、单子没退」补回来。

配置项：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `ORDER_PAY_TIMEOUT_MINUTES` | 30 | 超时时间 |
| `ORDER_AUTO_CLOSE_ENABLED` | true | 关单开关 |
| `ORDER_AUTO_CLOSE_INTERVAL_MS` | 60000 | 扫描间隔 |
| `ORDER_AUTO_CLOSE_BATCH_SIZE` | 50 | 单轮批量 |
| `REFUND_RECONCILE_ENABLED` | true | 对账开关 |
| `REFUND_RECONCILE_INTERVAL_MS` | 300000 | 对账间隔 |
| `REFUND_RECONCILE_BATCH_SIZE` | 50 | 单轮批量 |

本地验证关单：把 `ORDER_PAY_TIMEOUT_MINUTES` 临时设成 `1`，下单后等两分钟，看日志出现
`自动关闭超时未支付订单 N 笔`，并确认商品库存回滚、订单变成「已取消」。

## 10. 常见问题

| 现象 | 原因 |
| --- | --- |
| `sub_code: isv.invalid-signature` | 私钥与上传到平台的应用公钥不配对，或贴成了支付宝公钥 |
| 回调一直 `failure` | `ALIPAY_PUBLIC_KEY` 填的是应用公钥；或参数被网关/框架改动（本项目用 `@RequestParam Map` 原样接收，不受影响） |
| 付款成功但订单没变 | 通知没到（内网不可达），检查 `notify_url`；或前端没调用 `/status` 兜底 |
| `ACQ.TRADE_HAS_CLOSE` | 该 `out_trade_no` 的交易已关闭，代码已用后缀自动规避；如果是老数据请人工确认 |
| 沙箱登录提示账号密码错误 | 用沙箱页面里的「买家账号 + 登录密码 + 支付密码」，不是开放平台账号 |
