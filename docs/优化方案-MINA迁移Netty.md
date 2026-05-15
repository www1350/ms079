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

改造方案：继承 Netty 的 `ChannelInboundHandlerAdapter`，标注 `@Sharable`。

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

## 七、参考

- [Netty 4.x User Guide](https://netty.io/wiki/user-guide-for-4.x.html)
- [Netty 4.2 Migration Guide](https://netty.io/wiki/netty-4.2-migration-guide.html)
- [MINA 2.0 Documentation](https://mina.apache.org/mina-project/userguide/user-guide-toc.html)
- [Netty API Reference](https://netty.io/4.1/api/index.html)
