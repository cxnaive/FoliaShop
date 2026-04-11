package dev.user.shop.globalshop;

import dev.user.shop.util.ItemDataUtil;
import org.bukkit.inventory.ItemStack;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 全球商店上架数据模型
 */
public class GlobalShopListing {

    private final long id;
    private final String sellerUuid;
    private final String sellerName;
    private final byte[] itemData;
    private final String itemKey;
    private final String itemDisplayName;
    private final int amount;
    private final double price;
    private final String status;
    private final long createdAt;
    private final long expireAt;

    public GlobalShopListing(long id, String sellerUuid, String sellerName, byte[] itemData,
                             String itemKey, String itemDisplayName, int amount, double price,
                             String status, long createdAt, long expireAt) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemData = itemData;
        this.itemKey = itemKey;
        this.itemDisplayName = itemDisplayName;
        this.amount = amount;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
    }

    public static GlobalShopListing fromResultSet(ResultSet rs) throws SQLException {
        return new GlobalShopListing(
                rs.getLong("id"),
                rs.getString("seller_uuid"),
                rs.getString("seller_name"),
                rs.getBytes("item_data"),
                rs.getString("item_key"),
                rs.getString("item_display_name"),
                rs.getInt("amount"),
                rs.getDouble("price"),
                rs.getString("status"),
                rs.getLong("created_at"),
                rs.getLong("expire_at")
        );
    }

    public ItemStack deserializeItem() {
        return ItemDataUtil.deserializeItem(itemData);
    }

    public long getId() { return id; }
    public String getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public byte[] getItemData() { return itemData; }
    public String getItemKey() { return itemKey; }
    public String getItemDisplayName() { return itemDisplayName; }
    public int getAmount() { return amount; }
    public double getPrice() { return price; }
    public String getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public long getExpireAt() { return expireAt; }

    /**
     * 获取剩余展示时间（毫秒），负数表示已过期
     */
    public long getRemainingTimeMs() {
        return expireAt - System.currentTimeMillis();
    }
}
