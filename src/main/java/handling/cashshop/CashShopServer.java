package handling.cashshop;

import com.github.mrzhqiang.maplestory.config.ServerProperties;
import handling.channel.PlayerStorage;
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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public final class CashShopServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CashShopServer.class);

    private static String IP;

    private final ServerProperties properties;
    private final NettyMapleServerHandler serverHandler;

    private static PlayerStorage players, playersMTS;
    private static boolean finishedShutdown = false;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Inject
    public CashShopServer(ServerProperties properties, NettyMapleServerHandler serverHandler) {
        this.properties = properties;
        this.serverHandler = serverHandler;
    }

    public void start() {
        String address = properties.getAddress();
        int port = properties.getMallPort();
        IP = address + ":" + port;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        players = new PlayerStorage(-10);
        playersMTS = new PlayerStorage(-20);

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
            PORT_CS_CACHED.put(port, true);
            LOGGER.info("商城服务器绑定端口: {}", port);
        } catch (Exception e) {
            LOGGER.error("Binding to port " + port + " failed", e);
            throw new RuntimeException("Binding failed.", e);
        }
    }


    private static final Map<Integer, Boolean> PORT_CS_CACHED = new ConcurrentHashMap<>();

    public static boolean getCsByPort(Integer port) {
        return PORT_CS_CACHED.getOrDefault(port, false);
    }

    public static String getIP() {
        return IP;
    }

    public static PlayerStorage getPlayerStorage() {
        return players;
    }

    public static PlayerStorage getPlayerStorageMTS() {
        return playersMTS;
    }

    public static void shutdown() {
        if (finishedShutdown) {
            return;
        }
        LOGGER.info("正在断开商城内玩家...");
        players.disconnectAll();
        LOGGER.info("正在关闭商城伺服器...");
        finishedShutdown = true;
    }

    public static boolean isShutdown() {
        return finishedShutdown;
    }
}
