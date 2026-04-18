package dev.user.shop.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.user.shop.FoliaShopPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class DatabaseManager {

    private final FoliaShopPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        // 先关闭可能存在的旧连接（处理PlugManX重载情况）
        close();

        // 保存原始 ClassLoader
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // 使用插件的 ClassLoader 作为上下文 ClassLoader
            // 这样 HikariCP 就能找到打包的 H2 驱动
            Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());

            String type = plugin.getShopConfig().getDatabaseType();

            if (type.equalsIgnoreCase("mysql")) {
                initMySQL();
            } else {
                initH2();
            }

            // 创建表
            createTables();

            plugin.getLogger().info("数据库连接成功！类型: " + type);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // 恢复原始 ClassLoader
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void initMySQL() {
        HikariConfig config = new HikariConfig();
        String host = plugin.getShopConfig().getMysqlHost();
        int port = plugin.getShopConfig().getMysqlPort();
        String database = plugin.getShopConfig().getMysqlDatabase();
        String username = plugin.getShopConfig().getMysqlUsername();
        String password = plugin.getShopConfig().getMysqlPassword();
        int poolSize = plugin.getShopConfig().getMysqlPoolSize();

        // 手动注册 MySQL 驱动（libraries 由服务端加载，使用原始类名）
        try {
            Driver mysqlDriver = (Driver) Class.forName("com.mysql.cj.jdbc.Driver", true, plugin.getClass().getClassLoader()).getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(mysqlDriver));
        } catch (Exception e) {
            plugin.getLogger().warning("MySQL 驱动注册失败（可能已注册）: " + e.getMessage());
        }

        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        dataSource = new HikariDataSource(config);
    }

    private void initH2() {
        HikariConfig config = new HikariConfig();
        String filename = plugin.getShopConfig().getH2Filename();
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // 手动注册 H2 驱动（libraries 由服务端加载，使用原始类名）
        try {
            Driver h2Driver = (Driver) Class.forName("org.h2.Driver", true, plugin.getClass().getClassLoader()).getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(h2Driver));
        } catch (Exception e) {
            plugin.getLogger().warning("H2 驱动注册失败（可能已注册）: " + e.getMessage());
        }

        // DB_CLOSE_DELAY=0: 连接关闭时立即释放文件锁（对PlugMan重载很重要）
        // DB_CLOSE_ON_EXIT=FALSE: 防止VM关闭时自动关闭数据库
        // AUTO_RECONNECT=TRUE: 自动重连
        config.setJdbcUrl("jdbc:h2:" + new File(dataFolder, filename).getAbsolutePath() +
                ";AUTO_RECONNECT=TRUE;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setDriverClassName("org.h2.Driver");
        // 连接测试查询，确保连接可用
        config.setConnectionTestQuery("SELECT 1");

        dataSource = new HikariDataSource(config);
    }

    /**
     * JDBC 驱动包装类，用于在 Shadow 打包后正确注册驱动
     */
    private static class DriverShim implements Driver {
        private final Driver driver;

        DriverShim(Driver driver) {
            this.driver = driver;
        }

        @Override
        public java.sql.Connection connect(String url, java.util.Properties info) throws SQLException {
            return driver.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return driver.acceptsURL(url);
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException {
            return driver.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return driver.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return driver.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger(DriverShim.class.getName());
        }
    }

    /**
     * 清空商店商品表（用于重置数据库）
     */
    public void clearShopItems() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM shop_items");
        } catch (SQLException e) {
            plugin.getLogger().warning("清空商店商品表失败: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        boolean isMySQL = isMySQL();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 商店商品表
            String shopItemsTable = "CREATE TABLE IF NOT EXISTS shop_items (" +
                    "    id VARCHAR(64) PRIMARY KEY," +
                    "    item_key VARCHAR(128) NOT NULL," +
                    "    buy_price DECIMAL(18,2) DEFAULT 0," +
                    "    sell_price DECIMAL(18,2) DEFAULT 0," +
                    "    buy_points INT DEFAULT 0," +
                    "    stock INT DEFAULT -1," +
                    "    category VARCHAR(32) DEFAULT 'misc'," +
                    "    slot INT DEFAULT 0," +
                    "    enabled BOOLEAN DEFAULT TRUE," +
                    "    daily_limit INT DEFAULT 0," +
                    "    player_limit INT DEFAULT 0," +
                    "    components TEXT," +
                    "    commands TEXT," +
                    "    conditions TEXT," +
                    "    give_item BOOLEAN DEFAULT TRUE" +
                    ")";
            stmt.execute(shopItemsTable);

            // 数据库迁移：添加缺失的列
            migrateAddDailyLimitColumn(conn);
            migrateAddComponentsColumn(conn);
            migrateAddBuyPointsColumn(conn);
            migrateAddPlayerLimitColumn(conn);
            migrateAddCommandsColumn(conn);
            migrateAddConditionsColumn(conn);
            migrateAddGiveItemColumn(conn);

            // 交易记录表
            String idColumn = isMySQL ? "id BIGINT AUTO_INCREMENT PRIMARY KEY" : "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
            String transactionsTable = "CREATE TABLE IF NOT EXISTS transactions (" +
                    idColumn + "," +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    player_name VARCHAR(32) NOT NULL," +
                    "    item_id VARCHAR(64) NOT NULL," +
                    "    item_key VARCHAR(128) NOT NULL," +
                    "    amount INT NOT NULL," +
                    "    price DECIMAL(18,2) NOT NULL," +
                    "    type VARCHAR(16) NOT NULL," +
                    "    timestamp BIGINT NOT NULL" +
                    ")";
            stmt.execute(transactionsTable);

            // 扭蛋记录表
            String gachaRecordsTable = "CREATE TABLE IF NOT EXISTS gacha_records (" +
                    idColumn + "," +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    player_name VARCHAR(32) NOT NULL," +
                    "    machine_id VARCHAR(32) NOT NULL," +
                    "    reward_id VARCHAR(64) NOT NULL," +
                    "    item_key VARCHAR(128) NOT NULL," +
                    "    amount INT NOT NULL," +
                    "    cost DECIMAL(18,2) NOT NULL," +
                    "    timestamp BIGINT NOT NULL" +
                    ")";
            stmt.execute(gachaRecordsTable);

            // 玩家每日购买限制表（每个物品独立计数）
            String dailyLimitsTable = "CREATE TABLE IF NOT EXISTS daily_limits (" +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    item_id VARCHAR(64) NOT NULL," +
                    "    buy_count INT DEFAULT 0," +
                    "    last_date VARCHAR(10) NOT NULL," +
                    "    PRIMARY KEY (player_uuid, item_id)" +
                    ")";
            stmt.execute(dailyLimitsTable);

            // 玩家终身购买限制表（player-limit功能）
            String playerItemLimitsTable = "CREATE TABLE IF NOT EXISTS player_item_limits (" +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    item_id VARCHAR(64) NOT NULL," +
                    "    buy_count INT DEFAULT 0," +
                    "    PRIMARY KEY (player_uuid, item_id)" +
                    ")";
            stmt.execute(playerItemLimitsTable);

            // 分类每日出售金币限额表
            String categoryDailySellLimitsTable = "CREATE TABLE IF NOT EXISTS category_daily_sell_limits (" +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    category_id VARCHAR(64) NOT NULL," +
                    "    sell_amount DECIMAL(18,2) DEFAULT 0," +
                    "    last_date VARCHAR(10) NOT NULL," +
                    "    PRIMARY KEY (player_uuid, category_id)" +
                    ")";
            stmt.execute(categoryDailySellLimitsTable);

            // 扭蛋保底计数表（软保底，单段计数）
            String pityCounterTable = "CREATE TABLE IF NOT EXISTS gacha_pity (" +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    machine_id VARCHAR(32) NOT NULL," +
                    "    draw_count INT DEFAULT 0," +
                    "    last_draw_time BIGINT DEFAULT 0," +
                    "    PRIMARY KEY (player_uuid, machine_id)" +
                    ")";
            stmt.execute(pityCounterTable);

            // 扭蛋机方块绑定表
            String blockBindingIdColumn = isMySQL ? "id BIGINT AUTO_INCREMENT PRIMARY KEY" : "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
            String blockBindingUnique = isMySQL ? "UNIQUE KEY unique_block (world_uuid, block_x, block_y, block_z)" : "UNIQUE (world_uuid, block_x, block_y, block_z)";
            String blockBindingTable = "CREATE TABLE IF NOT EXISTS gacha_block_bindings (" +
                    blockBindingIdColumn + "," +
                    "    world_uuid VARCHAR(36) NOT NULL," +
                    "    block_x INT NOT NULL," +
                    "    block_y INT NOT NULL," +
                    "    block_z INT NOT NULL," +
                    "    machine_id VARCHAR(32) NOT NULL," +
                    "    created_by VARCHAR(36)," +
                    "    created_at BIGINT NOT NULL," +
                    "    display_entity_uuid VARCHAR(36)," +
                    "    outdated BOOLEAN DEFAULT FALSE," +
                    "    " + blockBindingUnique +
                    ")";
            stmt.execute(blockBindingTable);

            // 收集兑换领取记录表
            String collectionTable = "CREATE TABLE IF NOT EXISTS gacha_collections (" +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    collection_id VARCHAR(64) NOT NULL," +
                    "    claim_count INT DEFAULT 1," +
                    "    last_claim_time BIGINT NOT NULL," +
                    "    PRIMARY KEY (player_uuid, collection_id)" +
                    ")";
            stmt.execute(collectionTable);

            // ========== 全球商店表 ==========

            // 全球商店上架商品表
            String gsIdColumn = isMySQL ? "id BIGINT AUTO_INCREMENT PRIMARY KEY" : "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
            String globalShopListingsTable = "CREATE TABLE IF NOT EXISTS global_shop_listings (" +
                    gsIdColumn + "," +
                    "    seller_uuid VARCHAR(36) NOT NULL," +
                    "    seller_name VARCHAR(32) NOT NULL," +
                    "    item_data BLOB NOT NULL," +
                    "    item_key VARCHAR(128) NOT NULL," +
                    "    item_display_name VARCHAR(256)," +
                    "    amount INT NOT NULL DEFAULT 1," +
                    "    price DECIMAL(18,2) NOT NULL," +
                    "    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'," +
                    "    created_at BIGINT NOT NULL," +
                    "    expire_at BIGINT NOT NULL" +
                    ")";
            stmt.execute(globalShopListingsTable);

            // 全球商店待领取物品/收益表
            String globalShopReturnsTable = "CREATE TABLE IF NOT EXISTS global_shop_returns (" +
                    gsIdColumn + "," +
                    "    owner_uuid VARCHAR(36) NOT NULL," +
                    "    listing_id BIGINT," +
                    "    item_data BLOB," +
                    "    item_key VARCHAR(128)," +
                    "    item_display_name VARCHAR(256)," +
                    "    amount INT NOT NULL DEFAULT 0," +
                    "    earnings DECIMAL(18,2) NOT NULL DEFAULT 0," +
                    "    reason VARCHAR(16) NOT NULL DEFAULT 'SOLD'," +
                    "    created_at BIGINT NOT NULL," +
                    "    claimed BOOLEAN NOT NULL DEFAULT FALSE" +
                    ")";
            stmt.execute(globalShopReturnsTable);

            // 全球商店交易记录表
            String globalShopTransactionsTable = "CREATE TABLE IF NOT EXISTS global_shop_transactions (" +
                    gsIdColumn + "," +
                    "    buyer_uuid VARCHAR(36) NOT NULL," +
                    "    buyer_name VARCHAR(32) NOT NULL," +
                    "    seller_uuid VARCHAR(36) NOT NULL," +
                    "    seller_name VARCHAR(32) NOT NULL," +
                    "    listing_id BIGINT NOT NULL," +
                    "    item_key VARCHAR(128) NOT NULL," +
                    "    item_display_name VARCHAR(256)," +
                    "    amount INT NOT NULL," +
                    "    total_price DECIMAL(18,2) NOT NULL," +
                    "    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                    "    seller_earnings DECIMAL(18,2) NOT NULL DEFAULT 0," +
                    "    timestamp BIGINT NOT NULL" +
                    ")";
            stmt.execute(globalShopTransactionsTable);

            // 数据库迁移
            migrateAddDisplayEntityUuidColumn(conn);
            migrateAddOutdatedColumn(conn);
            migrateAddSourceColumn(conn);
            migratePityTableToSoftPity(conn);

            // 创建索引
            createIndexes(stmt, isMySQL);
        }
    }

    /**
     * 创建数据库索引
     * MySQL 不支持 IF NOT EXISTS，需要手动检查
     */
    private void createIndexes(Statement stmt, boolean isMySQL) throws SQLException {
        // 定义要创建的索引
        String[][] indexes = {
            {"idx_player", "transactions", "player_uuid"},
            {"idx_timestamp", "transactions", "timestamp"},
            {"idx_gacha_player", "gacha_records", "player_uuid"},
            {"idx_gacha_timestamp", "gacha_records", "timestamp"},
            {"idx_pity_player", "gacha_pity", "player_uuid"},
            {"idx_block_world", "gacha_block_bindings", "world_uuid"},
            {"idx_block_machine", "gacha_block_bindings", "machine_id"},
            {"idx_gacha_source", "gacha_records", "player_uuid, machine_id, source"},

            // 全球商店索引
            {"idx_gs_listing_seller", "global_shop_listings", "seller_uuid"},
            {"idx_gs_listing_status", "global_shop_listings", "status"},
            {"idx_gs_listing_expire", "global_shop_listings", "expire_at"},
            {"idx_gs_return_owner", "global_shop_returns", "owner_uuid"},
            {"idx_gs_return_claimed", "global_shop_returns", "owner_uuid, claimed"},
            {"idx_gs_return_listing", "global_shop_returns", "listing_id"},
            {"idx_gs_tx_buyer", "global_shop_transactions", "buyer_uuid"},
            {"idx_gs_tx_seller", "global_shop_transactions", "seller_uuid"},
            {"idx_gs_tx_time", "global_shop_transactions", "timestamp"}
        };

        for (String[] index : indexes) {
            String indexName = index[0];
            String tableName = index[1];
            String columnName = index[2];

            try {
                if (isMySQL) {
                    // MySQL: 直接执行，如果索引已存在会报错，捕获异常即可
                    stmt.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columnName + ")");
                } else {
                    // H2: 支持 IF NOT EXISTS
                    stmt.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + " (" + columnName + ")");
                }
            } catch (SQLException e) {
                // 检查是否是"索引已存在"的错误
                String msg = e.getMessage().toLowerCase();
                if (msg.contains("duplicate") || msg.contains("already exists") ||
                    msg.contains("idx_player") || msg.contains("idx_timestamp") ||
                    msg.contains("idx_gacha") || msg.contains("idx_pity") ||
                    msg.contains("idx_block")) {
                    // 索引已存在，忽略错误
                    plugin.getLogger().fine("索引 " + indexName + " 已存在，跳过创建");
                } else {
                    // 其他错误，重新抛出
                    throw e;
                }
            }
        }
    }

    /**
     * 数据库迁移：添加 daily_limit 列到 shop_items 表
     */
    private void migrateAddDailyLimitColumn(Connection conn) {
        try {
            // 检查列是否存在
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "SHOP_ITEMS", "DAILY_LIMIT")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }

            // 如果列不存在，添加它
            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 daily_limit 列到 shop_items 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE shop_items ADD COLUMN daily_limit INT DEFAULT 0");
                    plugin.getLogger().info("[数据库迁移] daily_limit 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 daily_limit 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：添加 components 列到 shop_items 表
     */
    private void migrateAddComponentsColumn(Connection conn) {
        try {
            // 检查列是否存在
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "SHOP_ITEMS", "COMPONENTS")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }

            // 如果列不存在，添加它
            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 components 列到 shop_items 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE shop_items ADD COLUMN components TEXT");
                    plugin.getLogger().info("[数据库迁移] components 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 components 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：添加 buy_points 列到 shop_items 表
     */
    private void migrateAddBuyPointsColumn(Connection conn) {
        try {
            // 检查列是否存在
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "SHOP_ITEMS", "BUY_POINTS")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }

            // 如果列不存在，添加它
            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 buy_points 列到 shop_items 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE shop_items ADD COLUMN buy_points INT DEFAULT 0");
                    plugin.getLogger().info("[数据库迁移] buy_points 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 buy_points 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：添加 player_limit 列到 shop_items 表
     */
    private void migrateAddPlayerLimitColumn(Connection conn) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "SHOP_ITEMS", "PLAYER_LIMIT")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }
            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 player_limit 列到 shop_items 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE shop_items ADD COLUMN player_limit INT DEFAULT 0");
                    plugin.getLogger().info("[数据库迁移] player_limit 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 player_limit 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：添加 commands 列到 shop_items 表
     */
    private void migrateAddCommandsColumn(Connection conn) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "SHOP_ITEMS", "COMMANDS")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }
            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 commands 列到 shop_items 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE shop_items ADD COLUMN commands TEXT");
                    plugin.getLogger().info("[数据库迁移] commands 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 commands 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：添加 conditions 列到 shop_items 表
     */
    private void migrateAddConditionsColumn(Connection conn) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "SHOP_ITEMS", "CONDITIONS")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }
            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 conditions 列到 shop_items 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE shop_items ADD COLUMN conditions TEXT");
                    plugin.getLogger().info("[数据库迁移] conditions 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 conditions 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：添加 give_item 列到 shop_items 表
     */
    private void migrateAddGiveItemColumn(Connection conn) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "SHOP_ITEMS", "GIVE_ITEM")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }
            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 give_item 列到 shop_items 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE shop_items ADD COLUMN give_item BOOLEAN DEFAULT TRUE");
                    plugin.getLogger().info("[数据库迁移] give_item 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 give_item 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：添加 display_entity_uuid 列到 gacha_block_bindings 表
     */
    private void migrateAddDisplayEntityUuidColumn(Connection conn) {
        try {
            // 检查列是否存在
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "GACHA_BLOCK_BINDINGS", "DISPLAY_ENTITY_UUID")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }

            // 如果列不存在，添加它
            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 display_entity_uuid 列到 gacha_block_bindings 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE gacha_block_bindings ADD COLUMN display_entity_uuid VARCHAR(36)");
                    plugin.getLogger().info("[数据库迁移] display_entity_uuid 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 display_entity_uuid 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：添加 outdated 列到 gacha_block_bindings 表
     */
    private void migrateAddOutdatedColumn(Connection conn) {
        try {
            // 检查列是否存在
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "GACHA_BLOCK_BINDINGS", "OUTDATED")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }

            // 如果列不存在，添加它
            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 outdated 列到 gacha_block_bindings 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE gacha_block_bindings ADD COLUMN outdated BOOLEAN DEFAULT FALSE");
                    plugin.getLogger().info("[数据库迁移] outdated 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 outdated 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：给 gacha_records 添加 source 列
     */
    private void migrateAddSourceColumn(Connection conn) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "GACHA_RECORDS", "SOURCE")) {
                if (columns.next()) {
                    columnExists = true;
                }
            }

            if (!columnExists) {
                plugin.getLogger().info("[数据库迁移] 正在添加 source 列到 gacha_records 表...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE gacha_records ADD COLUMN source VARCHAR(10) DEFAULT 'draw'");
                    plugin.getLogger().info("[数据库迁移] source 列添加成功");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 添加 source 列失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移：将多段硬保底表迁移到单段软保底表
     * 检测旧表结构（有 rule_hash 列），迁移数据后重建新表
     */
    private void migratePityTableToSoftPity(Connection conn) {
        try {
            // 检查表是否存在（尝试多种大小写，数据库可能不区分大小写）
            DatabaseMetaData metaData = conn.getMetaData();
            boolean tableExists = false;

            // 尝试不同的大小写形式
            String[] tableNames = {"GACHA_PITY", "gacha_pity", "Gacha_Pity"};
            for (String tableName : tableNames) {
                try (ResultSet tables = metaData.getTables(null, null, tableName, null)) {
                    if (tables.next()) {
                        tableExists = true;
                        break;
                    }
                }
            }

            if (!tableExists) {
                // 表不存在，是新数据库，无需迁移
                plugin.getLogger().info("[数据库迁移] gacha_pity 表不存在，无需迁移");
                return;
            }

            plugin.getLogger().info("[数据库迁移] 检测到 gacha_pity 表，检查表结构...");

            // 检查是否是旧表结构（有 rule_hash 列）
            // 尝试通过查询表结构来判断
            boolean hasRuleHash = false;
            try (ResultSet columns = metaData.getColumns(null, null, "GACHA_PITY", "RULE_HASH")) {
                if (columns.next()) {
                    hasRuleHash = true;
                }
            }

            // 如果上面没找到，尝试小写
            if (!hasRuleHash) {
                try (ResultSet columns = metaData.getColumns(null, null, "gacha_pity", "rule_hash")) {
                    if (columns.next()) {
                        hasRuleHash = true;
                    }
                }
            }

            // 再尝试直接查询表结构
            if (!hasRuleHash) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM gacha_pity WHERE 1=0")) {
                    ResultSetMetaData rsMeta = rs.getMetaData();
                    for (int i = 1; i <= rsMeta.getColumnCount(); i++) {
                        if (rsMeta.getColumnName(i).equalsIgnoreCase("rule_hash")) {
                            hasRuleHash = true;
                            break;
                        }
                    }
                } catch (SQLException e) {
                    // 表可能不存在，忽略错误
                }
            }

            if (!hasRuleHash) {
                // 没有 rule_hash 列，已经是新表结构
                plugin.getLogger().info("[数据库迁移] gacha_pity 表已经是新版结构，无需迁移");
                return;
            }

            plugin.getLogger().info("[数据库迁移] 检测到旧版保底表结构（有 rule_hash 列），开始迁移到软保底表...");

            plugin.getLogger().info("[数据库迁移] 检测到旧版保底表结构，开始迁移到软保底表...");

            // 备份旧数据：取每个 (player_uuid, machine_id) 的最大 draw_count
            Map<String, Map<String, Integer>> pityData = new HashMap<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT player_uuid, machine_id, MAX(draw_count) as max_count " +
                     "FROM gacha_pity GROUP BY player_uuid, machine_id")) {
                while (rs.next()) {
                    String playerUuid = rs.getString("player_uuid");
                    String machineId = rs.getString("machine_id");
                    int maxCount = rs.getInt("max_count");

                    pityData.computeIfAbsent(playerUuid, k -> new HashMap<>())
                            .put(machineId, maxCount);
                }
            }

            int migratedCount = pityData.values().stream()
                .mapToInt(Map::size)
                .sum();

            plugin.getLogger().info("[数据库迁移] 找到 " + migratedCount + " 条需要迁移的保底记录");

            // 删除旧表
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE gacha_pity");
                plugin.getLogger().info("[数据库迁移] 已删除旧版保底表");
            }

            // 创建新表
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            String newTableSql = "CREATE TABLE IF NOT EXISTS gacha_pity (" +
                    "    player_uuid VARCHAR(36) NOT NULL," +
                    "    machine_id VARCHAR(32) NOT NULL," +
                    "    draw_count INT DEFAULT 0," +
                    "    last_draw_time BIGINT DEFAULT 0," +
                    "    PRIMARY KEY (player_uuid, machine_id)" +
                    ")";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(newTableSql);
                plugin.getLogger().info("[数据库迁移] 已创建新版软保底表");
            }

            // 插入迁移后的数据
            long currentTime = System.currentTimeMillis();
            String insertSql = isMySQL
                ? "INSERT INTO gacha_pity (player_uuid, machine_id, draw_count, last_draw_time) VALUES (?, ?, ?, ?)"
                : "MERGE INTO gacha_pity KEY(player_uuid, machine_id) VALUES (?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Map.Entry<String, Map<String, Integer>> playerEntry : pityData.entrySet()) {
                    String playerUuid = playerEntry.getKey();
                    for (Map.Entry<String, Integer> machineEntry : playerEntry.getValue().entrySet()) {
                        String machineId = machineEntry.getKey();
                        int count = machineEntry.getValue();

                        ps.setString(1, playerUuid);
                        ps.setString(2, machineId);
                        ps.setInt(3, count);
                        ps.setLong(4, currentTime);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

            plugin.getLogger().info("[数据库迁移] 保底表迁移完成，共迁移 " + migratedCount + " 条记录");
            plugin.getLogger().info("[数据库迁移] 注意：旧版多段保底数据已合并为单段计数（取最高值）");

        } catch (SQLException e) {
            plugin.getLogger().warning("[数据库迁移] 保底表迁移失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("数据库连接池未初始化或已关闭");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            // 关闭连接池（DB_CLOSE_DELAY=0 确保连接立即释放）
            dataSource.close();

            // H2 数据库在关闭后需要一点时间完全释放文件锁
            // PlugMan 重载时需要确保文件锁被释放
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}

            // 强制触发GC以清理未关闭的资源引用
            System.gc();

            // 再等待一段时间确保文件锁被释放
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}

            // 尝试注销驱动（解决PlugMan重载时的驱动冲突）
            try {
                // 使用插件ClassLoader查找驱动类并注销
                ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
                java.util.Enumeration<Driver> drivers = DriverManager.getDrivers();
                while (drivers.hasMoreElements()) {
                    Driver driver = drivers.nextElement();
                    // 检查驱动是否来自此插件的ClassLoader
                    if (driver.getClass().getClassLoader() == pluginClassLoader) {
                        DriverManager.deregisterDriver(driver);
                    }
                }
            } catch (Exception ignored) {
                // 驱动可能不存在或未注册，忽略错误
            }
        }
    }

    public boolean isMySQL() {
        String dbType = plugin.getShopConfig().getDatabaseType().toLowerCase();
        return dbType.equals("mysql") || dbType.equals("mariadb");
    }
}
