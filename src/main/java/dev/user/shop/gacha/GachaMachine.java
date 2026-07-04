package dev.user.shop.gacha;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static dev.user.shop.util.ItemUtil.applyComponents;

public class GachaMachine {

    private final String id;
    private final String name;
    private final List<String> description;
    private final String icon;
    private final double cost;
    private final int animationDuration;
    private final int animationDurationTen;
    private final boolean broadcastRare;
    private final double broadcastThreshold;
    private final int slot;
    private final List<GachaReward> rewards;
    private boolean enabled;

    // 软保底配置
    private final boolean pityEnabled;
    private final int pityStart;
    private final int pityMax;
    private final double pityTargetMaxProbability;

    // 展示实体覆盖配置
    private final DisplayEntityConfig displayConfig;

    // ICON NBT 组件配置
    private Map<String, String> iconComponents;

    // 累抽自选配置
    private final int milepostInterval; // 每 N 次抽奖获得 1 次自选，0=不启用
    private final int milepostMaxPicks; // 每个玩家最多获得多少次自选，0=无限制

    private double totalProbability;
    private double pityTargetBaseProbability;
    private List<GachaReward> pityTargetRewards;
    private List<GachaReward> nonPityRewards;
    private List<org.bukkit.inventory.ItemStack> cachedAnimationItems;

    // 附魔书模式（书模式扭蛋机：抽奖产出 Aiyatsbus 附魔书，无 rewards 列表）
    private boolean bookMode;
    private EnchantBookPool enchantPool;
    private List<org.bukkit.inventory.ItemStack> bookAnimationItems;

    public GachaMachine(String id, String name, List<String> description, String icon, double cost,
                        int animationDuration, int animationDurationTen, boolean broadcastRare, double broadcastThreshold, int slot,
                        boolean enabled, boolean pityEnabled, int pityStart, int pityMax, double pityTargetMaxProbability) {
        this(id, name, description, icon, cost, animationDuration, animationDurationTen, broadcastRare, broadcastThreshold, slot,
            enabled, pityEnabled, pityStart, pityMax, pityTargetMaxProbability, null, null, 0, 0);
    }

    public GachaMachine(String id, String name, List<String> description, String icon, double cost,
                        int animationDuration, int animationDurationTen, boolean broadcastRare, double broadcastThreshold, int slot,
                        boolean enabled, boolean pityEnabled, int pityStart, int pityMax, double pityTargetMaxProbability,
                        DisplayEntityConfig displayConfig) {
        this(id, name, description, icon, cost, animationDuration, animationDurationTen, broadcastRare, broadcastThreshold, slot,
            enabled, pityEnabled, pityStart, pityMax, pityTargetMaxProbability, displayConfig, null, 0, 0);
    }

    public GachaMachine(String id, String name, List<String> description, String icon, double cost,
                        int animationDuration, int animationDurationTen, boolean broadcastRare, double broadcastThreshold, int slot,
                        boolean enabled, boolean pityEnabled, int pityStart, int pityMax, double pityTargetMaxProbability,
                        DisplayEntityConfig displayConfig, Map<String, String> iconComponents) {
        this(id, name, description, icon, cost, animationDuration, animationDurationTen, broadcastRare, broadcastThreshold, slot,
            enabled, pityEnabled, pityStart, pityMax, pityTargetMaxProbability, displayConfig, iconComponents, 0, 0);
    }

    public GachaMachine(String id, String name, List<String> description, String icon, double cost,
                        int animationDuration, int animationDurationTen, boolean broadcastRare, double broadcastThreshold, int slot,
                        boolean enabled, boolean pityEnabled, int pityStart, int pityMax, double pityTargetMaxProbability,
                        DisplayEntityConfig displayConfig, Map<String, String> iconComponents,
                        int milepostInterval, int milepostMaxPicks) {
        this.id = id;
        this.name = name;
        this.description = description != null ? description : new ArrayList<>();
        this.icon = icon;
        this.cost = cost;
        this.animationDuration = animationDuration;
        this.animationDurationTen = animationDurationTen;
        this.broadcastRare = broadcastRare;
        this.broadcastThreshold = broadcastThreshold;
        this.slot = slot;
        this.enabled = enabled;
        this.pityEnabled = pityEnabled;
        this.pityStart = pityStart;
        this.pityMax = pityMax;
        this.pityTargetMaxProbability = pityTargetMaxProbability;
        this.displayConfig = displayConfig;
        this.iconComponents = iconComponents != null ? iconComponents : new HashMap<>();
        this.milepostInterval = milepostInterval;
        this.milepostMaxPicks = milepostMaxPicks;
        this.rewards = new ArrayList<>();
        this.pityTargetRewards = new ArrayList<>();
        this.nonPityRewards = new ArrayList<>();
    }

    public void addReward(GachaReward reward) {
        rewards.add(reward);
        recalculateProbabilities();
    }

    private void recalculateProbabilities() {
        totalProbability = 0;
        pityTargetBaseProbability = 0;
        pityTargetRewards.clear();
        nonPityRewards.clear();

        for (GachaReward reward : rewards) {
            totalProbability += reward.getProbability();
            if (reward.getProbability() <= pityTargetMaxProbability) {
                pityTargetRewards.add(reward);
                pityTargetBaseProbability += reward.getProbability();
            } else {
                nonPityRewards.add(reward);
            }
        }
    }

    /**
     * 随机抽取一个奖品（使用原始概率）
     */
    public GachaReward roll() {
        if (rewards.isEmpty()) return null;

        double random = Math.random() * totalProbability;
        double current = 0;

        for (GachaReward reward : rewards) {
            current += reward.getProbability();
            if (random <= current) {
                return reward;
            }
        }

        return rewards.get(rewards.size() - 1);
    }

    /**
     * 带软保底的抽奖
     * @param pityCount 当前保底计数
     * @return 抽奖结果和是否触发保底
     */
    public PityResult rollWithPity(int pityCount) {
        if (!pityEnabled || pityTargetRewards.isEmpty()) {
            return new PityResult(roll(), false);
        }

        // 计算保底目标奖品的动态总概率
        double pityTargetTotalProb = calculatePityTargetProbability(pityCount);

        // 如果达到硬保底，必出保底目标
        if (pityCount >= pityMax) {
            return new PityResult(selectFromPityTargets(), true);
        }

        // 计算非保底目标奖品的缩放比例
        double nonPityBase = 1.0 - pityTargetBaseProbability;
        double nonPityScale = nonPityBase > 0.0001 ? (1.0 - pityTargetTotalProb) / nonPityBase : 0.0;

        // 构建动态概率表
        double random = Math.random();
        double current = 0;

        // 先尝试保底目标奖品（按原始概率比例分配动态概率）
        if (pityTargetBaseProbability > 0) {
            for (GachaReward reward : pityTargetRewards) {
                double dynamicProb = (reward.getProbability() / pityTargetBaseProbability) * pityTargetTotalProb;
                current += dynamicProb;
                if (random <= current) {
                    return new PityResult(reward, pityCount >= pityStart);
                }
            }
        }

        // 再尝试非保底目标奖品（概率被压缩）
        for (GachaReward reward : nonPityRewards) {
            double dynamicProb = reward.getProbability() * nonPityScale;
            current += dynamicProb;
            if (random <= current) {
                return new PityResult(reward, false);
            }
        }

        // 兜底返回最后一个保底目标
        return new PityResult(pityTargetRewards.get(pityTargetRewards.size() - 1), true);
    }

    /**
     * 计算保底目标奖品的动态总概率
     */
    private double calculatePityTargetProbability(int pityCount) {
        if (pityCount < pityStart) {
            return pityTargetBaseProbability;
        }
        if (pityCount >= pityMax) {
            return 1.0;
        }

        // 线性增长
        double progress = (double) (pityCount - pityStart) / (pityMax - pityStart);
        return pityTargetBaseProbability + (1.0 - pityTargetBaseProbability) * progress;
    }

    /**
     * 从保底目标中按原始概率比例随机选择一个
     */
    private GachaReward selectFromPityTargets() {
        if (pityTargetRewards.isEmpty()) {
            return rewards.get(rewards.size() - 1);
        }

        double random = Math.random() * pityTargetBaseProbability;
        double current = 0;

        for (GachaReward reward : pityTargetRewards) {
            current += reward.getProbability();
            if (random <= current) {
                return reward;
            }
        }

        return pityTargetRewards.get(pityTargetRewards.size() - 1);
    }

    /**
     * 检查奖品是否是保底目标
     */
    public boolean isPityTarget(GachaReward reward) {
        return reward != null && reward.getProbability() <= pityTargetMaxProbability;
    }

    /**
     * 获取当前保底进度百分比（用于显示）
     */
    public double getPityProgress(int pityCount) {
        if (!pityEnabled) return 0;
        if (pityCount >= pityMax) return 100;
        return (double) pityCount / pityMax * 100;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getDescription() { return description; }
    public String getIcon() { return icon; }
    public double getCost() { return cost; }
    public int getAnimationDuration() { return animationDuration; }
    public int getAnimationDurationTen() { return animationDurationTen; }
    public boolean isBroadcastRare() { return broadcastRare; }
    public double getBroadcastThreshold() { return broadcastThreshold; }
    public List<GachaReward> getRewards() { return new ArrayList<>(rewards); }

    public int getSlot() { return slot; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getTotalProbability() { return totalProbability; }

    // 软保底配置 Getters
    public boolean isPityEnabled() { return pityEnabled; }
    public int getPityStart() { return pityStart; }
    public int getPityMax() { return pityMax; }
    public double getPityTargetMaxProbability() { return pityTargetMaxProbability; }

    /**
     * 保底抽奖结果
     */
    public record PityResult(GachaReward reward, boolean isPityTriggered) {
    }

    public boolean shouldBroadcast(GachaReward reward) {
        return broadcastRare && reward.getProbability() <= broadcastThreshold;
    }

    /**
     * 获取指定稀有度及以上的奖品（用于动画展示）
     */
    public List<GachaReward> getRarifiedRewards(int minRarity) {
        List<GachaReward> result = new ArrayList<>();
        for (GachaReward reward : rewards) {
            if (reward.getRarityLevel() >= minRarity) {
                result.add(reward);
            }
        }
        return result.isEmpty() ? rewards : result;
    }

    /**
     * 获取动画物品列表（带缓存）
     */
    public synchronized List<org.bukkit.inventory.ItemStack> getAnimationItems() {
        // 书模式：使用加载期预生成的样本书
        if (bookMode) {
            return bookAnimationItems != null ? new ArrayList<>(bookAnimationItems) : new ArrayList<>();
        }
        if (cachedAnimationItems == null) {
            cachedAnimationItems = new ArrayList<>();
            for (GachaReward reward : rewards) {
                org.bukkit.inventory.ItemStack item = reward.getDisplayItem();
                if (item != null) {
                    cachedAnimationItems.add(item.clone());
                }
            }
            Collections.shuffle(cachedAnimationItems);
        }
        return new ArrayList<>(cachedAnimationItems);
    }

    /**
     * 清除动画物品缓存（当奖品改变时调用）
     */
    public void clearAnimationCache() {
        cachedAnimationItems = null;
    }

    public ItemStack createIconItem(FoliaShopPlugin plugin) {
        ItemStack item = ItemUtil.createItemFromKey(plugin, icon);
        if (item == null) {
            item = new ItemStack(Material.CHEST);
        }
        if (iconComponents != null && !iconComponents.isEmpty()) {
            item = applyComponents(item, iconComponents);
        }
        return item;
    }

    public Map<String, String> getIconComponents() { return iconComponents; }
    public void setIconComponents(Map<String, String> iconComponents) { this.iconComponents = iconComponents != null ? iconComponents : new HashMap<>(); }
    public boolean hasIconComponents() { return iconComponents != null && !iconComponents.isEmpty(); }

    public DisplayEntityConfig getDisplayConfig() {
        return displayConfig;
    }

    public boolean hasDisplayConfig() {
        return displayConfig != null;
    }

    // 累抽自选 Getters
    public int getMilepostInterval() { return milepostInterval; }
    public int getMilepostMaxPicks() { return milepostMaxPicks; }
    public boolean isMilepostEnabled() { return milepostInterval > 0; }

    // ==================== 附魔书模式 ====================

    public boolean isBookMode() { return bookMode; }
    public void setBookMode(boolean bookMode) { this.bookMode = bookMode; }

    public EnchantBookPool getEnchantPool() { return enchantPool; }
    public void setEnchantPool(EnchantBookPool enchantPool) { this.enchantPool = enchantPool; }

    public List<org.bukkit.inventory.ItemStack> getBookAnimationItems() { return bookAnimationItems; }
    public void setBookAnimationItems(List<org.bukkit.inventory.ItemStack> bookAnimationItems) { this.bookAnimationItems = bookAnimationItems; }
}
