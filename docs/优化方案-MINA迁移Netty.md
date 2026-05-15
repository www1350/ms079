# 网络框架迁移方案：MINA → Netty

> 文档状态：方案评估
> 创建日期：2026-05-15
> 目标：将服务端网络层从 Apache MINA 2.0.9 迁移到 Netty 4.x

---

## 一、背景

### 1.1 为什么要迁移

Apache MINA 2.0.9 发布于 2014 年，至今已超过 12 年未更新。该项目当前仅依赖 `mina-core`，使用其最基础的 NIO Socket Acceptor + Protocol Codec Filter 能力。

迁移到 Netty 的主要收益：

1. **持续维护**：Netty 是 Java 生态最活跃的网络框架，有完善的社区支持和安全补丁
2. **性能提升**：Netty 的内存管理（PooledByteBufAllocator）、零拷贝、Epoll 支持等远超 MINA
3. **更好的 Pipeline 模型**：Netty 的 ChannelPipeline 比 MINA 的 IoFilterChain 更灵活，天然支持按连接创建 Handler 实例（无需手动管理 session attributes）
4. **生态一致性**：现代 Java 游戏服务器几乎全部使用 Netty，方便后续引入 WebSocket、HTTP/2、gRPC 等协议
5. **减少依赖**：MINA 自身依赖 SLF4J 1.6.x（古老版本），与项目当前使用的 Logback/SLF4J 1.7.x 存在潜在冲突风险

### 1.2 不做迁移的风险

- MINA 2.0.9 已知有内存泄漏和 NIO selector 死锁问题（高并发场景下）
- 无法获得安全漏洞修复
- 与 JDK 新版本演进脱节（当前 JDK 8 尚可运行，JDK 11+ 未验证）

---

## 二、现状分析

### 2.1 Maven 依赖

**pom.xml** 中仅声明一个 MINA 模块：

```xml
<dependency>
    <groupId>org.apache.mina</groupId>
    <artifactId>mina-core</artifactId>
    <version>2.0.9</version>
</dependency>
```

未使用 `mina-filter-ssl`、`mina-integration-beans` 等其他模块，依赖面窄。

### 2.2 网络架构总览

```
┌──────────────────────────────────────────────────────────┐
│                    MapleServerHandler                     │
│              (IoHandlerAdapter, 单例共享)                  │
│  sessionOpened / messageReceived / sessionClosed / idle   │
└──────────────────┬───────────────────────────────────────┘
                   │
         ┌─────────┼─────────┐
         │         │         │
    LoginServer  Channel   CashShop
    (port 9595)  (7575+)  (port 8600)
         │         │         │
    NioSocketAcceptor (每个 server 独立实例)
         │         │         │
    FilterChain: "codec" → ProtocolCodecFilter(MapleCodecFactory)
         │         │         │
    ┌────┴─────────┴─────────┴────┐
    │     MapleCodecFactory       │
    │  ┌──────────────────────┐   │
    │  │ MaplePacketEncoder   │   │
    │  │ (ProtocolEncoder)    │   │
    │  ├──────────────────────┤   │
    │  │ MaplePacketDecoder   │   │
    │  │ (CumulativeProtocol  │   │
    │  │  Decoder)            │   │
    │  └──────────────────────┘   │
    └─────────────────────────────┘
```

三个服务器端口：

| 服务器 | 默认端口 | 实例数 | Acceptor 持有方式 |
|--------|----------|--------|------------------|
| LoginServer | 9595 | 1 | 局部变量 |
| ChannelServer | 7575 + channel | N（可配） | 实例字段 `IoAcceptor acceptor` |
| CashShopServer | 8600 | 1 | 局部变量 |
| AuthenticationServer | — | 1（新版） | 实例字段（Guice 注入） |

### 2.3 数据包处理链路

#### 入站（接收封包）

```
TCP 字节到达
  │
  ▼
NioSocketAcceptor (MINA NIO selector)
  │
  ▼
ProtocolCodecFilter → MaplePacketDecoder (CumulativeProtocolDecoder)
  │  1. 读 4 字节包头
  │  2. MapleAESOFB.checkPacket() 校验包头合法性
  │  3. 提取 payload 长度
  │  4. 读 payload 字节
  │  5. MapleAESOFB.crypt() 解密（AES OFB 模式）
  │  6. MapleCustomEncryption.decryptData() 解密（自定义 XOR/rotate）
  │  7. 输出原始 byte[]
  ▼
MapleServerHandler.messageReceived()
  │  1. GenericSeekableLittleEndianAccessor 包装 byte[]
  │  2. 读 2 字节 opcode（unsigned short）
  │  3. 查 OPCODE_MAP 获取 RecvPacketOpcode
  │  4. 校验客户端登录状态
  │  5. 分发到 handlePacket() switch 语句
  ▼
handlePacket() → 具体 Handler 处理
```

#### 出站（发送封包）

```
MaplePacket 对象（含 byte[] + onSend 回调）
  │
  ▼
MaplePacketEncoder.encode()
  │  1. 获取 client.getLock()（ReentrantLock 保护 IV 不被并发破坏）
  │  2. 生成 4 字节包头（sendCrypto.getPacketHeader）
  │  3. MapleCustomEncryption.encryptData() 加密
  │  4. MapleAESOFB.crypt() 加密
  │  5. 组装包头 + 加密 payload → IoBuffer
  ▼
ProtocolCodecFilter → NioSocketAcceptor → TCP 发送
```

#### Handshake 特殊路径

首次连接时，`sessionOpened` 直接通过 `session.write()` 发送握手包（未经 MaplePacketEncoder 的加密路径），因为此时客户端尚未协商加密 IV。

握手包格式：`[2B locator][2B version][2B zeros][4B recvIv][4B sendIv][1B locale]` = 16 字节。

### 2.4 Session 属性管理

MINA 通过 `IoSession.setAttribute(key, value)` 管理 per-connection 状态：

| Key | 类型 | 用途 |
|-----|------|------|
| `MapleClient.CLIENT_KEY` | `MapleClient` | 连接对应的客户端对象 |
| `MaplePacketDecoder.DECODER_STATE_KEY` | `DecoderState` | 解码器状态（packetlength 暂存） |
| `IdleStatus.READER_IDLE` | `Integer(60)` | 读空闲超时（秒） |
| `IdleStatus.WRITER_IDLE` | `Integer(60)` | 写空闲超时（秒） |

### 2.5 线程模型

```
MINA I/O Worker 线程池（默认 CPU 核数 + 1）
     │
     ├─ 执行 ProtocolCodecFilter（编解码）
     ├─ 执行 MapleServerHandler.sessionOpened()
     ├─ 执行 MapleServerHandler.messageReceived()
     └─ 执行 MapleServerHandler.sessionClosed()
           │
           ▼
      大部分逻辑在 I/O 线程同步执行
      仅少数场景通过 PlayerActorExecutor 异步化
```

### 2.6 关键文件清单

| 文件 | 作用 |
|------|------|
| `pom.xml` | MINA 依赖声明 |
| `handling/mina/MapleCodecFactory.java` | 编解码工厂 |
| `handling/mina/MaplePacketDecoder.java` | 粘包处理 + 解密 |
| `handling/mina/MaplePacketEncoder.java` | 加密 + 组包 |
| `handling/MapleServerHandler.java` | I/O 事件处理（~530 行 switch） |
| `handling/login/LoginServer.java` | 登录服启动 |
| `handling/channel/ChannelServer.java` | 频道服启动 |
| `handling/cashshop/CashShopServer.java` | 商城服启动 |
| `com/.../auth/AuthenticationServer.java` | 新版认证服 |
| `com/.../di/MinaModule.java` | Guice DI 配置 |
| `tools/MockIOSession.java` | 测试用 Mock 会话 |
| `client/MapleClient.java` | 客户端连接对象 |
| `tools/MapleAESOFB.java` | AES OFB 加密 |
| `tools/MapleCustomEncryption.java` | 自定义加密 |

---

## 三、Netty 调研

### 3.1 版本选择

| 版本 | JDK 要求 | 状态 |
|------|----------|------|
| **Netty 4.2.13.Final** | JDK 8+ | 最新稳定版，推荐 |
| **Netty 4.1.133.Final** | JDK 6+ | LTS 维护线，保守选择 |

**推荐使用 Netty 4.2.13.Final**，原因：
- 项目使用 JDK 8，满足最低要求
- 内置 `PooledByteBufAllocator` 自适应分配器
- `MultiThreadIoEventLoopGroup` 更好的多线程调度
- 持续的功能更新和性能优化

### 3.2 MINA → Netty 概念映射

| MINA | Netty | 说明 |
|------|-------|------|
| `NioSocketAcceptor` | `ServerBootstrap` + `NioServerSocketChannel` | 服务端启动器 |
| `IoSession` | `Channel` / `ChannelHandlerContext` | 连接上下文 |
| `IoHandlerAdapter` | `ChannelInboundHandlerAdapter` | 入站事件处理器 |
| `sessionOpened()` | `channelActive()` | 连接建立 |
| `sessionClosed()` | `channelInactive()` | 连接关闭 |
| `messageReceived(session, msg)` | `channelRead(ctx, msg)` | 消息到达 |
| `exceptionCaught(session, cause)` | `exceptionCaught(ctx, cause)` | 异常处理 |
| `IoFilter` / `FilterChain` | `ChannelHandler` / `Pipeline` | 拦截链 |
| `ProtocolCodecFilter` | `ByteToMessageDecoder` + `MessageToByteEncoder` | 编解码 |
| `IoBuffer` | `ByteBuf` | 字节缓冲（读写双指针，无需 flip） |
| `session.setAttribute(key, val)` | `channel.attr(AttributeKey).set(val)` | 属性存储 |
| `session.write(msg)` | `ctx.writeAndFlush(msg)` | 写响应 |
| `session.setIdleTime()` | `IdleStateHandler`（Pipeline 中添加） | 空闲检测 |
| `CumulativeProtocolDecoder` | `ByteToMessageDecoder`（内置累积缓冲） | 粘包拆包 |

### 3.3 关键差异

1. **Handler 实例化**：MINA 的 IoHandler 默认为单例共享；Netty 的 ChannelHandler 可通过 `@Sharable` 标注控制是否共享。不标 `@Sharable` 的 handler，Netty 为每个连接创建独立实例，天然隔离 per-connection 状态。

2. **ByteBuf vs IoBuffer**：Netty 的 ByteBuf 有独立的 read/write 指针，`flip()` 不需要；MINA 的 IoBuffer 类似 Java NIO Buffer，需要 `flip()`。ByteBuf 支持池化（`PooledByteBufAllocator`），减少 GC 压力。

3. **Pipeline 执行顺序**：MINA 的 FilterChain 是 `addLast` 顺序执行；Netty 的 ChannelPipeline 严格分 Inbound（正向）和 Outbound（反向），入站 Handler 按添加顺序执行，出站 Handler 按逆序执行。

4. **EventLoop 模型**：Netty 的 `NioEventLoopGroup` 可指定 boss（accept）和 worker（I/O read/write）线程数；MINA 的 I/O Processor 线程数是全局的。

---

## 四、改造点

### 4.1 依赖变更

**移除**：
```xml
<dependency>
    <groupId>org.apache.mina</groupId>
    <artifactId>mina-core</artifactId>
    <version>2.0.9</version>
</dependency>
```

**新增**：
```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
    <version>4.2.13.Final</version>
</dependency>
```

### 4.2 编解码层改造

这是改造核心，涉及文件：

#### 4.2.1 MaplePacketDecoder → MaplePacketDecoderNetty

当前 `MaplePacketDecoder` 继承 MINA 的 `CumulativeProtocolDecoder`。

改造方案：继承 Netty 的 `ByteToMessageDecoder`，重写 `decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)`。

改动要点：
- `DecoderState` 从 `IoSession.getAttribute()` 改为 `Channel.attr(DECODER_STATE_KEY)` 或使用 handler 实例字段（不标 `@Sharable` 则每连接独立）
- `IoBuffer` → `ByteBuf`：`in.getInt()` → `in.readInt()`，`in.remaining()` → `in.readableBytes()`
- 解密逻辑（MapleAESOFB + MapleCustomEncryption）不变
- 输出从 `out.write(byte[])` 改为 `out.add(byte[])`

#### 4.2.2 MaplePacketEncoder → MaplePacketEncoderNetty

当前 `MaplePacketEncoder` 实现 MINA 的 `ProtocolEncoder`。

改造方案：继承 Netty 的 `MessageToByteEncoder<MaplePacket>`，重写 `encode(ChannelHandlerContext ctx, MaplePacket msg, ByteBuf out)`。

改动要点：
- `client.getLock()` 仍需要保护 IV 并发访问
- 输出从 `out.write(IoBuffer.wrap(ret))` 改为 `out.writeBytes(ret)`
- Handshake 路径（client == null）需特殊处理

#### 4.2.3 MapleCodecFactory → 直接组装 Pipeline

当前 `MapleCodecFactory` 是一个简单的工厂，包装 Encoder 和 Decoder。Netty 中不再需要中间工厂，直接将 Encoder/Decoder 添加到 Pipeline：

```java
pipeline.addLast("decoder", new MaplePacketDecoderNetty());
pipeline.addLast("encoder", new MaplePacketEncoderNetty());
```

### 4.3 Handler 层改造

#### 4.3.1 MapleServerHandler

当前 `MapleServerHandler` 继承 MINA 的 `IoHandlerAdapter`，是单例共享。

**状态管理规范**：

⚠️ **重要决策**：Netty 的 `@Sharable` 标注需根据 Handler 是否持有 per-connection 状态决定：

1. **无状态 Handler**（推荐）：不标注 `@Sharable`，让 Netty 为每个 Channel 创建独立实例
   - 优点：天然隔离 per-connection 状态，无需担心并发问题
   - 缺点：每连接创建一个 Handler 实例（内存开销小，可忽略）
   - 适用场景：Handler 中有实例字段存储连接状态（如计数器、缓存等）

2. **共享 Handler**：标注 `@Sharable`，全局单例
   - 优点：减少对象创建开销
   - 缺点：必须保证 Handler 完全无状态，所有状态必须通过 `channel.attr()` 存储
   - 适用场景：Handler 仅有方法局部变量，无实例字段

**推荐方案**：**不使用 `@Sharable`，每连接独立实例**。原因：
- 当前 `MapleServerHandler` 可能有 per-connection 状态（需要审查源码确认）
- 独立实例模式更安全，避免状态污染
- 性能影响微乎其微（单个对象创建开销 ~几 KB）

改造方案：继承 Netty 的 `ChannelInboundHandlerAdapter`，**不标注** `@Sharable`。

```java
public class MapleServerHandler extends ChannelInboundHandlerAdapter {
    // 如果有 per-connection 状态，可以直接作为实例字段
    // private int packetCount = 0; // 示例：统计当前连接的包数量
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 初始化 MapleClient、发送 Handshake
    }
}
```

事件映射：

| MINA 方法 | Netty 方法 |
|-----------|-----------|
| `sessionOpened(IoSession)` | `channelActive(ChannelHandlerContext)` |
| `sessionClosed(IoSession)` | `channelInactive(ChannelHandlerContext)` |
| `messageReceived(IoSession, Object)` | `channelRead(ChannelHandlerContext, Object)` |
| `exceptionCaught(IoSession, Throwable)` | `exceptionCaught(ChannelHandlerContext, Throwable)` |
| `sessionIdle(IoSession, IdleStatus)` | 改用 `IdleStateHandler` + `userEventTriggered` |

具体要求：
- `sessionOpened` → `channelActive`：初始化 MapleClient、发送 Handshake 包、设置属性
- `sessionClosed` → `channelInactive`：调用 `client.disconnect()`
- `messageReceived` → `channelRead`：opcode 解析 + handlePacket 分发（逻辑基本不变）
- 空闲检测：移除 MINA 的 `session.getConfig().setIdleTime()`，改用 Netty `IdleStateHandler` 添加到 Pipeline

**异常处理差异**：

⚠️ MINA 和 Netty 的异常处理机制不同：

- **MINA**：异常传播到 `exceptionCaught()` 后，连接默认保持，需手动关闭
- **Netty**：异常默认会关闭 Channel（除非 handler 中处理并阻止传播）

改造要点：
```java
@Override
public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    // 业务异常：记录日志，保持连接
    if (cause instanceof BusinessException) {
        log.warn("业务异常", cause);
        return; // 不关闭连接
    }
    
    // IO/编解码异常：关闭连接
    log.error("连接异常，即将关闭", cause);
    ctx.close();
}
```

#### 4.3.2 MapleClient 改动

- `session.write(packet)` → `ctx.writeAndFlush(packet)`（需要持有 ChannelHandlerContext 引用）
- `getLock()`（ReentrantLock）保留，仍用于保护 IV 并发访问
- `MockIOSession` 需重写为 Mock Channel

### 4.4 Server 启动层改造

三类服务器均需从 `NioSocketAcceptor` 改为 `ServerBootstrap`。

#### 标准启动代码模板

```java
EventLoopGroup bossGroup = new NioEventLoopGroup(1);
EventLoopGroup workerGroup = new NioEventLoopGroup();
try {
    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ch.pipeline()
                    .addLast("idle", new IdleStateHandler(60, 60, 0))
                    .addLast("decoder", new MaplePacketDecoderNetty())
                    .addLast("encoder", new MaplePacketEncoderNetty())
                    .addLast("handler", serverHandler);
            }
        })
        .childOption(ChannelOption.TCP_NODELAY, true);
    
    ChannelFuture f = b.bind(port).sync();
    f.channel().closeFuture().sync();
} finally {
    workerGroup.shutdownGracefully();
    bossGroup.shutdownGracefully();
}
```

改动文件：
- `LoginServer.java` → `LoginServerNetty` 或直接修改
- `ChannelServer.java` → 将 `IoAcceptor acceptor` 改为 `EventLoopGroup bossGroup/workerGroup`
- `CashShopServer.java` → 同理
- `AuthenticationServer.java` → 同理
- `MinaModule.java` → 重命名为 `NettyModule`，提供 `EventLoopGroup` 等

### 4.5 Guice DI 改造

`MinaModule.java` 当前提供：
- `NioSocketAcceptor`（非 Singleton，每次创建新实例）
- `ProtocolCodecFilter`（包装 MapleCodecFactory）

改造为 `NettyModule.java`：
- 提供 `EventLoopGroup`（boss + worker）
- 提供 `MapleServerHandler`（保持 @Sharable 单例）
- 不再需要 `ProtocolCodecFilter` 和 `MapleCodecFactory`

### 4.6 全局 IoBuffer 配置移除

三处冗余的：
```java
IoBuffer.setUseDirectBuffer(false);
IoBuffer.setAllocator(new SimpleBufferAllocator());
```

在 Netty 中，Buffer 分配通过 `ServerBootstrap.option(ChannelOption.ALLOCATOR, ...)` 配置，推荐使用 `PooledByteBufAllocator.DEFAULT`（Netty 默认行为，无需额外配置）。

### 4.7 MockIOSession 改写

`tools/MockIOSession.java` 当前继承 MINA `DummySession`，用于离线创建 MapleClient（fakechar 等场景）。

改造为：
- 创建 `EmbeddedChannel`（Netty 测试工具类，无需真实网络连接）
- 或自行实现 `Channel` 的 mock 行为

### 4.8 改造清单汇总

| 改造项 | 涉及文件数 | 复杂度 | 工作量 |
|--------|-----------|--------|--------|
| 依赖变更（pom.xml） | 1 | 低 | 0.5h |
| Decoder 重写 | 1 | 中 | 3h |
| Encoder 重写 | 1 | 中 | 2h |
| CodecFactory → Pipeline 组装 | 删除 1，改 4 | 低 | 1h |
| MapleServerHandler 重写 | 1 | 中 | 4h |
| MapleClient 适配 | 1 | 中 | 3h |
| LoginServer 启动改造 | 1 | 低 | 1h |
| ChannelServer 启动改造 | 1 | 中 | 2h |
| CashShopServer 启动改造 | 1 | 低 | 1h |
| AuthenticationServer 改造 | 1 | 低 | 1h |
| Guice Module 改造 | 1 | 低 | 1h |
| MockIOSession 改写 | 1 | 中 | 2h |
| IoBuffer 配置移除 | 3 | 低 | 0.5h |
| IdleStateHandler 替代 | 1 | 低 | 1h |
| 集成测试 & 调试 | — | 高 | 12h |
| **合计** | **约 20 文件** | — | **约 35h** |

---

## 五、风险分析

### 5.1 高风险

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| **加密正确性** | AES OFB 是有状态流密码，改造过程中编解码顺序、字节序错误会导致对端无法解密，客户端 100% 掉线 | 保留现有加密逻辑代码不变，只改外层 ByteBuf 封装；做二进制级别的入/出站 byte 对比测试 |
| **粘包拆包** | 当前 `CumulativeProtocolDecoder` 的累积缓冲语义与 `ByteToMessageDecoder` 基本一致，但边界条件（如 header 已到但 body 不足）必须严格复刻 | 对照原 doDecode 逻辑逐行验证 Netty 版本的 decode 方法 |
| **线程安全** | 当前 `MaplePacketEncoder` 使用 `client.getLock()` 保护 IV，改造后 channel 可能被多个线程写（IO worker + Timer + Actor），需确保 `writeAndFlush` 的线程安全 | 保留 `client.getLock()`，或确保所有 write 操作在统一线程执行 |

### 5.2 中风险

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| **性能回退** | Netty 池化缓冲和零拷贝带来性能增益，但配置不当（如 TCP_NODELAY 未设、SO_BACKLOG 默认值等）可能导致延迟增加 | 对齐现有 MINA 的 TCP 配置项，启用 NODELAY |
| **EventLoop 线程模型变更** | MINA 中所有 I/O 事件在 I/O Processor 线程执行，Netty 的 channelRead 同样在 EventLoop 线程。但如果误用了 `addLast(group, handler)`，可能导致 handler 在不同线程执行，破坏现有同步假设 | 不与 EventExecutorGroup 绑定，让 handler 在默认 EventLoop 中执行 |
| **Pipeline 顺序错误** | Netty Pipeline 的 Inbound 顺序执行、Outbound 逆序执行，如果 Encoder/Decoder 添加顺序错误，加密解密顺序可能颠倒 | 按 Inbound→Decoder→Handler, Outbound→Encoder 的顺序配置 |

### 5.3 低风险

| 风险 | 说明 | 缓解措施 |
|------|------|---------|
| **Session 属性迁移** | MINA 的 `session.getAttribute()` → Netty 的 `channel.attr(key).get()`，API 变化但语义一致 | 定义对应的 `AttributeKey` 常量 |
| **MockIOSession** | 只有 fakechar 等少数场景使用，影响面小 | 使用 `EmbeddedChannel` 替代 |
| **Guice 注入变更** | `MinaModule` 提供的类型变化，需更新依赖注入点 | 逐文件确认注入类型 |
| **日志输出** | MINA 内部日志变为 Netty 内部日志，可能产生新的噪音日志 | 配置 logback 屏蔽 Netty 内部 DEBUG 日志 |

### 5.4 回滚策略

- 所有改造在独立分支（`feature/netty-migration`）上进行
- 保留原有 MINA 代码（通过 rename 如 `MaplePacketDecoder_MINA` 保留）
- 每完成一个模块就编译测试
- 如果集成测试无法通过，可完整回退到 MINA 版本

---

## 六、建议实施步骤

1. **Phase 0**：添加 Netty 依赖，验证编译通过（不影响现有代码）
2. **Phase 1**：改造 Decoder + Encoder，编写单元测试验证加密解密正确性（用已知明文/密文对）
3. **Phase 2**：改造 MapleServerHandler + IdleStateHandler
4. **Phase 3**：改造 LoginServer（单一端口，最简单），端到端验证握手→登录→进游戏
5. **Phase 4**：改造 ChannelServer + CashShopServer
6. **Phase 5**：改造 AuthenticationServer + Guice Module
7. **Phase 6**：清理旧代码（MockIOSession、IoBuffer 配置、MINA 依赖）

---

## 七、测试方案

### 7.1 单元测试

#### 7.1.1 编解码器测试

**目标**：验证 Netty 版本的编解码逻辑与 MINA 版本完全一致。

**测试方法**：
1. **录制 MINA 版本的测试数据**：
   - 准备多组测试用例（明文 → 密文对）
   - 覆盖边界情况：空包、小包（< 100B）、中包（100B ~ 1KB）、大包（> 1KB）、MTU 边界

2. **编写 Netty 版本的单元测试**：
```java
@Test
public void testEncoderEquivalence() {
    // 使用相同的明文
    byte[] plaintext = getTestPacket();
    
    // MINA 编码
    byte[] minaEncrypted = minaEncoder.encode(plaintext);
    
    // Netty 编码
    ByteBuf buf = Unpooled.buffer();
    nettyEncoder.encode(ctx, plaintext, buf);
    byte[] nettyEncrypted = new byte[buf.readableBytes()];
    buf.readBytes(nettyEncrypted);
    
    // 对比必须完全一致
    assertArrayEquals(minaEncrypted, nettyEncrypted);
}

@Test
public void testDecoderEquivalence() {
    // 使用相同的密文
    byte[] ciphertext = getTestEncryptedPacket();
    
    // MINA 解码
    byte[] minaDecrypted = minaDecoder.decode(ciphertext);
    
    // Netty 解码
    List<Object> out = new ArrayList<>();
    nettyDecoder.decode(ctx, Unpooled.wrappedBuffer(ciphertext), out);
    byte[] nettyDecrypted = (byte[]) out.get(0);
    
    // 对比必须完全一致
    assertArrayEquals(minaDecrypted, nettyDecrypted);
}
```

3. **测试覆盖率要求**：
   - `MaplePacketDecoderNetty`: 行覆盖率 > 90%
   - `MaplePacketEncoderNetty`: 行覆盖率 > 90%

#### 7.1.2 Handler 测试

**目标**：验证 Handler 的事件处理逻辑正确。

**测试方法**：使用 Netty 的 `EmbeddedChannel` 进行单元测试。

```java
@Test
public void testHandlerChannelActive() {
    EmbeddedChannel channel = new EmbeddedChannel(
        new MaplePacketDecoderNetty(),
        new MaplePacketEncoderNetty(),
        new MapleServerHandler()
    );
    
    // 验证握手包发送
    ByteBuf handshake = channel.readOutbound();
    assertNotNull(handshake);
    assertEquals(16, handshake.readableBytes());
    
    // 验证 MapleClient 创建
    MapleClient client = channel.attr(MapleClient.CLIENT_KEY).get();
    assertNotNull(client);
}
```

### 7.2 集成测试

#### 7.2.1 完整登录流程测试

**测试步骤**：
1. 启动 Netty 版本的 LoginServer
2. 使用真实客户端连接并完成登录流程
3. 验证关键节点：
   - 握手包格式正确
   - 加密通道建立成功
   - 登录请求/响应正常
   - 角色列表加载正常

**自动化测试工具**：
- 使用 Wireshark 抓包对比 MINA/Netty 版本的网络数据
- 编写 Python 脚本模拟客户端行为（推荐使用 `scapy` 库）

#### 7.2.2 断线重连测试

**测试场景**：
- 客户端主动断开，服务器资源释放正常
- 服务器主动关闭连接，客户端收到通知
- 网络中断（模拟丢包、延迟），IdleStateHandler 触发超时关闭

#### 7.2.3 并发压力测试

**测试工具**：JMeter + 自定义 TCP Sampler

**测试场景**：
- 100 并发连接同时登录
- 1000 并发连接保持空闲（测试 IdleStateHandler）
- 单连接高频发包（100 TPS，测试粘包拆包）

### 7.3 性能测试

#### 7.3.1 基线测试（迁移前）

**测试环境**：
- 服务器：CPU 4核、内存 8GB
- 客户端：真实游戏客户端或压测工具

**测试指标**：

| 指标 | MINA 基线值 | 备注 |
|------|------------|------|
| 单机最大并发连接数 | ___ | 逐步加压至服务端拒绝 |
| 平均消息延迟（P50/P95/P99） | ___ ms | 使用真实游戏场景 |
| 吞吐量（TPS） | ___ | 每秒处理消息数 |
| CPU 使用率（峰值） | ___% | 压测期间最高值 |
| 内存占用（峰值） | ___ MB | 压测期间最高值 |
| GC 暂停时间（平均） | ___ ms | Young GC + Full GC |

#### 7.3.2 对比测试（迁移后）

**测试要求**：
- 使用相同的测试环境、测试工具、测试场景
- 关键指标差异 < 10% 为合格
- 如果 Netty 版本性能更差，需排查原因（如 ByteBuf 泄漏、线程配置错误等）

#### 7.3.3 长期稳定性测试

**测试场景**：
- 服务器连续运行 72 小时
- 定期模拟客户端连接/断开（每小时 1000 次连接）
- 监控内存泄漏、CPU 使用率、GC 频率

### 7.4 兼容性测试

#### 7.4.1 客户端版本兼容

**测试范围**：
- 官方客户端 v079
- 主流私服客户端版本（如有）
- 不同操作系统版本（Windows 7/10/11）

#### 7.4.2 边界条件测试

**测试场景**：
- **网络异常**：断网、弱网、高延迟（> 500ms）、丢包（> 5%）
- **数据异常**：恶意构造的封包（超大包、畸形包、非法 opcode）
- **并发异常**：同一账号多点登录、快速重连

---

## 八、灰度发布与回滚方案

### 8.1 灰度发布策略

#### 方案A：端口并行（推荐）

**实施步骤**：
1. **部署阶段**：
   - MINA 版本继续监听原端口（8484）
   - Netty 版本新增监听端口（8485）
   - 两套服务同时运行，共享后端数据

2. **灰度切流**：
   - 通过负载均衡（Nginx/HAProxy）控制流量分配
   - 切流比例：5% → 20% → 50% → 80% → 100%
   - 每个阶段观察至少 24 小时，无异常再继续

3. **全量切换**：
   - 确认 Netty 版本稳定后，停止 MINA 服务
   - 将 Netty 服务切换回原端口（可选）

**优点**：
- 可随时回滚，只需调整负载均衡配置
- 两套服务独立，互不影响

**缺点**：
- 需要额外服务器资源
- 端口管理稍复杂

#### 方案B：配置开关

**实施步骤**：
1. 在配置中心（如 Diamond）添加开关：`use_netty=true/false`
2. 启动时根据开关决定使用 MINA 或 Netty
3. 通过配置中心动态切换，无需重启服务

**优点**：
- 无需额外端口
- 切换快速

**缺点**：
- 切换时需要重启服务（或实现热切换，复杂度高）
- 同一时间只能运行一套服务

#### 方案C：金丝雀发布（适用于多节点部署）

**实施步骤**：
1. 生产环境有 N 个节点（如 10 个）
2. 先将 1 个节点切换到 Netty 版本（10% 流量）
3. 观察稳定后，逐步增加 Netty 节点：2 → 5 → 10

### 8.2 灰度发布计划

| 时间 | 灰度比例 | 观察重点 | 决策点 |
|------|---------|---------|--------|
| Day 0 | 测试服全量 | 功能正确性、登录成功率 | 测试通过后进入生产灰度 |
| Day 1-2 | 生产服 5% | 错误日志、客户端掉线率 | 无异常继续 |
| Day 3-4 | 生产服 20% | 性能指标、用户反馈 | 无异常继续 |
| Day 5-6 | 生产服 50% | 并发能力、稳定性 | 无异常继续 |
| Day 7-8 | 生产服 100% | 全量观察 | 稳定后下线 MINA |

### 8.3 回滚方案

#### 回滚触发条件

立即回滚的场景：
- 登录成功率下降 > 5%
- 客户端掉线率上升 > 10%
- 出现大量异常日志（> 10 条/分钟）
- 性能指标严重退化（延迟增加 > 50%）
- 出现数据损坏或安全问题

#### 回滚步骤

**方案A（端口并行）**：
1. 负载均衡配置：Netty 端口权重改为 0，MINA 端口权重改为 100%
2. 等待现有 Netty 连接自然断开（约 5 分钟）
3. 停止 Netty 服务
4. 验证 MINA 服务正常

**预计回滚时间**：5-10 分钟

**方案B（配置开关）**：
1. 配置中心修改：`use_netty=false`
2. 重启所有服务节点（滚动重启，避免服务中断）
3. 验证 MINA 版本启动成功

**预计回滚时间**：10-15 分钟（取决于节点数量）

#### 回滚后处理

1. 分析 Netty 版本失败原因
2. 修复问题后重新测试
3. 更新迁移方案文档
4. 下次灰度从小比例重新开始

### 8.4 应急预案

#### 8.4.1 监控告警

**实时监控指标**：
- 当前连接数
- 每分钟登录成功/失败次数
- 每分钟异常断开次数
- 消息处理延迟（P99）
- 服务器 CPU/内存使用率

**告警规则**：
- 连接数骤降 > 30% → P1 告警（电话+短信）
- 登录失败率 > 5% → P1 告警
- 异常断开率 > 10% → P2 告警（短信）
- 消息延迟 P99 > 500ms → P2 告警

#### 8.4.2 应急联系人

- 技术负责人：[姓名] - [电话]
- 运维负责人：[姓名] - [电话]
- 值班热线：[电话]

#### 8.4.3 应急流程

1. 告警触发后，值班人员 5 分钟内响应
2. 确认问题严重程度，决定是否回滚
3. 如需回滚，按回滚步骤执行，并通知相关人员
4. 回滚后记录事故报告，分析根因

---

## 九、监控与告警

### 9.1 监控指标

#### 9.1.1 连接层指标

| 指标名称 | 说明 | 告警阈值 |
|---------|------|---------|
| `netty.active.connections` | 当前活跃连接数 | 骤降 > 30% |
| `netty.connection.accept.rate` | 每秒新建连接数 | < 1（异常低） |
| `netty.connection.reject.rate` | 每秒拒绝连接数 | > 10 |
| `netty.connection.timeout.rate` | 连接超时率 | > 1% |

#### 9.1.2 性能指标

| 指标名称 | 说明 | 告警阈值 |
|---------|------|---------|
| `netty.message.throughput` | 消息吞吐量（TPS） | 骤降 > 50% |
| `netty.message.latency.p50` | 消息延迟 P50 | > 100ms |
| `netty.message.latency.p99` | 消息延迟 P99 | > 500ms |
| `netty.bytebuf.active.count` | 活跃 ByteBuf 数量 | 持续增长（泄漏） |
| `netty.bytebuf.active.memory` | ByteBuf 内存占用 | > 1GB |

#### 9.1.3 异常指标

| 指标名称 | 说明 | 告警阈值 |
|---------|------|---------|
| `netty.exception.rate` | 每分钟异常次数 | > 10 |
| `netty.idle.disconnect.rate` | 空闲超时断开率 | > 5% |
| `netty.encode.error.rate` | 编码错误率 | > 0.1% |
| `netty.decode.error.rate` | 解码错误率 | > 0.1% |

#### 9.1.4 系统指标

| 指标名称 | 说明 | 告警阈值 |
|---------|------|---------|
| `jvm.cpu.usage` | JVM CPU 使用率 | > 80% |
| `jvm.memory.used` | JVM 堆内存使用 | > 80% |
| `jvm.gc.pause.time` | GC 暂停时间 | P99 > 100ms |
| `jvm.gc.young.count` | Young GC 次数 | > 100 次/分钟 |

### 9.2 监控工具

**推荐工具**：
- **Prometheus + Grafana**：指标采集和可视化
- **CAT / SkyWalking**：APM 链路追踪
- **Arthas**：JVM 在线诊断

**Grafana Dashboard 示例**：
```yaml
# Netty 关键指标面板
- 连接数趋势图（实时 + 1小时历史）
- 消息吞吐量趋势图
- 延迟分布直方图（P50/P95/P99）
- 异常次数热力图
```

### 9.3 日志规范

#### 9.3.1 日志级别

| 级别 | 场景 | 示例 |
|------|------|------|
| ERROR | 严重异常，需要立即处理 | 加密失败、解码异常、连接异常关闭 |
| WARN | 潜在问题，需要关注 | 空闲超时、重试、降级 |
| INFO | 关键业务节点 | 连接建立、登录成功、重要状态变更 |
| DEBUG | 调试信息（生产环境关闭） | 详细封包内容、内部状态 |

#### 9.3.2 日志格式

```java
// 推荐日志格式
log.info("[Netty][Connection] channelActive: channelId={}, remoteAddr={}", 
    ctx.channel().id().asShortText(), 
    ctx.channel().remoteAddress());

log.error("[Netty][Decode] decode failed: channelId={}, error={}", 
    ctx.channel().id().asShortText(), 
    cause.getMessage(), 
    cause);
```

#### 9.3.3 敏感信息脱敏

**禁止记录**：
- 用户密码明文
- 加密密钥（IV）
- 完整封包内容（仅在 DEBUG 级别可记录前 16 字节）

### 9.4 ByteBuf 泄漏检测

#### 9.4.1 开发阶段

启动时启用泄漏检测：
```java
// 在 main() 方法开头添加
System.setProperty("io.netty.leakDetection.level", "PARANOID");
```

**检测级别**：
- `DISABLED`：关闭检测（不推荐）
- `SIMPLE`：简单检测（默认，开销小）
- `ADVANCED`：高级检测（开销中等，采样率 1%）
- `PARANOID`：偏执模式（开销大，每个 ByteBuf 都检测，**开发阶段必开**）

#### 9.4.2 生产阶段

设置 `SIMPLE` 或 `ADVANCED`：
```java
System.setProperty("io.netty.leakDetection.level", "ADVANCED");
System.setProperty("io.netty.leakDetection.targetRecords", "10"); // 每次采样 10 个
```

#### 9.4.3 泄漏告警

当检测到泄漏时，Netty 会输出日志：
```
LEAK: ByteBuf.release() was not called before it's garbage-collected.
Recent access records: ...
```

**处理流程**：
1. 立即排查代码中 `ByteBuf` 的创建和释放逻辑
2. 确保所有 handler 中正确使用 `ReferenceCountUtil.release(buf)`
3. 考虑使用 `SimpleChannelInboundHandler` 自动释放

### 9.5 日志配置

**logback.xml 配置示例**：
```xml
<!-- Netty 日志配置 -->
<logger name="io.netty" level="INFO"/>
<logger name="io.netty.channel" level="WARN"/>  <!-- 连接关闭等异常 -->
<logger name="io.netty.buffer" level="ERROR"/>   <!-- ByteBuf 泄漏检测 -->
<logger name="io.netty.util.ResourceLeakDetector" level="WARN"/> <!-- 泄漏告警 -->

<!-- 业务日志配置 -->
<logger name="com.alibaba.force" level="INFO"/>
<logger name="handling" level="INFO"/>

<!-- 屏蔽 Netty 内部 DEBUG 日志 -->
<logger name="io.netty.util.Recycler" level="ERROR"/>
```

---

## 十、技术细节补充

### 10.1 字节序处理

⚠️ **重要**：MapleStory 协议使用**小端序（Little Endian）**。

#### 10.1.1 MINA 中的字节序处理

检查当前 MINA 实现中是否正确处理：
```java
// 查找类似代码
IoBuffer buf = IoBuffer.allocate(1024);
buf.order(ByteOrder.LITTLE_ENDIAN); // 必须设置小端序
buf.putShort(opcode); // 写入 2 字节 opcode（小端序）
```

#### 10.1.2 Netty 中的字节序处理

**方案1：使用 ByteBuf 的 LE 后缀方法（推荐）**
```java
// 编码器
ByteBuf buf = Unpooled.buffer(1024);
buf.writeShortLE(opcode);   // 小端序写入 short
buf.writeIntLE(length);     // 小端序写入 int
buf.writeLongLE(timestamp); // 小端序写入 long

// 解码器
int opcode = buf.readUnsignedShortLE(); // 小端序读取 unsigned short
int length = buf.readIntLE();           // 小端序读取 int
```

**方案2：使用 LittleEndianByteBuf（性能稍优）**
```java
// 在 Pipeline 开头添加
pipeline.addFirst("littleEndian", new LittleEndianByteBuf());

// 之后所有读写无需 LE 后缀
buf.writeShort(opcode); // 自动小端序
buf.writeInt(length);   // 自动小端序
```

**实现自定义 LittleEndianByteBuf**：
```java
public class LittleEndianByteBuf extends ByteBuf {
    private final ByteBuf wrapped;
    
    public LittleEndianByteBuf(ByteBuf wrapped) {
        this.wrapped = wrapped.order(ByteOrder.LITTLE_ENDIAN);
    }
    
    @Override
    public ByteBuf order(ByteOrder endianness) {
        return this; // 强制小端序
    }
    
    // 其他方法委托给 wrapped
}
```

#### 10.1.3 验证方法

编写单元测试验证字节序正确：
```java
@Test
public void testLittleEndian() {
    ByteBuf buf = Unpooled.buffer(4);
    buf.writeIntLE(0x12345678);
    
    byte[] bytes = new byte[4];
    buf.readBytes(bytes);
    
    // 小端序：低字节在前
    assertEquals(0x78, bytes[0] & 0xFF);
    assertEquals(0x56, bytes[1] & 0xFF);
    assertEquals(0x34, bytes[2] & 0xFF);
    assertEquals(0x12, bytes[3] & 0xFF);
}
```

### 10.2 IdleStateHandler 配置细节

#### 10.2.1 MINA 原配置验证

检查 MINA 中的空闲检测配置：
```java
// 查找类似代码
session.getConfig().setIdleTime(IdleStatus.READER_IDLE, 60); // 读取空闲 60 秒
session.getConfig().setIdleTime(IdleStatus.WRITER_IDLE, 60); // 写入空闲 60 秒
```

#### 10.2.2 Netty 配置

```java
// 添加到 Pipeline
pipeline.addLast("idle", new IdleStateHandler(
    60,  // readerIdleTime: 读取空闲超时（秒）
    60,  // writerIdleTime: 写入空闲超时（秒）
    0    // allIdleTime: 读写都空闲超时（秒），0 表示禁用
));

// Handler 中处理空闲事件
@Override
public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof IdleStateEvent) {
        IdleStateEvent event = (IdleStateEvent) evt;
        if (event.state() == IdleState.READER_IDLE) {
            log.warn("读空闲超时，关闭连接: {}", ctx.channel().remoteAddress());
            ctx.close();
        } else if (event.state() == IdleState.WRITER_IDLE) {
            log.warn("写空闲超时，发送心跳包: {}", ctx.channel().remoteAddress());
            // 发送心跳包（如果有）
            // ctx.writeAndFlush(new HeartbeatPacket());
        }
    }
}
```

#### 10.2.3 空闲检测策略

**推荐策略**：
- **读空闲**：60 秒无数据到达，视为客户端断开，服务器主动关闭连接
- **写空闲**：60 秒无数据发送，发送心跳包保持连接（如果协议支持）
- **全空闲**：不启用（通常不需要）

### 10.3 MockIOSession 改造详细方案

#### 方案1：使用 EmbeddedChannel（推荐）

```java
public class MockChannel {
    private static final AttributeKey<MapleClient> CLIENT_KEY = 
        AttributeKey.valueOf("MapleClient.CLIENT_KEY");
    
    public static MapleClient createFakeClient() {
        // 创建 EmbeddedChannel，模拟真实连接
        EmbeddedChannel channel = new EmbeddedChannel(
            new MaplePacketDecoderNetty(),
            new MaplePacketEncoderNetty()
        );
        
        // 初始化 MapleClient
        MapleClient client = new MapleClient(channel);
        channel.attr(CLIENT_KEY).set(client);
        
        return client;
    }
    
    // 模拟接收封包
    public static byte[] receivePacket(EmbeddedChannel channel, byte[] data) {
        channel.writeInbound(Unpooled.wrappedBuffer(data));
        return channel.readInbound();
    }
    
    // 模拟发送封包
    public static byte[] sendPacket(EmbeddedChannel channel, MaplePacket packet) {
        channel.writeOutbound(packet);
        ByteBuf buf = channel.readOutbound();
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }
}
```

#### 方案2：自定义 Mock Channel（如果 EmbeddedChannel 不满足需求）

```java
public class MockChannel extends AbstractChannel {
    private final ChannelConfig config;
    private final Unsafe unsafe;
    
    public MockChannel() {
        super(null); // 无父 Channel
        this.config = new DefaultChannelConfig(this);
        this.unsafe = new MockUnsafe();
    }
    
    @Override
    public ChannelConfig config() {
        return config;
    }
    
    @Override
    public Unsafe unsafe() {
        return unsafe;
    }
    
    @Override
    protected Unsafe newUnsafe() {
        return new MockUnsafe();
    }
    
    private class MockUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            promise.setSuccess(); // 模拟连接成功
        }
    }
    
    // 实现其他必要方法...
}
```

### 10.4 Pipeline 顺序配置

#### 10.4.1 正确的 Pipeline 顺序

```java
ServerBootstrap b = new ServerBootstrap();
b.childHandler(new ChannelInitializer<SocketChannel>() {
    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        
        // Inbound 处理顺序（入站）：
        // 1. 空闲检测 → 2. 解码 → 3. 业务处理
        
        // Outbound 处理顺序（出站）：
        // 3. 业务处理 → 2. 编码 → 1. 空闲检测（IdleStateHandler 也处理出站事件）
        
        pipeline
            .addLast("idle", new IdleStateHandler(60, 60, 0))  // 入站：检测空闲，出站：心跳
            .addLast("decoder", new MaplePacketDecoderNetty()) // 入站：解码
            .addLast("encoder", new MaplePacketEncoderNetty()) // 出站：编码
            .addLast("handler", new MapleServerHandler());     // 业务处理
    }
});
```

**执行流程说明**：

**入站（接收封包）**：
1. `IdleStateHandler`：检测读空闲
2. `MaplePacketDecoderNetty`：解密 + 拆包
3. `MapleServerHandler`：业务处理

**出站（发送封包）**：
1. `MapleServerHandler`：生成 `MaplePacket` 对象
2. `MaplePacketEncoderNetty`：加密 + 组包
3. `IdleStateHandler`：更新写空闲时间戳

#### 10.4.2 常见错误

**错误1：Encoder 和 Decoder 顺序颠倒**
```java
// ❌ 错误示例
pipeline
    .addLast("encoder", new MaplePacketEncoderNetty()) // 出站：编码
    .addLast("decoder", new MaplePacketDecoderNetty()) // 入站：解码
    .addLast("handler", new MapleServerHandler());
```
虽然顺序看起来不对，但实际上 Netty 会根据 Handler 类型自动判断入站/出站，所以这种写法也能工作。但为了代码可读性，建议统一按顺序添加。

**错误2：Handler 在 Encoder 之前**
```java
// ❌ 可能的错误（如果 Handler 直接写 ByteBuf）
pipeline
    .addLast("handler", new MapleServerHandler())     // 业务处理
    .addLast("encoder", new MaplePacketEncoderNetty()); // 编码
```
如果 Handler 输出的是 `MaplePacket` 对象，这样写没问题；但如果 Handler 直接写 `ByteBuf`，会绕过编码器。

---

## 十一、参考
