package handling.netty;

import client.MapleClient;
import com.github.mrzhqiang.maplestory.config.ServerProperties;
import handling.RecvPacketOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.FileoutputUtil;
import tools.HexTool;
import tools.MapleAESOFB;
import tools.MapleCustomEncryption;
import tools.PacketInstructionLogger;
import tools.data.input.ByteArrayByteStream;
import tools.data.input.GenericLittleEndianAccessor;

import javax.inject.Inject;
import java.nio.ByteOrder;
import java.util.List;

public final class MaplePacketDecoderNetty extends ByteToMessageDecoder {

    public static final AttributeKey<MapleClient> CLIENT_KEY = AttributeKey.valueOf("CLIENT");
    private static final AttributeKey<DecoderState> DECODER_STATE_KEY = AttributeKey.valueOf("DECODER_STATE");

    public static class DecoderState {
        public int packetlength = -1;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MaplePacketDecoderNetty.class);
    private static final Logger CLIENT_PACKET_LOGGER = LoggerFactory.getLogger("CLIENT_PACKET");

    private final ServerProperties properties;

    @Inject
    public MaplePacketDecoderNetty(ServerProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        MapleClient client = ctx.channel().attr(CLIENT_KEY).get();
        
        // ✅ 修复：从 Channel 属性中获取 DecoderState，每个连接独立
        DecoderState state = ctx.channel().attr(DECODER_STATE_KEY).get();
        if (state == null) {
            state = new DecoderState();
            ctx.channel().attr(DECODER_STATE_KEY).set(state);
        }

        if (state.packetlength == -1) {
            if (in.readableBytes() >= 4) {
                // ✅ 诊断日志：原始数据包内容
                byte[] rawHeader = new byte[4];
                in.getBytes(in.readerIndex(), rawHeader);
                LOGGER.info("[DECODE] 接收到数据包头 - raw={}, readableBytes={}", 
                        tools.HexTool.toString(rawHeader),
                        in.readableBytes());
                
                // 使用 readInt() 读取大端序整数，与 MINA IoBuffer.getInt() 行为一致
                int packetHeader = in.readInt();
                
                // ✅ 诊断日志：解析后的 header 值
                LOGGER.info("[DECODE] 解析包头 - packetHeader=0x{}, checkPacket结果={}", 
                        Integer.toHexString(packetHeader).toUpperCase(),
                        client.getReceiveCrypto().checkPacket(packetHeader));
                
                if (!client.getReceiveCrypto().checkPacket(packetHeader)) {
                    LOGGER.warn("[DECODE] checkPacket failed, closing channel. address={}, player={}",
                            ctx.channel().remoteAddress(),
                            client.getPlayer() != null ? client.getPlayer().getName() : "null");
                    ctx.close();
                    return;
                }
                state.packetlength = MapleAESOFB.getPacketLength(packetHeader);
                LOGGER.info("[DECODE] 包长度计算完成 - packetlength={}", state.packetlength);
            } else {
                return;
            }
        }
        if (in.readableBytes() >= state.packetlength) {
            LOGGER.info("[DECODE] 开始读取包体 - packetlength={}, readableBytes={}", 
                    state.packetlength, in.readableBytes());
            
            byte[] decryptedPacket = new byte[state.packetlength];
            in.readBytes(decryptedPacket, 0, state.packetlength);
            state.packetlength = -1;

            // ✅ 诊断日志：解密前的数据
            LOGGER.debug("[DECODE] 解密前数据 - length={}, data={}", 
                    decryptedPacket.length,
                    decryptedPacket.length <= 32 ? tools.HexTool.toString(decryptedPacket) : tools.HexTool.toString(decryptedPacket));
            
            client.getReceiveCrypto().crypt(decryptedPacket);
            MapleCustomEncryption.decryptData(decryptedPacket);
            
            // ✅ 诊断日志：解密后的数据
            int opcode = readFirstShort(decryptedPacket);
            String opcodeStr = Integer.toHexString(opcode).toUpperCase();
            LOGGER.info("[DECODE] 解密后数据 - opcode=0x{}, length={}", opcodeStr, decryptedPacket.length);
            
            out.add(decryptedPacket);

            if (properties.isPacketLogger()) {
                int packetLen = decryptedPacket.length;
                int pHeader = readFirstShort(decryptedPacket);
                String pHeaderStr = Integer.toHexString(pHeader).toUpperCase();
                String op = lookupSend(pHeader);
                boolean show = true;
                switch (op) {
                    case "PONG":
                    case "NPC_ACTION":
                    case "MOVE_LIFE":
                    case "MOVE_PLAYER":
                    case "MOVE_ANDROID":
                    case "MOVE_SUMMON":
                    case "AUTO_AGGRO":
                    case "HEAL_OVER_TIME":
                    case "BUTTON_PRESSED":
                    case "STRANGE_DATA":
                    case "ERROR_LOG":
                        show = false;
                }
                String Send = "客户端发送 " + op + " [" + pHeaderStr + "] (" + packetLen + ")\r\n";
                if (packetLen <= 3000) {
                    String SendTo = Send + HexTool.toString(decryptedPacket) + "\r\n" + HexTool.toStringFromAscii(decryptedPacket);
                    if (show) {
                        CLIENT_PACKET_LOGGER.info(SendTo);
                        LOGGER.debug(SendTo);
                    }
                    String SendTos = "\r\n时间：" + FileoutputUtil.CurrentReadable_Time() + "  ";
                    if (op.equals("UNKNOWN")) {
                        CLIENT_PACKET_LOGGER.info(SendTos + SendTo);
                    }
                } else {
                    LOGGER.info(HexTool.toString(new byte[]{decryptedPacket[0], decryptedPacket[1]}) + "...");
                }
            }
            if (properties.isPacketInstructionLogger()) {
                PacketInstructionLogger.logRecv(decryptedPacket);
            }
        }
    }

    private String lookupSend(int val) {
        for (RecvPacketOpcode op : RecvPacketOpcode.values()) {
            if (op.getValue() == val) {
                return op.name();
            }
        }
        return "UNKNOWN";
    }

    private int readFirstShort(byte[] arr) {
        return new GenericLittleEndianAccessor(new ByteArrayByteStream(arr)).readShort();
    }
}
