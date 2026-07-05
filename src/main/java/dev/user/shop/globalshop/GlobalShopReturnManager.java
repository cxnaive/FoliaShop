package dev.user.shop.globalshop;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.database.DatabaseQueue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 全球商店待领取物品/收益管理器
 */
public class GlobalShopReturnManager {

    private final FoliaShopPlugin plugin;

    public GlobalShopReturnManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 获取玩家所有未领取的条目
     */
    public void getUnclaimedReturns(UUID ownerUuid, Consumer<List<GlobalShopReturnEntry>> callback) {
        plugin.getDatabaseQueue().submit("查询待领取条目", conn -> {
            List<GlobalShopReturnEntry> entries = new ArrayList<>();
            String sql = "SELECT * FROM global_shop_returns WHERE owner_uuid = ? AND claimed = FALSE ORDER BY created_at DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    entries.add(GlobalShopReturnEntry.fromResultSet(rs));
                }
            }
            return entries;
        }, callback, e -> {
            plugin.getLogger().severe("查询待领取条目失败: " + e.getMessage());
            callback.accept(new ArrayList<>());
        });
    }

    /**
     * 领取单个物品条目（同时领取该条目关联的收益）
     * 防刷物: 先标记 claimed，再给物品，背包满则回滚
     * @return 通过 ClaimItemResult 返回是否成功和收益金额
     */
    public void claimItem(PlayerContext ctx, long returnId, Consumer<ClaimOutcome> callback) {
        plugin.getDatabaseQueue().submit("领取物品", conn -> {
            // 先标记 claimed，防止重复领取
            String updateSql = "UPDATE global_shop_returns SET claimed = TRUE WHERE id = ? AND claimed = FALSE AND owner_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setLong(1, returnId);
                ps.setString(2, ctx.uuid().toString());
                if (ps.executeUpdate() == 0) {
                    return null; // 已被领取或不存在
                }
            }

            // 读取物品数据和收益
            String selectSql = "SELECT item_data, amount, earnings FROM global_shop_returns WHERE id = ?";
            byte[] itemData = null;
            int amount = 0;
            double earnings = 0;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, returnId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    itemData = rs.getBytes("item_data");
                    amount = rs.getInt("amount");
                    earnings = rs.getDouble("earnings");
                }
            }

            if (itemData == null && earnings <= 0) {
                return null; // 无物品也无收益
            }

            return new ClaimItemData(itemData, amount, earnings);
        }, result -> {
            if (result == null) {
                callback.accept(new ClaimOutcome(false, 0));
                return;
            }

            if (result instanceof ClaimItemData data) {
                // 在主线程给物品和收益
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    org.bukkit.entity.Player player = plugin.getServer().getPlayer(ctx.uuid());
                    if (player == null || !player.isOnline()) {
                        rollbackClaim(returnId);
                        callback.accept(new ClaimOutcome(false, 0));
                        return;
                    }

                    if (data.itemData != null) {
                        org.bukkit.inventory.ItemStack item = dev.user.shop.util.ItemDataUtil.deserializeItem(data.itemData);
                        if (item == null) {
                            // 反序列化失败：回滚 claimed，保留条目供玩家重试/管理员排查（不吞物品）
                            rollbackClaim(returnId);
                            player.sendMessage("§c物品数据异常，领取已回滚，请联系管理员");
                            callback.accept(new ClaimOutcome(false, 0));
                            return;
                        }
                        item.setAmount(data.amount);
                        if (!hasSpace(player, item)) {
                            rollbackClaim(returnId);
                            player.sendMessage("§c背包空间不足，请清理背包后再领取");
                            callback.accept(new ClaimOutcome(false, 0));
                            return;
                        }
                        java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(item);
                        if (!leftover.isEmpty()) {
                            int leftoverCount = leftover.values().stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
                            if (leftoverCount > 0) {
                                // 背包空间计算与实际不符时，剩余物品重新存入待领取（防丢）
                                plugin.getGlobalShopManager().createReturnEntryForOffline(
                                        player.getUniqueId(), data.itemData, null, null, leftoverCount);
                                player.sendMessage("§e背包空间不足，" + leftoverCount + " 个物品已存入待领取列表");
                            }
                        }
                    }

                    // 给予收益（检查 success，失败回滚 claimed 保留条目供重试，不静默吞钱）
                    if (data.earnings > 0) {
                        final double earnings = data.earnings;
                        plugin.getEconomyManager().depositAsync(player, earnings, success -> {
                            if (success) {
                                callback.accept(new ClaimOutcome(true, earnings));
                            } else if (data.itemData == null) {
                                // 纯收益条目（无物品发出）：安全回滚 claimed，收益保留可重试
                                rollbackClaim(returnId);
                                player.sendMessage("§c收益到账失败，请稍后重试");
                                callback.accept(new ClaimOutcome(false, 0));
                            } else {
                                // 异常脏数据（物品已发出却带收益）：不能 rollback（否则物品可再领=复制），仅告警
                                plugin.getLogger().warning("[全球商店] 领取条目 " + returnId + " 物品已发但收益存款失败，收益未发放: " + earnings);
                                callback.accept(new ClaimOutcome(true, 0));
                            }
                        });
                        return;
                    }

                    callback.accept(new ClaimOutcome(true, 0));
                });
            } else {
                callback.accept(new ClaimOutcome(false, 0));
            }
        }, e -> {
            plugin.getLogger().severe("领取物品失败: " + e.getMessage());
            callback.accept(new ClaimOutcome(false, 0));
        });
    }

    /**
     * 领取全部收益
     * @return 领取到的总收益金额
     */
    public void claimAllEarnings(UUID ownerUuid, Consumer<Double> callback) {
        plugin.getDatabaseQueue().submit("领取全部收益", conn -> {
            // 查询所有未领取收益
            String selectSql = "SELECT id, earnings FROM global_shop_returns WHERE owner_uuid = ? AND claimed = FALSE AND earnings > 0 AND item_data IS NULL";
            List<Long> ids = new ArrayList<>();
            double totalEarnings = 0;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, ownerUuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                    totalEarnings += rs.getDouble("earnings");
                }
            }

            if (ids.isEmpty() || totalEarnings <= 0) {
                return new EarningsClaimResult(null, 0);
            }

            // 标记为已领取
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String updateSql = "UPDATE global_shop_returns SET claimed = TRUE WHERE id IN (" + placeholders + ") AND owner_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                for (int i = 0; i < ids.size(); i++) {
                    ps.setLong(i + 1, ids.get(i));
                }
                ps.setString(ids.size() + 1, ownerUuid.toString());
                ps.executeUpdate();
            }

            return new EarningsClaimResult(ids, totalEarnings);
        }, result -> {
            if (result == null || result.total() <= 0) {
                callback.accept(0.0);
                return;
            }

            // 存入玩家账户
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(ownerUuid);
            if (player != null && player.isOnline()) {
                plugin.getEconomyManager().depositAsync(player, result.total(), success -> {
                    if (!success) {
                        // 存入失败，回滚 claimed
                        plugin.getLogger().warning("[全球商店] 收益存入失败，回滚领取状态: " + ownerUuid);
                        rollbackEarningsClaim(ownerUuid, result.total());
                        callback.accept(0.0);
                    } else {
                        callback.accept(result.total());
                    }
                });
            } else {
                // 玩家离线，回滚 claimed 状态防止金钱丢失
                rollbackClaimsByIds(result.ids());
                callback.accept(0.0);
            }
        }, e -> {
            plugin.getLogger().severe("领取全部收益失败: " + e.getMessage());
            callback.accept(0.0);
        });
    }

    /**
     * 获取未领取条目数量 (用于登录提醒)
     */
    public void getUnclaimedCount(UUID ownerUuid, Consumer<Integer> callback) {
        plugin.getDatabaseQueue().submit("查询未领取数量", conn -> {
            String sql = "SELECT COUNT(*) FROM global_shop_returns WHERE owner_uuid = ? AND claimed = FALSE";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return 0;
        }, callback, e -> callback.accept(0));
    }

    private void rollbackClaim(long returnId) {
        plugin.getDatabaseQueue().submit("回滚领取状态", conn -> {
            String sql = "UPDATE global_shop_returns SET claimed = FALSE WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, returnId);
                ps.executeUpdate();
            }
            return null;
        }, r -> {}, e -> plugin.getLogger().warning("回滚领取状态失败: " + e.getMessage()));
    }

    private void rollbackEarningsClaim(UUID ownerUuid, double amount) {
        // 简单处理：重新创建一条收益条目
        plugin.getDatabaseQueue().submit("回滚收益领取", conn -> {
            String sql = "INSERT INTO global_shop_returns (owner_uuid, listing_id, earnings, reason, created_at, claimed) VALUES (?, NULL, ?, 'ROLLBACK', ?, FALSE)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ps.setDouble(2, amount);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        }, r -> {}, e -> plugin.getLogger().warning("回滚收益领取失败: " + e.getMessage()));
    }

    private boolean hasSpace(org.bukkit.entity.Player player, org.bukkit.inventory.ItemStack item) {
        int remaining = item.getAmount();
        for (org.bukkit.inventory.ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= item.getMaxStackSize();
            } else if (slot.isSimilar(item)) {
                remaining -= (item.getMaxStackSize() - slot.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    // 辅助记录类型
    public record PlayerContext(UUID uuid) {}
    public record ClaimOutcome(boolean success, double earnings) {}
    private record ClaimItemData(byte[] itemData, int amount, double earnings) {}
    private record EarningsClaimResult(List<Long> ids, double total) {}

    /**
     * 按 ID 列表回滚 claimed 状态
     */
    private void rollbackClaimsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        plugin.getDatabaseQueue().submit("回滚收益领取", conn -> {
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String sql = "UPDATE global_shop_returns SET claimed = FALSE WHERE id IN (" + placeholders + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < ids.size(); i++) {
                    ps.setLong(i + 1, ids.get(i));
                }
                ps.executeUpdate();
            }
            return null;
        }, r -> {}, e -> plugin.getLogger().warning("回滚收益领取状态失败: " + e.getMessage()));
    }
}
