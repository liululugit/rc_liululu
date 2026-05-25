# Right Capital - 批量通知系统 PRD (Java版)

## 1. 需求描述

企业内部多个业务系统在关键事件发生时，需要调用外部系统供应商提供的 HTTP(S) API 进行通知。例如：

- 用户通过第三方广告系统引流并成功注册后，通知对应的广告系统
- 用户订阅付款成功后，通知 CRM 系统更改 Contact 状态
- 用户购买商品后，通知库存系统进行库存变更

不同供应商的 API 存在差异：
- 请求地址不同
- Header / Body 格式不同

业务系统本身：
- 不需要关心外部 API 的返回值
- 只需确保通知请求能够被稳定、可靠地送达

---

## 2. 目标

构建一个 Java 后端服务，提供三个核心接口实现通知的接收、发送和回调处理。

---

## 3. 接口设计

### 3.1 接口1：接受请求并持久化

```java
/**
 * 接受业务系统的通知请求并持久化到内存
 * @param request HTTP请求对象
 */
void receiveRequest(HttpRequest request);
```

**入参 `HttpRequest`：**

| 字段 | 类型 | 说明 |
|-----|------|------|
| requestId | String | 唯一标识，用于幂等性和追踪 |
| url | String | 目标HTTP地址 |
| headers | Map<String, String> | HTTP请求头 |
| body | String | 请求体内容 |
| supplierId | String | 供应商标识，用于路由到对应线程池 |

**返回值：** 无

**实现细节：**
- 使用 `ConcurrentHashMap<String, RequestInfo>` 存储所有请求
- Key: requestId
- Value: RequestInfo（包含HttpRequest + 元数据）

---

### 3.2 接口2：发送请求

```java
/**
 * 根据requestId发送HTTP通知请求
 * @param requestId 请求唯一标识
 */
void sendRequest(String requestId);
```

**入参：** requestId

**返回值：** 无

**实现细节：**
1. 从 requestMap 中获取对应的 RequestInfo
2. 根据 supplierId 路由到对应的线程池（3个线程池对应3个供应商）
3. 封装 HTTP 请求（URL、Headers、Body）
4. 提交到线程池异步执行
5. 执行 HTTP 调用（使用 HttpClient 或 RestTemplate）

**线程池设计：**

```java
// 3个供应商对应的线程池
Map<String, ThreadPoolExecutor> supplierThreadPools = new HashMap<>();

// 初始化示例
supplierThreadPools.put("supplierA", new ThreadPoolExecutor(
    4, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000)
));
supplierThreadPools.put("supplierB", new ThreadPoolExecutor(
    4, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000)
));
supplierThreadPools.put("supplierC", new ThreadPoolExecutor(
    4, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000)
));
```

---

### 3.3 接口3：服务方回调函数

```java
/**
 * 接收外部服务方的回调通知
 * @param requestId 请求唯一标识
 * @param success 是否发送成功
 */
void handleCallback(String requestId, boolean success);
```

**入参：**

| 字段 | 类型 | 说明 |
|-----|------|------|
| requestId | String | 请求唯一标识 |
| success | boolean | true=发送成功, false=发送失败 |

**返回值：** 无

**实现逻辑：**

```
if success == true:
    从 requestMap 中删除该 requestId
    记录成功日志
else:
    if requestId 不在 requestMap 中:
        报错：找不到该URL / 可能已经发送成功
    else:
        获取 RequestInfo
        if 重试次数 >= 10:
            移入死信队列 / 记录最终失败
        else:
            重试次数 + 1
            重新调用 sendRequest(requestId)
```

---

## 4. 数据模型

### 4.1 RequestInfo

```java
public class RequestInfo {
    // 原始请求信息
    private HttpRequest request;
    
    // 元数据
    private int retryCount;        // 当前重试次数
    private long createTime;       // 创建时间
    private long lastRetryTime;    // 最后重试时间
    private RequestStatus status;  // 当前状态
    
    // 枚举：状态
    public enum RequestStatus {
        PENDING,      // 待发送
        SENDING,      // 发送中
        WAITING_ACK,  // 等待回调确认
        SUCCESS,      // 发送成功
        FAILED        // 最终失败
    }
}
```

### 4.2 HttpRequest

```java
public class HttpRequest {
    private String requestId;              // 唯一标识
    private String url;                    // 目标地址
    private Map<String, String> headers;   // 请求头
    private String body;                   // 请求体
    private String supplierId;             // 供应商标识
    private String method;                 // HTTP方法：GET/POST/PUT
    private int timeout;                   // 超时时间（毫秒）
}
```

---

## 5. 整体架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           业务系统调用层                                 │
│                    调用 receiveRequest() 提交通知                        │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                           Right Capital 服务                             │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     NotificationService                          │   │
│  │                                                                  │   │
│  │  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐ │   │
│  │  │ receiveRequest()│    │  sendRequest()  │    │handleCallback│ │   │
│  │  │   (接口1)       │    │   (接口2)       │    │   (接口3)    │ │   │
│  │  └────────┬────────┘    └────────┬────────┘    └──────┬──────┘ │   │
│  │           ↓                      ↓                     ↓       │   │
│  │  ┌──────────────────────────────────────────────────────────┐  │   │
│  │  │              RequestStorage (内存存储)                    │  │   │
│  │  │         ConcurrentHashMap<String, RequestInfo>            │  │   │
│  │  └──────────────────────────────────────────────────────────┘  │   │
│  │           ↓                      ↓                            │   │
│  │  ┌──────────────────────────────────────────────────────────┐  │   │
│  │  │              ThreadPoolRouter (线程池路由)                │  │   │
│  │  │   Map<supplierId, ThreadPoolExecutor>                    │  │   │
│  │  │   supplierA → ThreadPool-A                               │  │   │
│  │  │   supplierB → ThreadPool-B                               │  │   │
│  │  │   supplierC → ThreadPool-C                               │  │   │
│  │  └──────────────────────────────────────────────────────────┘  │   │
│  │                            ↓                                  │   │
│  │  ┌──────────────────────────────────────────────────────────┐  │   │
│  │  │              HttpSender (HTTP执行器)                      │  │   │
│  │  │   - 封装HTTP请求                                          │  │   │
│  │  │   - 异步执行发送                                          │  │   │
│  │  │   - 调用外部供应商API                                     │  │   │
│  │  └──────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                           外部供应商系统                                 │
│                    处理请求 → 回调 handleCallback()                      │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 6. 核心流程时序图

### 6.1 正常流程

```
业务系统          NotificationService         RequestStorage      ThreadPool        HttpSender      外部供应商
    │                    │                        │                  │                │              │
    │─receiveRequest()──→│                        │                  │                │              │
    │                    │────store request────→│                  │                │              │
    │                    │←───────ok────────────│                  │                │              │
    │←───────────────────│                        │                  │                │              │
    │                    │                        │                  │                │              │
    │──sendRequest()────→│                        │                  │                │              │
    │                    │────get request──────→│                  │                │              │
    │                    │←────RequestInfo──────│                  │                │              │
    │                    │                        │                  │                │              │
    │                    │────────submit task──────────────────────→│                │              │
    │                    │                        │                  │────execute()──→│              │
    │                    │                        │                  │                │──HTTP请求───→│
    │                    │                        │                  │                │              │
    │                    │                        │                  │                │←───响应──────│
    │                    │                        │                  │←───返回────────│              │
    │                    │                        │                  │                │              │
    │                    │                        │                  │                │              │
    │                    │←───────────────────────────────────────────────────────────handleCallback()│
    │                    │                        │                  │                │              │
    │                    │────remove request───→│                  │                │              │
    │                    │                        │                  │                │              │
```

### 6.2 失败重试流程

```
外部供应商          NotificationService         RequestStorage      ThreadPool        HttpSender
    │                    │                        │                  │                │
    │                    │←───────────────────────────────────────────────────────────handleCallback(success=false)│
    │                    │                        │                  │                │
    │                    │────get request──────→│                  │                │
    │                    │←────RequestInfo──────│                  │                │
    │                    │                        │                  │                │
    │                    │ [检查重试次数 < 10?]   │                  │                │
    │                    │                        │                  │                │
    │                    │────update retryCount→│                  │                │
    │                    │                        │                  │                │
    │                    │────────submit task──────────────────────→│                │
    │                    │                        │                  │────execute()──→│
    │                    │                        │                  │                │──HTTP请求───→
    │                    │                        │                  │                │
```

### 6.3 回调时找不到ID

```
外部供应商          NotificationService         RequestStorage
    │                    │                        │
    │                    │←───────────────────────handleCallback(requestId, false)│
    │                    │                        │
    │                    │────get request──────→│
    │                    │←────null─────────────│
    │                    │                        │
    │                    │ [抛出异常]             │
    │                    │ "找不到该URL/可能已发送成功" │
    │                    │                        │
```

---

## 7. 核心功能点

### 7.1 内存持久化

| 功能 | 说明 |
|-----|------|
| 存储结构 | `ConcurrentHashMap<String, RequestInfo>` |
| 线程安全 | ConcurrentHashMap 保证并发安全 |
| 容量控制 | 可配置最大容量，超限拒绝或LRU淘汰 |
| 过期清理 | 定时任务清理已成功的过期记录 |

### 7.2 线程池路由

| 功能 | 说明 |
|-----|------|
| 路由策略 | 按 supplierId 哈希路由 |
| 线程池隔离 | 不同供应商独立线程池，互不影响 |
| 线程池配置 | 核心线程数、最大线程数、队列大小可配置 |
| 拒绝策略 | 队列满时拒绝或降级处理 |

### 7.3 重试机制

| 功能 | 说明 |
|-----|------|
| 最大重试次数 | 10次（可配置） |
| 重试触发 | 回调返回 success=false 时触发 |
| 重试限制 | 超过10次移入死信队列 |
| 重试间隔 | 可配置固定间隔或指数退避 |

### 7.4 幂等性

| 功能 | 说明 |
|-----|------|
| 唯一ID | 业务系统生成 requestId |
| 重复检测 | 接收时检查 requestId 是否已存在 |
| 重复处理 | 已存在的直接返回，不重复存储 |

### 7.5 回调处理

| 场景 | 处理逻辑 |
|-----|---------|
| success=true | 从 map 中删除，释放内存 |
| success=false & 存在 & 重试<10 | 重试次数+1，重新发送 |
| success=false & 存在 & 重试>=10 | 移入死信队列 |
| success=false & 不存在 | 抛出异常：找不到该URL |

---

## 8. 代码结构

```
right-capital/
├── src/main/java/com/right/capital/
│   ├── api/
│   │   └── NotificationController.java      # REST接口层
│   ├── service/
│   │   └── NotificationService.java         # 核心业务逻辑
│   ├── storage/
│   │   └── RequestStorage.java              # 内存存储
│   ├── pool/
│   │   └── ThreadPoolRouter.java            # 线程池路由
│   ├── sender/
│   │   └── HttpSender.java                  # HTTP发送器
│   ├── model/
│   │   ├── HttpRequest.java                 # 请求对象
│   │   ├── RequestInfo.java                 # 存储对象
│   │   └── RequestStatus.java               # 状态枚举
│   └── exception/
│       └── RequestNotFoundException.java    # 自定义异常
└── src/test/java/
    └── NotificationServiceTest.java         # 单元测试
```

---

## 9. 边界说明

### 9.1 本系统解决
- 请求的内存持久化
- 多线程池路由发送
- 失败重试（最多10次）
- 回调确认与状态管理

### 9.2 本系统不解决
- 数据库持久化（仅内存存储）
- 服务重启后的数据恢复
- 分布式部署（单机版）
- 复杂的路由策略（仅按supplierId）
- 定时重试（仅回调触发重试）

---

## 10. 演进方向

| 版本 | 功能 |
|-----|------|
| V1（当前） | 内存存储 + 单机线程池 + 回调重试 |
| V2 | 增加数据库持久化 + 服务重启恢复 |
| V3 | 引入消息队列（Kafka/RabbitMQ）+ 定时重试任务 |
| V4 | 分布式部署 + 分片路由 |

---

*文档版本: v2.0*  
*更新时间: 2025-05-25*  
*变更: 从Web页面改为Java后端服务，简化架构*
