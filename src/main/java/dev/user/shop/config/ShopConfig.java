package dev.user.shop.config;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class ShopConfig {

    private final FoliaShopPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration shopConfig;
    private FileConfiguration gachaConfig;

    // 数据库设置
    private String databaseType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int mysqlPoolSize;
    private String h2Filename;

    // 经济设置
    private boolean economyEnabled;
    private String currencyName;
    private String currencyFormat;

    // 商店设置
    private boolean shopEnabled;
    private String shopTitle;
    private boolean allowSell;
    private double sellDiscount;
    private boolean logTransactions;
    private int refreshInterval;
    private int dailyBuyLimit;

    // 系统回收设置
    private boolean sellSystemEnabled;
    private String sellSystemMode;
    private boolean addStockOnSell;
    private Map<String, Double> customSellItems;

    // 扭蛋设置
    private boolean gachaEnabled;

    // 展示实体设置
    private boolean displayEntityEnabled;
    private float displayEntityScale;
    private float displayEntityRotationY;
    private boolean displayEntityFacePlayer;
    private boolean displayEntityFloatingAnimation;
    private float displayEntityFloatAmplitude;
    private float displayEntityFloatSpeed;
    private float displayEntityHeightOffset;
    private int displayEntityAnimationPeriod;
    private float displayEntityViewRange;
    private float displayEntityShadowRadius;
    private float displayEntityShadowStrength;
    private boolean displayEntityGlowing;
    private String displayEntityGlowColor;
    private float displayEntityCleanupRange;
    private dev.user.shop.gacha.ParticleEffectConfig displayEntityParticleEffect;

    // GUI设置
    private Map<String, String> guiTitles;
    private Map<String, ItemConfig> guiDecorations;

    // 消息
    private Map<String, String> messages;

    public ShopConfig(FoliaShopPlugin plugin) {
        this.plugin = plugin;
        this.guiTitles = new HashMap<>();
        this.guiDecorations = new HashMap<>();
        this.messages = new HashMap<>();
        load();
    }

    /**
     * 加载或创建单独的配置文件
     */
    private FileConfiguration loadOrCreateConfig(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * 释放示例配置文件（仅首次运行，不覆盖已有文件）
     */
    private void saveExampleIfNotExists(String fileName) {
        File exampleFile = new File(plugin.getDataFolder(), fileName);
        if (!exampleFile.exists()) {
            try {
                plugin.saveResource(fileName, false);
                plugin.getLogger().info("已释放示例配置: " + fileName + "，可参考修改后作为拆分配置使用");
            } catch (IllegalArgumentException e) {
                // jar 中不存在该资源文件，忽略
            }
        }
    }


    /**
     * 扫描并合并额外配置文件（如 shop_xxx.yml, gacha_xxx.yml）
     * 将子文件的配置合并到基础配置中，重复 key 后加载的覆盖先加载的
     */
    private void mergeExtraConfigs(String prefix, FileConfiguration base) {
        File dataFolder = plugin.getDataFolder();
        File[] files = dataFolder.listFiles((dir, name) ->
            name.startsWith(prefix) && name.endsWith(".yml") && name.length() > prefix.length() + 4);

        if (files == null || files.length == 0) return;

        // 按文件名排序，保证加载顺序稳定
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));

        for (File file : files) {
            FileConfiguration extra = YamlConfiguration.loadConfiguration(file);
            String fileName = file.getName();

            // 记录合并前 base 中的 key 集合，用于检测覆盖
            java.util.Set<String> baseKeys = new java.util.HashSet<>(base.getKeys(true));

            // 合并叶子节点（跳过 section 本身）
            java.util.Set<String> mergedKeys = new java.util.HashSet<>();
            java.util.Set<String> overriddenKeys = new java.util.HashSet<>();
            for (String key : extra.getKeys(true)) {
                if (extra.isConfigurationSection(key)) continue;
                mergedKeys.add(key);
                if (baseKeys.contains(key)) {
                    overriddenKeys.add(key);
                }
                base.set(key, extra.get(key));
            }

            plugin.getLogger().info("已加载额外配置: " + fileName + " (" + mergedKeys.size() + " 项)");

            if (!overriddenKeys.isEmpty()) {
                plugin.getLogger().warning("配置 " + fileName + " 覆盖了以下已有配置项: " + String.join(", ", overriddenKeys));
            }
        }
    }

    /**
     * 从 shop.yml 或主配置获取值（优先使用 shop.yml）
     */
    private String getShopString(String path, String defaultValue) {
        if (shopConfig != null && shopConfig.contains(path)) {
            return shopConfig.getString(path, defaultValue);
        }
        return config.getString("shop." + path, defaultValue);
    }

    private boolean getShopBoolean(String path, boolean defaultValue) {
        if (shopConfig != null && shopConfig.contains(path)) {
            return shopConfig.getBoolean(path, defaultValue);
        }
        return config.getBoolean("shop." + path, defaultValue);
    }

    private double getShopDouble(String path, double defaultValue) {
        if (shopConfig != null && shopConfig.contains(path)) {
            return shopConfig.getDouble(path, defaultValue);
        }
        return config.getDouble("shop." + path, defaultValue);
    }

    private int getShopInt(String path, int defaultValue) {
        if (shopConfig != null && shopConfig.contains(path)) {
            return shopConfig.getInt(path, defaultValue);
        }
        return config.getInt("shop." + path, defaultValue);
    }

    private ConfigurationSection getShopSection(String path) {
        if (shopConfig != null) {
            ConfigurationSection section = shopConfig.getConfigurationSection(path);
            if (section != null) return section;
        }
        return config.getConfigurationSection("shop." + path);
    }

    /**
     * 从 gacha.yml 或主配置获取值（优先使用 gacha.yml）
     */
    private String getGachaString(String path, String defaultValue) {
        if (gachaConfig != null && gachaConfig.contains(path)) {
            return gachaConfig.getString(path, defaultValue);
        }
        return config.getString("gacha." + path, defaultValue);
    }

    private boolean getGachaBoolean(String path, boolean defaultValue) {
        if (gachaConfig != null && gachaConfig.contains(path)) {
            return gachaConfig.getBoolean(path, defaultValue);
        }
        return config.getBoolean("gacha." + path, defaultValue);
    }

    private double getGachaDouble(String path, double defaultValue) {
        if (gachaConfig != null && gachaConfig.contains(path)) {
            return gachaConfig.getDouble(path, defaultValue);
        }
        return config.getDouble("gacha." + path, defaultValue);
    }

    private int getGachaInt(String path, int defaultValue) {
        if (gachaConfig != null && gachaConfig.contains(path)) {
            return gachaConfig.getInt(path, defaultValue);
        }
        return config.getInt("gacha." + path, defaultValue);
    }

    private ConfigurationSection getGachaSection(String path) {
        if (gachaConfig != null) {
            ConfigurationSection section = gachaConfig.getConfigurationSection(path);
            if (section != null) return section;
        }
        return config.getConfigurationSection("gacha." + path);
    }

    public void load() {
        this.config = plugin.getConfig();

        // 首次运行时释放示例拆分配置文件
        saveExampleIfNotExists("shop_example.yml");
        saveExampleIfNotExists("gacha_example.yml");

        // 加载单独的配置文件（如果存在）
        this.shopConfig = loadOrCreateConfig("shop.yml");
        mergeExtraConfigs("shop_", this.shopConfig);
        this.gachaConfig = loadOrCreateConfig("gacha.yml");
        mergeExtraConfigs("gacha_", this.gachaConfig);

        // 数据库设置（仅从主配置读取）
        this.databaseType = config.getString("database.type", "h2");
        this.mysqlHost = config.getString("database.mysql.host", "localhost");
        this.mysqlPort = config.getInt("database.mysql.port", 3306);
        this.mysqlDatabase = config.getString("database.mysql.database", "foliashop");
        this.mysqlUsername = config.getString("database.mysql.username", "root");
        this.mysqlPassword = config.getString("database.mysql.password", "password");
        this.mysqlPoolSize = config.getInt("database.mysql.pool-size", 10);
        this.h2Filename = config.getString("database.h2.filename", "foliashop");

        // 经济设置（仅从主配置读取）
        this.economyEnabled = config.getBoolean("economy.enabled", true);
        this.currencyName = config.getString("economy.currency-name", "金币");
        this.currencyFormat = config.getString("economy.currency-format", "{amount} {currency}");

        // 商店设置（优先从 shop.yml 读取，shop.yml 中在根级别）
        this.shopEnabled = getShopBoolean("enabled", true);
        this.shopTitle = getShopString("title", "系统商店");
        this.allowSell = getShopBoolean("allow-sell", true);
        this.sellDiscount = getShopDouble("sell-discount", 0.7);
        this.logTransactions = getShopBoolean("log-transactions", true);
        this.refreshInterval = getShopInt("refresh-interval", 0);
        this.dailyBuyLimit = getShopInt("daily-buy-limit", 0);

        // 系统回收设置（优先从 shop.yml 读取，shop.yml 中在根级别）
        this.sellSystemEnabled = getShopBoolean("sell-system.enabled", true);
        this.sellSystemMode = getShopString("sell-system.mode", "SHOP_ONLY").toUpperCase();
        this.addStockOnSell = getShopBoolean("sell-system.add-stock-on-sell", false);
        this.customSellItems = new HashMap<>();
        ConfigurationSection customItemsSection = getShopSection("sell-system.custom-items");
        if (customItemsSection != null) {
            for (String itemKey : customItemsSection.getKeys(false)) {
                double price = customItemsSection.getDouble(itemKey, 0);
                if (price > 0) {
                    customSellItems.put(itemKey, price);
                }
            }
        }

        // 扭蛋设置（优先从 gacha.yml 读取，gacha.yml 中在根级别）
        this.gachaEnabled = getGachaBoolean("enabled", true);

        // 展示实体设置（gacha.yml 中 display-entity 在根级别）
        this.displayEntityEnabled = getGachaBoolean("display-entity.enabled", true);
        this.displayEntityScale = (float) getGachaDouble("display-entity.scale", 0.8);
        this.displayEntityRotationY = (float) getGachaDouble("display-entity.rotation-y", 45.0);
        this.displayEntityFacePlayer = getGachaBoolean("display-entity.face-player", false);
        this.displayEntityFloatingAnimation = getGachaBoolean("display-entity.floating-animation", true);
        this.displayEntityFloatAmplitude = (float) getGachaDouble("display-entity.float-amplitude", 0.1);
        this.displayEntityFloatSpeed = (float) getGachaDouble("display-entity.float-speed", 1.0);
        this.displayEntityHeightOffset = (float) getGachaDouble("display-entity.height-offset", 1.5);
        this.displayEntityAnimationPeriod = getGachaInt("display-entity.animation-period", 3);
        // 确保至少为1
        if (this.displayEntityAnimationPeriod < 1) {
            this.displayEntityAnimationPeriod = 1;
        }
        this.displayEntityViewRange = (float) getGachaDouble("display-entity.view-range", 32.0);
        this.displayEntityShadowRadius = (float) getGachaDouble("display-entity.shadow-radius", 0.3);
        this.displayEntityShadowStrength = (float) getGachaDouble("display-entity.shadow-strength", 0.3);
        this.displayEntityGlowing = getGachaBoolean("display-entity.glowing", false);
        this.displayEntityGlowColor = getGachaString("display-entity.glow-color", null);
        this.displayEntityCleanupRange = (float) getGachaDouble("display-entity.cleanup-range", 2.0);
        this.displayEntityParticleEffect = dev.user.shop.gacha.ParticleEffectConfig.fromConfig(
            getGachaSection("display-entity.particle-effect")
        );

        // GUI标题（仅从主配置读取）
        ConfigurationSection titlesSection = config.getConfigurationSection("gui.titles");
        if (titlesSection != null) {
            for (String key : titlesSection.getKeys(false)) {
                guiTitles.put(key, titlesSection.getString(key));
            }
        }

        // GUI装饰（仅从主配置读取）
        ConfigurationSection decorationSection = config.getConfigurationSection("gui.decoration");
        if (decorationSection != null) {
            for (String key : decorationSection.getKeys(false)) {
                ConfigurationSection itemSection = decorationSection.getConfigurationSection(key);
                if (itemSection != null) {
                    guiDecorations.put(key, new ItemConfig(
                        itemSection.getString("material", "minecraft:stone"),
                        itemSection.getString("name", "")
                    ));
                }
            }
        }

        // 消息（仅从主配置读取）
        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key));
            }
        }
        // 收集兑换消息默认值（兼容旧配置文件）
        setDefaultMessage("collect-empty", "<red>暂无可用的收集兑换任务");
        setDefaultMessage("collect-not-found", "<red>收集任务 '<yellow>{id}<red>' 不存在！");
        setDefaultMessage("collect-checking", "<yellow>正在检查收集进度...");
        setDefaultMessage("collect-incomplete", "<red>收集未完成！进度: <yellow>{collected}<gray>/<yellow>{required}");
        setDefaultMessage("collect-claim-success", "<green>✔ 成功领取收集奖励: <yellow>{name}");
        setDefaultMessage("collect-claim-fail", "<red>领取失败，条件不满足或已领取过");
    }

    private void setDefaultMessage(String key, String defaultValue) {
        messages.putIfAbsent(key, defaultValue);
    }

    // Getters
    public String getDatabaseType() { return databaseType; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public int getMysqlPoolSize() { return mysqlPoolSize; }
    public String getH2Filename() { return h2Filename; }

    public boolean isEconomyEnabled() { return economyEnabled; }
    public String getCurrencyName() { return currencyName; }
    public String getCurrencyFormat() { return currencyFormat; }
    public String formatCurrency(double amount) {
        return currencyFormat.replace("{amount}", String.format("%.2f", amount))
                           .replace("{currency}", currencyName);
    }

    public boolean isShopEnabled() { return shopEnabled; }
    public String getShopTitle() { return shopTitle; }
    public boolean isAllowSell() { return allowSell; }
    public double getSellDiscount() { return sellDiscount; }
    public boolean isLogTransactions() { return logTransactions; }
    public int getRefreshInterval() { return refreshInterval; }
    public int getDailyBuyLimit() { return dailyBuyLimit; }

    // 系统回收设置 Getter
    public boolean isSellSystemEnabled() { return sellSystemEnabled; }
    public String getSellSystemMode() { return sellSystemMode; }
    public boolean isAddStockOnSell() { return addStockOnSell; }
    public Map<String, Double> getCustomSellItems() { return Collections.unmodifiableMap(customSellItems); }
    public double getCustomSellPrice(String itemKey) { return customSellItems.getOrDefault(itemKey, 0.0); }

    public boolean isGachaEnabled() { return gachaEnabled; }

    // 展示实体设置 Getters
    public boolean isDisplayEntityEnabled() { return displayEntityEnabled; }
    public float getDisplayEntityScale() { return displayEntityScale; }
    public float getDisplayEntityRotationY() { return displayEntityRotationY; }
    public boolean isDisplayEntityFacePlayer() { return displayEntityFacePlayer; }
    public boolean isDisplayEntityFloatingAnimation() { return displayEntityFloatingAnimation; }
    public float getDisplayEntityFloatAmplitude() { return displayEntityFloatAmplitude; }
    public float getDisplayEntityFloatSpeed() { return displayEntityFloatSpeed; }
    public float getDisplayEntityHeightOffset() { return displayEntityHeightOffset; }
    public int getDisplayEntityAnimationPeriod() { return displayEntityAnimationPeriod; }
    public float getDisplayEntityViewRange() { return displayEntityViewRange; }
    public float getDisplayEntityShadowRadius() { return displayEntityShadowRadius; }
    public float getDisplayEntityShadowStrength() { return displayEntityShadowStrength; }
    public boolean isDisplayEntityGlowing() { return displayEntityGlowing; }
    public String getDisplayEntityGlowColor() { return displayEntityGlowColor; }
    public float getDisplayEntityCleanupRange() { return displayEntityCleanupRange; }
    public dev.user.shop.gacha.ParticleEffectConfig getDisplayEntityParticleEffect() { return displayEntityParticleEffect; }

    public String getGUITitle(String key) {
        // 提供有意义的默认值，当配置文件中没有时显示
        String title = guiTitles.getOrDefault(key, switch (key) {
            case "main-menu" -> "主菜单";
            case "shop" -> "系统商店";
            case "gacha" -> "扭蛋中心";
            case "gacha-animation" -> "扭蛋抽奖中...";
            case "gacha-ten-animation" -> "10连抽抽奖中...";
            case "gacha-ten-result" -> "10连抽结果";
            case "gacha-result" -> "抽奖结果";
            case "gacha-history" -> "抽奖记录";
            case "sell-menu" -> "出售物品";
            case "transaction-history" -> "交易记录";
            default -> "菜单";
        });
        return MessageUtil.convertMiniMessageToLegacy(title);
    }

    public ItemConfig getGUIDecoration(String key) {
        return guiDecorations.get(key);
    }

    /**
     * 获取原始消息（不替换占位符，不转换 MiniMessage）
     * 用于需要特殊处理占位符的场景（如广播中的可翻译物品）
     */
    public String getRawMessage(String key) {
        String msg = messages.get(key);
        if (msg == null) {
            msg = "";
        }
        // 只替换 prefix，保留其他占位符和 MiniMessage 标签
        return msg.replace("<prefix>", messages.getOrDefault("prefix", ""));
    }

    // ==================== Component API (New) ====================

    /**
     * 获取 Component 格式的消息（推荐新方法）
     * 支持 MiniMessage 解析和特殊占位符
     *
     * @param key 消息键
     * @param placeholders 占位符数组
     * @return Component 消息
     */
    public Component getComponent(String key, MessageUtil.Placeholder... placeholders) {
        String template = getRawMessage(key);
        if (template.isEmpty()) {
            return Component.empty();
        }
        return MessageUtil.buildMessage(template, placeholders);
    }

    /**
     * 获取带物品占位符的 Component 消息
     * 用于 purchase-success, sell-success 等包含物品名称的消息
     *
     * @param key 消息键
     * @param itemPlaceholder 物品占位符名称（如 "item"）
     * @param item 物品（用于提取名称或翻译键）
     * @param otherPlaceholders 其他普通占位符
     * @return Component 消息
     */
    public Component getItemMessage(String key, String itemPlaceholder, ItemStack item,
                                     Map<String, String> otherPlaceholders) {
        String template = getRawMessage(key);
        if (template.isEmpty()) {
            return Component.empty();
        }

        // 构建占位符列表
        java.util.List<MessageUtil.Placeholder> placeholders = new java.util.ArrayList<>();

        // 添加物品占位符
        placeholders.add(MessageUtil.Placeholder.item(itemPlaceholder, item));

        // 添加其他占位符
        if (otherPlaceholders != null) {
            for (Map.Entry<String, String> entry : otherPlaceholders.entrySet()) {
                placeholders.add(MessageUtil.Placeholder.text(entry.getKey(), entry.getValue()));
            }
        }

        return MessageUtil.buildMessage(template, placeholders.toArray(new MessageUtil.Placeholder[0]));
    }

    /**
     * 获取 Component 格式的消息（使用 Map 占位符，向后兼容）
     */
    public Component getComponent(String key, Map<String, String> placeholders) {
        String template = getRawMessage(key);
        if (template.isEmpty()) {
            return Component.empty();
        }
        return MessageUtil.buildMessage(template, placeholders);
    }

    public ConfigurationSection getShopCategories() {
        // 优先从 shop.yml 读取（shop.yml 中 categories 在根级别）
        if (shopConfig != null) {
            ConfigurationSection section = shopConfig.getConfigurationSection("categories");
            if (section != null) {
                plugin.getLogger().info("[ShopConfig] 从 shop.yml 加载 categories, 包含 " + section.getKeys(false).size() + " 个分类");
                return section;
            }
            plugin.getLogger().warning("[ShopConfig] shop.yml 中没有 categories 节点");
        } else {
            plugin.getLogger().warning("[ShopConfig] shopConfig 为 null");
        }
        // 回退到主配置的 shop.categories 路径
        plugin.getLogger().info("[ShopConfig] 回退到 config.yml 的 shop.categories");
        return config.getConfigurationSection("shop.categories");
    }

    public ConfigurationSection getShopItems() {
        // 优先从 shop.yml 读取（shop.yml 中 items 在根级别）
        if (shopConfig != null) {
            ConfigurationSection section = shopConfig.getConfigurationSection("items");
            if (section != null) {
                plugin.getLogger().info("[ShopConfig] 从 shop.yml 加载 items, 包含 " + section.getKeys(false).size() + " 个商品");
                return section;
            }
            plugin.getLogger().warning("[ShopConfig] shop.yml 中没有 items 节点");
        } else {
            plugin.getLogger().warning("[ShopConfig] shopConfig 为 null");
        }
        // 回退到主配置的 shop.items 路径
        plugin.getLogger().info("[ShopConfig] 回退到 config.yml 的 shop.items");
        return config.getConfigurationSection("shop.items");
    }

    public ConfigurationSection getGachaMachines() {
        // 优先从 gacha.yml 读取（gacha.yml 中 machines 在根级别）
        if (gachaConfig != null) {
            ConfigurationSection section = gachaConfig.getConfigurationSection("machines");
            if (section != null) {
                plugin.getLogger().info("[ShopConfig] 从 gacha.yml 加载 machines, 包含 " + section.getKeys(false).size() + " 个扭蛋机");
                return section;
            }
            plugin.getLogger().warning("[ShopConfig] gacha.yml 中没有 machines 节点");
        } else {
            plugin.getLogger().warning("[ShopConfig] gachaConfig 为 null");
        }
        // 回退到主配置的 gacha.machines 路径
        plugin.getLogger().info("[ShopConfig] 回退到 config.yml 的 gacha.machines");
        return config.getConfigurationSection("gacha.machines");
    }

    public ConfigurationSection getGachaCollections() {
        if (gachaConfig != null) {
            ConfigurationSection section = gachaConfig.getConfigurationSection("collections");
            if (section != null) return section;
        }
        return config.getConfigurationSection("gacha.collections");
    }

    public static class ItemConfig {
        private final String material;
        private final String name;

        public ItemConfig(String material, String name) {
            this.material = material;
            this.name = name;
        }

        public String getMaterial() { return material; }
        public String getName() { return name; }

        public Material getBukkitMaterial() {
            String mat = material.toUpperCase();
            if (mat.startsWith("MINECRAFT:")) {
                mat = mat.substring(10);
            }
            try {
                return Material.valueOf(mat);
            } catch (IllegalArgumentException e) {
                return Material.STONE;
            }
        }
    }
}
