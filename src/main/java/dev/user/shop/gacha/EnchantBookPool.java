package dev.user.shop.gacha;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 附魔书池配置（扭蛋机书模式专用）
 * <p>
 * 描述一台书模式扭蛋机从哪些品质档抽附魔、品质权重如何衰减、附魔书等级如何决定。
 * 品质衰减公式：weight[i] = base * ratio^i（i 为从低到高的档位序号），归一化后选档，
 * 天然适配任意档位数量。
 */
public class EnchantBookPool {

    /** 品质档位 key（从低到高），如 [common, uncommon, rare, epic, legendary] */
    private final List<String> rarities;
    /** 最低档权重 */
    private final double decayBase;
    /** 每升一档权重的倍率（<1 则高档越来越稀有） */
    private final double decayRatio;
    /** 等级模式：max | random | decay | fixed */
    private final String levelMode;
    /** 最低等级（random/decay 模式下等级下限） */
    private final int levelMin;
    /** fixed 模式下的固定等级 */
    private final int levelFixed;
    /** 仅从这些 Aiyatsbus 分组里抽（为空表示不按分组过滤） */
    private final List<String> groups;
    /** 排除这些附魔 id */
    private final List<String> exclude;
    /** 是否排除原版附魔的重实现（Aiyatsbus 的 isVanilla 附魔，即 Packet-Vanilla 那 45 个） */
    private final boolean excludeVanilla;
    /** 是否启用定向过滤（玩家放入物品后，按该物品的适用附魔过滤池子） */
    private final boolean targetFilter;

    public EnchantBookPool(List<String> rarities, double decayBase, double decayRatio,
                           String levelMode, int levelMin, int levelFixed,
                           List<String> groups, List<String> exclude, boolean excludeVanilla, boolean targetFilter) {
        this.rarities = rarities != null ? rarities : new ArrayList<>();
        this.decayBase = decayBase;
        this.decayRatio = decayRatio;
        this.levelMode = levelMode != null ? levelMode.toLowerCase() : "random";
        this.levelMin = levelMin;
        this.levelFixed = levelFixed;
        this.groups = groups != null ? groups : new ArrayList<>();
        this.exclude = exclude != null ? exclude : new ArrayList<>();
        this.excludeVanilla = excludeVanilla;
        this.targetFilter = targetFilter;
    }

    /**
     * 从配置节解析附魔书池
     */
    public static EnchantBookPool fromConfig(ConfigurationSection section) {
        if (section == null) return null;

        List<String> rarities = section.getStringList("rarities");
        // 默认包含常见品质档（从低到高）
        if (rarities.isEmpty()) {
            rarities = List.of("common", "uncommon", "rare", "epic", "legendary");
        }

        ConfigurationSection decaySection = section.getConfigurationSection("rarity-decay");
        double base = 100.0;
        double ratio = 0.25;
        if (decaySection != null) {
            base = decaySection.getDouble("base", 100.0);
            ratio = decaySection.getDouble("ratio", 0.25);
        }

        ConfigurationSection levelSection = section.getConfigurationSection("level");
        String levelMode = "random";
        int levelMin = 1;
        int levelFixed = 1;
        if (levelSection != null) {
            levelMode = levelSection.getString("mode", "random");
            levelMin = levelSection.getInt("min", 1);
            levelFixed = levelSection.getInt("fixed", 1);
        }

        List<String> groups = section.getStringList("groups");
        List<String> exclude = section.getStringList("exclude");
        boolean excludeVanilla = section.getBoolean("exclude-vanilla", false);
        boolean targetFilter = section.getBoolean("target-filter", false);

        return new EnchantBookPool(rarities, base, ratio, levelMode, levelMin, levelFixed, groups, exclude, excludeVanilla, targetFilter);
    }

    /**
     * 计算指定档位序号（从 0 起）的衰减权重
     */
    public double weightAt(int tierIndex) {
        return decayBase * Math.pow(decayRatio, tierIndex);
    }

    /**
     * 所有档位权重之和
     */
    public double totalWeight() {
        double sum = 0;
        for (int i = 0; i < rarities.size(); i++) {
            sum += weightAt(i);
        }
        return sum;
    }

    public List<String> getRarities() { return rarities; }
    public double getDecayBase() { return decayBase; }
    public double getDecayRatio() { return decayRatio; }
    public String getLevelMode() { return levelMode; }
    public int getLevelMin() { return levelMin; }
    public int getLevelFixed() { return levelFixed; }
    public List<String> getGroups() { return groups; }
    public List<String> getExclude() { return exclude; }
    public boolean isExcludeVanilla() { return excludeVanilla; }
    public boolean isTargetFilter() { return targetFilter; }
}
