package handling.netty;

import client.MapleClient;
import com.github.mrzhqiang.maplestory.config.ServerProperties;
import com.github.mrzhqiang.maplestory.domain.LoginState;
import constants.ServerConstants;
import handling.MapleServerHandler;
import handling.RecvPacketOpcode;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.login.LoginServer;
import handling.login.handler.CharLoginHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.FileoutputUtil;
import tools.MapleAESOFB;
import tools.Pair;
import server.Randomizer;
import tools.data.input.ByteArrayByteStream;
import tools.data.input.GenericSeekableLittleEndianAccessor;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.LoginPacket;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;

@Sharable
public final class NettyMapleServerHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyMapleServerHandler.class);
    private static final Logger CLIENT_PACKET_LOGGER = LoggerFactory.getLogger("CLIENT_PACKET");

    private static final Map<Integer, RecvPacketOpcode> OPCODE_MAP = new HashMap<>();

    static {
        for (RecvPacketOpcode opcode : RecvPacketOpcode.values()) {
            OPCODE_MAP.put(opcode.getValue(), opcode);
        }
    }

    private final ServerProperties properties;
    public final CharLoginHandler handler;
    private final List<String> blockedIPs = new CopyOnWriteArrayList<>();
    private final Map<String, Pair<Long, Byte>> tracker = new ConcurrentHashMap<>();

    @Inject
    public NettyMapleServerHandler(ServerProperties properties, CharLoginHandler handler) {
        this.properties = properties;
        this.handler = handler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LOGGER.info("[ACTIVE] channelActive fired, remote={}", ctx.channel().remoteAddress());
        InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
        String address = remote.getAddress().getHostAddress();
        int port = ((InetSocketAddress) ctx.channel().localAddress()).getPort();

        int channel = ChannelServer.getChannelByPort(port);
        boolean cs = CashShopServer.getCsByPort(port);

        ctx.channel().attr(ChannelAttributes.CHANNEL).set(channel);
        ctx.channel().attr(ChannelAttributes.CS).set(cs);

        if (blockedIPs.contains(address)) {
            ctx.close();
            return;
        }
        Pair<Long, Byte> track = tracker.get(address);
        byte count;
        if (track == null) {
            count = 1;
        } else {
            count = track.right;
            long difference = System.currentTimeMillis() - track.left;
            if (difference < 2000) {
                count++;
            } else if (difference > 20000) {
                count = 1;
            }
            if (count >= 10) {
                LOGGER.warn("自动断开连接A2");
                blockedIPs.add(address);
                tracker.remove(address);
                ctx.close();
                return;
            }
        }
        tracker.put(address, new Pair<>(System.currentTimeMillis(), count));

        if (channel > -1) {
            if (ChannelServer.getInstance(channel) != null && ChannelServer.getInstance(channel).isShutdown()) {
                LOGGER.warn("自动断开连接B");
                ctx.close();
                return;
            }
            if (!LoginServer.containsIPAuth(address)) {
                ctx.close();
                return;
            }
        } else if (cs) {
            if (CashShopServer.isShutdown()) {
                LOGGER.warn("自动断开连接D");
                ctx.close();
                return;
            }
        } else if (handler.loginServer.isShutdown()) {
            LOGGER.warn("自动断开连接E");
            ctx.close();
            return;
        }
        LoginServer.removeIPAuth(address);

        byte[] serverRecv = new byte[]{70, 114, 122, (byte) Randomizer.nextInt(255)};
        byte[] serverSend = new byte[]{82, 48, 120, (byte) Randomizer.nextInt(255)};
        byte[] ivRecv = ServerConstants.Use_Fixed_IV ? new byte[]{9, 0, 0x5, 0x5F} : serverRecv;
        byte[] ivSend = ServerConstants.Use_Fixed_IV ? new byte[]{1, 0x5F, 4, 0x3F} : serverSend;

        NettySession session = new NettySession(ctx.channel());
        MapleClient client = new MapleClient(
                new MapleAESOFB(ivSend, (short) (0xFFFF - ServerConstants.MAPLE_VERSION)),
                new MapleAESOFB(ivRecv, ServerConstants.MAPLE_VERSION),
                session, properties);

        client.setChannel(channel);

        // Write hello as raw ByteBuf to bypass encoder — must never be encrypted,
        // because Netty's async write may process the encoder AFTER CLIENT_KEY is set.
        byte[] helloBytes = LoginPacket.getHello(ServerConstants.MAPLE_VERSION,
                ServerConstants.Use_Fixed_IV ? serverSend : ivSend,
                ServerConstants.Use_Fixed_IV ? serverRecv : ivRecv).getBytes();
        ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(helloBytes));

        ctx.channel().attr(MaplePacketDecoderNetty.CLIENT_KEY).set(client);
        session.setAttribute(MapleClient.CLIENT_KEY, client);

        // Diagnostic heartbeat — logs every 10s on the event loop.
        // If this stops, the event loop is blocked/deadlocked.
        ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
            LOGGER.info("[HEARTBEAT] eventLoop alive, channel={}, address={}", channel, address);
        }, 10, 10, java.util.concurrent.TimeUnit.SECONDS);

        StringBuilder sb = new StringBuilder();
        if (channel > -1) {
            sb.append("[频道服务器] 频道 ").append(channel).append(" : ");
        } else if (cs) {
            sb.append("[商城服务器]");
        } else {
            sb.append("[登录服务器]");
        }
        sb.append("Channel opened ").append(address).append(" cs:").append(cs).append(" channel:").append(channel);
        CLIENT_PACKET_LOGGER.debug(sb.toString());

        if (channel > -1) {
            LOGGER.info("logged into channel {}", channel);
        } else if (cs) {
            LOGGER.info("logged into cash shop server");
        } else {
            LOGGER.info("logged into login server");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        MapleClient client = ctx.channel().attr(MaplePacketDecoderNetty.CLIENT_KEY).get();

        if (client != null) {
            try {
                int channel = ctx.channel().attr(ChannelAttributes.CHANNEL).get();
                boolean cs = ctx.channel().attr(ChannelAttributes.CS).get();

                StringBuilder sb = new StringBuilder();
                if (channel > -1) {
                    sb.append("[频道服务器] 频道 ").append(channel).append(" : ");
                } else if (cs) {
                    sb.append("[商城服务器]");
                } else {
                    sb.append("[登录服务器]");
                }
                LOGGER.warn("{} Channel closed - address={} cs={} channel={} player={}",
                        sb.toString(), client.getSession().getRemoteAddress(), cs, channel,
                        client.getPlayer() != null ? client.getPlayer().getName() : "null");
                CLIENT_PACKET_LOGGER.info("{} Channel closed {} cs {} channel {}",
                        sb.toString(), client.getSession().getRemoteAddress(), cs, channel);
                client.disconnect(true, cs);
            } finally {
                ctx.channel().attr(MaplePacketDecoderNetty.CLIENT_KEY).set(null);
            }
        } else {
            LOGGER.warn("Channel closed with no client - address={}", ctx.channel().remoteAddress());
        }
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            byte[] message = (byte[]) msg;
            SeekableLittleEndianAccessor slea = new GenericSeekableLittleEndianAccessor(new ByteArrayByteStream(message));
            if (slea.available() < 2) {
                return;
            }
            int header_num = slea.readShort() & 0xFFFF;
            RecvPacketOpcode recv = OPCODE_MAP.get(header_num);
            if (recv != null) {
                if (ServerConstants.properties.isDebug()) {
                    LOGGER.debug("Received data 已處理 :" + recv + "\n"
                            + tools.HexTool.toString(message) + "\n"
                            + tools.HexTool.toStringFromAscii(message));
                }
                MapleClient client = ctx.channel().attr(MaplePacketDecoderNetty.CLIENT_KEY).get();
                if (!client.isReceiving()) {
                    return;
                }
                if (recv.checkState()) {
                    if (!client.isLoggedIn()
                            && client.getLoginState() != LoginState.SERVER_TRANSITION
                            && client.getLoginState() != LoginState.CASH_SHOP_TRANSITION
                            && client.getLoginState() != LoginState.CHANGE_CHANNEL) {
                        LOGGER.error("消息接受提前返回{},header{}", client.getLoginState(), recv);
                        return;
                    }
                }
                boolean cs = ctx.channel().attr(ChannelAttributes.CS).get();
                MapleServerHandler.handlePacket(recv, slea, client, cs, handler);

                if (recv == RecvPacketOpcode.PLAYER_LOGGEDIN) {
                    LOGGER.info(">> [AccountName: "
                            + (client.getAccountName() == null ? "null" : client.getAccountName())
                            + "] | [IGN: "
                            + (client.getPlayer() == null || client.getPlayer().getName() == null
                            ? "null"
                            : client.getPlayer().getName())
                            + "] | [Time: " + FileoutputUtil.CurrentReadable_Time() + "]");
                }
                LOGGER.info("[" + recv + "]" + slea.toString(true));
                return;
            }
            if (ServerConstants.properties.isDebug()) {
                String sb = "Received data 未處理 : "
                        + tools.HexTool.toString(message) + "\n"
                        + tools.HexTool.toStringFromAscii(message);
                LOGGER.debug(sb);
            }
        } catch (RejectedExecutionException ree) {
            LOGGER.warn("[CHANNEL_READ] RejectedExecutionException - actor likely shutdown. address={}",
                    ctx.channel().remoteAddress(), ree);
        } catch (Exception e) {
            FileoutputUtil.outputFileError(FileoutputUtil.PacketEx_Log, e);
            LOGGER.error("处理消息时出错", e);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE || e.state() == IdleState.WRITER_IDLE) {
                MapleClient client = ctx.channel().attr(MaplePacketDecoderNetty.CLIENT_KEY).get();
                if (client != null && client.getPlayer() != null) {
                    LOGGER.info("[IDLE] 发送Ping, 地址={}, player={}",
                            ctx.channel().remoteAddress(), client.getPlayer().getName());
                    client.sendPing();
                } else {
                    LOGGER.info("[IDLE] 关闭空闲连接, 地址={}, client={}",
                            ctx.channel().remoteAddress(), client);
                    ctx.close();
                    return;
                }
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof handling.MaplePacket) {
            Runnable r = ((handling.MaplePacket) msg).getOnSend();
            if (r != null) {
                promise.addListener(future -> r.run());
            }
        }
        ctx.write(msg, promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        MapleClient client = ctx.channel().attr(MaplePacketDecoderNetty.CLIENT_KEY).get();
        String playerInfo = client != null && client.getPlayer() != null
                ? client.getPlayer().getName() : "null";
        if (cause instanceof java.net.SocketException) {
            LOGGER.warn("客户端连接重置, address={}, player={}", ctx.channel().remoteAddress(), playerInfo, cause);
        } else {
            LOGGER.error("连接出现异常, address={}, player={}", ctx.channel().remoteAddress(), playerInfo, cause);
        }
        ctx.close();
    }

    /**
     * Channel attribute keys for storing per-connection state.
     */
    public static final class ChannelAttributes {
        public static final AttributeKey<Integer> CHANNEL = AttributeKey.valueOf("CHANNEL");
        public static final AttributeKey<Boolean> CS = AttributeKey.valueOf("CS");
    }
}
