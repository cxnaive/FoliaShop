package dev.user.shop.globalshop;

import dev.user.shop.util.ItemDataUtil;
import org.bukkit.inventory.ItemStack;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 全球商店待领取条目数据模型
 */
public class GlobalShopReturnEntry {

    private final long id;
    private final String ownerUuid;
    private final byte[] itemData;
    private final String itemKey;
    private final String itemDisplayName;
    private final int amount;
    private final double earnings;
    private final String reason;
    private final long createdAt;
    private final boolean claimed;

    public GlobalShopReturnEntry(long id, String ownerUuid, byte[] itemData, String itemKey,
                                  String itemDisplayName, int amount, double earnings, String reason,
                                  long createdAt, boolean claimed) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.itemData = itemData;
        this.itemKey = itemKey;
        this.itemDisplayName = itemDisplayName;
        this.amount = amount;
        this.earnings = earnings;
        this.reason = reason;
        this.createdAt = createdAt;
        this.claimed = claimed;
    }

    public static GlobalShopReturnEntry fromResultSet(ResultSet rs) throws SQLException {
        return new GlobalShopReturnEntry(
                rs.getLong("id"),
                rs.getString("owner_uuid"),
                rs.getBytes("item_data"),
                rs.getString("item_key"),
                rs.getString("item_display_name"),
                rs.getInt("amount"),
                rs.getDouble("earnings"),
                rs.getString("reason"),
                rs.getLong("created_at"),
                rs.getBoolean("claimed")
        );
    }

    public ItemStack deserializeItem() {
        return itemData != null ? ItemDataUtil.deserializeItem(itemData) : null;
    }

    public boolean hasItem() { return itemData != null; }
    public boolean hasEarnings() { return earnings > 0; }

    public long getId() { return id; }
    public String getOwnerUuid() { return ownerUuid; }
    public byte[] getItemData() { return itemData; }
    public String getItemKey() { return itemKey; }
    public String getItemDisplayName() { return itemDisplayName; }
    public int getAmount() { return amount; }
    public double getEarnings() { return earnings; }
    public String getReason() { return reason; }
    public long getCreatedAt() { return createdAt; }
    public boolean isClaimed() { return claimed; }
}
