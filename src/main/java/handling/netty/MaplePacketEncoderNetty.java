package handling.netty;

import client.MapleClient;
import com.github.mrzhqiang.maplestory.config.ServerProperties;
import handling.MaplePacket;
import handling.SendPacketOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
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
import java.util.concurrent.locks.Lock;

public final class MaplePacketEncoderNetty extends MessageToByteEncoder<MaplePacket> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaplePacketEncoderNetty.class);
    private static final Logger SERVER_PACKET_LOGGER = LoggerFactory.getLogger("SERVER_PACKET");

    private final ServerProperties properties;

    @Inject
    public MaplePacketEncoderNetty(ServerProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, MaplePacket msg, ByteBuf out) {
        // ✅ 修复：设置 ByteBuf 为小端序（MapleStory 协议要求）
        out = out.order(ByteOrder.LITTLE_ENDIAN);
        
        MapleClient client = ctx.channel().attr(MaplePacketDecoderNetty.CLIENT_KEY).get();
        if (client != null) {
            MapleAESOFB sendCrypto = client.getSendCrypto();
            byte[] inputInitialPacket = msg.getBytes();

            if (properties.isPacketLogger()) {
                int packetLen = inputInitialPacket.length;
                int pHeader = readFirstShort(inputInitialPacket);
                String pHeaderStr = Integer.toHexString(pHeader).toUpperCase();
                String op = lookupRecv(pHeader);
                String Recv = "服务端发送 " + op + " [" + pHeaderStr + "] (" + packetLen + ")\r\n";
                if (packetLen <= 50000) {
                    String recvTo = Recv + HexTool.toString(inputInitialPacket) + "\r\n" + HexTool.toStringFromAscii(inputInitialPacket);
                    SERVER_PACKET_LOGGER.info(recvTo);
                    LOGGER.debug(recvTo);
                } else {
                    LOGGER.info(HexTool.toString(new byte[]{inputInitialPacket[0], inputInitialPacket[1]}) + " ...");
                }
            }
            if (properties.isPacketInstructionLogger()) {
                PacketInstructionLogger.logSend(inputInitialPacket);
            }

            byte[] unencrypted = new byte[inputInitialPacket.length];
            System.arraycopy(inputInitialPacket, 0, unencrypted, 0, inputInitialPacket.length);
            byte[] ret = new byte[unencrypted.length + 4];

            Lock mutex = client.getLock();
            mutex.lock();
            try {
                byte[] header = sendCrypto.getPacketHeader(unencrypted.length);
                MapleCustomEncryption.encryptData(unencrypted);
                sendCrypto.crypt(unencrypted);
                System.arraycopy(header, 0, ret, 0, 4);
            } finally {
                mutex.unlock();
            }
            System.arraycopy(unencrypted, 0, ret, 4, unencrypted.length);
            out.writeBytes(ret);
        } else {
            out.writeBytes(msg.getBytes());
        }
    }

    private String lookupRecv(int val) {
        for (SendPacketOpcode op : SendPacketOpcode.values()) {
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
