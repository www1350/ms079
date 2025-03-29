package com.github.mrzhqiang.maplestory.domain;

import io.ebean.annotation.DbEnumType;
import io.ebean.annotation.DbEnumValue;

/**
 * 上线：LOGIN_PASSWORD(0)->CHAR_SELECT(1)->PLAYER_LOGGEDIN(2)
 * 下线：PLAYER_LOGGEDIN(2)->sessionClosed(0)
 * 更换频道:PLAYER_LOGGEDIN(2)->CHANGE_CHANNEL(6)->PLAYER_LOGGEDIN(2)
 * 进入商城:PLAYER_LOGGEDIN(2)->CASH_SHOP_TRANSITION(4)->PLAYER_LOGGEDIN(2)
 * 离开商城:PLAYER_LOGGEDIN(2)->CASH_SHOP_TRANSITION(4)->PLAYER_LOGGEDIN(2)
 * 登录状态。
 * <p>
 * 0 -- 默认：未登录
 * <p>
 * 1 -- 服务的过渡：应该是登录之后，还没选择角色
 * <p>
 * 2 -- 已登录
 * <p>
 * 3 -- 等待
 * <p>
 * 4 -- 商城的过渡：代码中未直接使用常量，可能在其他地方直接用整数表示
 * <p>
 * 5 -- 商城已登录：代码中未直接使用常量
 * <p>
 * 6 -- 切换频道
 */
public enum LoginState {

    NOT_LOGIN("0"),
    SERVER_TRANSITION("1"),
    LOGGED_IN("2"),
    WAITING("3"),
    CASH_SHOP_TRANSITION("4"),
    CS_LOGGED_IN("5"),
    CHANGE_CHANNEL("6"),
    ;

    final String code;

    LoginState(String code) {
        this.code = code;
    }

    @DbEnumValue(storage = DbEnumType.INTEGER)
    public String getCode() {
        return code;
    }
}
