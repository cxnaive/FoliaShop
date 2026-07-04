package dev.user.shop.gacha;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.enchant.AiyatsbusEnchantManager;
import dev.user.shop.util.ItemUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GachaManager {

    private final FoliaShopPlugin plugin;
    private final Map<String, GachaMachine> machines;
    private final Map<String, CollectionSet> collectionSets;

    public GachaManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
        this.machines = new ConcurrentHashMap<>();
        this.collectionSets = new ConcurrentHashMap<>();
        load();
    }

    public void load() {
        machines.clear();

        ConfigurationSection section = plugin.getShopConfig().getGachaMachines();
        if (section == null) {
            plugin.getLogger().warning("未配置扭蛋机");
            return;
        }

        for (String machineId : section.getKeys(false)) {
            ConfigurationSection machineSection = section.getConfigurationSection(machineId);
            if (machineSection == null) continue;

            String name = machineSection.getString("name", machineId);
            List<String> description = machineSection.getStringList("description");
            String icon = machineSection.getString("icon", "minecraft:chest");
            double cost = machineSection.getDouble("cost", 100.0);
            int animationDuration = machineSection.getInt("animation-duration", 3);
            // 10连抽动画时间，默认是单抽的3倍
            int animationDurationTen = machineSection.getInt("animation-duration-ten", animationDuration * 3);
            boolean broadcastRare = machineSection.getBoolean("broadcast-rare", true);
            double broadcastThreshold = machineSection.getDouble("broadcast-threshold", 0.05);
            // GUI位置（0-26），默认为0表示自动分配
            int slot = machineSection.getInt("slot", 0);
            // 是否启用，默认true
            boolean enabled = machineSection.getBoolean("enabled", true);

            // 加载软保底配置
            boolean pityEnabled = machineSection.getBoolean("pity.enabled", false);
            int pityStart = machineSection.getInt("pity.start", 70);
            int pityMax = machineSection.getInt("pity.max", 90);
            double pityTargetMaxProb = machineSection.getDouble("pity.target-max-probability", 0.05);

            // 跳过禁用的扭蛋机（仍然加载但标记为禁用）
            // enabled 字段已读取，后续通过 isEnabled() 过滤显示

            // 加载展示实体覆盖配置
            DisplayEntityConfig displayConfig = DisplayEntityConfig.fromConfig(
                machineSection.getConfigurationSection("display-entity")
            );

            // 加载 ICON NBT 组件配置
            Map<String, String> iconComponents = ItemUtil.parseComponents(
                machineSection.get("icon-components")
            );

            // 加载累抽自选配置
            ConfigurationSection milepostSection = machineSection.getConfigurationSection("milepost-pick");
            int milepostInterval = 0;
            int milepostMaxPicks = 0;
            if (milepostSection != null) {
                milepostInterval = milepostSection.getInt("interval", 0);
                milepostMaxPicks = milepostSection.getInt("max-picks", 0);
            }

            // 检测附魔书模式（mode: ENCHANT_BOOK，或配置了 enchant-pool 段）
            String mode = machineSection.getString("mode", "");
            ConfigurationSection poolSection = machineSection.getConfigurationSection("enchant-pool");
            boolean isBookMachine = "ENCHANT_BOOK".equalsIgnoreCase(mode) || poolSection != null;
            EnchantBookPool pool = null;
            if (isBookMachine) {
                pool = EnchantBookPool.fromConfig(poolSection);
                if (pool == null || pool.getRarities().isEmpty()) {
                    plugin.getLogger().warning("扭蛋机 '" + machineId + "' 配置为附魔书模式但缺少有效的 enchant-pool，已跳过");
                    continue;
                }
            }

            GachaMachine machine = new GachaMachine(
                machineId, name, description, icon, cost,
                animationDuration, animationDurationTen, broadcastRare, broadcastThreshold, slot,
                enabled, pityEnabled, pityStart, pityMax, pityTargetMaxProb,
                displayConfig, iconComponents, milepostInterval, milepostMaxPicks
            );

            // 附魔书模式：配置池 + 预生成动画样本书，跳过 rewards 解析与总概率检查
            if (isBookMachine) {
                machine.setBookMode(true);
                machine.setEnchantPool(pool);
                if (!plugin.getAiyatsbusEnchantManager().isEnabled()) {
                    plugin.getLogger().warning("扭蛋机 '" + machineId + "' 为附魔书模式，但 Aiyatsbus 插件未安装/未就绪，该扭蛋机已禁用");
                    machine.setEnabled(false);
                } else {
                    List<ItemStack> samples = plugin.getAiyatsbusEnchantManager().sampleAnimationBooks(pool, 16);
                    if (samples.isEmpty()) {
                        // 池里没有任何可用附魔：禁用机器，避免抽奖时动画越界 / 抽空
                        plugin.getLogger().warning("扭蛋机 '" + machineId + "' 的附魔池未匹配到任何可用附魔（检查 rarities/groups/exclude 配置），该扭蛋机已禁用");
                        machine.setEnabled(false);
                    } else {
                        machine.setBookAnimationItems(samples);
                    }
                }
                if (machines.containsKey(machineId)) {
                    plugin.getLogger().warning("扭蛋机 '" + machineId + "' 重复定义，后加载的配置将覆盖之前的");
                }
                machines.put(machineId, machine);
                continue;
            }

            // 加载奖品
            // 先尝试作为 ConfigurationSection 读取（支持 comments 和复杂结构）
            ConfigurationSection rewardsSection = machineSection.getConfigurationSection("rewards");
            List<Map<?, ?>> rewardsList = machineSection.getMapList("rewards");

            for (int i = 0; i < rewardsList.size(); i++) {
                Map<?, ?> rewardMap = rewardsList.get(i);
                String id = String.valueOf(rewardMap.get("id"));
                String itemKey = String.valueOf(rewardMap.get("item"));
                int amount = rewardMap.get("amount") instanceof Number ? ((Number) rewardMap.get("amount")).intValue() : 1;
                double probability = rewardMap.get("probability") instanceof Number ? ((Number) rewardMap.get("probability")).doubleValue() : 0.1;
                String displayName = rewardMap.get("display-name") != null ? String.valueOf(rewardMap.get("display-name")) : null;
                boolean broadcast = rewardMap.get("broadcast") instanceof Boolean ? (Boolean) rewardMap.get("broadcast") : false;

                // 加载奖品 NBT 组件配置
                // 直接从 rewardMap 获取 components（getMapList 已经把 YAML 列表项转为 Map）
                Map<String, String> rewardComponents = ItemUtil.parseComponents(rewardMap.get("components"));

                // 创建显示物品并应用 NBT 组件
                ItemStack item = ItemUtil.createItemFromKey(plugin, itemKey);
                if (item != null) {
                    if (!rewardComponents.isEmpty()) {
                        item = ItemUtil.applyComponents(item, rewardComponents);
                    }
                }

                GachaReward reward = new GachaReward(id, itemKey, amount, probability, displayName, broadcast, rewardComponents);
                if (item != null) {
                    reward.setDisplayItem(item);
                }

                // 检查奖品ID重复
                if (machine.getRewards().stream().anyMatch(r -> r.getId().equals(id))) {
                    plugin.getLogger().warning("扭蛋机 '" + machineId + "' 中奖品 '" + id + "' 重复定义，可能导致收集兑换等功能异常");
                }

                machine.addReward(reward);
            }

            if (machines.containsKey(machineId)) {
                plugin.getLogger().warning("扭蛋机 '" + machineId + "' 重复定义，后加载的配置将覆盖之前的");
            }
            machines.put(machineId, machine);

            // 检查总概率
            double totalProb = machine.getTotalProbability();
            if (Math.abs(totalProb - 1.0) > 0.001) {
                plugin.getLogger().warning("扭蛋机 '" + machineId + "' 的总概率为 " + String.format("%.8f", totalProb) + "，建议调整为 1.0");
            }
        }

        plugin.getLogger().info("已加载 " + machines.size() + " 个扭蛋机");

        // 加载收集兑换配置
        loadCollections();
    }

    /**
     * 加载收集兑换配置
     */
    private void loadCollections() {
        collectionSets.clear();

        ConfigurationSection collSection = plugin.getShopConfig().getGachaCollections();
        if (collSection == null) return;

        for (String collId : collSection.getKeys(false)) {
            ConfigurationSection cs = collSection.getConfigurationSection(collId);
            if (cs == null) continue;

            String name = cs.getString("name", collId);
            String icon = cs.getString("icon", null);
            List<String> description = cs.getStringList("description");

            // 解析 requires
            List<CollectionSet.RequireEntry> requires = new ArrayList<>();
            List<?> requiresList = cs.getList("requires");
            if (requiresList != null) {
                for (Object reqObj : requiresList) {
                    if (reqObj instanceof String str) {
                        // 简写格式: "machine:reward"
                        String[] parts = str.split(":", 2);
                        if (parts.length == 2) {
                            requires.add(new CollectionSet.RequireEntry(parts[0], parts[1]));
                        }
                    } else if (reqObj instanceof Map<?, ?> map) {
                        // 完整格式: {machine: "xxx", reward: "yyy"}
                        String machine = String.valueOf(map.get("machine"));
                        String reward = String.valueOf(map.get("reward"));
                        requires.add(new CollectionSet.RequireEntry(machine, reward));
                    }
                }
            }

            // 解析 reward
            ConfigurationSection rewardSection = cs.getConfigurationSection("reward");
            CollectionSet.CollectionReward reward = null;
            if (rewardSection != null) {
                String item = rewardSection.getString("item", "minecraft:air");
                int amount = rewardSection.getInt("amount", 1);
                Map<String, String> components = ItemUtil.parseComponents(rewardSection.get("components"));
                List<String> commands = rewardSection.getStringList("commands");
                boolean giveItem = rewardSection.getBoolean("give-item", true);
                reward = new CollectionSet.CollectionReward(item, amount, components, commands, giveItem);
            }

            boolean repeatable = cs.getBoolean("repeatable", false);
            int slot = cs.getInt("slot", 0);

            if (!requires.isEmpty() && reward != null) {
                if (collectionSets.containsKey(collId)) {
                    plugin.getLogger().warning("收集兑换 '" + collId + "' 重复定义，后加载的配置将覆盖之前的");
                }
                collectionSets.put(collId, new CollectionSet(collId, name, icon, description, requires, reward, repeatable, slot));
            } else {
                plugin.getLogger().warning("收集兑换 '" + collId + "' 配置不完整，跳过加载");
            }
        }

        plugin.getLogger().info("已加载 " + collectionSets.size() + " 个收集兑换");
    }

    public void reload() {
        load();
    }

    /**
     * 记录扭蛋抽奖
     */
    public void logGacha(UUID playerUuid, String playerName, String machineId, GachaReward reward, double cost) {
        logGacha(playerUuid, playerName, machineId, reward, cost, "draw");
    }

    /**
     * 记录扭蛋抽奖
     * @param source 来源: "draw"=正常抽奖, "pick"=自选获得
     */
    public void logGacha(UUID playerUuid, String playerName, String machineId, GachaReward reward, double cost, String source) {
        plugin.getDatabaseQueue().submit("logGacha", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO gacha_records (player_uuid, player_name, machine_id, reward_id, item_key, amount, cost, timestamp, source) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, machineId);
                ps.setString(4, reward.getId());
                ps.setString(5, reward.getItemKey());
                ps.setInt(6, reward.getAmount());
                ps.setDouble(7, cost);
                ps.setLong(8, System.currentTimeMillis());
                ps.setString(9, source);
                ps.executeUpdate();
            }
            return null;
        }, null, error -> {
            plugin.getLogger().warning("记录扭蛋抽奖失败: " + error.getMessage());
        });
    }

    /**
     * 执行10连抽（带软保底计算）
     * @param machine 扭蛋机
     * @param pityCount 当前保底计数
     * @param playerUuid 玩家UUID（用于查询历史记录）
     * @param callback 回调函数，返回结果
     */
    public void performTenGacha(GachaMachine machine, int pityCount, UUID playerUuid,
                                Consumer<TenGachaResult> callback) {
        // 附魔书模式：直接抽 10 本书包装为 GachaReward，跳过历史查询与软保底
        if (machine.isBookMode()) {
            List<GachaReward> rewards = drawBookRewards(machine, 10);
            callback.accept(new TenGachaResult(rewards, pityCount, 0, new HashMap<>()));
            return;
        }

        // 先查询每个奖品的历史记录，用于计算显示次数
        queryRewardHistories(playerUuid, machine.getId(), machine.getRewards(), histories -> {
            List<GachaReward> rewards = new ArrayList<>();
            Map<String, Integer> rewardDrawCounts = new HashMap<>();
            int finalPityCount = pityCount;
            int triggeredCount = 0;

            // 用于跟踪本次十连抽中每个奖品已经抽到的次数
            Map<String, Integer> rewardOccurrencesInBatch = new HashMap<>();

            for (int i = 0; i < 10; i++) {
                // 使用软保底抽奖
                GachaMachine.PityResult result = machine.rollWithPity(finalPityCount);
                GachaReward reward = result.reward();
                String rewardId = reward.getId();

                // 计算显示次数
                int occurrenceInBatch = rewardOccurrencesInBatch.getOrDefault(rewardId, 0);
                int drawCount;

                if (occurrenceInBatch == 0) {
                    // 第一次抽到该奖品，使用历史记录
                    int historyCount = histories.getOrDefault(rewardId, 0);
                    drawCount = historyCount + 1;  // +1 表示第N抽才抽到
                } else {
                    // 本次十连抽中已经抽到过，显示1抽（因为是本次中的）
                    drawCount = 1;
                }

                rewardDrawCounts.put(String.valueOf(i), drawCount);
                rewardOccurrencesInBatch.put(rewardId, occurrenceInBatch + 1);

                // 更新保底计数
                if (machine.isPityTarget(reward)) {
                    finalPityCount = 0;
                    if (result.isPityTriggered()) {
                        triggeredCount++;
                    }
                } else {
                    finalPityCount++;
                }

                rewards.add(reward);
            }

            // 立即更新数据库中的保底计数，确保后续抽奖基于最新状态
            batchUpdatePityCount(playerUuid, machine.getId(), finalPityCount);

            callback.accept(new TenGachaResult(rewards, finalPityCount, triggeredCount, rewardDrawCounts));
        });
    }

    /**
     * 查询玩家对每个奖品的历史抽奖次数（距离上次抽到的次数）
     */
    private void queryRewardHistories(UUID playerUuid, String machineId, List<GachaReward> rewards,
                                      Consumer<Map<String, Integer>> callback) {
        Map<String, Integer> histories = new HashMap<>();

        if (rewards.isEmpty()) {
            callback.accept(histories);
            return;
        }

        // 批量查询每个奖品的历史
        plugin.getDatabaseQueue().submit("queryRewardHistories", conn -> {
            for (GachaReward reward : rewards) {
                try {
                    // 查询上次抽到该奖品的时间（仅正常抽奖记录）
                    Long lastTime = null;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT timestamp FROM gacha_records " +
                            "WHERE player_uuid = ? AND machine_id = ? AND reward_id = ? AND source = 'draw' " +
                            "ORDER BY timestamp DESC, id DESC LIMIT 1")) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, machineId);
                        ps.setString(3, reward.getId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                lastTime = rs.getLong("timestamp");
                            }
                        }
                    }

                    // 统计从那时到现在抽了多少次（仅正常抽奖记录）
                    if (lastTime == null) {
                        // 第一次抽到，查询总次数
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT COUNT(*) as count FROM gacha_records " +
                                "WHERE player_uuid = ? AND machine_id = ? AND source = 'draw'")) {
                            ps.setString(1, playerUuid.toString());
                            ps.setString(2, machineId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    histories.put(reward.getId(), rs.getInt("count"));
                                }
                            }
                        }
                    } else {
                        // 有记录，统计间隔（仅正常抽奖记录）
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT COUNT(*) as count FROM gacha_records " +
                                "WHERE player_uuid = ? AND machine_id = ? AND timestamp > ? AND source = 'draw'")) {
                            ps.setString(1, playerUuid.toString());
                            ps.setString(2, machineId);
                            ps.setLong(3, lastTime);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    histories.put(reward.getId(), rs.getInt("count"));
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("查询奖品历史失败: " + reward.getId() + " - " + e.getMessage());
                }
            }
            return histories;
        }, callback, error -> {
            plugin.getLogger().warning("批量查询奖品历史失败: " + error.getMessage());
            callback.accept(histories);
        });
    }

    /**
     * 10连抽结果
     */
    public record TenGachaResult(List<GachaReward> rewards, int finalPityCount, int triggeredCount,
                                  Map<String, Integer> rewardDrawCounts) {
        /**
         * 获取指定奖品在指定位置的显示次数
         * @param rewardIndex 奖品在列表中的索引
         * @return 显示次数（距离上次抽到该奖品的次数+1）
         */
        public int getDrawCountForReward(int rewardIndex) {
            if (rewardDrawCounts == null) return 1;
            return rewardDrawCounts.getOrDefault(String.valueOf(rewardIndex), 1);
        }
    }

    /**
     * 为书模式扭蛋机抽取多本附魔书并包装为 GachaReward 列表（复用全部现有动画/结果/发放/记录链路）。
     * 个别抽取失败（返回 null）会被跳过；调用方应处理「一本都没抽到」的退款场景。
     */
    public List<GachaReward> drawBookRewards(GachaMachine machine, int count) {
        List<GachaReward> rewards = new ArrayList<>();
        if (!machine.isBookMode() || machine.getEnchantPool() == null) return rewards;
        AiyatsbusEnchantManager mgr = plugin.getAiyatsbusEnchantManager();
        // 多尝试几次以尽量避免个别 null 导致数量不足
        int attempts = 0;
        while (rewards.size() < count && attempts < count * 3) {
            attempts++;
            AiyatsbusEnchantManager.DrawnBook drawn = mgr.drawBook(machine.getEnchantPool());
            if (drawn == null) continue;
            rewards.add(wrapBookAsReward(drawn));
        }
        return rewards;
    }

    /**
     * 把一本抽到的附魔书包装成合成 GachaReward。
     * id 编码为 "book:<附魔id>:<等级>"，供 logGacha 存库与历史 GUI 重建；
     * probability 设为该品质档的实际中选率，使现有颜色/百分比/广播逻辑自动生效。
     */
    private GachaReward wrapBookAsReward(AiyatsbusEnchantManager.DrawnBook drawn) {
        String id = "book:" + drawn.enchId + ":" + drawn.level;
        GachaReward reward = new GachaReward(
            id,
            "minecraft:enchanted_book",
            1,
            drawn.tierProbability,
            drawn.displayName,
            false
        );
        reward.setDisplayItem(drawn.book.clone());
        return reward;
    }

    public GachaMachine getMachine(String id) {
        return machines.get(id);
    }

    public Collection<GachaMachine> getAllMachines() {
        return machines.values();
    }

    /**
     * 获取所有已启用的扭蛋机
     */
    public Collection<GachaMachine> getEnabledMachines() {
        return machines.values().stream()
            .filter(GachaMachine::isEnabled)
            .collect(java.util.stream.Collectors.toList());
    }

    public boolean hasMachine(String id) {
        return machines.containsKey(id);
    }

    // ==================== 收集兑换相关 ====================

    /**
     * 发放收集兑换奖励（在主线程调用）
     */
    public void giveCollectionReward(org.bukkit.entity.Player player, CollectionSet collSet) {
        CollectionSet.CollectionReward reward = collSet.getReward();
        if (reward.isGiveItem()) {
            org.bukkit.inventory.ItemStack rewardItem = dev.user.shop.util.ItemUtil.createItemFromKey(plugin, reward.getItem());
            if (rewardItem != null) {
                rewardItem.setAmount(reward.getAmount());
                if (reward.hasComponents()) {
                    rewardItem = dev.user.shop.util.ItemUtil.applyComponents(rewardItem, reward.getComponents());
                }
                java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(rewardItem);
                for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }

        if (reward.hasCommands()) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                for (String cmd : reward.getCommands()) {
                    String parsed = cmd.replace("{player}", player.getName());
                    try {
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), parsed);
                    } catch (Exception e) {
                        plugin.getLogger().warning("执行收集奖励命令失败: " + parsed + " - " + e.getMessage());
                    }
                }
            });
        }
    }

    public Collection<CollectionSet> getAllCollections() {
        return collectionSets.values();
    }

    public CollectionSet getCollection(String id) {
        return collectionSets.get(id);
    }

    public boolean hasCollections() {
        return !collectionSets.isEmpty();
    }

    /**
     * 查询玩家的收集进度
     * @param callback Map<machineId, Set<rewardId>> 玩家在各扭蛋机中已抽到的奖品
     */
    public void getCollectionProgress(UUID playerUuid, CollectionSet collSet,
                                       Consumer<Map<String, Set<String>>> callback) {
        // 收集所有需要的 (machineId, rewardId) 对
        Set<String> machineIds = new HashSet<>();
        for (CollectionSet.RequireEntry req : collSet.getRequires()) {
            machineIds.add(req.getMachineId());
        }

        plugin.getDatabaseQueue().submit("getCollectionProgress", conn -> {
            Map<String, Set<String>> result = new HashMap<>();

            for (String machineId : machineIds) {
                Set<String> rewardIds = new HashSet<>();
                // 检查是否已领取过（repeatable 时需要查 last_claim_time）
                long lastClaimTime = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_claim_time FROM gacha_collections WHERE player_uuid = ? AND collection_id = ?")) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, collSet.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            lastClaimTime = rs.getLong("last_claim_time");
                        }
                    }
                }

                // 查询该扭蛋机下玩家抽到过的所有 reward_id
                String sql;
                if (lastClaimTime > 0 && collSet.isRepeatable()) {
                    // repeatable: 只统计上次领取之后的记录
                    sql = "SELECT DISTINCT reward_id FROM gacha_records WHERE player_uuid = ? AND machine_id = ? AND timestamp > ?";
                } else {
                    sql = "SELECT DISTINCT reward_id FROM gacha_records WHERE player_uuid = ? AND machine_id = ?";
                }

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, machineId);
                    if (lastClaimTime > 0 && collSet.isRepeatable()) {
                        ps.setLong(3, lastClaimTime);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rewardIds.add(rs.getString("reward_id"));
                        }
                    }
                }
                result.put(machineId, rewardIds);
            }
            return result;
        }, callback, error -> {
            plugin.getLogger().warning("查询收集进度失败: " + error.getMessage());
            callback.accept(new HashMap<>());
        });
    }

    /**
     * 检查玩家是否已领取过该收集奖励
     */
    public void hasClaimedCollection(UUID playerUuid, String collectionId, Consumer<Boolean> callback) {
        plugin.getDatabaseQueue().submit("hasClaimedCollection", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM gacha_collections WHERE player_uuid = ? AND collection_id = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, collectionId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }, callback, error -> {
            plugin.getLogger().warning("查询收集领取状态失败: " + error.getMessage());
            callback.accept(false);
        });
    }

    /**
     * 领取收集奖励
     * @return true=领取成功, false=已领取或不满足条件
     */
    public void claimCollection(UUID playerUuid, String collectionId, Consumer<Boolean> callback) {
        CollectionSet collSet = collectionSets.get(collectionId);
        if (collSet == null) {
            callback.accept(false);
            return;
        }

        plugin.getDatabaseQueue().submit("claimCollection", conn -> {
            // 1. 检查是否已领取（非 repeatable）
            if (!collSet.isRepeatable()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT 1 FROM gacha_collections WHERE player_uuid = ? AND collection_id = ?")) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, collectionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return false; // 已领取
                        }
                    }
                }
            }

            // 2. 查询收集进度并验证
            Set<String> machineIds = new HashSet<>();
            for (CollectionSet.RequireEntry req : collSet.getRequires()) {
                machineIds.add(req.getMachineId());
            }

            Map<String, Set<String>> playerRewards = new HashMap<>();
            long lastClaimTime = 0;

            // 获取上次领取时间
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT last_claim_time FROM gacha_collections WHERE player_uuid = ? AND collection_id = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, collectionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        lastClaimTime = rs.getLong("last_claim_time");
                    }
                }
            }

            for (String machineId : machineIds) {
                Set<String> rewardIds = new HashSet<>();
                String sql;
                if (lastClaimTime > 0 && collSet.isRepeatable()) {
                    sql = "SELECT DISTINCT reward_id FROM gacha_records WHERE player_uuid = ? AND machine_id = ? AND timestamp > ?";
                } else {
                    sql = "SELECT DISTINCT reward_id FROM gacha_records WHERE player_uuid = ? AND machine_id = ?";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, machineId);
                    if (lastClaimTime > 0 && collSet.isRepeatable()) {
                        ps.setLong(3, lastClaimTime);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rewardIds.add(rs.getString("reward_id"));
                        }
                    }
                }
                playerRewards.put(machineId, rewardIds);
            }

            // 3. 验证是否满足条件
            if (!collSet.isComplete(playerRewards)) {
                return false;
            }

            // 4. 记录领取
            long now = System.currentTimeMillis();
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            if (isMySQL) {
                String sql = "INSERT INTO gacha_collections (player_uuid, collection_id, claim_count, last_claim_time) " +
                             "VALUES (?, ?, 1, ?) " +
                             "ON DUPLICATE KEY UPDATE claim_count = claim_count + 1, last_claim_time = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, collectionId);
                    ps.setLong(3, now);
                    ps.setLong(4, now);
                    ps.executeUpdate();
                }
            } else {
                // H2: 先尝试 UPDATE，无匹配行时 INSERT（原子语义，避免 TOCTOU）
                String updateSql = "UPDATE gacha_collections SET claim_count = claim_count + 1, last_claim_time = ? WHERE player_uuid = ? AND collection_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setLong(1, now);
                    ps.setString(2, playerUuid.toString());
                    ps.setString(3, collectionId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        try (PreparedStatement ps2 = conn.prepareStatement(
                                "INSERT INTO gacha_collections (player_uuid, collection_id, claim_count, last_claim_time) VALUES (?, ?, 1, ?)")) {
                            ps2.setString(1, playerUuid.toString());
                            ps2.setString(2, collectionId);
                            ps2.setLong(3, now);
                            ps2.executeUpdate();
                        }
                    }
                }
            }

            return true;
        }, callback, error -> {
            plugin.getLogger().warning("领取收集奖励失败: " + error.getMessage());
            callback.accept(false);
        });
    }

    /**
     * 获取玩家的保底计数
     * @return 当前保底计数，如果没有记录返回0
     */
    public void getPityCount(UUID playerUuid, String machineId, Consumer<Integer> callback) {
        plugin.getDatabaseQueue().submit("getPityCount", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT draw_count FROM gacha_pity WHERE player_uuid = ? AND machine_id = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, machineId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("draw_count");
                    }
                }
            }
            return 0;
        }, callback, error -> {
            plugin.getLogger().warning("获取保底计数失败: " + error.getMessage());
            callback.accept(0);
        });
    }

    /**
     * 更新保底计数（单抽逻辑）
     * @param isPityTarget 是否抽中了保底目标奖品
     */
    public void updatePityCount(UUID playerUuid, String machineId, boolean isPityTarget) {
        plugin.getDatabaseQueue().submit("updatePityCount", conn -> {
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            long currentTime = System.currentTimeMillis();

            if (isPityTarget) {
                // 重置计数
                if (isMySQL) {
                    String sql = "INSERT INTO gacha_pity (player_uuid, machine_id, draw_count, last_draw_time) " +
                                 "VALUES (?, ?, 0, ?) " +
                                 "ON DUPLICATE KEY UPDATE draw_count = 0, last_draw_time = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, machineId);
                        ps.setLong(3, currentTime);
                        ps.setLong(4, currentTime);
                        ps.executeUpdate();
                    }
                } else {
                    String sql = "MERGE INTO gacha_pity KEY(player_uuid, machine_id) " +
                                 "VALUES (?, ?, 0, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, machineId);
                        ps.setLong(3, currentTime);
                        ps.executeUpdate();
                    }
                }
            } else {
                // 计数+1
                if (isMySQL) {
                    String sql = "INSERT INTO gacha_pity (player_uuid, machine_id, draw_count, last_draw_time) " +
                                 "VALUES (?, ?, 1, ?) " +
                                 "ON DUPLICATE KEY UPDATE draw_count = draw_count + 1, last_draw_time = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, machineId);
                        ps.setLong(3, currentTime);
                        ps.setLong(4, currentTime);
                        ps.executeUpdate();
                    }
                } else {
                    // H2: 先查询再更新
                    int currentCount = 0;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT draw_count FROM gacha_pity WHERE player_uuid = ? AND machine_id = ?")) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, machineId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                currentCount = rs.getInt("draw_count");
                            }
                        }
                    }

                    String sql = "MERGE INTO gacha_pity KEY(player_uuid, machine_id) " +
                                 "VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, machineId);
                        ps.setInt(3, currentCount + 1);
                        ps.setLong(4, currentTime);
                        ps.executeUpdate();
                    }
                }
            }
            return null;
        });
    }

    /**
     * 批量更新保底计数（用于10连抽）
     * @param finalPityCount 最终保底计数
     */
    public void batchUpdatePityCount(UUID playerUuid, String machineId, int finalPityCount) {
        plugin.getDatabaseQueue().submit("batchUpdatePityCount", conn -> {
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            long currentTime = System.currentTimeMillis();

            if (isMySQL) {
                String sql = "INSERT INTO gacha_pity (player_uuid, machine_id, draw_count, last_draw_time) " +
                             "VALUES (?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE draw_count = ?, last_draw_time = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, machineId);
                    ps.setInt(3, finalPityCount);
                    ps.setLong(4, currentTime);
                    ps.setInt(5, finalPityCount);
                    ps.setLong(6, currentTime);
                    ps.executeUpdate();
                }
            } else {
                String sql = "MERGE INTO gacha_pity KEY(player_uuid, machine_id) " +
                             "VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, machineId);
                    ps.setInt(3, finalPityCount);
                    ps.setLong(4, currentTime);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    /**
     * 获取玩家的抽奖记录（最近20次）
     */
    public void getPlayerGachaRecords(UUID playerUuid, Consumer<List<GachaRecord>> callback) {
        plugin.getDatabaseQueue().submit("getGachaRecords", conn -> {
            List<GachaRecord> records = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM gacha_records WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT 20")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String sourceVal;
                        try {
                            sourceVal = rs.getString("source");
                        } catch (SQLException e) {
                            sourceVal = "draw";
                        }
                        records.add(new GachaRecord(
                            rs.getString("player_name"),
                            rs.getString("machine_id"),
                            rs.getString("reward_id"),
                            rs.getString("item_key"),
                            rs.getInt("amount"),
                            rs.getDouble("cost"),
                            rs.getLong("timestamp"),
                            sourceVal
                        ));
                    }
                }
            }
            return records;
        }, callback, error -> {
            plugin.getLogger().warning("查询抽奖记录失败: " + error.getMessage());
            callback.accept(new ArrayList<>());
        });
    }

    /**
     * 抽奖记录数据类
     */
    public static class GachaRecord {
        private final String playerName;
        private final String machineId;
        private final String rewardId;
        private final String itemKey;
        private final int amount;
        private final double cost;
        private final long timestamp;
        private final String source;

        public GachaRecord(String playerName, String machineId, String rewardId, String itemKey,
                          int amount, double cost, long timestamp) {
            this(playerName, machineId, rewardId, itemKey, amount, cost, timestamp, "draw");
        }

        public GachaRecord(String playerName, String machineId, String rewardId, String itemKey,
                          int amount, double cost, long timestamp, String source) {
            this.playerName = playerName;
            this.machineId = machineId;
            this.rewardId = rewardId;
            this.itemKey = itemKey;
            this.amount = amount;
            this.cost = cost;
            this.timestamp = timestamp;
            this.source = source != null ? source : "draw";
        }

        public String getPlayerName() { return playerName; }
        public String getMachineId() { return machineId; }
        public String getRewardId() { return rewardId; }
        public String getItemKey() { return itemKey; }
        public int getAmount() { return amount; }
        public double getCost() { return cost; }
        public long getTimestamp() { return timestamp; }
        public String getSource() { return source; }
        public boolean isPick() { return "pick".equals(source); }
    }

    /**
     * 查询距离上次抽到指定奖品已经抽了多少次
     * 如果是第一次抽到，返回该玩家在该扭蛋机的总抽奖次数
     * @param playerUuid 玩家UUID
     * @param machineId 扭蛋机ID
     * @param rewardId 奖品ID
     * @param callback 回调函数，参数为次数
     */
    public void getDrawsSinceLastReward(UUID playerUuid, String machineId, String rewardId,
                                        java.util.function.Consumer<Integer> callback) {
        plugin.getDatabaseQueue().submit("getDrawsSinceLastReward", conn -> {
            // 1. 查询上次抽到该奖品的时间（仅正常抽奖记录）
            Long lastTime = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT timestamp FROM gacha_records " +
                    "WHERE player_uuid = ? AND machine_id = ? AND reward_id = ? AND source = 'draw' " +
                    "ORDER BY timestamp DESC, id DESC LIMIT 1")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, machineId);
                ps.setString(3, rewardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        lastTime = rs.getLong("timestamp");
                    }
                }
            }

            // 2. 统计次数（仅正常抽奖记录）
            String countSql;
            if (lastTime == null) {
                // 第一次抽到：统计该玩家在该扭蛋机的总抽奖次数
                countSql = "SELECT COUNT(*) as count FROM gacha_records " +
                          "WHERE player_uuid = ? AND machine_id = ? AND source = 'draw'";
                try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, machineId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("count");
                        }
                    }
                }
            } else {
                // 有记录：统计从那时到现在抽了多少次（任何奖品，仅正常抽奖）
                countSql = "SELECT COUNT(*) as count FROM gacha_records " +
                          "WHERE player_uuid = ? AND machine_id = ? AND timestamp > ? AND source = 'draw'";
                try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, machineId);
                    ps.setLong(3, lastTime);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("count");
                        }
                    }
                }
            }
            return 0;
        }, callback, error -> {
            plugin.getLogger().warning("查询抽奖次数失败: " + error.getMessage());
            callback.accept(0);
        });
    }

    /**
     * 查询玩家抽中某个奖品的统计信息
     * @param playerUuid 玩家UUID（null表示查询所有玩家）
     * @param machineId 扭蛋机ID
     * @param rewardId 奖品ID
     * @param callback 回调函数，参数为 [总抽奖次数, 抽中次数, 平均花费次数]
     */
    public void getRewardStats(UUID playerUuid, String machineId, String rewardId,
                               java.util.function.Consumer<StatsResult> callback) {
        plugin.getDatabaseQueue().submit("getRewardStats", conn -> {
            int totalDraws = 0;
            int hitCount = 0;

            // 1. 查询总抽奖次数（仅正常抽奖）
            String totalSql = playerUuid == null
                ? "SELECT COUNT(*) as count FROM gacha_records WHERE machine_id = ? AND source = 'draw'"
                : "SELECT COUNT(*) as count FROM gacha_records WHERE player_uuid = ? AND machine_id = ? AND source = 'draw'";
            try (PreparedStatement ps = conn.prepareStatement(totalSql)) {
                if (playerUuid == null) {
                    ps.setString(1, machineId);
                } else {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, machineId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalDraws = rs.getInt("count");
                    }
                }
            }

            // 2. 查询抽中该奖品的次数（仅正常抽奖）
            String hitSql = playerUuid == null
                ? "SELECT COUNT(*) as count FROM gacha_records WHERE machine_id = ? AND reward_id = ? AND source = 'draw'"
                : "SELECT COUNT(*) as count FROM gacha_records WHERE player_uuid = ? AND machine_id = ? AND reward_id = ? AND source = 'draw'";
            try (PreparedStatement ps = conn.prepareStatement(hitSql)) {
                if (playerUuid == null) {
                    ps.setString(1, machineId);
                    ps.setString(2, rewardId);
                } else {
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, machineId);
                    ps.setString(3, rewardId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        hitCount = rs.getInt("count");
                    }
                }
            }

            return new StatsResult(totalDraws, hitCount);
        }, callback, error -> {
            plugin.getLogger().warning("查询奖品统计失败: " + error.getMessage());
            callback.accept(new StatsResult(0, 0));
        });
    }

    /**
     * 统计结果数据类
     */
    public record StatsResult(int totalDraws, int hitCount) {
        /**
         * 获取平均花费次数（总抽奖次数 / 抽中次数）
         * @return 平均次数，如果未抽中返回 -1
         */
        public double getAverageDraws() {
            if (hitCount == 0) return -1;
            return (double) totalDraws / hitCount;
        }

        /**
         * 获取格式化后的统计信息
         */
        public String getFormattedStats() {
            if (hitCount == 0) {
                return "§c暂无抽中记录";
            }
            double avg = getAverageDraws();
            return String.format("§7总抽奖: §e%d §7次 | 抽中: §e%d §7次 | 平均: §e%.2f §7次/个",
                totalDraws, hitCount, avg);
        }
    }

    /**
     * 清理旧的抽奖记录
     * @param days 清理多少天以前的数据
     * @param callback 回调函数，参数为删除的记录数
     */
    public void cleanupOldRecords(int days, java.util.function.Consumer<Integer> callback) {
        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);

        plugin.getDatabaseQueue().submit("cleanupGachaRecords", conn -> {
            int deleted = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM gacha_records WHERE timestamp < ?")) {
                ps.setLong(1, cutoffTime);
                deleted = ps.executeUpdate();
            }
            return deleted;
        }, callback, error -> {
            plugin.getLogger().warning("清理抽奖记录失败: " + error.getMessage());
            callback.accept(0);
        });
    }

    // =============================
    // 累抽自选相关方法
    // =============================

    /**
     * 查询玩家的累抽自选进度
     * @param playerUuid 玩家UUID
     * @param machineId 扭蛋机ID
     * @param callback 回调函数，参数为 MilepostInfo
     */
    public void getMilepostProgress(UUID playerUuid, String machineId, Consumer<MilepostInfo> callback) {
        GachaMachine machine = machines.get(machineId);
        if (machine == null || !machine.isMilepostEnabled()) {
            callback.accept(new MilepostInfo(0, 0, 0, machine != null ? machine.getMilepostInterval() : 0, machine != null ? machine.getMilepostMaxPicks() : 0));
            return;
        }

        int interval = machine.getMilepostInterval();
        int maxPicks = machine.getMilepostMaxPicks();

        plugin.getDatabaseQueue().submit("getMilepostProgress", conn -> {
            int totalDraws = 0;
            int usedPicks = 0;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT source, COUNT(*) as cnt FROM gacha_records " +
                    "WHERE player_uuid = ? AND machine_id = ? GROUP BY source")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, machineId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String source = rs.getString("source");
                        int cnt = rs.getInt("cnt");
                        if ("draw".equals(source)) {
                            totalDraws = cnt;
                        } else if ("pick".equals(source)) {
                            usedPicks = cnt;
                        }
                    }
                }
            }

            int earnedPicks = totalDraws / interval;
            int cappedEarned = maxPicks > 0 ? Math.min(earnedPicks, maxPicks) : earnedPicks;
            int availablePicks = Math.max(0, cappedEarned - usedPicks);

            return new MilepostInfo(totalDraws, availablePicks, usedPicks, interval, maxPicks);
        }, callback, error -> {
            plugin.getLogger().warning("查询累抽自选进度失败: " + error.getMessage());
            callback.accept(new MilepostInfo(0, 0, 0, interval, maxPicks));
        });
    }

    /**
     * 使用一次自选机会
     * @param playerUuid 玩家UUID
     * @param machineId 扭蛋机ID
     * @param reward 选择的奖品
     * @param playerName 玩家名称
     * @param callback 回调函数，参数为是否成功
     */
    public void usePick(UUID playerUuid, String machineId, GachaReward reward, String playerName, Consumer<Boolean> callback) {
        GachaMachine machine = machines.get(machineId);
        if (machine == null || !machine.isMilepostEnabled()) {
            callback.accept(false);
            return;
        }

        int interval = machine.getMilepostInterval();
        int maxPicks = machine.getMilepostMaxPicks();

        plugin.getDatabaseQueue().submit("usePick", conn -> {
            // 1. 查询当前状态
            int totalDraws = 0;
            int usedPicks = 0;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT source, COUNT(*) as cnt FROM gacha_records " +
                    "WHERE player_uuid = ? AND machine_id = ? GROUP BY source")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, machineId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String source = rs.getString("source");
                        int cnt = rs.getInt("cnt");
                        if ("draw".equals(source)) {
                            totalDraws = cnt;
                        } else if ("pick".equals(source)) {
                            usedPicks = cnt;
                        }
                    }
                }
            }

            // 2. 计算可用次数
            int earnedPicks = totalDraws / interval;
            int cappedEarned = maxPicks > 0 ? Math.min(earnedPicks, maxPicks) : earnedPicks;
            int availablePicks = Math.max(0, cappedEarned - usedPicks);

            if (availablePicks <= 0) {
                return false;
            }

            // 3. 记录自选结果
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO gacha_records (player_uuid, player_name, machine_id, reward_id, item_key, amount, cost, timestamp, source) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, machineId);
                ps.setString(4, reward.getId());
                ps.setString(5, reward.getItemKey());
                ps.setInt(6, reward.getAmount());
                ps.setDouble(7, 0.0);
                ps.setLong(8, System.currentTimeMillis());
                ps.setString(9, "pick");
                ps.executeUpdate();
            }

            return true;
        }, callback, error -> {
            plugin.getLogger().warning("使用自选失败: " + error.getMessage());
            callback.accept(false);
        });
    }

    /**
     * 累抽自选进度数据类
     */
    public static class MilepostInfo {
        private final int totalDraws;
        private final int availablePicks;
        private final int usedPicks;
        private final int interval;
        private final int maxPicks;

        public MilepostInfo(int totalDraws, int availablePicks, int usedPicks, int interval, int maxPicks) {
            this.totalDraws = totalDraws;
            this.availablePicks = availablePicks;
            this.usedPicks = usedPicks;
            this.interval = interval;
            this.maxPicks = maxPicks;
        }

        public int getTotalDraws() { return totalDraws; }
        public int getAvailablePicks() { return availablePicks; }
        public int getUsedPicks() { return usedPicks; }
        public int getInterval() { return interval; }
        public int getMaxPicks() { return maxPicks; }
        public boolean hasAvailable() { return availablePicks > 0; }
        public boolean hasMaxPicks() { return maxPicks > 0; }
    }
}
