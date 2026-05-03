package tools;

import tools.data.input.ByteArrayByteStream;
import tools.data.input.GenericLittleEndianAccessor;
import tools.data.input.LittleEndianAccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 可读性封包指令日志 —— 将十六进制封包翻译为可读的操作说明。
 * <p>
 * 输出到独立日志文件 (server_instruction.log / client_packet_instruction.log)，
 * 不影响原有的十六进制封包日志。
 */
public final class PacketInstructionLogger {

    // ====================== Loggers ======================
    private static final org.slf4j.Logger SEND_LOG =
            org.slf4j.LoggerFactory.getLogger("SERVER_INSTRUCTION");
    private static final org.slf4j.Logger RECV_LOG =
            org.slf4j.LoggerFactory.getLogger("CLIENT_INSTRUCTION");

    // ====================== 数据类型 ======================
    enum T { BYTE, SHORT, INT, LONG, STR, BOOL, POS, IP }

    static final class F {
        final String name; final T type;
        F(String n, T t) { name = n; type = t; }
        static F f(String n, T t) { return new F(n, t); }
    }

    // ====================== 结构注册表 ======================
    private static final Map<String, F[]> sendDefs = new HashMap<>();
    private static final Map<String, F[]> recvDefs = new HashMap<>();
    private static final Set<String> skipSend = new HashSet<>();
    private static final Set<String> skipRecv = new HashSet<>();

    static {
        registerAll();
    }

    private static void registerAll() {
        // ──────────── 服务端 → 客户端 (Send) ────────────
        regS("LOGIN_STATUS",  "登录状态",
                F.f("状态码", T.INT), F.f("保留", T.SHORT));

        regS("SERVER_IP", "服务器IP",
                F.f("保留", T.SHORT), F.f("IP地址", T.IP), F.f("端口", T.SHORT), F.f("客户端ID", T.INT));

        regS("CHATTEXT", "聊天消息",
                F.f("说话角色ID", T.INT), F.f("GM说话", T.BOOL), F.f("消息内容", T.STR), F.f("显示方式", T.BYTE));

        regS("SERVERMESSAGE", "服务端消息",
                F.f("消息类型", T.BYTE),  // 后跟变长内容, 通用解码会尽力读取
                F.f("消息内容", T.STR));

        regS("WARP_TO_MAP", "切换地图",
                F.f("频道", T.INT), F.f("角色数量", T.BYTE), F.f("传送门数量", T.BYTE),
                F.f("保留", T.SHORT), F.f("地图ID", T.INT), F.f("出生点", T.BYTE), F.f("HP", T.SHORT));

        regS("SPAWN_NPC", "生成NPC",
                F.f("对象ID", T.INT), F.f("NPC模板ID", T.INT), F.f("X坐标", T.SHORT), F.f("Y坐标", T.SHORT),
                F.f("朝向", T.BYTE), F.f("FH", T.SHORT), F.f("RX0", T.SHORT), F.f("RX1", T.SHORT),
                F.f("显示", T.BOOL));

        regS("SPAWN_NPC_REQUEST_CONTROLLER", "NPC控制器",
                F.f("未知", T.BYTE), F.f("对象ID", T.INT), F.f("NPC模板ID", T.INT),
                F.f("X坐标", T.SHORT), F.f("Y坐标", T.SHORT), F.f("朝向", T.BYTE),
                F.f("FH", T.SHORT), F.f("RX0", T.SHORT), F.f("RX1", T.SHORT), F.f("小地图显示", T.BOOL));

        regS("REMOVE_NPC", "移除NPC",
                F.f("对象ID", T.INT));

        regS("DROP_ITEM_FROM_MAPOBJECT", "掉落物品",
                F.f("模式", T.BYTE), F.f("对象ID", T.INT), F.f("是否金币", T.BOOL),
                F.f("物品ID", T.INT), F.f("归属角色ID", T.INT), F.f("掉落类型", T.BYTE));

        regS("SHOW_ITEM_GAIN_INCHAT", "物品获得",
                F.f("模式", T.BYTE), F.f("物品ID", T.INT), F.f("数量", T.INT));

        regS("SHOW_STATUS_INFO", "状态信息",
                F.f("类型", T.BYTE), F.f("白色", T.BOOL), F.f("数值", T.INT));

        regS("DAMAGE_PLAYER", "伤害玩家",
                F.f("角色ID", T.INT), F.f("技能号", T.BYTE), F.f("伤害值", T.INT), F.f("怪物ID", T.INT), F.f("方向", T.BYTE));

        regS("MOVE_PLAYER", "玩家移动",
                F.f("角色ID", T.INT), F.f("保留", T.INT));

        regS("UPDATE_STATS", "更新属性",
                F.f("物品触发", T.BOOL), F.f("更新掩码", T.INT));

        regS("ENABLE_ACTIONS", "恢复操作");

        regS("SPAWN_PLAYER", "生成玩家",
                F.f("角色ID", T.INT), F.f("等级", T.BYTE), F.f("角色名", T.STR));

        regS("REMOVE_PLAYER", "移除玩家",
                F.f("角色ID", T.INT));

        regS("SPAWN_MONSTER", "生成怪物",
                F.f("对象ID", T.INT), F.f("控制器", T.BYTE), F.f("怪物模板ID", T.INT),
                F.f("状态", T.INT), F.f("X坐标", T.SHORT), F.f("Y坐标", T.SHORT), F.f("朝向", T.BYTE),
                F.f("FH", T.SHORT), F.f("RX0", T.SHORT), F.f("RX1", T.SHORT), F.f("显示", T.BOOL));

        regS("SPAWN_MONSTER_CONTROL", "怪物控制",
                F.f("对象ID", T.INT), F.f("控制器", T.BYTE), F.f("怪物模板ID", T.INT),
                F.f("状态", T.BYTE), F.f("X坐标", T.SHORT), F.f("Y坐标", T.SHORT), F.f("朝向", T.BYTE),
                F.f("FH", T.SHORT), F.f("RX0", T.SHORT), F.f("RX1", T.SHORT), F.f("显示", T.BOOL));

        regS("KILL_MONSTER", "击杀怪物",
                F.f("对象ID", T.INT), F.f("动画", T.BYTE));

        regS("SHOW_MONSTER_HP", "怪物血量",
                F.f("对象ID", T.INT), F.f("血量百分比", T.BYTE));

        regS("NPC_TALK", "NPC对话",
                F.f("模式", T.BYTE), F.f("NPC模板ID", T.INT), F.f("消息类型", T.BYTE), F.f("对话类型", T.BYTE), F.f("对话内容", T.STR));

        regS("ENABLE_REPORT", "允许举报");

        regS("SHOW_FOREIGN_EFFECT", "外部特效",
                F.f("角色ID", T.INT), F.f("特效ID", T.BYTE));

        regS("MOVE_MONSTER_RESPONSE", "怪物移动响应",
                F.f("对象ID", T.INT), F.f("Action", T.SHORT), F.f("技能ID", T.INT), F.f("技能等级", T.BYTE), F.f("剩余移动点数", T.BYTE));

        regS("MODIFY_INVENTORY_ITEM", "背包变更",
                F.f("模式", T.BYTE), F.f("背包类型", T.BYTE), F.f("格子", T.BYTE));

        regS("SHOW_SCROLL_EFFECT", "卷轴特效", F.f("角色ID", T.INT), F.f("成功", T.BOOL), F.f("毁坏", T.BOOL));

        regS("SHOW_QUEST_COMPLETION", "任务完成", F.f("任务ID", T.SHORT));

        regS("USE_SKILL_BOOK", "技能书",
                F.f("角色ID", T.INT), F.f("技能ID", T.INT), F.f("新最大等级", T.BYTE), F.f("成功", T.BOOL));

        regS("FISHING_BOARD_UPDATE", "钓鱼更新", F.f("模式", T.BYTE));

        regS("SHOW_POTENTIAL_EFFECT", "潜能效果", F.f("角色ID", T.INT), F.f("成功", T.BOOL), F.f("道具ID", T.INT));

        regS("SHOW_MAGNETIC_EFFECT", "磁铁效果", F.f("角色ID", T.INT), F.f("成功", T.BOOL), F.f("道具ID", T.INT));

        regS("SHOW_CHAOS_EFFECT", "混沌效果", F.f("角色ID", T.INT), F.f("成功", T.BOOL), F.f("道具ID", T.INT));

        regS("UPDATE_BUDDY_CHANNEL", "好友频道", F.f("角色ID", T.INT), F.f("频道", T.BYTE));

        regS("BLOCKED_MAP", "被屏蔽地图");

        // ──────────── 跳过的高频包 ────────────
        // 双方都有的高频包
        skipAll("MOVE_LIFE");
        skipAll("MOVE_MONSTER");
        skipAll("NPC_ACTION");
        skipAll("HEAL_OVER_TIME");
        skipAll("AUTO_AGGRO");
        // 仅服务端
        skipS("PING");
        skipS("MOVE_MONSTER_RESPONSE");
        // 仅客户端
        skipR("PONG");
        skipR("MOVE_ANDROID");
        skipR("MOVE_SUMMON");
        skipR("BUTTON_PRESSED");
        skipR("STRANGE_DATA");
        skipR("ERROR_LOG");

        // ──────────── 客户端 → 服务端 (Recv) ────────────
        regR("LOGIN_PASSWORD", "登录密码",
                F.f("账号", T.STR), F.f("密码", T.STR));

        regR("PLAYER_LOGGEDIN", "角色登录",
                F.f("角色ID", T.INT));

        regR("CHATTEXT", "玩家聊天",
                F.f("Tick", T.INT), F.f("消息内容", T.STR), F.f("方式", T.BYTE));

        regR("MOVE_PLAYER", "玩家移动",
                F.f("Tick", T.INT), F.f("角色ID", T.INT), F.f("移动点数", T.BYTE));

        regR("NPC_TALK", "与NPC对话",
                F.f("Tick", T.INT), F.f("NPC对象ID", T.INT));

        regR("NPC_TALK_MORE", "NPC对话继续",
                F.f("最后消息", T.BYTE), F.f("动作", T.BYTE), F.f("选择", T.BYTE));

        regR("CHANGE_MAP", "切换地图",
                F.f("Tick", T.INT), F.f("地图ID", T.INT), F.f("起始位置", T.BYTE));

        regR("CHANGE_MAP_SPECIAL", "特殊切换地图",
                F.f("Tick", T.INT), F.f("起始位置", T.BYTE));

        regR("TAKE_DAMAGE", "受到伤害",
                F.f("Tick", T.INT), F.f("怪物ID", T.INT), F.f("姿态", T.BYTE), F.f("命中次数", T.BYTE));

        regR("USE_ITEM", "使用物品",
                F.f("Tick", T.INT), F.f("背包类型", T.BYTE), F.f("格子", T.BYTE), F.f("物品ID", T.SHORT), F.f("对象ID", T.INT));

        regR("DISTRIBUTE_AP", "分配属性点",
                F.f("Tick", T.INT), F.f("数量", T.INT));

        regR("DISTRIBUTE_SP", "分配技能点",
                F.f("Tick", T.INT), F.f("技能ID", T.INT));

        regR("MOVE_ITEM", "移动物品",
                F.f("Tick", T.INT), F.f("源背包", T.BYTE), F.f("源格子", T.BYTE), F.f("目标背包", T.BYTE), F.f("目标格子", T.BYTE), F.f("数量", T.SHORT));

        regR("DROP_ITEM", "丢弃物品",
                F.f("Tick", T.INT), F.f("背包类型", T.BYTE), F.f("格子", T.BYTE), F.f("数量", T.SHORT));

        regR("USE_CASH_ITEM", "使用现金物品",
                F.f("Tick", T.INT), F.f("物品ID", T.SHORT), F.f("背包类型", T.BYTE), F.f("格子", T.BYTE));

        regR("ATTACK", "普通攻击",
                F.f("Tick", T.INT), F.f("攻击次数", T.BYTE), F.f("怪物数量", T.BYTE));

        regR("MAGIC_ATTACK", "魔法攻击",
                F.f("Tick", T.INT), F.f("技能ID", T.INT), F.f("技能等级", T.BYTE));

        regR("SPECIAL_MOVE", "特殊移动",
                F.f("Tick", T.INT), F.f("技能ID", T.INT), F.f("技能等级", T.BYTE));

        regR("FACIAL_EXPRESSION", "表情",
                F.f("Tick", T.INT), F.f("表情ID", T.INT));

        regR("MOVE_PET", "宠物移动",
                F.f("Tick", T.INT), F.f("宠物ID", T.INT));

        regR("SUMMON_ATTACK", "召唤兽攻击",
                F.f("Tick", T.INT), F.f("召唤兽ID", T.INT));

        regR("PARTY_OPERATION", "组队操作",
                F.f("Tick", T.INT), F.f("操作", T.BYTE));

        regR("FRIEND_OPERATION", "好友操作",
                F.f("Tick", T.INT), F.f("操作", T.BYTE));

        regR("MESSENGER", "聊天系统",
                F.f("Tick", T.INT), F.f("模式", T.BYTE));

        regR("ENTER_CS", "进入商城",
                F.f("Tick", T.INT));

        regR("ENTER_MTS", "进入拍卖",
                F.f("Tick", T.INT));

        regR("CHANGE_KEYMAP", "修改按键",
                F.f("Tick", T.INT), F.f("变更数量", T.INT));

        regR("USE_SPECIAL_ITEM", "使用特殊物品",
                F.f("Tick", T.INT), F.f("物品ID", T.SHORT), F.f("背包类型", T.BYTE), F.f("格子", T.BYTE));

        regR("PET_CHAT", "宠物对话",
                F.f("Tick", T.INT), F.f("宠物ID", T.INT), F.f("方式", T.BYTE), F.f("对话内容", T.STR));

        regR("GAME_POLL", "问卷",
                F.f("Tick", T.INT), F.f("选择", T.BYTE));

        regR("PLAYER_UPDATE", "玩家更新",
                F.f("Tick", T.INT));

        regR("USE_SKILL", "使用技能",
                F.f("Tick", T.INT), F.f("技能ID", T.INT), F.f("技能等级", T.BYTE));

        regR("USE_SKILL_BOOK", "使用技能书",
                F.f("Tick", T.INT), F.f("技能ID", T.INT), F.f("技能书ID", T.INT));

    }

    // ──── 注册辅助方法 ────
    private static void regS(String op, String desc, F... fields) { sendDefs.put(op, fields); }
    private static void regR(String op, String desc, F... fields) { recvDefs.put(op, fields); }
    private static void skipAll(String op) { skipSend.add(op); skipRecv.add(op); }
    private static void skipS(String op) { skipSend.add(op); }
    private static void skipR(String op) { skipRecv.add(op); }

    // ====================== 公开入口 ======================
    public static void logSend(byte[] data) {
        if (data == null || data.length < 2) return;
        logPacket(SEND_LOG, "[发送]", data, sendDefs, skipSend);
    }

    public static void logRecv(byte[] data) {
        if (data == null || data.length < 2) return;
        logPacket(RECV_LOG, "[接收]", data, recvDefs, skipRecv);
    }

    // ====================== 核心解码逻辑 ======================
    private static void logPacket(org.slf4j.Logger logger, String prefix,
                                  byte[] data, Map<String, F[]> defs, Set<String> skipSet) {
        LittleEndianAccessor lea = new GenericLittleEndianAccessor(new ByteArrayByteStream(data));
        int opcode = lea.readShort();
        String name = lookupOpcodeFromDefs(opcode, defs);

        if (skipSet.contains(name)) return;

        F[] fields = defs.get(name);
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(' ').append(name).append("(0x").append(Integer.toHexString(opcode).toUpperCase()).append(')');

        if (fields == null || fields.length == 0) {
            // 无结构定义, 仅显示剩余字节数
            int remaining = data.length - 2;
            sb.append(" | 数据=").append(remaining).append("字节");
            if (remaining > 0 && remaining <= 128) {
                sb.append(" | Hex=").append(HexTool.toString(data));
            }
        } else {
            try {
                decodeFields(lea, fields, sb);
            } catch (Exception e) {
                sb.append(" | [解码错误: ").append(e.getMessage()).append(']');
            }
        }

        logger.info(sb.toString());
    }

    private static void decodeFields(LittleEndianAccessor lea, F[] fields, StringBuilder sb) {
        for (F f : fields) {
            sb.append(" | ").append(f.name).append('(');
            switch (f.type) {
                case BYTE: {
                    sb.append("1B)=").append(lea.readByte());
                    break;
                }
                case SHORT: {
                    sb.append("2B)=").append(lea.readShort());
                    break;
                }
                case INT: {
                    sb.append("4B)=").append(lea.readInt());
                    break;
                }
                case LONG: {
                    sb.append("8B)=").append(lea.readLong());
                    break;
                }
                case STR: {
                    if (lea.available() < 2) { sb.append("?)=<不足>"); break; }
                    String str = lea.readMapleAsciiString();
                    if (str.length() > 200) str = str.substring(0, 197) + "...";
                    sb.append("Str)=").append(escape(str));
                    break;
                }
                case BOOL: {
                    sb.append("1B)=").append(lea.readByte() != 0 ? "是" : "否");
                    break;
                }
                case POS: {
                    if (lea.available() < 4) { sb.append("?)=<不足>"); break; }
                    short x = lea.readShort();
                    short y = lea.readShort();
                    sb.append("4B)=(").append(x).append(',').append(y).append(')');
                    break;
                }
                case IP: {
                    if (lea.available() < 4) { sb.append("?)=<不足>"); break; }
                    byte[] ip = new byte[4];
                    for (int i = 0; i < 4; i++) ip[i] = lea.readByte();
                    try {
                        sb.append("4B)=").append(InetAddress.getByAddress(ip).getHostAddress());
                    } catch (UnknownHostException e) {
                        sb.append("4B)=<无效IP>");
                    }
                    break;
                }
            }
        }
        if (lea.available() > 0) {
            sb.append(" | [剩余").append(lea.available()).append("字节]");
        }
    }

    private static String escape(String s) {
        if (s.isEmpty()) return "\"\"";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    /** 根据 opcode 数值查找名称（仅在已注册的 defs 中查找，以区分 Send/Recv） */
    private static String lookupOpcodeFromDefs(int opcode, Map<String, F[]> defs) {
        // 先尝试按 Send/Recv 枚举查找
        for (handling.SendPacketOpcode op : handling.SendPacketOpcode.values()) {
            if (op.getValue() == opcode) return op.name();
        }
        for (handling.RecvPacketOpcode op : handling.RecvPacketOpcode.values()) {
            if (op.getValue() == opcode) return op.name();
        }
        return "UNKNOWN";
    }
}
