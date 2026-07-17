# Bychat protocol-v2

## 传输信封

每个逻辑数据包包含以下字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| `v` | 整数 | 协议版本，当前为 `2` |
| `type` | 字符串 | 数据包类型 |
| `id` | 字符串 | 请求、响应或事件关联ID |
| `ts` | 长整数 | UTC Unix毫秒时间 |
| `payload` | JSON | 对应类型的业务内容 |
| `error` | JSON/null | 失败时的标准错误 |

示例：

    {"v":2,"type":"ping","id":"r-acde","ts":1700000000000,"payload":{"nonce":"n-1"}}

本阶段只定义逻辑信封。下一阶段将使用长度前缀帧承载JSON，不依赖换行分隔。

## 数据包类型

- `hello`：客户端发送版本及能力。
- `hello_ack`：服务器确认协议及能力。
- `auth_challenge`：服务器签名挑战。
- `auth_request`：客户端身份与挑战签名。
- `auth_result`：认证结果及会话令牌。
- `ready`：会话可用及同步游标。
- `message_send`：客户端发送消息。
- `message_event`：服务器广播消息事件。
- `sync_request`：请求游标后的增量事件。
- `sync_batch`：增量事件批次。
- `member_event`：成员资料或状态事件。
- `admin_command`：管理命令。
- `admin_result`：管理命令结果。
- `ping`、`pong`：连接心跳。
- `error`：无法关联到正常响应的协议错误。

## 能力协商

客户端在 `hello.capabilities` 中发送支持能力，服务器在 `hello_ack.capabilities` 返回本次连接可用能力。当前能力包括：

- `voice_message`
- `image_message`
- `file_message`
- `multi_community`
- `reactions`
- `edit_message`
- `delete_message`

接收方必须忽略未知能力，不得因此断开连接。

## 错误码

| 数值 | 名称 | 可重试 | 默认提示 |
|---:|---|:---:|---|
| 0 | `ok` | 否 | 操作成功 |
| 1000 | `bad_request` | 否 | 请求格式错误 |
| 1001 | `unsupported_protocol` | 否 | 协议版本不受支持 |
| 1002 | `unauthorized` | 否 | 身份验证失败 |
| 1003 | `forbidden` | 否 | 没有执行此操作的权限 |
| 1004 | `not_found` | 否 | 请求的内容不存在 |
| 1005 | `rate_limited` | 是 | 操作过于频繁，请稍后重试 |
| 1006 | `payload_too_large` | 否 | 发送的内容过大 |
| 1007 | `internal` | 是 | 服务器内部错误，请稍后重试 |
| 1008 | `conflict` | 否 | 当前状态与请求冲突 |

错误对象结构：

    {"code":1000,"name":"bad_request","message":"JSON格式错误","retryable":false}

## ID约定

- 请求ID：`r-<UUID>`
- 事件ID：`e-<UUID>`
- 客户端临时消息ID：`c-<UUID>`

ID用于关联和幂等处理，不作为认证凭据。
