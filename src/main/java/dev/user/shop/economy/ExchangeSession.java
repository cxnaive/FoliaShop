package dev.user.shop.economy;

import java.util.UUID;

/**
 * 点券兑换会话
 * 玩家点击兑换按钮后创建，等待聊天输入兑换金额
 */
public class ExchangeSession {

    private final UUID playerUuid;
    private final long createTime;

    public ExchangeSession(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.createTime = System.currentTimeMillis();
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public long getCreateTime() { return createTime; }
}
