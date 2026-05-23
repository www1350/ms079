package com.github.mrzhqiang.maplestory.auth;

import com.github.mrzhqiang.maplestory.api.RunnableServer;
import com.github.mrzhqiang.maplestory.config.ServerProperties;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import handling.netty.MaplePacketDecoderNetty;
import handling.netty.MaplePacketEncoderNetty;
import handling.netty.NettyMapleServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.Triple;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Singleton
public final class AuthenticationServer implements RunnableServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationServer.class);

    private final Set<String> loginIpAuth = Sets.newConcurrentHashSet();
    private final Map<Integer, Triple<String, String, Integer>> loginAuth = Maps.newConcurrentMap();

    private Map<Integer, Integer> load = Maps.newConcurrentMap();
    private int usersOn = 0;
    private int userLimit;

    private final ServerProperties properties;
    private final NettyMapleServerHandler serverHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private boolean finishedShutdown = true;

    @Inject
    public AuthenticationServer(ServerProperties properties, NettyMapleServerHandler serverHandler) {
        this.properties = properties;
        this.serverHandler = serverHandler;
        this.userLimit = properties.getOnlineLimit();
    }

    public void put(int characterId, String ip, String tempIp, int channel) {
        loginAuth.put(characterId, Triple.of(ip, tempIp, channel));
        loginIpAuth.add(ip);
    }

    public boolean containsIpAuth(String ip) {
        return loginIpAuth.contains(ip);
    }

    public void removeIpAuth(String ip) {
        loginIpAuth.remove(ip);
    }

    public void addIpAuth(String ip) {
        loginIpAuth.add(ip);
    }

    public void addChannel(int channel) {
        load.put(channel, 0);
    }

    public void removeChannel(int channel) {
        load.remove(channel);
    }

    public Map<Integer, Integer> getLoad() {
        return load;
    }

    public void setLoad(Map<Integer, Integer> load, int usersOn) {
        this.load = load;
        this.usersOn = usersOn;
    }

    public int getUsersOn() {
        return usersOn;
    }

    public int getUserLimit() {
        return userLimit;
    }

    public void setUserLimit(int userLimit) {
        this.userLimit = userLimit;
    }

    public boolean isShutdown() {
        return finishedShutdown;
    }

    public void setOn() {
        finishedShutdown = false;
    }

    @Override
    public void init() {
        bossGroup = new NioEventLoopGroup(1, r -> { Thread t = new Thread(r); t.setDaemon(false); return t; });
        workerGroup = new NioEventLoopGroup(0,r -> { Thread t = new Thread(r); t.setDaemon(false); return t; });
    }

    @Override
    public void run() {
        int port = properties.getLoginPort();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast("idle", new IdleStateHandler(60, 60, 0))
                                    .addLast("decoder", new MaplePacketDecoderNetty(properties))
                                    .addLast("encoder", new MaplePacketEncoderNetty(properties))
                                    .addLast("handler", serverHandler);
                        }
                    })
                    .childOption(ChannelOption.TCP_NODELAY, true);
            serverChannel = b.bind(port).sync().channel();
            LOGGER.info("登录器服务器绑定端口：" + port);
        } catch (Exception e) {
            LOGGER.error("绑定到端口 " + port + " 失败！", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (finishedShutdown) {
            return;
        }
        LOGGER.info("正在关闭登录服务器...");
        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
        }
        finishedShutdown = true;
    }
}
