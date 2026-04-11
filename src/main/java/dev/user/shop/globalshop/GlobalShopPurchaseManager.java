package dev.user.shop.globalshop;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.util.ItemDataUtil;
import me.yic.xconomy.api.XConomyAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * 全球商店原子购买处理器
 * 独立单线程执行器，确保购买操作的原子性和顺序性
 */
public class GlobalShopPurchaseManager {

    private final FoliaShopPlugin plugin;
    private final XConomyAPI xconomyAPI;
    private final BlockingQueue<PurchaseTask> taskQueue;
    private final ExecutorService executor;
    private volatile boolean running = true;

    public GlobalShopPurchaseManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
        this.xconomyAPI = new XConomyAPI();
        this.taskQueue = new LinkedBlockingQueue<>();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FoliaShop-GlobalShop-Purchase-Queue");
            t.setDaemon(true);
            return t;
        });
        startProcessing();
    }

    private void startProcessing() {
        executor.submit(() -> {
            while (running || !taskQueue.isEmpty()) {
                try {
                    PurchaseTask task = taskQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (task != null) {
                        processPurchase(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    plugin.getLogger().severe("全球商店购买处理异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    public void submitPurchase(Player buyer, long listingId, Consumer<GlobalShopPurchaseResult> callback) {
        if (!running) {
            callback.accept(GlobalShopPurchaseResult.fail("商店系统已关闭"));
            return;
        }
        taskQueue.add(new PurchaseTask(buyer.getUniqueId(), buyer.getName(), listingId, callback));
    }

    private void processPurchase(PurchaseTask task) {
        Connection conn = null;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();

            // 1. 查询上架状态（FOR UPDATE 行锁）
            String selectSql = isMySQL
                    ? "SELECT id, seller_uuid, seller_name, item_data, item_key, item_display_name, amount, price, status FROM global_shop_listings WHERE id = ? AND status = 'ACTIVE' FOR UPDATE"
                    : "SELECT id, seller_uuid, seller_name, item_data, item_key, item_display_name, amount, price, status FROM global_shop_listings WHERE id = ? AND status = 'ACTIVE'";

            long listingId = task.listingId;
            String sellerUuid = null;
            String sellerName = null;
            byte[] itemData = null;
            String itemKey = null;
            String itemDisplayName = null;
            int amount = 0;
            double price = 0;

            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, listingId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    conn.rollback();
                    sendResult(task, GlobalShopPurchaseResult.fail("§c物品已售出或不存在"));
                    return;
                }
                sellerUuid = rs.getString("seller_uuid");
                sellerName = rs.getString("seller_name");
                itemData = rs.getBytes("item_data");
                itemKey = rs.getString("item_key");
                itemDisplayName = rs.getString("item_display_name");
                amount = rs.getInt("amount");
                price = rs.getDouble("price");
            }

            // 2. 防止自购
            if (task.buyerUuid.toString().equals(sellerUuid)) {
                conn.rollback();
                sendResult(task, GlobalShopPurchaseResult.fail("§c不能购买自己上架的物品"));
                return;
            }

            // 3. 条件更新：标记为 SOLD
            String updateSql = "UPDATE global_shop_listings SET status = 'SOLD' WHERE id = ? AND status = 'ACTIVE'";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setLong(1, listingId);
                if (ps.executeUpdate() == 0) {
                    conn.rollback();
                    sendResult(task, GlobalShopPurchaseResult.fail("§c物品已被他人购买"));
                    return;
                }
            }

            // 4. 预检买家余额（仅作友好提示，实际扣款在 commit 后）
            Player buyer = Bukkit.getPlayer(task.buyerUuid());
            if (buyer == null || !buyer.isOnline()) {
                conn.rollback();
                sendResult(task, GlobalShopPurchaseResult.fail("§c你已离线"));
                return;
            }

            double buyerBalance = xconomyAPI.getPlayerData(buyer.getUniqueId()).getBalance().doubleValue();
            if (buyerBalance < price) {
                conn.rollback();
                sendResult(task, GlobalShopPurchaseResult.fail("§c余额不足！需要 §e" + String.format("%.2f", price) + " " + plugin.getShopConfig().getCurrencyName()));
                return;
            }

            // 5. 计算卖家收益和税费
            double taxRate = plugin.getShopConfig().getGlobalShopTaxRate();
            double taxAmount = price * taxRate;
            double sellerEarnings = price - taxAmount;

            // 6. 为卖家创建收益待领取条目
            String insertReturnSql = "INSERT INTO global_shop_returns (owner_uuid, listing_id, earnings, reason, created_at, claimed) VALUES (?, ?, ?, 'SOLD', ?, FALSE)";
            try (PreparedStatement ps = conn.prepareStatement(insertReturnSql)) {
                ps.setString(1, sellerUuid);
                ps.setLong(2, listingId);
                ps.setDouble(3, sellerEarnings);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            }

            // 7. 记录交易
            String insertTxSql = "INSERT INTO global_shop_transactions (buyer_uuid, buyer_name, seller_uuid, seller_name, listing_id, item_key, item_display_name, amount, total_price, tax_amount, seller_earnings, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertTxSql)) {
                ps.setString(1, task.buyerUuid.toString());
                ps.setString(2, task.buyerName);
                ps.setString(3, sellerUuid);
                ps.setString(4, sellerName);
                ps.setLong(5, listingId);
                ps.setString(6, itemKey);
                ps.setString(7, itemDisplayName);
                ps.setInt(8, amount);
                ps.setDouble(9, price);
                ps.setDouble(10, taxAmount);
                ps.setDouble(11, sellerEarnings);
                ps.setLong(12, System.currentTimeMillis());
                ps.executeUpdate();
            }

            // 8. 提交事务（先确保 DB 操作全部成功，再扣款）
            conn.commit();

            // 9. 扣除买家金钱（commit 后执行，避免 commit 失败时买家丢钱）
            boolean withdrawn = xconomyAPI.changePlayerBalance(buyer.getUniqueId(), buyer.getName(), BigDecimal.valueOf(-price), false) == 0;
            if (!withdrawn) {
                // 极端情况：DB 已标记 SOLD 但扣款失败。记录日志供管理员排查。
                plugin.getLogger().warning("[全球商店] 购买扣款失败但已 commit! buyer=" + task.buyerName + " listing=" + listingId + " price=" + price);
                // 尝试原子回滚：恢复上架状态、删除收益和交易记录
                plugin.getDatabaseQueue().submit("回滚扣款失败购买", rollbackConn -> {
                    rollbackConn.setAutoCommit(false);
                    try {
                        String rollbackSql = "UPDATE global_shop_listings SET status = 'ACTIVE' WHERE id = ? AND status = 'SOLD'";
                        try (PreparedStatement ps = rollbackConn.prepareStatement(rollbackSql)) {
                            ps.setLong(1, listingId);
                            if (ps.executeUpdate() > 0) {
                                try (PreparedStatement delReturns = rollbackConn.prepareStatement(
                                        "DELETE FROM global_shop_returns WHERE listing_id = ?")) {
                                    delReturns.setLong(1, listingId);
                                    delReturns.executeUpdate();
                                }
                                try (PreparedStatement delTx = rollbackConn.prepareStatement(
                                        "DELETE FROM global_shop_transactions WHERE listing_id = ?")) {
                                    delTx.setLong(1, listingId);
                                    delTx.executeUpdate();
                                }
                            }
                        }
                        rollbackConn.commit();
                    } catch (Exception e) {
                        rollbackConn.rollback();
                        throw e;
                    }
                    return null;
                }, r -> {}, e -> plugin.getLogger().severe("回滚扣款失败购买异常: " + e.getMessage()));
                sendResult(task, GlobalShopPurchaseResult.fail("§c扣款失败，请稍后再试"));
                return;
            }

            // 10. 给予买家物品（扣款成功后）
            byte[] finalItemData = itemData;
            int finalAmount = amount;
            String finalItemKey = itemKey;
            double finalPrice = price;
            double finalTax = taxAmount;

            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                Player onlineBuyer = Bukkit.getPlayer(task.buyerUuid);
                if (onlineBuyer == null || !onlineBuyer.isOnline()) {
                    // 买家已离线，创建待领取条目（完整数量）
                    createPurchaseReturnEntry(task.buyerUuid, finalItemData, finalItemKey, null, finalAmount, listingId);
                    return;
                }

                ItemStack item = ItemDataUtil.deserializeItem(finalItemData);
                if (item != null) {
                    item.setAmount(finalAmount);
                    java.util.Map<Integer, ItemStack> leftover = onlineBuyer.getInventory().addItem(item);
                    if (!leftover.isEmpty()) {
                        // 背包空间不足，仅将未放入的物品存入待领取
                        int leftoverCount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                        if (leftoverCount > 0) {
                            createPurchaseReturnEntry(task.buyerUuid, finalItemData, finalItemKey, null, leftoverCount, listingId);
                        }
                        onlineBuyer.sendMessage("§e背包空间不足，部分物品已存入待领取列表");
                    }
                } else {
                    // 反序列化失败，保留原始数据存入待领取
                    plugin.getLogger().warning("[全球商店] 购买物品反序列化失败，已创建待领取条目: listing=" + listingId);
                    createPurchaseReturnEntry(task.buyerUuid, finalItemData, finalItemKey, null, finalAmount, listingId);
                    onlineBuyer.sendMessage("§c物品数据异常，已存入待领取列表，请联系管理员");
                }
            });

            sendResult(task, GlobalShopPurchaseResult.success(
                    "§a购买成功！", finalItemKey, finalAmount, finalPrice, finalTax));

        } catch (Exception e) {
            try { if (conn != null) conn.rollback(); } catch (Exception ignored) {}
            plugin.getLogger().severe("全球商店购买处理失败: " + e.getMessage());
            e.printStackTrace();
            sendResult(task, GlobalShopPurchaseResult.fail("§c购买失败，请稍后再试"));
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 为买家创建购买物品的待领取条目（背包满、离线或反序列化失败时使用）
     */
    private void createPurchaseReturnEntry(UUID buyerUuid, byte[] itemData, String itemKey, String itemDisplayName, int amount, long listingId) {
        plugin.getDatabaseQueue().submit("创建购买待领取", conn -> {
            String sql = "INSERT INTO global_shop_returns (owner_uuid, listing_id, item_data, item_key, item_display_name, amount, earnings, reason, created_at, claimed) VALUES (?, ?, ?, ?, ?, ?, 0, 'PURCHASE', ?, FALSE)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, buyerUuid.toString());
                ps.setLong(2, listingId);
                ps.setBytes(3, itemData);
                ps.setString(4, itemKey);
                ps.setString(5, itemDisplayName);
                ps.setInt(6, amount);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        }, r -> {}, e -> plugin.getLogger().severe("创建购买待领取条目失败: " + e.getMessage()));
    }

    private void sendResult(PurchaseTask task, GlobalShopPurchaseResult result) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            task.callback.accept(result);
        });
    }

    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    private record PurchaseTask(UUID buyerUuid, String buyerName, long listingId, Consumer<GlobalShopPurchaseResult> callback) {}
}
