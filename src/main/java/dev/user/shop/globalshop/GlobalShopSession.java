package dev.user.shop.globalshop;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全球商店上架会话
 * 玩家确认上架后创建，等待聊天输入价格
 */
public class GlobalShopSession {

    private final UUID playerUuid;
    private final byte[] itemData;
    private final String itemKey;
    private final String itemDisplayName;
    private final int amount;
    private final long createTime;
    private final AtomicBoolean consumed = new AtomicBoolean(false);

    public GlobalShopSession(UUID playerUuid, byte[] itemData, String itemKey,
                             String itemDisplayName, int amount) {
        this.playerUuid = playerUuid;
        this.itemData = itemData;
        this.itemKey = itemKey;
        this.itemDisplayName = itemDisplayName;
        this.amount = amount;
        this.createTime = System.currentTimeMillis();
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public byte[] getItemData() { return itemData; }
    public String getItemKey() { return itemKey; }
    public String getItemDisplayName() { return itemDisplayName; }
    public int getAmount() { return amount; }
    public long getCreateTime() { return createTime; }

    /**
     * 标记会话已消费（物品数据已用于创建上架或退回），防止重复使用
     * 使用 CAS 保证原子性，防止超时处理器和聊天监听器并发导致刷物
     * @return true 如果这是首次标记（即之前未消费），false 如果已经被消费过
     */
    public boolean markConsumed() {
        return consumed.compareAndSet(false, true);
    }

    public boolean isConsumed() { return consumed.get(); }
}
