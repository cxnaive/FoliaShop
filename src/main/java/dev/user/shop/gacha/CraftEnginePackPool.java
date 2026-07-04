package dev.user.shop.gacha;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CraftEngine pack 物品池配置（扭蛋机 CE pack 模式专用）
 * <p>
 * 池来源为 packs(命名空间) / tags(CE 标签) / items(显式 key) 三者的并集，减去 exclude。
 * 由于 CraftEngine 物品本身没有稀有度/权重字段，概率采用「等权重 + 可选覆盖」：
 * 每件物品默认权重 1.0，可在 weights 里按 "namespace:value" 覆盖个别物品权重。
 */
public class CraftEnginePackPool {

    /** 来源：按 pack 命名空间枚举（主用法） */
    private final List<String> packs;
    /** 来源：按 CE tag 枚举（形如 "myserver:gacha"） */
    private final List<String> tags;
    /** 来源：显式指定物品 key（形如 "mythril:sword1"） */
    private final List<String> items;
    /** 排除的物品 key */
    private final List<String> exclude;
    /** 个别物品的权重覆盖（key -> 权重），未列出者默认 1.0 */
    private final Map<String, Double> weights;
    /** 每件物品给多少个（默认 1） */
    private final int amount;

    public CraftEnginePackPool(List<String> packs, List<String> tags, List<String> items,
                               List<String> exclude, Map<String, Double> weights, int amount) {
        this.packs = packs != null ? packs : new ArrayList<>();
        this.tags = tags != null ? tags : new ArrayList<>();
        this.items = items != null ? items : new ArrayList<>();
        this.exclude = exclude != null ? exclude : new ArrayList<>();
        this.weights = weights != null ? weights : new HashMap<>();
        this.amount = amount <= 0 ? 1 : amount;
    }

    public static CraftEnginePackPool fromConfig(ConfigurationSection section) {
        if (section == null) return null;

        List<String> packs = section.getStringList("packs");
        List<String> tags = section.getStringList("tags");
        List<String> items = section.getStringList("items");
        List<String> exclude = section.getStringList("exclude");
        int amount = section.getInt("amount", 1);

        Map<String, Double> weights = new HashMap<>();
        ConfigurationSection weightsSection = section.getConfigurationSection("weights");
        if (weightsSection != null) {
            for (String key : weightsSection.getKeys(false)) {
                weights.put(key, weightsSection.getDouble(key, 1.0));
            }
        }

        return new CraftEnginePackPool(packs, tags, items, exclude, weights, amount);
    }

    public List<String> getPacks() { return packs; }
    public List<String> getTags() { return tags; }
    public List<String> getItems() { return items; }
    public List<String> getExclude() { return exclude; }
    public Map<String, Double> getWeights() { return weights; }
    public int getAmount() { return amount; }
}
