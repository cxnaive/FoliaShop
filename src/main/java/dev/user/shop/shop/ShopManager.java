package dev.user.shop.shop;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.util.ItemUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ShopManager {

    private final FoliaShopPlugin plugin;
    private final Map<String, ShopItem> items;
    private final Map<String, ShopCategory> categories;
    // 物品缓存：以物品ItemStack的hash为key，加速查找
    private final Map<Integer, ShopItem> itemCacheBySimilarity;

    public ShopManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
        this.items = new HashMap<>();
        this.categories = new HashMap<>();
        this.itemCacheBySimilarity = new HashMap<>();
        load();
    }

    public void load() {
        items.clear();
        categories.clear();
        itemCacheBySimilarity.clear();

        // 加载分类
        loadCategories();

        // 先从数据库异步加载商品，加载完成后再处理配置
        loadItemsFromDatabaseAsync(() -> {
            // 数据库加载完成后，从配置加载进行增量更新
            loadItemsFromConfig();
            // 重建缓存
            rebuildItemCache();

            plugin.getLogger().info("已加载 " + items.size() + " 个商店商品，" + categories.size() + " 个分类");
        });
    }

    /**
     * 从配置文件重新加载（清空数据库并重新导入）
     */
    public void reloadFromConfig() {
        items.clear();
        categories.clear();
        itemCacheBySimilarity.clear();

        // 清空数据库中的商品
        plugin.getDatabaseManager().clearShopItems();
        plugin.getLogger().info("已清空数据库商店商品表");

        // 加载分类
        loadCategories();

        // 从配置加载所有商品（并保存到数据库）
        loadItemsFromConfig();

        // 重建物品缓存
        rebuildItemCache();

        plugin.getLogger().info("已从配置重新加载 " + items.size() + " 个商店商品");
    }

    /**
     * 重建物品查找缓存
     */
    private void rebuildItemCache() {
        itemCacheBySimilarity.clear();
        for (ShopItem shopItem : items.values()) {
            if (shopItem.getDisplayItem() != null) {
                int hash = calculateItemHash(shopItem.getDisplayItem());
                itemCacheBySimilarity.put(hash, shopItem);
            }
        }
    }

    /**
     * 计算物品的哈希值（用于缓存）
     * 考虑物品ID、显示名称和耐久度
     */
    private int calculateItemHash(ItemStack item) {
        if (item == null) return 0;
        // 基础哈希：物品类型 + ID
        String itemKey = ItemUtil.getItemKey(item);
        int result = item.getType().hashCode();
        result = 31 * result + itemKey.hashCode();

        if (item.hasItemMeta() && item.getItemMeta() != null) {
            // 考虑耐久度
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
                result = 31 * result + damageable.getDamage();
            }
            // 考虑显示名称（使用新的 Adventure API）
            var meta = item.getItemMeta();
            if (meta.hasDisplayName() && meta.displayName() != null) {
                String displayName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(meta.displayName());
                result = 31 * result + displayName.hashCode();
            }
        }
        return result;
    }

    /**
     * 通过物品查找对应的商店物品（使用缓存优化）
     */
    public ShopItem findShopItemByStack(ItemStack item) {
        if (item == null) return null;

        // 先用哈希快速查找
        int hash = calculateItemHash(item);
        ShopItem cached = itemCacheBySimilarity.get(hash);
        if (cached != null && cached.getDisplayItem() != null && cached.getDisplayItem().isSimilar(item)) {
            return cached;
        }

        // 哈希冲突或缓存未命中，遍历查找
        for (ShopItem shopItem : items.values()) {
            if (shopItem.getDisplayItem() != null && shopItem.getDisplayItem().isSimilar(item)) {
                return shopItem;
            }
        }
        return null;
    }

    public void reload() {
        load();
    }

    private void loadCategories() {
        ConfigurationSection section = plugin.getShopConfig().getShopCategories();
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection catSection = section.getConfigurationSection(key);
            if (catSection == null) continue;

            String name = catSection.getString("name", key);
            String icon = catSection.getString("icon", "minecraft:chest");
            int slot = catSection.getInt("slot", 10);

            // 加载子分类
            Map<String, SubCategory> subcategories = new LinkedHashMap<>();
            ConfigurationSection subSection = catSection.getConfigurationSection("subcategories");
            if (subSection != null) {
                for (String subKey : subSection.getKeys(false)) {
                    ConfigurationSection subCatSection = subSection.getConfigurationSection(subKey);
                    if (subCatSection == null) continue;
                    String subName = subCatSection.getString("name", subKey);
                    String subIcon = subCatSection.getString("icon", "minecraft:chest");
                    int subSlot = subCatSection.getInt("slot", 0);
                    SubCategory sub = new SubCategory(subKey, subName, subIcon, subSlot);
                    sub.setParentId(key);
                    subcategories.put(subKey, sub);
                }
            }

            categories.put(key, new ShopCategory(key, name, icon, slot, subcategories));
        }
    }

    /**
     * 从配置加载商品，进行增量更新
     * - 已有商品：更新价格、每日限额等配置，保留库存
     * - 新商品：创建并保存到数据库
     */
    private void loadItemsFromConfig() {
        ConfigurationSection section = plugin.getShopConfig().getShopItems();
        if (section == null) return;

        int newCount = 0;
        int updatedCount = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) continue;

            String itemKey = itemSection.getString("item");
            if (itemKey == null || itemKey.isEmpty()) continue;

            double buyPrice = itemSection.getDouble("buy-price", 0);
            double sellPrice = itemSection.getDouble("sell-price", 0);
            int buyPoints = itemSection.getInt("buy-points", 0);
            int stock = itemSection.getInt("stock", -1);
            String category = itemSection.getString("category", "misc");
            int slot = itemSection.getInt("slot", 0);
            int dailyLimit = itemSection.getInt("daily-limit", 0);
            int playerLimit = itemSection.getInt("player-limit", 0);

            // 加载 NBT 组件配置
            Map<String, String> components = ItemUtil.parseComponents(itemSection.get("components"));

            // 加载命令和条件
            java.util.List<String> commands = itemSection.getStringList("commands");
            java.util.List<String> conditions = itemSection.getStringList("conditions");
            boolean giveItem = itemSection.getBoolean("give-item", true);

            ShopItem existingItem = items.get(id);
            if (existingItem != null) {
                // 已有商品：更新配置（保留数据库中的库存）
                existingItem.setBuyPrice(buyPrice);
                existingItem.setSellPrice(sellPrice);
                existingItem.setBuyPoints(buyPoints);
                existingItem.setCategory(category);
                existingItem.setSlot(slot);
                existingItem.setDailyLimit(dailyLimit);
                existingItem.setPlayerLimit(playerLimit);
                existingItem.setComponents(components);
                existingItem.setCommands(commands);
                existingItem.setConditions(conditions);
                existingItem.setGiveItem(giveItem);
                // 库存保留数据库值，不覆盖
                // 更新显示物品（可能配置改了itemKey）
                ItemStack item = ItemUtil.createItemFromKey(plugin, itemKey);
                if (item != null) {
                    // 应用 NBT 组件
                    if (!components.isEmpty()) {
                        item = ItemUtil.applyComponents(item, components);
                    }
                    existingItem.setDisplayItem(item);
                }
                // 同步回数据库
                saveItem(existingItem);
                updatedCount++;
            } else {
                // 新商品：创建并保存
                ShopItem shopItem = new ShopItem(id, itemKey, buyPrice, sellPrice, buyPoints, stock, category, slot, dailyLimit, components);
                shopItem.setPlayerLimit(playerLimit);
                shopItem.setCommands(commands);
                shopItem.setConditions(conditions);
                shopItem.setGiveItem(giveItem);
                ItemStack item = ItemUtil.createItemFromKey(plugin, itemKey);
                if (item != null) {
                    // 应用 NBT 组件
                    if (!components.isEmpty()) {
                        item = ItemUtil.applyComponents(item, components);
                    }
                    shopItem.setDisplayItem(item);
                }
                items.put(id, shopItem);
                saveItem(shopItem);
                newCount++;
            }
        }

        if (newCount > 0 || updatedCount > 0) {
            plugin.getLogger().info("从配置同步: 新增 " + newCount + " 个商品, 更新 " + updatedCount + " 个商品");
        }
    }

    private void loadItemsFromDatabaseAsync(Runnable callback) {
        plugin.getDatabaseQueue().submit("loadShopItems", conn -> {
            // 只在数据库线程中查询数据
            List<Object[]> rawData = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM shop_items WHERE enabled = TRUE")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    rawData.add(new Object[]{
                        rs.getString("id"),
                        rs.getString("item_key"),
                        rs.getDouble("buy_price"),
                        rs.getDouble("sell_price"),
                        rs.getInt("buy_points"),
                        rs.getInt("stock"),
                        rs.getString("category"),
                        rs.getInt("slot"),
                        rs.getInt("daily_limit"),
                        rs.getInt("player_limit"),
                        rs.getString("components"),
                        rs.getString("commands"),
                        rs.getString("conditions"),
                        rs.getBoolean("give_item")
                    });
                }
            }
            return rawData;
        }, rawData -> {
            // 在主线程中创建物品（CE物品需要在主线程创建）
            int count = 0;
            for (Object[] data : rawData) {
                String id = (String) data[0];
                String itemKey = (String) data[1];
                double buyPrice = (Double) data[2];
                double sellPrice = (Double) data[3];
                int buyPoints = (Integer) data[4];
                int stock = (Integer) data[5];
                String category = (String) data[6];
                int slot = (Integer) data[7];
                int dailyLimit = (Integer) data[8];
                int playerLimit = data.length > 9 && data[9] != null ? (Integer) data[9] : 0;
                @SuppressWarnings("unchecked")
                Map<String, String> components = data.length > 10 && data[10] != null ?
                    parseComponentsFromJson((String) data[10]) : new HashMap<>();
                java.util.List<String> commands = data.length > 11 && data[11] != null ?
                    parseListFromJson((String) data[11]) : new java.util.ArrayList<>();
                java.util.List<String> conditions = data.length > 12 && data[12] != null ?
                    parseListFromJson((String) data[12]) : new java.util.ArrayList<>();
                boolean giveItem = data.length > 13 ? (Boolean) data[13] : true;

                ShopItem shopItem = new ShopItem(id, itemKey, buyPrice, sellPrice, buyPoints, stock, category, slot, dailyLimit, components);
                shopItem.setPlayerLimit(playerLimit);
                shopItem.setCommands(commands);
                shopItem.setConditions(conditions);
                shopItem.setGiveItem(giveItem);
                ItemStack item = ItemUtil.createItemFromKey(plugin, itemKey);
                if (item != null) {
                    // 应用 NBT 组件
                    if (!components.isEmpty()) {
                        item = ItemUtil.applyComponents(item, components);
                    }
                    shopItem.setDisplayItem(item);
                }
                items.put(id, shopItem);
                count++;
            }
            plugin.getLogger().info("从数据库加载了 " + count + " 个商品");

            // 执行回调（继续加载配置）
            if (callback != null) {
                callback.run();
            }
        }, error -> {
            plugin.getLogger().warning("从数据库加载商品失败: " + error.getMessage());
            // 即使失败也继续加载配置
            if (callback != null) {
                callback.run();
            }
        });
    }

    /**
     * 更新商品库存（管理界面使用）
     * @param itemId 商品ID
     * @param newStock 新库存
     * @param callback 回调函数，true表示成功（可选）
     */
    public void updateItemStock(String itemId, int newStock, java.util.function.Consumer<Boolean> callback) {
        // 先更新数据库，成功后更新内存
        plugin.getDatabaseQueue().submit("updateStock", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE shop_items SET stock = ? WHERE id = ?")) {
                ps.setInt(1, newStock);
                ps.setString(2, itemId);
                return ps.executeUpdate() > 0;
            }
        }, success -> {
            if (success) {
                ShopItem item = items.get(itemId);
                if (item != null) {
                    item.setStock(newStock);
                }
            }
            if (callback != null) {
                callback.accept(success);
            }
        }, error -> {
            plugin.getLogger().warning("更新库存失败 [" + itemId + "]: " + error.getMessage());
            if (callback != null) {
                callback.accept(false);
            }
        });
    }

    /**
     * 更新商品库存（无回调版本，向后兼容）
     */
    public void updateItemStock(String itemId, int newStock) {
        updateItemStock(itemId, newStock, null);
    }

    public void saveItem(ShopItem item) {
        plugin.getDatabaseQueue().submit("saveShopItem", conn -> {
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();

            // 将集合转换为 JSON 字符串
            String componentsJson = item.hasComponents() ? componentsToJson(item.getComponents()) : null;
            String commandsJson = item.hasCommands() ? listToJson(item.getCommands()) : null;
            String conditionsJson = item.hasConditions() ? listToJson(item.getConditions()) : null;

            String sql;
            if (isMySQL) {
                // MySQL/MariaDB 语法
                sql = "INSERT INTO shop_items (id, item_key, buy_price, sell_price, buy_points, stock, category, slot, enabled, daily_limit, player_limit, components, commands, conditions, give_item) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                      "ON DUPLICATE KEY UPDATE item_key=?, buy_price=?, sell_price=?, buy_points=?, stock=?, category=?, slot=?, enabled=?, daily_limit=?, player_limit=?, components=?, commands=?, conditions=?, give_item=?";
            } else {
                // H2 语法
                sql = "MERGE INTO shop_items (id, item_key, buy_price, sell_price, buy_points, stock, category, slot, enabled, daily_limit, player_limit, components, commands, conditions, give_item) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, item.getId());
                ps.setString(2, item.getItemKey());
                ps.setDouble(3, item.getBuyPrice());
                ps.setDouble(4, item.getSellPrice());
                ps.setInt(5, item.getBuyPoints());
                ps.setInt(6, item.getStock());
                ps.setString(7, item.getCategory());
                ps.setInt(8, item.getSlot());
                ps.setBoolean(9, item.isEnabled());
                ps.setInt(10, item.getDailyLimit());
                ps.setInt(11, item.getPlayerLimit());
                ps.setString(12, componentsJson);
                ps.setString(13, commandsJson);
                ps.setString(14, conditionsJson);
                ps.setBoolean(15, item.isGiveItem());

                if (isMySQL) {
                    ps.setString(16, item.getItemKey());
                    ps.setDouble(17, item.getBuyPrice());
                    ps.setDouble(18, item.getSellPrice());
                    ps.setInt(19, item.getBuyPoints());
                    ps.setInt(20, item.getStock());
                    ps.setString(21, item.getCategory());
                    ps.setInt(22, item.getSlot());
                    ps.setBoolean(23, item.isEnabled());
                    ps.setInt(24, item.getDailyLimit());
                    ps.setInt(25, item.getPlayerLimit());
                    ps.setString(26, componentsJson);
                    ps.setString(27, commandsJson);
                    ps.setString(28, conditionsJson);
                    ps.setBoolean(29, item.isGiveItem());
                }

                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 原子性扣减库存（防止超卖）
     * @param itemId 商品ID
     * @param amount 扣减数量
     * @param callback 回调函数，参数为实际扣减的数量（0表示失败/库存不足）
     */
    public void atomicReduceStock(String itemId, int amount, java.util.function.Consumer<Integer> callback) {
        plugin.getDatabaseQueue().submit("atomicReduceStock", conn -> {
            String selectSql = plugin.getDatabaseManager().isMySQL()
                    ? "SELECT stock FROM shop_items WHERE id = ? AND enabled = TRUE FOR UPDATE"
                    : "SELECT stock FROM shop_items WHERE id = ? AND enabled = TRUE";

            String updateSql = plugin.getDatabaseManager().isMySQL()
                    ? "UPDATE shop_items SET stock = stock - ? WHERE id = ? AND stock >= ?"
                    : "UPDATE shop_items SET stock = stock - ? WHERE id = ? AND stock >= ?";

            conn.setAutoCommit(false);
            try {
                // 1. 查询当前库存（MySQL使用FOR UPDATE锁定行）
                int currentStock;
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setString(1, itemId);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        conn.rollback();
                        return 0; // 商品不存在
                    }
                    currentStock = rs.getInt("stock");
                }

                // 无限库存检查
                if (currentStock < 0) {
                    conn.commit();
                    return amount; // 无限库存，直接成功
                }

                // 检查库存是否足够
                if (currentStock < amount) {
                    conn.rollback();
                    return 0; // 库存不足
                }

                // 2. 扣减库存（使用条件更新确保原子性）
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, amount);
                    ps.setString(2, itemId);
                    ps.setInt(3, amount); // 确保库存>=要扣减的数量
                    int updated = ps.executeUpdate();

                    if (updated == 0) {
                        conn.rollback();
                        return 0; // 扣减失败（可能并发导致）
                    }
                }

                conn.commit();

                // 从数据库刷新最新库存到内存（确保跨服一致性）
                refreshItemStockFromDatabase(conn, itemId);

                return amount;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }, callback, error -> {
            plugin.getLogger().warning("扣减库存失败: " + error.getMessage());
            callback.accept(0);
        });
    }

    /**
     * 原子性增加库存（用于出售回商店）
     * @param itemId 商品ID
     * @param amount 增加数量
     */
    public void atomicAddStock(String itemId, int amount) {
        plugin.getDatabaseQueue().submit("atomicAddStock", conn -> {
            String sql = "UPDATE shop_items SET stock = stock + ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, amount);
                ps.setString(2, itemId);
                ps.executeUpdate();
            }

            // 从数据库刷新最新库存到内存（确保跨服一致性）
            refreshItemStockFromDatabase(conn, itemId);
            return null;
        });
    }

    /**
     * 从数据库刷新指定商品的库存到内存（在同一连接中）
     */
    private void refreshItemStockFromDatabase(java.sql.Connection conn, String itemId) throws java.sql.SQLException {
        String selectSql = "SELECT stock FROM shop_items WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int latestStock = rs.getInt("stock");
                ShopItem item = items.get(itemId);
                if (item != null) {
                    item.setStock(latestStock);
                }
            }
        }
    }

    /**
     * 从数据库刷新所有库存到内存（用于跨服同步）
     * @param callback 回调函数，参数为成功刷新的数量
     */
    public void refreshAllStocksFromDatabase(java.util.function.Consumer<Integer> callback) {
        plugin.getDatabaseQueue().submit("refreshAllStocks", conn -> {
            String sql = "SELECT id, stock FROM shop_items";
            int refreshedCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String itemId = rs.getString("id");
                    int stock = rs.getInt("stock");
                    ShopItem item = items.get(itemId);
                    if (item != null) {
                        item.setStock(stock);
                        refreshedCount++;
                    }
                }
            }
            return refreshedCount;
        }, callback, error -> {
            plugin.getLogger().warning("刷新所有库存失败: " + error.getMessage());
            if (callback != null) {
                callback.accept(0);
            }
        });
    }

    /**
     * 从配置文件重新加载单个物品
     * @return 是否成功重置（配置文件中有该物品定义则返回true）
     */
    public boolean resetItemFromConfig(String id) {
        ConfigurationSection section = plugin.getShopConfig().getShopItems();
        if (section == null) return false;

        ConfigurationSection itemSection = section.getConfigurationSection(id);
        if (itemSection == null) return false;

        String itemKey = itemSection.getString("item");
        if (itemKey == null || itemKey.isEmpty()) return false;

        double buyPrice = itemSection.getDouble("buy-price", 0);
        double sellPrice = itemSection.getDouble("sell-price", 0);
        int buyPoints = itemSection.getInt("buy-points", 0);
        int stock = itemSection.getInt("stock", -1);
        String category = itemSection.getString("category", "misc");
        int slot = itemSection.getInt("slot", 0);
        int dailyLimit = itemSection.getInt("daily-limit", 0);
        int playerLimit = itemSection.getInt("player-limit", 0);

        // 移除旧的物品
        items.remove(id);

        // 加载 NBT 组件配置
        Map<String, String> components = ItemUtil.parseComponents(itemSection.get("components"));

        // 加载命令和条件
        java.util.List<String> commands = itemSection.getStringList("commands");
        java.util.List<String> conditions = itemSection.getStringList("conditions");
        boolean giveItem = itemSection.getBoolean("give-item", true);

        // 创建新物品
        ShopItem shopItem = new ShopItem(id, itemKey, buyPrice, sellPrice, buyPoints, stock, category, slot, dailyLimit, components);
        shopItem.setPlayerLimit(playerLimit);
        shopItem.setCommands(commands);
        shopItem.setConditions(conditions);
        shopItem.setGiveItem(giveItem);
        ItemStack item = ItemUtil.createItemFromKey(plugin, itemKey);
        if (item != null) {
            shopItem.setDisplayItem(item);
        }

        items.put(id, shopItem);

        // 保存到数据库（覆盖原有数据）
        saveItem(shopItem);

        // 重建缓存
        rebuildItemCache();

        return true;
    }

    public void deleteItem(String id) {
        items.remove(id);
        plugin.getDatabaseQueue().submit("deleteShopItem", conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM shop_items WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void logTransaction(UUID playerUuid, String playerName, String itemId, String itemKey, int amount, double price, String type) {
        if (!plugin.getShopConfig().isLogTransactions()) return;

        plugin.getDatabaseQueue().submit("logTransaction", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions (player_uuid, player_name, item_id, item_key, amount, price, type, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, itemId);
                ps.setString(4, itemKey);
                ps.setInt(5, amount);
                ps.setDouble(6, price);
                ps.setString(7, type);
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 获取玩家的交易记录（最近20次）
     */
    public void getPlayerTransactions(UUID playerUuid, java.util.function.Consumer<List<TransactionRecord>> callback) {
        plugin.getDatabaseQueue().submit("getTransactions", conn -> {
            List<TransactionRecord> records = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM transactions WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT 20")) {
                ps.setString(1, playerUuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    records.add(new TransactionRecord(
                        rs.getString("player_name"),
                        rs.getString("item_id"),
                        rs.getString("item_key"),
                        rs.getInt("amount"),
                        rs.getDouble("price"),
                        rs.getString("type"),
                        rs.getLong("timestamp")
                    ));
                }
            }
            return records;
        }, callback, error -> {
            plugin.getLogger().warning("查询交易记录失败: " + error.getMessage());
            callback.accept(new ArrayList<>());
        });
    }

    /**
     * 尝试增加玩家今日购买计数（原子操作）
     * 如果超过每日限额，则不会增加计数并返回 false
     *
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param amount 要增加的数量
     * @param dailyLimit 每日限额（如果为0表示无限制，直接返回true）
     * @param callback 回调函数，参数为是否成功（true=已增加计数，false=超过限额）
     */
    public void tryIncrementDailyBuyCount(UUID playerUuid, String itemId, int amount, int dailyLimit,
                                          java.util.function.Consumer<Boolean> callback) {
        // 无限制直接返回成功
        if (dailyLimit <= 0) {
            callback.accept(true);
            return;
        }

        String today = getTodayString();
        plugin.getDatabaseQueue().submit("tryIncrementDailyBuyCount", conn -> {
            conn.setAutoCommit(false);
            try {
                // 1. 查询当前记录（加锁防止并发）
                String selectSql = plugin.getDatabaseManager().isMySQL()
                        ? "SELECT buy_count, last_date FROM daily_limits WHERE player_uuid = ? AND item_id = ? FOR UPDATE"
                        : "SELECT buy_count, last_date FROM daily_limits WHERE player_uuid = ? AND item_id = ?";

                int currentCount = 0;
                String lastDate = null;
                boolean recordExists = false;

                try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                    selectPs.setString(1, playerUuid.toString());
                    selectPs.setString(2, itemId);
                    ResultSet rs = selectPs.executeQuery();
                    if (rs.next()) {
                        recordExists = true;
                        currentCount = rs.getInt("buy_count");
                        lastDate = rs.getString("last_date");
                    }
                }

                // 2. 计算新计数（考虑跨天）
                int newCount;
                if (recordExists && today.equals(lastDate)) {
                    // 同一天，正常累加
                    newCount = currentCount + amount;
                } else {
                    // 跨天或无记录，从 amount 开始
                    newCount = amount;
                }

                // 3. 检查是否超过限额
                if (newCount > dailyLimit) {
                    conn.rollback();
                    return false; // 超过限额
                }

                // 4. 更新计数
                // 根据数据库类型选择SQL语法
                boolean isMySQL = plugin.getDatabaseManager().isMySQL();
                String updateSql;
                if (isMySQL) {
                    updateSql = "INSERT INTO daily_limits (player_uuid, item_id, buy_count, last_date) VALUES (?, ?, ?, ?) " +
                              "ON DUPLICATE KEY UPDATE buy_count = ?, last_date = ?";
                } else {
                    // H2: 使用 MERGE INTO ... KEY(...) 语法支持 UPSERT
                    updateSql = "MERGE INTO daily_limits KEY(player_uuid, item_id) VALUES (?, ?, ?, ?)";
                }

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, itemId);
                    ps.setInt(3, newCount);
                    ps.setString(4, today);
                    if (isMySQL) {
                        ps.setInt(5, newCount);
                        ps.setString(6, today);
                    }

                    ps.executeUpdate();
                }

                conn.commit();
                return true; // 成功增加计数

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }, callback, error -> {
            plugin.getLogger().warning("增加每日购买计数失败: " + error.getMessage());
            callback.accept(false);
        });
    }

    private String getTodayString() {
        return java.time.LocalDate.now().toString();
    }

    /**
     * 检查玩家的终身购买限额
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param playerLimit 限制数量（0表示无限制）
     * @param callback 回调函数，参数为剩余可购买数量（-1表示无限制，0表示已达上限）
     */
    public void checkPlayerLimit(UUID playerUuid, String itemId, int playerLimit,
                                  java.util.function.Consumer<Integer> callback) {
        // 无限制直接返回 -1
        if (playerLimit <= 0) {
            callback.accept(-1);
            return;
        }

        plugin.getDatabaseQueue().submit("checkPlayerLimit", conn -> {
            String sql = "SELECT buy_count FROM player_item_limits WHERE player_uuid = ? AND item_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, itemId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int currentCount = rs.getInt("buy_count");
                    return Math.max(0, playerLimit - currentCount);
                } else {
                    // 无记录，返回完整限额
                    return playerLimit;
                }
            }
        }, callback, error -> {
            plugin.getLogger().warning("检查玩家购买限额失败: " + error.getMessage());
            callback.accept(0); // 出错时保守返回0
        });
    }

    /**
     * 增加玩家的终身购买计数
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param amount 增加数量
     * @param callback 回调函数，参数为是否成功（可选）
     */
    public void incrementPlayerLimit(UUID playerUuid, String itemId, int amount,
                                     java.util.function.Consumer<Boolean> callback) {
        plugin.getDatabaseQueue().submit("incrementPlayerLimit", conn -> {
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            String sql;
            if (isMySQL) {
                sql = "INSERT INTO player_item_limits (player_uuid, item_id, buy_count) VALUES (?, ?, ?) " +
                      "ON DUPLICATE KEY UPDATE buy_count = buy_count + ?";
            } else {
                sql = "MERGE INTO player_item_limits KEY(player_uuid, item_id) VALUES (?, ?, buy_count + ?)";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, itemId);
                if (isMySQL) {
                    ps.setInt(3, amount);
                    ps.setInt(4, amount);
                } else {
                    ps.setInt(3, amount);
                }
                ps.executeUpdate();
                return true;
            }
        }, success -> {
            if (callback != null) callback.accept(true);
        }, error -> {
            plugin.getLogger().warning("增加玩家购买计数失败: " + error.getMessage());
            if (callback != null) callback.accept(false);
        });
    }

    /**
     * 重置玩家的终身购买限额（管理命令用）
     * @param playerUuid 玩家UUID（null表示重置所有玩家）
     * @param itemId 物品ID（null表示重置所有物品）
     * @param callback 回调函数，参数为影响的记录数
     */
    public void resetPlayerLimit(UUID playerUuid, String itemId,
                                  java.util.function.Consumer<Integer> callback) {
        plugin.getDatabaseQueue().submit("resetPlayerLimit", conn -> {
            String sql;
            PreparedStatement ps;

            if (playerUuid != null && itemId != null) {
                // 重置特定玩家特定物品
                sql = "DELETE FROM player_item_limits WHERE player_uuid = ? AND item_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, playerUuid.toString());
                ps.setString(2, itemId);
            } else if (playerUuid != null) {
                // 重置特定玩家所有物品
                sql = "DELETE FROM player_item_limits WHERE player_uuid = ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, playerUuid.toString());
            } else if (itemId != null) {
                // 重置所有玩家特定物品
                sql = "DELETE FROM player_item_limits WHERE item_id = ?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, itemId);
            } else {
                // 重置所有记录（危险操作）
                sql = "DELETE FROM player_item_limits";
                ps = conn.prepareStatement(sql);
            }

            try {
                int affected = ps.executeUpdate();
                return affected;
            } finally {
                ps.close();
            }
        }, callback, error -> {
            plugin.getLogger().warning("重置玩家购买限额失败: " + error.getMessage());
            callback.accept(0);
        });
    }

    /**
     * 清理旧数据
     * @param days 清理多少天以前的数据
     * @param callback 回调函数，参数为清理的记录数
     */
    public void cleanupOldData(int days, java.util.function.Consumer<int[]> callback) {
        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        String cutoffDate = java.time.LocalDate.now().minusDays(days).toString();

        plugin.getDatabaseQueue().submit("cleanupOldData", conn -> {
            int deletedTransactions = 0;
            int deletedDailyLimits = 0;

            // 清理交易记录
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM transactions WHERE timestamp < ?")) {
                ps.setLong(1, cutoffTime);
                deletedTransactions = ps.executeUpdate();
            }

            // 清理过期的每日限额记录（last_date 早于 cutoffDate 的）
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM daily_limits WHERE last_date < ?")) {
                ps.setString(1, cutoffDate);
                deletedDailyLimits = ps.executeUpdate();
            }

            return new int[]{deletedTransactions, deletedDailyLimits};
        }, callback, error -> {
            plugin.getLogger().warning("清理旧数据失败: " + error.getMessage());
            callback.accept(new int[]{0, 0});
        });
    }

    /**
     * 交易记录数据类
     */
    public static class TransactionRecord {
        private final String playerName;
        private final String itemId;
        private final String itemKey;
        private final int amount;
        private final double price;
        private final String type;
        private final long timestamp;

        public TransactionRecord(String playerName, String itemId, String itemKey,
                                 int amount, double price, String type, long timestamp) {
            this.playerName = playerName;
            this.itemId = itemId;
            this.itemKey = itemKey;
            this.amount = amount;
            this.price = price;
            this.type = type;
            this.timestamp = timestamp;
        }

        public String getPlayerName() { return playerName; }
        public String getItemId() { return itemId; }
        public String getItemKey() { return itemKey; }
        public int getAmount() { return amount; }
        public double getPrice() { return price; }
        public String getType() { return type; }
        public long getTimestamp() { return timestamp; }

        private static final java.time.format.DateTimeFormatter TIME_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        public String getFormattedTime() {
            return java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .format(TIME_FORMATTER);
        }

        public boolean isBuy() { return "BUY".equals(type); }
        public boolean isSell() { return "SELL".equals(type); }
    }

    public ShopItem getItem(String id) {
        return items.get(id);
    }

    public java.util.Collection<ShopItem> getAllItems() {
        return items.values();
    }

    public List<ShopItem> getItemsByCategory(String category) {
        List<ShopItem> result = new ArrayList<>();
        for (ShopItem item : items.values()) {
            if (item.getCategory().equalsIgnoreCase(category) && item.isEnabled()) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 根据完整分类路径获取商品（支持 parent:child 子分类格式）
     * @param categoryPath 分类路径，如 "building" 或 "building:blocks"
     * @return 匹配的商品列表
     */
    public List<ShopItem> getItemsByCategoryPath(String categoryPath) {
        List<ShopItem> result = new ArrayList<>();
        String lowerPath = categoryPath.toLowerCase();

        for (ShopItem item : items.values()) {
            if (!item.isEnabled()) continue;
            String itemCat = item.getCategory().toLowerCase();

            if (lowerPath.contains(":")) {
                // 精确匹配子分类路径
                if (itemCat.equals(lowerPath)) {
                    result.add(item);
                }
            } else {
                // 父分类路径：匹配 "parent" 或 "parent:*"
                if (itemCat.equals(lowerPath) || itemCat.startsWith(lowerPath + ":")) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    public ShopCategory getCategory(String id) {
        return categories.get(id);
    }

    public Collection<ShopCategory> getAllCategories() {
        return categories.values();
    }

    /**
     * 获取指定父分类的子分类列表
     */
    public Collection<SubCategory> getSubcategories(String parentId) {
        ShopCategory cat = categories.get(parentId);
        if (cat == null) return Collections.emptyList();
        return cat.getSubcategories().values();
    }

    /**
     * 获取指定的子分类
     */
    public SubCategory getSubcategory(String parentId, String subId) {
        ShopCategory cat = categories.get(parentId);
        if (cat == null) return null;
        return cat.getSubcategories().get(subId);
    }

    /**
     * 重新加载所有物品的显示物品（用于延迟加载CE物品）
     */
    public void reloadDisplayItems() {
        int count = 0;
        for (ShopItem item : items.values()) {
            ItemStack displayItem = ItemUtil.createItemFromKey(plugin, item.getItemKey());
            if (displayItem != null) {
                item.setDisplayItem(displayItem);
                count++;
            }
        }
        plugin.getLogger().info("重新加载了 " + count + " 个物品的显示物品");
        rebuildItemCache();
    }

    public static class ShopCategory {
        private final String id;
        private final String name;
        private final String icon;
        private final int slot;
        private final Map<String, SubCategory> subcategories;

        public ShopCategory(String id, String name, String icon, int slot, Map<String, SubCategory> subcategories) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.slot = slot;
            this.subcategories = subcategories != null ? subcategories : new LinkedHashMap<>();
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getIcon() { return icon; }
        public int getSlot() { return slot; }
        public Map<String, SubCategory> getSubcategories() { return subcategories; }
        public boolean hasSubcategories() { return !subcategories.isEmpty(); }
    }

    public static class SubCategory {
        private final String id;
        private final String name;
        private final String icon;
        private final int slot;

        public SubCategory(String id, String name, String icon, int slot) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.slot = slot;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getIcon() { return icon; }
        public int getSlot() { return slot; }
        public String getFullPath() { return getParentId() + ":" + id; }
        // 子分类需要知道父分类ID，但这里简化处理，由调用方传入
        private String parentId;

        public void setParentId(String parentId) { this.parentId = parentId; }
        public String getParentId() { return parentId != null ? parentId : ""; }
    }

    /**
     * 导出商店数据到 backup_shop.yml
     * @param callback 回调函数，参数为导出的商品数量
     */
    public void exportToYaml(java.util.function.Consumer<Integer> callback) {
        plugin.getDatabaseQueue().submit("exportShopToYaml", conn -> {
            // 查询所有商品数据
            String sql = "SELECT id, item_key, buy_price, sell_price, stock, category, slot, daily_limit, enabled FROM shop_items ORDER BY id";
            List<ShopItemData> items = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new ShopItemData(
                        rs.getString("id"),
                        rs.getString("item_key"),
                        rs.getDouble("buy_price"),
                        rs.getDouble("sell_price"),
                        rs.getInt("stock"),
                        rs.getString("category"),
                        rs.getInt("slot"),
                        rs.getInt("daily_limit"),
                        rs.getBoolean("enabled")
                    ));
                }
            }

            // 创建 YAML 配置
            org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();

            // 添加头部注释
            yaml.options().header("FoliaShop - 商店数据备份\n导出时间: " + new java.util.Date() + "\n商品数量: " + items.size());

            // 写入配置
            yaml.set("enabled", true);
            yaml.set("title", "系统商店");
            yaml.set("allow-sell", plugin.getShopConfig().isAllowSell());
            yaml.set("sell-discount", plugin.getShopConfig().getSellDiscount());
            yaml.set("log-transactions", plugin.getShopConfig().isLogTransactions());
            yaml.set("refresh-interval", plugin.getShopConfig().getRefreshInterval());
            yaml.set("daily-buy-limit", plugin.getShopConfig().getDailyBuyLimit());

            // 写入分类
            ConfigurationSection categoriesSection = yaml.createSection("categories");
            for (ShopCategory category : categories.values()) {
                ConfigurationSection catSection = categoriesSection.createSection(category.getId());
                catSection.set("name", category.getName());
                catSection.set("icon", category.getIcon());
                catSection.set("slot", category.getSlot());
                // 写入子分类
                if (category.hasSubcategories()) {
                    ConfigurationSection subSection = catSection.createSection("subcategories");
                    for (SubCategory sub : category.getSubcategories().values()) {
                        ConfigurationSection subCatSection = subSection.createSection(sub.getId());
                        subCatSection.set("name", sub.getName());
                        subCatSection.set("icon", sub.getIcon());
                        subCatSection.set("slot", sub.getSlot());
                    }
                }
            }

            // 写入商品
            ConfigurationSection itemsSection = yaml.createSection("items");
            for (ShopItemData item : items) {
                ConfigurationSection itemSection = itemsSection.createSection(item.id);
                itemSection.set("item", item.itemKey);
                itemSection.set("buy-price", item.buyPrice);
                itemSection.set("sell-price", item.sellPrice);
                itemSection.set("stock", item.stock);
                itemSection.set("category", item.category);
                itemSection.set("slot", item.slot);
                if (item.dailyLimit > 0) {
                    itemSection.set("daily-limit", item.dailyLimit);
                }
                if (!item.enabled) {
                    itemSection.set("enabled", false);
                }
            }

            // 保存到文件
            java.io.File backupFile = new java.io.File(plugin.getDataFolder(), "backup_shop.yml");
            try {
                yaml.save(backupFile);
                plugin.getLogger().info("商店数据已导出到: " + backupFile.getAbsolutePath());
                return items.size();
            } catch (Exception e) {
                plugin.getLogger().warning("导出商店数据失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, callback, error -> {
            plugin.getLogger().warning("导出商店数据失败: " + error.getMessage());
            callback.accept(0);
        });
    }

    /**
     * 商店数据临时类（用于导出）
     */
    private static class ShopItemData {
        final String id;
        final String itemKey;
        final double buyPrice;
        final double sellPrice;
        final int stock;
        final String category;
        final int slot;
        final int dailyLimit;
        final boolean enabled;

        ShopItemData(String id, String itemKey, double buyPrice, double sellPrice,
                     int stock, String category, int slot, int dailyLimit, boolean enabled) {
            this.id = id;
            this.itemKey = itemKey;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.stock = stock;
            this.category = category;
            this.slot = slot;
            this.dailyLimit = dailyLimit;
            this.enabled = enabled;
        }
    }

    // ==================== NBT Components 辅助方法 ====================

    private static final Gson GSON = new Gson();

    /**
     * 将 components Map 转换为 JSON 字符串
     */
    private String componentsToJson(Map<String, String> components) {
        if (components == null || components.isEmpty()) {
            return null;
        }
        return GSON.toJson(components);
    }

    /**
     * 从 JSON 字符串解析 components Map
     */
    private Map<String, String> parseComponentsFromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return GSON.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 将 List 转换为 JSON 字符串
     */
    private String listToJson(java.util.List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return GSON.toJson(list);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JSON 字符串解析 List
     */
    private java.util.List<String> parseListFromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            return GSON.fromJson(json, new TypeToken<java.util.List<String>>() {}.getType());
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }
}
