package dev.user.shop.craftengine;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.CraftEnginePackPool;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.ItemSettings;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CraftEngine pack 物品抽取管理器（CE pack 模式扭蛋机用）。
 * <p>
 * CraftEngine 是硬依赖（已在 plugin.yml depend + build.gradle compileOnly），无需 softdepend 探测，
 * 但仍对所有 CE 调用做 null/异常防御，避免个别物品构造失败影响抽奖。
 * <p>
 * 概率模型：等权重 + 可选 weights 覆盖。抽奖(drawItem)与预览(getPoolInfo)共用 {@link #buildPool}，
 * 保证「预览看到的概率 = 实际抽奖概率」。
 */
public class CraftEnginePackManager {

    private final FoliaShopPlugin plugin;

    public CraftEnginePackManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** 一次抽奖结果 */
    public static class DrawnItem {
        public final ItemStack item;
        /** CE 物品 key，形如 "mythril:sword1"（同时用作合成 GachaReward 的 itemKey，供历史重建） */
        public final String key;
        /** 该物品的有效中选概率（权重/总权重），用作合成 GachaReward 的 probability */
        public final double probability;

        public DrawnItem(ItemStack item, String key, double probability) {
            this.item = item;
            this.key = key;
            this.probability = probability;
        }
    }

    /** 预览用：池中一件物品的信息（含 CustomItem 便于预览构造展示） */
    public static class PoolEntryInfo {
        public final String key;
        public final double probability;
        public final CustomItem<ItemStack> customItem;

        public PoolEntryInfo(String key, double probability, CustomItem<ItemStack> customItem) {
            this.key = key;
            this.probability = probability;
            this.customItem = customItem;
        }
    }

    /** 池中一件物品（过滤后 + 权重） */
    private static class PoolEntry {
        final Key key;
        final CustomItem<ItemStack> customItem;
        final double weight;

        PoolEntry(Key key, CustomItem<ItemStack> customItem, double weight) {
            this.key = key;
            this.customItem = customItem;
            this.weight = weight;
        }
    }

    /**
     * 从池中加权抽取一件物品并构造 ItemStack
     * @return 抽取结果；池空/构造失败返回 null
     */
    public DrawnItem drawItem(CraftEnginePackPool pool) {
        if (pool == null) return null;
        try {
            List<PoolEntry> entries = buildPool(pool);
            if (entries.isEmpty()) return null;
            double total = 0;
            for (PoolEntry pe : entries) total += pe.weight;
            if (total <= 0) return null;

            double r = Math.random() * total;
            double acc = 0;
            PoolEntry chosen = entries.get(entries.size() - 1);
            for (PoolEntry pe : entries) {
                acc += pe.weight;
                if (r <= acc) {
                    chosen = pe;
                    break;
                }
            }
            ItemStack stack = chosen.customItem.buildItemStack(pool.getAmount());
            if (stack == null) return null;
            return new DrawnItem(stack, chosen.key.asString(), chosen.weight / total);
        } catch (Throwable e) {
            plugin.getLogger().warning("抽取 CE 物品失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 池信息（供预览），与 drawItem 共用 buildPool，概率口径一致
     */
    public List<PoolEntryInfo> getPoolInfo(CraftEnginePackPool pool) {
        List<PoolEntryInfo> info = new ArrayList<>();
        if (pool == null) return info;
        try {
            List<PoolEntry> entries = buildPool(pool);
            double total = 0;
            for (PoolEntry pe : entries) total += pe.weight;
            if (total <= 0) return info;
            for (PoolEntry pe : entries) {
                info.add(new PoolEntryInfo(pe.key.asString(), pe.weight / total, pe.customItem));
            }
        } catch (Throwable e) {
            plugin.getLogger().warning("获取 CE 池信息失败: " + e.getMessage());
        }
        return info;
    }

    /**
     * 生成滚动动画样本（池里取最多 count 件构造）
     */
    public List<ItemStack> sampleAnimationItems(CraftEnginePackPool pool, int count) {
        List<ItemStack> result = new ArrayList<>();
        if (pool == null || count <= 0) return result;
        try {
            List<PoolEntry> entries = buildPool(pool);
            int n = Math.min(count, entries.size());
            for (int i = 0; i < n; i++) {
                try {
                    ItemStack stack = entries.get(i).customItem.buildItemStack(pool.getAmount());
                    if (stack != null) result.add(stack);
                } catch (Throwable ignored) {
                    // 跳过单个构造失败的物品
                }
            }
        } catch (Throwable e) {
            plugin.getLogger().warning("生成 CE 动画样本失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 构建有效池：loadedItems() 按 packs/tags/items 过滤，减去 exclude，附加权重（覆盖或 1.0）。
     */
    private List<PoolEntry> buildPool(CraftEnginePackPool pool) {
        List<PoolEntry> result = new ArrayList<>();
        Map<Key, CustomItem<ItemStack>> all;
        try {
            all = CraftEngineItems.loadedItems();
        } catch (Throwable e) {
            plugin.getLogger().warning("读取 CraftEngine 物品列表失败: " + e.getMessage());
            return result;
        }
        if (all == null || all.isEmpty()) return result;

        Set<String> packSet = new HashSet<>(pool.getPacks());
        Set<String> excludeSet = new HashSet<>(pool.getExclude());
        Set<String> itemSet = new HashSet<>(pool.getItems());
        Set<Key> tagSet = new HashSet<>();
        for (String t : pool.getTags()) {
            Key tk = parseKey(t);
            if (tk != null) tagSet.add(tk);
        }
        boolean usePacks = !packSet.isEmpty();
        boolean useItems = !itemSet.isEmpty();
        boolean useTags = !tagSet.isEmpty();

        for (Map.Entry<Key, CustomItem<ItemStack>> e : all.entrySet()) {
            Key k = e.getKey();
            String keyStr = k.asString();
            if (excludeSet.contains(keyStr)) continue;

            boolean inPool = false;
            if (usePacks && packSet.contains(k.namespace())) inPool = true;
            if (!inPool && useItems && itemSet.contains(keyStr)) inPool = true;
            if (!inPool && useTags) {
                try {
                    ItemSettings settings = e.getValue().settings();
                    Set<Key> itemTags = settings != null ? settings.tags() : null;
                    if (itemTags != null) {
                        for (Key tk : itemTags) {
                            if (tagSet.contains(tk)) {
                                inPool = true;
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // 读取 tag 失败，按「不属于」处理
                }
            }
            if (!inPool) continue;

            double w = pool.getWeights().getOrDefault(keyStr, 1.0);
            if (w <= 0) continue; // 权重 0 = 永不抽取
            result.add(new PoolEntry(k, e.getValue(), w));
        }
        return result;
    }

    /** 解析 "ns:value" 为 Key；无冒号则返回 null（CE tag/物品 key 必须带命名空间） */
    private Key parseKey(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Key.of(s);
        } catch (Throwable e) {
            return null;
        }
    }
}
