package dev.user.shop;

import dev.user.shop.command.FoliaShopCommand;
import dev.user.shop.command.GachaCommand;
import dev.user.shop.command.ShopCommand;
import dev.user.shop.config.ShopConfig;
import dev.user.shop.database.BackupManager;
import dev.user.shop.database.DatabaseManager;
import dev.user.shop.database.DatabaseQueue;
import dev.user.shop.craftengine.CraftEnginePackManager;
import dev.user.shop.economy.EconomyManager;
import dev.user.shop.economy.ExchangeSessionManager;
import dev.user.shop.economy.PlayerPointsManager;
import dev.user.shop.enchant.AiyatsbusEnchantManager;
import dev.user.shop.shop.PurchaseManager;
import dev.user.shop.gacha.GachaBlockManager;
import dev.user.shop.gacha.GachaDisplayManager;
import dev.user.shop.gacha.GachaManager;
import dev.user.shop.gui.GUIManager;
import dev.user.shop.listener.BlockInteractListener;
import dev.user.shop.listener.ChunkListener;
import dev.user.shop.listener.ExchangeChatListener;
import dev.user.shop.listener.GlobalShopChatListener;
import dev.user.shop.listener.GlobalShopJoinListener;
import dev.user.shop.listener.GUIListener;
import dev.user.shop.shop.ShopManager;
import dev.user.shop.globalshop.GlobalShopManager;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaShopPlugin extends JavaPlugin {

    private static FoliaShopPlugin instance;

    private ShopConfig shopConfig;
    private DatabaseManager databaseManager;
    private DatabaseQueue databaseQueue;
    private EconomyManager economyManager;
    private PlayerPointsManager playerPointsManager;
    private AiyatsbusEnchantManager aiyatsbusEnchantManager;
    private CraftEnginePackManager craftEnginePackManager;
    private ExchangeSessionManager exchangeSessionManager;
    private volatile ShopManager shopManager;
    private volatile GachaManager gachaManager;
    private volatile GachaBlockManager gachaBlockManager;
    private volatile GachaDisplayManager gachaDisplayManager;
    private volatile GlobalShopManager globalShopManager;
    private PurchaseManager purchaseManager;
    private BackupManager backupManager;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();

        // 初始化配置
        this.shopConfig = new ShopConfig(this);

        // 初始化数据库
        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.init()) {
            getLogger().severe("数据库初始化失败，插件将禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 检查跨服配置
        if (shopConfig.getDatabaseType().equalsIgnoreCase("h2")) {
            getLogger().warning("============================================");
            getLogger().warning("当前使用 H2 数据库（本地文件模式）");
            getLogger().warning("H2 不支持多服务器同时访问！");
            getLogger().warning("如需跨服部署，请改用 MySQL 数据库");
            getLogger().warning("跨服使用 H2 会导致数据不一致和文件锁冲突");
            getLogger().warning("============================================");
        } else {
            getLogger().info("使用 MySQL 数据库，支持跨服部署");
        }

        // 概率显示警告
        getLogger().warning("============================================");
        getLogger().warning("扭蛋机概率显示已改为实际概率（归一化后）");
        getLogger().warning("注意：保底规则、稀有度判断仍使用配置中的相对概率");
        getLogger().warning("这不会影响抽奖逻辑，仅影响概率显示");
        getLogger().warning("============================================");

        // 初始化数据库队列
        this.databaseQueue = new DatabaseQueue(this);

        // 初始化经济系统
        this.economyManager = new EconomyManager(this);
        economyManager.init();

        // 初始化 PlayerPoints 点数系统（软依赖）
        this.playerPointsManager = new PlayerPointsManager(this);
        playerPointsManager.init();

        // 初始化 Aiyatsbus 更多附魔系统（软依赖，附魔书扭蛋用）
        this.aiyatsbusEnchantManager = new AiyatsbusEnchantManager(this);
        aiyatsbusEnchantManager.init();

        // 初始化 CraftEngine pack 抽取管理器（CE pack 扭蛋用，CE 为硬依赖）
        this.craftEnginePackManager = new CraftEnginePackManager(this);

        // 初始化购买事务管理器
        this.purchaseManager = new PurchaseManager(this);

        // 初始化兑换会话管理器
        this.exchangeSessionManager = new ExchangeSessionManager(this);

        // 初始化全球商店管理器
        this.globalShopManager = new GlobalShopManager(this);

        // 初始化备份管理器
        this.backupManager = new BackupManager(this);

        // 延迟初始化商店和扭蛋管理器（等待 CraftEngine 注册物品）
        getServer().getGlobalRegionScheduler().runDelayed(this, t -> {
            // 初始化商店管理器
            this.shopManager = new ShopManager(this);

            // 初始化扭蛋管理器
            this.gachaManager = new GachaManager(this);

            // 初始化扭蛋机方块绑定管理器
            this.gachaBlockManager = new GachaBlockManager(this);

            // 初始化扭蛋机展示实体管理器
            this.gachaDisplayManager = new GachaDisplayManager(this);
            this.gachaDisplayManager.loadAllDisplays();
            // unload+load 后残留实体无动画任务驱动（Folia 在 plugin disable 时取消所有绑定任务），
            // 主动重建已加载区块的展示实体以恢复动画；未加载区块由 onChunkLoad 重建
            this.gachaDisplayManager.rebuildLoadedDisplays();

            getLogger().info("商店和扭蛋系统已加载完成！");
        }, 2L);

        // 注册命令（提前注册，不影响命令使用）
        registerCommands();

        // 注册监听器
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(this), this);
        getServer().getPluginManager().registerEvents(new ExchangeChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GlobalShopChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GlobalShopJoinListener(this), this);

        getLogger().info("FoliaShop 插件已启用！");
    }

    @Override
    public void onDisable() {
        // 先关闭所有打开的GUI（包括取消扭蛋动画）
        GUIManager.closeAllGUIs();

        // 关闭兑换会话管理器
        if (exchangeSessionManager != null) {
            exchangeSessionManager.shutdown();
        }

        // 关闭全球商店管理器
        if (globalShopManager != null) {
            globalShopManager.shutdown();
        }

        // 关闭购买事务管理器
        if (purchaseManager != null) {
            purchaseManager.shutdown();
        }

        // 关闭经济队列（等待所有任务完成）
        if (economyManager != null) {
            economyManager.shutdown();
        }

        // 关闭数据库队列（等待所有任务完成）
        if (databaseQueue != null) {
            databaseQueue.shutdown();
        }

        // 关闭数据库连接池
        if (databaseManager != null) {
            databaseManager.close();
        }

        // 清理商店和扭蛋管理器
        if (shopManager != null) {
            shopManager = null;
        }
        if (gachaManager != null) {
            gachaManager = null;
        }

        getLogger().info("FoliaShop 插件已禁用！");
    }

    private void registerCommands() {
        FoliaShopCommand foliaShopCommand = new FoliaShopCommand(this);
        getCommand("foliashop").setExecutor(foliaShopCommand);
        getCommand("foliashop").setTabCompleter(foliaShopCommand);

        ShopCommand shopCommand = new ShopCommand(this);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        GachaCommand gachaCommand = new GachaCommand(this);
        getCommand("gacha").setExecutor(gachaCommand);
        getCommand("gacha").setTabCompleter(gachaCommand);
    }

    public void reload() {
        reloadConfig();
        shopConfig.load();
        if (shopManager != null) {
            shopManager.reload();
        }
        if (gachaManager != null) {
            gachaManager.reload();
        }
        if (gachaDisplayManager != null) {
            gachaDisplayManager.reload();
        }
        if (exchangeSessionManager != null) {
            exchangeSessionManager.shutdown();
        }
        this.exchangeSessionManager = new ExchangeSessionManager(this);
        if (globalShopManager != null) {
            globalShopManager.shutdown();
        }
        this.globalShopManager = new GlobalShopManager(this);
    }

    public static FoliaShopPlugin getInstance() {
        return instance;
    }

    public ShopConfig getShopConfig() {
        return shopConfig;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public DatabaseQueue getDatabaseQueue() {
        return databaseQueue;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public ShopManager getShopManager() {
        if (shopManager == null) {
            synchronized (this) {
                if (shopManager == null) {
                    getLogger().warning("ShopManager 未初始化，正在紧急初始化...");
                    this.shopManager = new ShopManager(this);
                }
            }
        }
        return shopManager;
    }

    public GachaManager getGachaManager() {
        if (gachaManager == null) {
            synchronized (this) {
                if (gachaManager == null) {
                    getLogger().warning("GachaManager 未初始化，正在紧急初始化...");
                    this.gachaManager = new GachaManager(this);
                }
            }
        }
        return gachaManager;
    }

    public GachaBlockManager getGachaBlockManager() {
        if (gachaBlockManager == null) {
            synchronized (this) {
                if (gachaBlockManager == null) {
                    getLogger().warning("GachaBlockManager 未初始化，正在紧急初始化...");
                    this.gachaBlockManager = new GachaBlockManager(this);
                }
            }
        }
        return gachaBlockManager;
    }

    public GachaDisplayManager getGachaDisplayManager() {
        if (gachaDisplayManager == null) {
            synchronized (this) {
                if (gachaDisplayManager == null) {
                    getLogger().warning("GachaDisplayManager 未初始化，正在紧急初始化...");
                    this.gachaDisplayManager = new GachaDisplayManager(this);
                }
            }
        }
        return gachaDisplayManager;
    }

    public PlayerPointsManager getPlayerPointsManager() {
        return playerPointsManager;
    }

    public AiyatsbusEnchantManager getAiyatsbusEnchantManager() {
        return aiyatsbusEnchantManager;
    }

    public CraftEnginePackManager getCraftEnginePackManager() {
        return craftEnginePackManager;
    }

    public PurchaseManager getPurchaseManager() {
        return purchaseManager;
    }

    public ExchangeSessionManager getExchangeSessionManager() {
        return exchangeSessionManager;
    }

    public GlobalShopManager getGlobalShopManager() {
        return globalShopManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }
}
