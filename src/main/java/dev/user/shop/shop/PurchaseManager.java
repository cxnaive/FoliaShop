package dev.user.shop.shop;

import dev.user.shop.FoliaShopPlugin;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 购买事务管理器
 * 统一处理商店购买流程，确保原子性和线程安全
 */
public class PurchaseManager {

    private final FoliaShopPlugin plugin;
    private final me.yic.xconomy.api.XConomyAPI xconomyAPI;
    private final PlayerPointsAPI playerPointsAPI;

    // 购买任务队列（单线程执行，保证顺序和原子性）
    private final BlockingQueue<PurchaseTask> taskQueue;
    private final ExecutorService executor;
    private volatile boolean running = true;

    public PurchaseManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
        this.xconomyAPI = plugin.getEconomyManager().isEnabled() ?
            new me.yic.xconomy.api.XConomyAPI() : null;

        PlayerPoints pp = PlayerPoints.getInstance();
        this.playerPointsAPI = (pp != null && pp.getAPI() != null) ? pp.getAPI() : null;

        this.taskQueue = new LinkedBlockingQueue<>();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FoliaShop-Purchase-Queue");
            t.setDaemon(true);
            return t;
        });

        startProcessing();
    }

    private void startProcessing() {
        executor.submit(() -> {
            while (running || !taskQueue.isEmpty()) {
                try {
                    PurchaseTask task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        processPurchase(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    plugin.getLogger().severe("购买处理异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 提交购买请求
     */
    public void submitPurchase(Player player, ShopItem shopItem, int amount,
                               Consumer<PurchaseResult> callback) {
        if (!running) {
            callback.accept(new PurchaseResult(false, "商店系统已关闭", null, 0, 0, 0));
            return;
        }

        PurchaseTask task = new PurchaseTask(
            player.getUniqueId(),
            player.getName(),
            shopItem,
            amount,
            callback
        );

        try {
            if (!taskQueue.offer(task, 5, TimeUnit.SECONDS)) {
                callback.accept(new PurchaseResult(false, "购买队列已满，请稍后再试", null, 0, 0, 0));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.accept(new PurchaseResult(false, "提交购买请求被中断", null, 0, 0, 0));
        }
    }

    /**
     * 处理单个购买任务
     */
    private void processPurchase(PurchaseTask task) {
        Player player = plugin.getServer().getPlayer(task.playerUuid);
        if (player == null || !player.isOnline()) {
            sendCallback(player, task.callback, new PurchaseResult(false, "玩家已离线", null, 0, 0, 0));
            return;
        }

        ShopItem shopItem = task.shopItem;
        int amount = task.amount;

        // 计算总费用
        double unitBuyPrice = shopItem.hasRandomBuyPrice()
            ? dev.user.shop.util.PriceUtil.computeDailyPrice(task.playerUuid, shopItem.getId(), shopItem.getBuyPrice(), shopItem.getBuyPriceMax())
            : shopItem.getBuyPrice();
        double totalCost = unitBuyPrice * amount;
        int totalPoints = shopItem.getBuyPoints() * amount;

        Connection conn = null;
        try {
            // 1. 先检查 conditions（如有）
            if (shopItem.hasConditions()) {
                String failedCondition = checkConditions(player, shopItem.getConditions());
                if (failedCondition != null) {
                    sendCallback(player, task.callback, new PurchaseResult(false, "不满足购买条件: " + failedCondition, null, 0, 0, 0));
                    return;
                }
            }

            // 2. 检查金币余额（只读，无副作用）
            if (totalCost > 0) {
                if (xconomyAPI == null) {
                    sendCallback(player, task.callback, new PurchaseResult(false, "经济系统未启用", null, 0, 0, 0));
                    return;
                }
                double balance = getPlayerBalance(player);
                if (balance < totalCost) {
                    sendCallback(player, task.callback, new PurchaseResult(false,
                        String.format("金币不足，需要 %.2f，拥有 %.2f", totalCost, balance),
                        null, 0, 0, 0));
                    return;
                }
            }

            // 3. 检查点券余额（只读，无副作用）
            if (totalPoints > 0) {
                if (playerPointsAPI == null) {
                    sendCallback(player, task.callback, new PurchaseResult(false, "点券系统未启用", null, 0, 0, 0));
                    return;
                }
                int points = playerPointsAPI.look(player.getUniqueId());
                if (points < totalPoints) {
                    sendCallback(player, task.callback, new PurchaseResult(false,
                        String.format("点券不足，需要 %d，拥有 %d", totalPoints, points),
                        null, 0, 0, 0));
                    return;
                }
            }

            // 5. 确认货币足够后，开始数据库事务并扣减库存
            conn = plugin.getDatabaseManager().getConnection();
            conn.setAutoCommit(false);

            // 4. 检查玩家每日购买限额（daily-limit）
            if (shopItem.hasDailyLimit()) {
                int remaining = getDailyLimitRemaining(conn, player.getUniqueId(), shopItem.getId(), shopItem.getDailyLimit());
                if (remaining < amount) {
                    conn.close();
                    conn = null;
                    sendCallback(player, task.callback, new PurchaseResult(false,
                        String.format("今日购买限额已满，剩余可购买 %d 个", remaining),
                        null, 0, 0, 0));
                    return;
                }
            }

            // 5. 检查玩家终身购买限额（player-limit）
            if (shopItem.hasPlayerLimit()) {
                int remaining = getPlayerLimitRemaining(conn, player.getUniqueId(), shopItem.getId(), shopItem.getPlayerLimit());
                if (remaining < amount) {
                    conn.close();
                    conn = null;
                    sendCallback(player, task.callback, new PurchaseResult(false,
                        String.format("您已购买过该物品，剩余可购买 %d 个", remaining),
                        null, 0, 0, 0));
                    return;
                }
            }

            int actualAmount = atomicReduceStock(conn, shopItem.getId(), amount);
            if (actualAmount == 0) {
                conn.rollback();
                sendCallback(player, task.callback, new PurchaseResult(false, "库存不足", null, 0, 0, 0));
                return;
            }
            // 调整实际购买数量
            if (actualAmount != amount) {
                totalCost = unitBuyPrice * actualAmount;
                totalPoints = shopItem.getBuyPoints() * actualAmount;

                // 重新检查货币（因为数量变了）
                if (totalCost > 0 && getPlayerBalance(player) < totalCost) {
                    conn.rollback();
                    sendCallback(player, task.callback, new PurchaseResult(false, "金币不足（库存调整后）", null, 0, 0, 0));
                    return;
                }
                if (totalPoints > 0 && playerPointsAPI.look(player.getUniqueId()) < totalPoints) {
                    conn.rollback();
                    sendCallback(player, task.callback, new PurchaseResult(false, "点券不足（库存调整后）", null, 0, 0, 0));
                    return;
                }
            }

            // 4. 扣除金币
            if (totalCost > 0) {
                boolean success = deductMoney(player, totalCost);
                if (!success) {
                    conn.rollback();
                    sendCallback(player, task.callback, new PurchaseResult(false, "扣除金币失败", null, 0, 0, 0));
                    return;
                }
            }

            // 5. 扣除点券
            if (totalPoints > 0) {
                boolean success = playerPointsAPI.take(player.getUniqueId(), totalPoints);
                if (!success) {
                    // 回滚金币
                    if (totalCost > 0) {
                        returnMoney(player, totalCost);
                    }
                    conn.rollback();
                    sendCallback(player, task.callback, new PurchaseResult(false, "扣除点券失败", null, 0, 0, 0));
                    return;
                }
            }

            // 6. 增加玩家每日购买计数
            if (shopItem.hasDailyLimit()) {
                incrementDailyLimit(conn, player.getUniqueId(), shopItem.getId(), actualAmount);
            }

            // 7. 增加玩家终身购买计数
            if (shopItem.hasPlayerLimit()) {
                incrementPlayerLimit(conn, player.getUniqueId(), shopItem.getId(), actualAmount);
            }

            // 提交库存事务
            conn.commit();

            // 7. 在玩家 EntityScheduler 中给予物品
            final int finalAmount = actualAmount;
            final double finalCost = totalCost;
            final int finalPoints = totalPoints;
            final ItemStack itemToGive = shopItem.getDisplayItem().clone();
            itemToGive.setAmount(finalAmount);

            // 获取 commands 用于回调内执行
            final java.util.List<String> commandsToExecute = shopItem.hasCommands() ?
                new java.util.ArrayList<>(shopItem.getCommands()) : null;

            player.getScheduler().execute(plugin, () -> {
                // 给予物品（如有）
                if (shopItem.isGiveItem()) {
                    giveItemsToPlayer(player, itemToGive);
                }

                // 执行命令（如有）
                if (commandsToExecute != null) {
                    executeCommands(player, commandsToExecute);
                }

                // 记录交易
                logTransaction(task.playerUuid, task.playerName, shopItem.getId(),
                    shopItem.getItemKey(), finalAmount, finalCost, finalPoints);

                // 成功回调
                task.callback.accept(new PurchaseResult(true, "购买成功",
                    shopItem.getItemKey(), finalAmount, finalCost, finalPoints));

            }, null, 1L);

        } catch (SQLException e) {
            plugin.getLogger().severe("购买数据库异常: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    plugin.getLogger().warning("回滚失败: " + ex.getMessage());
                }
            }
            sendCallback(player, task.callback, new PurchaseResult(false, "数据库错误: " + e.getMessage(), null, 0, 0, 0));
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().warning("关闭连接失败: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 在玩家区域线程上发送回调，避免在executor线程直接操作玩家相关API
     */
    private void sendCallback(Player player, Consumer<PurchaseResult> callback, PurchaseResult result) {
        if (player != null && player.isOnline()) {
            player.getScheduler().execute(plugin, () -> callback.accept(result), null, 1L);
        } else {
            // 玩家离线时直接回调（不涉及inventory操作）
            callback.accept(result);
        }
    }

    /**
     * 原子性扣减库存
     * @return 实际扣减的数量（0表示库存不足）
     */
    private int atomicReduceStock(Connection conn, String itemId, int amount) throws SQLException {
        // 查询当前库存
        String selectSql = plugin.getDatabaseManager().isMySQL()
            ? "SELECT stock FROM shop_items WHERE id = ? AND enabled = TRUE FOR UPDATE"
            : "SELECT stock FROM shop_items WHERE id = ? AND enabled = TRUE";

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return 0;
            }
            int currentStock = rs.getInt("stock");

            // 无限库存
            if (currentStock < 0) {
                return amount;
            }

            // 库存不足
            if (currentStock < amount) {
                return 0;
            }

            // 扣减库存
            String updateSql = "UPDATE shop_items SET stock = stock - ? WHERE id = ? AND stock >= ?";
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                updatePs.setInt(1, amount);
                updatePs.setString(2, itemId);
                updatePs.setInt(3, amount);
                int affected = updatePs.executeUpdate();
                return affected > 0 ? amount : 0;
            }
        }
    }

    /**
     * 获取玩家每日剩余购买次数
     * @param conn 数据库连接（必须在事务中）
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param limit 限制数量
     * @return 剩余可购买次数
     */
    private int getDailyLimitRemaining(Connection conn, UUID playerUuid, String itemId, int limit) throws SQLException {
        String today = getTodayString();
        String selectSql = plugin.getDatabaseManager().isMySQL()
            ? "SELECT buy_count, last_date FROM daily_limits WHERE player_uuid = ? AND item_id = ? FOR UPDATE"
            : "SELECT buy_count, last_date FROM daily_limits WHERE player_uuid = ? AND item_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int boughtCount = rs.getInt("buy_count");
                String lastDate = rs.getString("last_date");
                if (!today.equals(lastDate)) {
                    // 跨天了，返回完整限额
                    return limit;
                }
                return Math.max(0, limit - boughtCount);
            } else {
                // 没有记录表示今日还未购买
                return limit;
            }
        }
    }

    /**
     * 增加玩家每日购买计数
     * @param conn 数据库连接（必须在事务中）
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param amount 购买数量
     */
    private void incrementDailyLimit(Connection conn, UUID playerUuid, String itemId, int amount) throws SQLException {
        String today = getTodayString();
        boolean isMySQL = plugin.getDatabaseManager().isMySQL();

        // 先查询当前记录
        String selectSql = isMySQL
            ? "SELECT buy_count, last_date FROM daily_limits WHERE player_uuid = ? AND item_id = ? FOR UPDATE"
            : "SELECT buy_count, last_date FROM daily_limits WHERE player_uuid = ? AND item_id = ?";

        int newCount;
        boolean recordExists = false;
        String lastDate = null;

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                recordExists = true;
                int currentCount = rs.getInt("buy_count");
                lastDate = rs.getString("last_date");
                if (today.equals(lastDate)) {
                    newCount = currentCount + amount;
                } else {
                    newCount = amount; // 跨天重置
                }
            } else {
                newCount = amount; // 新记录
            }
        }

        // 插入或更新
        String upsertSql;
        if (isMySQL) {
            upsertSql = "INSERT INTO daily_limits (player_uuid, item_id, buy_count, last_date) VALUES (?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE buy_count = ?, last_date = ?";
        } else {
            upsertSql = "MERGE INTO daily_limits KEY(player_uuid, item_id) VALUES (?, ?, ?, ?)";
        }

        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemId);
            if (isMySQL) {
                ps.setInt(3, newCount);
                ps.setString(4, today);
                ps.setInt(5, newCount);
                ps.setString(6, today);
            } else {
                ps.setInt(3, newCount);
                ps.setString(4, today);
            }
            ps.executeUpdate();
        }
    }

    private String getTodayString() {
        return java.time.LocalDate.now().toString();
    }

    /**
     * 获取玩家剩余购买次数
     * @param conn 数据库连接（必须在事务中）
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param limit 限制数量
     * @return 剩余可购买次数
     */
    private int getPlayerLimitRemaining(Connection conn, UUID playerUuid, String itemId, int limit) throws SQLException {
        String selectSql = plugin.getDatabaseManager().isMySQL()
            ? "SELECT buy_count FROM player_item_limits WHERE player_uuid = ? AND item_id = ? FOR UPDATE"
            : "SELECT buy_count FROM player_item_limits WHERE player_uuid = ? AND item_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int boughtCount = rs.getInt("buy_count");
                return Math.max(0, limit - boughtCount);
            } else {
                // 没有记录表示还未购买
                return limit;
            }
        }
    }

    /**
     * 增加玩家购买计数
     * @param conn 数据库连接（必须在事务中）
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param amount 购买数量
     */
    private void incrementPlayerLimit(Connection conn, UUID playerUuid, String itemId, int amount) throws SQLException {
        String upsertSql;
        if (plugin.getDatabaseManager().isMySQL()) {
            upsertSql = "INSERT INTO player_item_limits (player_uuid, item_id, buy_count) VALUES (?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE buy_count = buy_count + ?";
        } else {
            // SQLite 使用 INSERT OR REPLACE
            upsertSql = "INSERT INTO player_item_limits (player_uuid, item_id, buy_count) VALUES (?, ?, ?) " +
                       "ON CONFLICT(player_uuid, item_id) DO UPDATE SET buy_count = buy_count + excluded.buy_count";
        }

        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, itemId);
            ps.setInt(3, amount);
            if (plugin.getDatabaseManager().isMySQL()) {
                ps.setInt(4, amount);
            }
            ps.executeUpdate();
        }
    }

    /**
     * 获取玩家金币余额
     */
    private double getPlayerBalance(Player player) {
        try {
            return xconomyAPI.getPlayerData(player.getUniqueId()).getBalance().doubleValue();
        } catch (Exception e) {
            plugin.getLogger().warning("获取金币余额失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 扣除玩家金币
     */
    private boolean deductMoney(Player player, double amount) {
        try {
            int result = xconomyAPI.changePlayerBalance(
                player.getUniqueId(),
                player.getName(),
                java.math.BigDecimal.valueOf(amount),
                false
            );
            return result == 0;
        } catch (Exception e) {
            plugin.getLogger().warning("扣除金币失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 退还玩家金币
     */
    private boolean returnMoney(Player player, double amount) {
        try {
            int result = xconomyAPI.changePlayerBalance(
                player.getUniqueId(),
                player.getName(),
                java.math.BigDecimal.valueOf(amount),
                true
            );
            return result == 0;
        } catch (Exception e) {
            plugin.getLogger().warning("退还金币失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 给予玩家物品（背包满了掉落脚下）
     */
    private void giveItemsToPlayer(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            // 背包满了，掉落脚下
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            player.sendMessage("§e背包已满，部分物品已掉落在脚下！");
        }
    }

    /**
     * 检查购买条件
     * 支持: !permission:xxx (没有权限) 或 permission:xxx (有权限)
     * @return null 表示通过，否则返回失败原因
     */
    private String checkConditions(Player player, java.util.List<String> conditions) {
        for (String condition : conditions) {
            String trimmed = condition.trim();
            // 检查是否没有权限: !permission:xxx
            if (trimmed.startsWith("!permission:")) {
                String perm = trimmed.substring(12);
                if (player.hasPermission(perm)) {
                    return "已拥有权限: " + perm;
                }
            }
            // 检查是否有权限: permission:xxx
            else if (trimmed.startsWith("permission:")) {
                String perm = trimmed.substring(11);
                if (!player.hasPermission(perm)) {
                    return "缺少权限: " + perm;
                }
            }
        }
        return null; // 全部通过
    }

    /**
     * 执行命令列表（默认在控制台执行，{player} 替换为玩家名）
     * 注意：此方法在 GlobalRegionScheduler 中执行，因为 Bukkit.dispatchCommand 必须在主线程调用
     */
    private void executeCommands(Player player, java.util.List<String> commands) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            for (String cmd : commands) {
                String parsed = cmd.replace("{player}", player.getName());
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                } catch (Exception e) {
                    plugin.getLogger().warning("执行购买命令失败: " + parsed + " - " + e.getMessage());
                }
            }
        });
    }

    /**
     * 记录交易日志
     */
    private void logTransaction(UUID playerUuid, String playerName, String itemId,
                                String itemKey, int amount, double cost, int points) {
        String type = points > 0 ? "BUY_POINTS" : "BUY";
        plugin.getDatabaseQueue().submit("logTransaction", conn -> {
            String sql = "INSERT INTO transactions (player_uuid, player_name, item_id, item_key, amount, price, type, timestamp) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, itemId);
                ps.setString(4, itemKey);
                ps.setInt(5, amount);
                ps.setDouble(6, cost);
                ps.setString(7, type);
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    /**
     * 购买任务
     */
    private static class PurchaseTask {
        final UUID playerUuid;
        final String playerName;
        final ShopItem shopItem;
        final int amount;
        final Consumer<PurchaseResult> callback;

        PurchaseTask(UUID playerUuid, String playerName, ShopItem shopItem,
                     int amount, Consumer<PurchaseResult> callback) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.shopItem = shopItem;
            this.amount = amount;
            this.callback = callback;
        }
    }

    /**
     * 购买结果
     */
    public static class PurchaseResult {
        public final boolean success;
        public final String message;
        public final String itemKey;
        public final int amount;
        public final double cost;
        public final int points;

        public PurchaseResult(boolean success, String message, String itemKey,
                              int amount, double cost, int points) {
            this.success = success;
            this.message = message;
            this.itemKey = itemKey;
            this.amount = amount;
            this.cost = cost;
            this.points = points;
        }
    }
}
