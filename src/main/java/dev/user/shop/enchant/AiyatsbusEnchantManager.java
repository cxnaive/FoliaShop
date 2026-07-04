package dev.user.shop.enchant;

import cc.polarastrum.aiyatsbus.core.Aiyatsbus;
import cc.polarastrum.aiyatsbus.core.AiyatsbusAPI;
import cc.polarastrum.aiyatsbus.core.AiyatsbusEnchantment;
import cc.polarastrum.aiyatsbus.core.AiyatsbusEnchantmentManager;
import cc.polarastrum.aiyatsbus.core.AiyatsbusUtilsKt;
import cc.polarastrum.aiyatsbus.core.data.registry.Rarity;
import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.EnchantBookPool;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aiyatsbus（更多附魔）软依赖管理器
 * <p>
 * 负责给「书模式扭蛋机」抽取并生成附魔书。装配方式照搬 {@link dev.user.shop.economy.PlayerPointsManager}：
 * init() 探测插件 + enabled 标志 + 全方法 null 防御 + try/catch，Aiyatsbus 未安装时优雅降级。
 * <p>
 * 抽取流程：按品质衰减权重选档 → 在该档过滤（排除 disabled / inaccessible / exclude / 不在分组）后
 * 用 Aiyatsbus 自带的 drawEt 按附魔自身 weight 加权抽 1 个 → 解析等级 → book(ench, level) 生成附魔书。
 */
public class AiyatsbusEnchantManager {

    private final FoliaShopPlugin plugin;
    private boolean enabled = false;

    public AiyatsbusEnchantManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化 Aiyatsbus 支持（软依赖）
     */
    public void init() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("Aiyatsbus") == null) {
                plugin.getLogger().info("Aiyatsbus 插件未找到，附魔书扭蛋功能不可用");
                return;
            }
            // 触发 API 就绪检查；Aiyatsbus.api() 在 ENABLE 前调用会抛 IllegalStateException
            AiyatsbusAPI api = Aiyatsbus.INSTANCE.api();
            if (api == null) {
                plugin.getLogger().info("Aiyatsbus API 未就绪，附魔书扭蛋功能不可用");
                return;
            }
            this.enabled = true;
            plugin.getLogger().info("已连接到 Aiyatsbus 更多附魔系统，附魔书扭蛋功能可用");
        } catch (Throwable e) {
            plugin.getLogger().info("Aiyatsbus 初始化失败，附魔书扭蛋功能不可用: " + e.getMessage());
            this.enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 一次抽奖的结果（附魔书 + 元信息，用于包装成 GachaReward）
     */
    public static class DrawnBook {
        public final ItemStack book;
        public final String enchId;
        public final int level;
        /** 该品质档的实际中选概率（衰减归一化后），用作合成 GachaReward 的 probability */
        public final double tierProbability;
        public final String displayName;

        public DrawnBook(ItemStack book, String enchId, int level, double tierProbability, String displayName) {
            this.book = book;
            this.enchId = enchId;
            this.level = level;
            this.tierProbability = tierProbability;
            this.displayName = displayName;
        }
    }

    /**
     * 从池中抽取一本附魔书
     * @return 抽取结果；未启用/池空/出错返回 null
     */
    public DrawnBook drawBook(EnchantBookPool pool) {
        if (!enabled || pool == null || pool.getRarities().isEmpty()) return null;
        try {
            AiyatsbusAPI api = Aiyatsbus.INSTANCE.api();
            AiyatsbusEnchantmentManager mgr = api.getEnchantmentManager();

            // 预过滤每个品质档，仅保留有可用附魔的档（保证一定能抽出）
            List<List<AiyatsbusEnchantment>> tiers = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            double total = 0;
            for (int i = 0; i < pool.getRarities().size(); i++) {
                Rarity rarity = AiyatsbusUtilsKt.aiyatsbusRarity(pool.getRarities().get(i));
                if (rarity == null) continue;
                List<AiyatsbusEnchantment> filtered = filterEnchants(AiyatsbusUtilsKt.aiyatsbusEts(rarity), pool);
                if (filtered.isEmpty()) continue;
                tiers.add(filtered);
                double w = pool.weightAt(i);
                weights.add(w);
                total += w;
            }
            if (tiers.isEmpty() || total <= 0) return null;

            // 按衰减权重选档
            double r = Math.random() * total;
            double acc = 0;
            int chosen = tiers.size() - 1;
            for (int i = 0; i < tiers.size(); i++) {
                acc += weights.get(i);
                if (r <= acc) {
                    chosen = i;
                    break;
                }
            }
            List<AiyatsbusEnchantment> tierEnchants = tiers.get(chosen);
            double tierProb = weights.get(chosen) / total;

            // 档内按附魔自身 weight 加权抽 1 个
            AiyatsbusEnchantment ench = AiyatsbusUtilsKt.drawEt(tierEnchants);
            if (ench == null) return null;

            int level = resolveLevel(ench, pool);
            ItemStack book = AiyatsbusUtilsKt.book(ench, level);
            String name = ench.getBasicData().getName();
            return new DrawnBook(book, ench.getId(), level, tierProb, name);
        } catch (Throwable e) {
            plugin.getLogger().warning("抽取附魔书失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 按 附魔id + 等级 重建附魔书（历史记录展示用）
     * @return 附魔书；未启用/附魔不存在返回 null
     */
    public ItemStack buildBook(String enchId, int level) {
        if (!enabled || enchId == null) return null;
        try {
            AiyatsbusEnchantment ench = Aiyatsbus.INSTANCE.api().getEnchantmentManager().getEnchant(enchId);
            if (ench == null) return null;
            int max = ench.getBasicData().getMaxLevel();
            int lvl = Math.max(1, Math.min(level, max));
            return AiyatsbusUtilsKt.book(ench, lvl);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 生成用于滚动动画展示的样本书（从池中各档各取若干附魔，满级）。
     * 必须与 {@link #filterEnchants} 用同一套过滤条件，确保「加载期能取到样本 ⇒ 运行时必然能抽出」。
     */
    public List<ItemStack> sampleAnimationBooks(EnchantBookPool pool, int count) {
        List<ItemStack> result = new ArrayList<>();
        if (!enabled || pool == null || count <= 0) return result;
        try {
            Set<String> seen = new HashSet<>();
            for (String rarityKey : pool.getRarities()) {
                Rarity rarity = AiyatsbusUtilsKt.aiyatsbusRarity(rarityKey);
                if (rarity == null) continue;
                // 复用 drawBook 的过滤逻辑（含 groups / weight / exclude）
                for (AiyatsbusEnchantment ench : filterEnchants(AiyatsbusUtilsKt.aiyatsbusEts(rarity), pool)) {
                    if (result.size() >= count) break;
                    if (seen.add(ench.getId())) {
                        try {
                            result.add(AiyatsbusUtilsKt.book(ench, ench.getBasicData().getMaxLevel()));
                        } catch (Throwable ignored) {
                            // 跳过单个生成失败的附魔
                        }
                    }
                }
                if (result.size() >= count) break;
            }
        } catch (Throwable e) {
            plugin.getLogger().warning("生成附魔书动画样本失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 过滤附魔：排除 disabled / inaccessible / exclude / 不在指定分组内的
     */
    private List<AiyatsbusEnchantment> filterEnchants(List<AiyatsbusEnchantment> enchants, EnchantBookPool pool) {
        List<AiyatsbusEnchantment> result = new ArrayList<>();
        Set<String> exclude = new HashSet<>(pool.getExclude());
        boolean useGroups = !pool.getGroups().isEmpty();
        for (AiyatsbusEnchantment ench : enchants) {
            try {
                if (!ench.getBasicData().getEnable()) continue;
                if (ench.getAlternativeData().getInaccessible()) continue;
                // weight<=0 的附魔 drawEt 永远抽不到，排除以保证过滤后必然可抽
                if (ench.getAlternativeData().getWeight() <= 0) continue;
                if (exclude.contains(ench.getId())) continue;
                if (useGroups) {
                    boolean inAny = false;
                    for (String g : pool.getGroups()) {
                        if (AiyatsbusUtilsKt.isInGroup(ench.getEnchantment(), g)) {
                            inAny = true;
                            break;
                        }
                    }
                    if (!inAny) continue;
                }
                result.add(ench);
            } catch (Throwable ignored) {
                // 跳过单个异常附魔
            }
        }
        return result;
    }

    /**
     * 解析附魔书等级
     * <ul>
     *   <li>max：恒为该附魔最高等级</li>
     *   <li>fixed：固定等级（不超过最高等级）</li>
     *   <li>decay：低等级更常见，权重 = decayRatio^(L-1)，自动适配各附魔不同 maxLevel</li>
     *   <li>random：[min, max] 均匀随机</li>
     * </ul>
     */
    private int resolveLevel(AiyatsbusEnchantment ench, EnchantBookPool pool) {
        int max = Math.max(1, ench.getBasicData().getMaxLevel());
        int min = Math.max(1, Math.min(pool.getLevelMin(), max));
        switch (pool.getLevelMode()) {
            case "max":
                return max;
            case "fixed":
                return Math.max(1, Math.min(pool.getLevelFixed(), max));
            case "decay": {
                if (max <= min) return min;
                double sum = 0;
                int span = max - min + 1;
                double[] weights = new double[span];
                for (int L = min; L <= max; L++) {
                    double w = Math.pow(pool.getDecayRatio(), L - 1);
                    weights[L - min] = w;
                    sum += w;
                }
                double r = Math.random() * sum;
                double acc = 0;
                for (int L = min; L <= max; L++) {
                    acc += weights[L - min];
                    if (r <= acc) return L;
                }
                return max;
            }
            case "random":
            default: {
                if (max <= min) return min;
                return min + (int) (Math.random() * (max - min + 1));
            }
        }
    }
}
