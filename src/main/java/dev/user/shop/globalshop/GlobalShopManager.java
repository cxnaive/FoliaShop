package dev.user.shop.globalshop;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.util.ItemDataUtil;
import dev.user.shop.util.ItemUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 全球商店核心管理器
 * 协调上架创建、过期检查、浏览查询、取消等操作
 */
public class GlobalShopManager {

    private final FoliaShopPlugin plugin;
    private final GlobalShopSessionManager sessionManager;
    private final GlobalShopPurchaseManager purchaseManager;
    private final GlobalShopReturnManager returnManager;
    private ScheduledTask expiryTask;
    private ScheduledTask cleanupTask;

    public GlobalShopManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
        this.sessionManager = new GlobalShopSessionManager(plugin);
        this.purchaseManager = new GlobalShopPurchaseManager(plugin);
        this.returnManager = new GlobalShopReturnManager(plugin);
        startExpiryCheck();
        startCleanupTask();
    }

    // ==================== 上架 ====================

    /**
     * 创建上架记录（从会话中获取物品数据，聊天价格输入完成后调用）
     */
    public void createListing(Player seller, GlobalShopSession session, double price, Consumer<Boolean> callback) {
        // 消费会话，防止重复使用
        if (!session.markConsumed()) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                seller.sendMessage("§c操作已过期"));
            callback.accept(false);
            return;
        }

        double fee = plugin.getShopConfig().getGlobalShopListingFee();

        // 扣除上架费
        if (fee > 0) {
            plugin.getEconomyManager().withdrawAsync(seller, fee, success -> {
                if (!success) {
                    // 扣费失败，退回物品
                    returnItemToPlayer(seller, session.getItemData(), session.getAmount());
                    seller.sendMessage("§c上架费用不足！需要 §e" + String.format("%.2f", fee) + " " + plugin.getShopConfig().getCurrencyName());
                    callback.accept(false);
                    return;
                }
                // 扣费成功，检查上架数量限制并创建
                checkAndCreateListing(seller, session, price, callback);
            });
        } else {
            checkAndCreateListing(seller, session, price, callback);
        }
    }

    private void checkAndCreateListing(Player seller, GlobalShopSession session, double price, Consumer<Boolean> callback) {
        int maxListings = plugin.getShopConfig().getGlobalShopMaxListings();

        plugin.getDatabaseQueue().submit("检查上架数量", conn -> {
            String countSql = "SELECT COUNT(*) FROM global_shop_listings WHERE seller_uuid = ? AND status = 'ACTIVE'";
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setString(1, seller.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) >= maxListings) {
                    return -1; // 超过限制
                }
            }

            // 创建上架记录
            long now = System.currentTimeMillis();
            long expireAt = now + plugin.getShopConfig().getGlobalShopRentalPeriodMs();
            String insertSql = "INSERT INTO global_shop_listings (seller_uuid, seller_name, item_data, item_key, item_display_name, amount, price, status, created_at, expire_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, seller.getUniqueId().toString());
                ps.setString(2, seller.getName());
                ps.setBytes(3, session.getItemData());
                ps.setString(4, session.getItemKey());
                ps.setString(5, session.getItemDisplayName());
                ps.setInt(6, session.getAmount());
                ps.setDouble(7, price);
                ps.setLong(8, now);
                ps.setLong(9, expireAt);
                ps.executeUpdate();
            }
            return 1;
        }, result -> {
            if (result == null || result.equals(-1)) {
                // 超过限制，退回物品和费用
                returnItemToPlayer(seller, session.getItemData(), session.getAmount());
                double fee = plugin.getShopConfig().getGlobalShopListingFee();
                if (fee > 0) {
                    plugin.getEconomyManager().depositAsync(seller, fee, r -> {});
                }
                seller.sendMessage("§c上架数量已达上限（" + maxListings + "个）");
                callback.accept(false);
                return;
            }

            String currencyName = plugin.getShopConfig().getCurrencyName();
            double fee = plugin.getShopConfig().getGlobalShopListingFee();
            seller.sendMessage("§a§l上架成功！");
            seller.sendMessage("§7物品: §f" + session.getItemDisplayName() + " §7x" + session.getAmount());
            seller.sendMessage("§7售价: §e" + String.format("%.2f", price) + " " + currencyName);
            if (fee > 0) {
                seller.sendMessage("§7上架费: §e" + String.format("%.2f", fee) + " " + currencyName);
            }
            callback.accept(true);
        }, e -> {
            plugin.getLogger().severe("创建上架记录失败: " + e.getMessage());
            returnItemToPlayer(seller, session.getItemData(), session.getAmount());
            double fee = plugin.getShopConfig().getGlobalShopListingFee();
            if (fee > 0) {
                plugin.getEconomyManager().depositAsync(seller, fee, r -> {});
            }
            seller.sendMessage("§c上架失败，请稍后再试");
            callback.accept(false);
        });
    }

    // ==================== 浏览 ====================

    /**
     * 浏览活跃上架（分页）
     */
    public void browseListings(int page, int pageSize, Consumer<List<GlobalShopListing>> callback) {
        int offset = page * pageSize;
        plugin.getDatabaseQueue().submit("浏览全球商店", conn -> {
            List<GlobalShopListing> listings = new ArrayList<>();
            String sql = "SELECT * FROM global_shop_listings WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pageSize);
                ps.setInt(2, offset);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    listings.add(GlobalShopListing.fromResultSet(rs));
                }
            }
            return listings;
        }, callback, e -> {
            plugin.getLogger().severe("浏览全球商店失败: " + e.getMessage());
            callback.accept(new ArrayList<>());
        });
    }

    /**
     * 获取活跃上架总数（用于分页计算）
     */
    public void getActiveListingCount(Consumer<Integer> callback) {
        plugin.getDatabaseQueue().submit("统计上架数量", conn -> {
            String sql = "SELECT COUNT(*) FROM global_shop_listings WHERE status = 'ACTIVE'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1);
            }
            return 0;
        }, callback, e -> callback.accept(0));
    }

    // ==================== 管理上架 ====================

    /**
     * 获取玩家的活跃上架
     */
    public void getMyListings(UUID sellerUuid, int page, int pageSize, Consumer<List<GlobalShopListing>> callback) {
        int offset = page * pageSize;
        plugin.getDatabaseQueue().submit("查询我的上架", conn -> {
            List<GlobalShopListing> listings = new ArrayList<>();
            String sql = "SELECT * FROM global_shop_listings WHERE seller_uuid = ? AND status = 'ACTIVE' ORDER BY created_at DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sellerUuid.toString());
                ps.setInt(2, pageSize);
                ps.setInt(3, offset);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    listings.add(GlobalShopListing.fromResultSet(rs));
                }
            }
            return listings;
        }, callback, e -> {
            plugin.getLogger().severe("查询我的上架失败: " + e.getMessage());
            callback.accept(new ArrayList<>());
        });
    }

    /**
     * 取消上架（防刷物: affected rows > 0 才创建退回条目）
     */
    public void cancelListing(Player seller, long listingId, Consumer<Boolean> callback) {
        plugin.getDatabaseQueue().submit("取消上架", conn -> {
            // 先查询物品数据
            String selectSql = "SELECT item_data, item_key, item_display_name, amount FROM global_shop_listings WHERE id = ? AND seller_uuid = ?";
            byte[] itemData = null;
            String itemKey = null;
            String displayName = null;
            int amount = 0;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, listingId);
                ps.setString(2, seller.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    return false;
                }
                itemData = rs.getBytes("item_data");
                itemKey = rs.getString("item_key");
                displayName = rs.getString("item_display_name");
                amount = rs.getInt("amount");
            }

            // 条件更新（防重复取消）
            String updateSql = "UPDATE global_shop_listings SET status = 'CANCELLED' WHERE id = ? AND status = 'ACTIVE' AND seller_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setLong(1, listingId);
                ps.setString(2, seller.getUniqueId().toString());
                if (ps.executeUpdate() == 0) {
                    return false;
                }
            }

            // 创建退回条目
            String insertReturnSql = "INSERT INTO global_shop_returns (owner_uuid, listing_id, item_data, item_key, item_display_name, amount, reason, created_at, claimed) VALUES (?, ?, ?, ?, ?, ?, 'CANCELLED', ?, FALSE)";
            try (PreparedStatement ps = conn.prepareStatement(insertReturnSql)) {
                ps.setString(1, seller.getUniqueId().toString());
                ps.setLong(2, listingId);
                ps.setBytes(3, itemData);
                ps.setString(4, itemKey);
                ps.setString(5, displayName);
                ps.setInt(6, amount);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }

            return true;
        }, result -> {
            if (result != null && result) {
                seller.sendMessage("§a已取消上架，物品已进入待领取列表");
                callback.accept(true);
            } else {
                seller.sendMessage("§c取消失败，物品可能已售出或不存在");
                callback.accept(false);
            }
        }, e -> {
            plugin.getLogger().severe("取消上架失败: " + e.getMessage());
            seller.sendMessage("§c取消失败，请稍后再试");
            callback.accept(false);
        });
    }

    // ==================== 过期检查 ====================

    private void startExpiryCheck() {
        expiryTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            checkExpiredListings();
        }, 60 * 20L, 60 * 20L); // 每 60 秒检查一次
    }

    /**
     * 定时清理过期的待领取条目
     */
    private void startCleanupTask() {
        int retainDays = plugin.getShopConfig().getGlobalShopExpiredRetainDays();
        if (retainDays <= 0) return; // 0 表示不清理
        long periodTicks = 60 * 60 * 20L; // 每 60 分钟清理一次
        cleanupTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            cleanupOldReturns(retainDays);
        }, periodTicks, periodTicks);
    }

    private void cleanupOldReturns(int retainDays) {
        long cutoffTime = System.currentTimeMillis() - (retainDays * 24L * 60 * 60 * 1000);
        plugin.getDatabaseQueue().submit("清理过期待领取", conn -> {
            // 只清理已领取且超过保留期的条目
            String sql = "DELETE FROM global_shop_returns WHERE claimed = TRUE AND created_at < ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, cutoffTime);
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    plugin.getLogger().info("[全球商店] 清理了 " + deleted + " 条过期待领取记录");
                }
            }
            return null;
        }, r -> {}, e -> plugin.getLogger().severe("清理过期待领取失败: " + e.getMessage()));
    }

    private void checkExpiredListings() {
        plugin.getDatabaseQueue().submit("检查过期上架", conn -> {
            long now = System.currentTimeMillis();
            int totalProcessed = 0;
            int batchSize = 50;

            while (true) {
                // 分批加载过期上架，避免一次加载所有 BLOB 数据
                String selectSql = "SELECT id, seller_uuid, seller_name, item_data, item_key, item_display_name, amount FROM global_shop_listings WHERE status = 'ACTIVE' AND expire_at < ? LIMIT ?";
                List<Object[]> batch = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setLong(1, now);
                    ps.setInt(2, batchSize);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        batch.add(new Object[]{
                                rs.getLong("id"),
                                rs.getString("seller_uuid"),
                                rs.getBytes("item_data"),
                                rs.getString("item_key"),
                                rs.getString("item_display_name"),
                                rs.getInt("amount")
                        });
                    }
                }

                if (batch.isEmpty()) break;

                for (Object[] row : batch) {
                    long id = (long) row[0];
                    String sellerUuid = (String) row[1];
                    byte[] itemData = (byte[]) row[2];
                    String itemKey = (String) row[3];
                    String displayName = (String) row[4];
                    int amount = (int) row[5];

                    // 幂等更新
                    String updateSql = "UPDATE global_shop_listings SET status = 'EXPIRED' WHERE id = ? AND status = 'ACTIVE'";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setLong(1, id);
                        if (ps.executeUpdate() > 0) {
                            // 仅在首次过期时创建退回条目
                            String insertReturnSql = "INSERT INTO global_shop_returns (owner_uuid, listing_id, item_data, item_key, item_display_name, amount, reason, created_at, claimed) VALUES (?, ?, ?, ?, ?, ?, 'EXPIRED', ?, FALSE)";
                            try (PreparedStatement ps2 = conn.prepareStatement(insertReturnSql)) {
                                ps2.setString(1, sellerUuid);
                                ps2.setLong(2, id);
                                ps2.setBytes(3, itemData);
                                ps2.setString(4, itemKey);
                                ps2.setString(5, displayName);
                                ps2.setInt(6, amount);
                                ps2.setLong(7, now);
                                ps2.executeUpdate();
                            }

                            // 通知在线卖家
                            Player seller = Bukkit.getPlayer(UUID.fromString(sellerUuid));
                            if (seller != null && seller.isOnline()) {
                                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                                    seller.sendMessage("§c你的全球商店物品 §f" + displayName + " §c已过期，物品已转入待领取列表");
                                });
                            }
                        }
                    }
                }

                totalProcessed += batch.size();
                // 如果本批不足 batchSize，说明已处理完毕
                if (batch.size() < batchSize) break;
            }

            return totalProcessed;
        }, count -> {
            if (count > 0) {
                plugin.getLogger().info("[全球商店] 处理了 " + count + " 条过期上架");
            }
        }, e -> plugin.getLogger().severe("检查过期上架失败: " + e.getMessage()));
    }

    // ==================== 辅助方法 ====================

    private void returnItemToPlayer(Player player, byte[] itemData, int amount) {
        // 调用方已在 GlobalRegionScheduler 上，无需再次调度
        if (!player.isOnline()) {
            // 玩家不在线，创建待领取条目
            createReturnEntryForOffline(player.getUniqueId(), itemData, null, null, amount);
            return;
        }
        ItemStack item = ItemDataUtil.deserializeItem(itemData);
        if (item != null) {
            item.setAmount(amount);
            java.util.Collection<ItemStack> leftover = player.getInventory().addItem(item).values();
            if (!leftover.isEmpty()) {
                // 背包满，仅将未放入的物品存入待领取（防止刷物）
                int leftoverCount = leftover.stream().mapToInt(ItemStack::getAmount).sum();
                if (leftoverCount > 0) {
                    createReturnEntryForOffline(player.getUniqueId(), itemData, null, null, leftoverCount);
                }
                plugin.getLogger().warning("[全球商店] 物品退回背包部分满，" + leftoverCount + "/" + amount + " 存入待领取: " + player.getName());
            }
        }
    }

    /**
     * 为离线玩家创建退回物品的待领取条目
     */
    public void createReturnEntryForOffline(UUID playerUuid, byte[] itemData, String itemKey, String displayName, int amount) {
        plugin.getDatabaseQueue().submit("创建退回待领取", conn -> {
            String sql = "INSERT INTO global_shop_returns (owner_uuid, listing_id, item_data, item_key, item_display_name, amount, earnings, reason, created_at, claimed) VALUES (?, NULL, ?, ?, ?, ?, 0, 'RETURN', ?, FALSE)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setBytes(2, itemData);
                ps.setString(3, itemKey);
                ps.setString(4, displayName);
                ps.setInt(5, amount);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        }, r -> {}, e -> plugin.getLogger().severe("创建退回待领取条目失败: " + e.getMessage()));
    }

    // ==================== 生命周期 ====================

    public GlobalShopSessionManager getSessionManager() { return sessionManager; }
    public GlobalShopPurchaseManager getPurchaseManager() { return purchaseManager; }
    public GlobalShopReturnManager getReturnManager() { return returnManager; }

    public void shutdown() {
        if (expiryTask != null) expiryTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        sessionManager.shutdown();
        purchaseManager.shutdown();
    }
}
