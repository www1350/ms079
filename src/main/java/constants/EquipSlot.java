package constants;

/**
 * 装备槽位常量 — EQUIPPED inventory 使用负数 byte 表示槽位。
 *
 * <pre>
 *   身体装备:  -99 &lt;= pos &lt; 0
 *   现金装备:  -999 &lt;= pos &lt;= -100
 *   屏蔽区间:  pos &lt;= -1000
 * </pre>
 */
public final class EquipSlot {

    private EquipSlot() {
    }

    // ======================== 身体装备 (-1 ~ -99) ========================

    /** 标记/默认值 */
    public static final byte SENTINEL = -1;

    /** 上衣 Top */
    public static final byte TOP = -5;

    /** 裤裙 Bottom */
    public static final byte BOTTOM = -6;

    /** 鞋子 Shoes */
    public static final byte SHOES = -7;

    /** 盾牌 Shield */
    public static final byte SHIELD = -10;

    /** 武器 Weapon */
    public static final byte WEAPON = -11;

    /** 骑宠 Mount */
    public static final byte MOUNT = -18;

    /** 骑宠同伴 Mount companion */
    public static final byte MOUNT_COMPANION = -19;

    /** 聊天勋章 Chat medal */
    public static final byte CHAT_MEDAL = -26;

    /** 信息勋章 Info medal */
    public static final byte INFO_MEDAL = -49;

    // ======================== 现金装备 (-100 ~ -999) ========================

    /** 现金武器 Cash weapon */
    public static final byte CASH_WEAPON = -111;

    /** 宠物装备1 Pet equip slot 1 */
    public static final byte PET_EQUIP_1 = -114;

    /** 现金骑宠 Cash mount */
    public static final byte CASH_MOUNT = -118;

    /** 现金骑宠同伴 Cash mount companion */
    public static final byte CASH_MOUNT_COMPANION = -119;

    /** 宠物装备2 Pet equip slot 2 */
    public static final byte PET_EQUIP_2 = -122;

    /** 宠物装备3 Pet equip slot 3 */
    public static final byte PET_EQUIP_3 = -124;

    // ======================== 边界常量 ========================

    /** 身体装备下界（最小负数）。pos &gt; BODY_SLOT_MIN 为身体装备 */
    public static final int BODY_SLOT_MIN = -99;

    /** 现金装备下界。pos &gt;= CASH_SLOT_MIN 为有效装备 */
    public static final int CASH_SLOT_MIN = -999;

    /** 屏蔽区间上界。pos &lt;= BLOCKED_BOUNDARY 无效 */
    public static final int BLOCKED_BOUNDARY = -1000;

    /** 不可见边界。pos &lt; NOT_VISIBLE_BOUNDARY 不显示，== -128 为戒指特殊处理 */
    public static final int NOT_VISIBLE_BOUNDARY = -128;

    // ======================== 工具方法 ========================

    /** pos 是否属于身体装备区间 (-99 &lt; pos &lt; 0) */
    public static boolean isBodySlot(int pos) {
        return pos < 0 && pos > BODY_SLOT_MIN;
    }

    /** pos 是否属于现金装备区间 (-999 &lt;= pos &lt;= -100) */
    public static boolean isCashSlot(int pos) {
        return pos <= BODY_SLOT_MIN && pos > BLOCKED_BOUNDARY;
    }

    /** pos 是否为宠物装备槽位 */
    public static boolean isPetEquipSlot(int pos) {
        return pos == PET_EQUIP_1 || pos == PET_EQUIP_2 || pos == PET_EQUIP_3;
    }
}
