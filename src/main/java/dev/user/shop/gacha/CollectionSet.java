package dev.user.shop.gacha;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收集兑换配置
 * 玩家抽到指定奖品组合后可兑换额外奖励
 */
public class CollectionSet {

    private final String id;
    private final String name;
    private final String icon; // 图标物品 key，如 "minecraft:diamond"，null 则使用默认
    private final List<String> description;
    private final List<RequireEntry> requires;
    private final CollectionReward reward;
    private final boolean repeatable;
    private final int slot;

    public CollectionSet(String id, String name, String icon, List<String> description,
                         List<RequireEntry> requires, CollectionReward reward, boolean repeatable) {
        this(id, name, icon, description, requires, reward, repeatable, 0);
    }

    public CollectionSet(String id, String name, String icon, List<String> description,
                         List<RequireEntry> requires, CollectionReward reward, boolean repeatable, int slot) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.description = description != null ? description : new ArrayList<>();
        this.requires = requires;
        this.reward = reward;
        this.repeatable = repeatable;
        this.slot = slot;
    }

    /**
     * 检查收集条件是否满足
     * @param playerRewards 玩家已收集的奖品 Map<machineId, Set<rewardId>>
     * @return 未满足的条件列表，空列表表示全部满足
     */
    public List<RequireEntry> getUnsatisfied(Map<String, Set<String>> playerRewards) {
        List<RequireEntry> unsatisfied = new ArrayList<>();
        for (RequireEntry req : requires) {
            Set<String> rewards = playerRewards.get(req.machineId);
            if (rewards == null || !rewards.contains(req.rewardId)) {
                unsatisfied.add(req);
            }
        }
        return unsatisfied;
    }

    /**
     * 是否全部收集完成
     */
    public boolean isComplete(Map<String, Set<String>> playerRewards) {
        return getUnsatisfied(playerRewards).isEmpty();
    }

    /**
     * 已收集数量
     */
    public int getCollectedCount(Map<String, Set<String>> playerRewards) {
        int count = 0;
        for (RequireEntry req : requires) {
            Set<String> rewards = playerRewards.get(req.machineId);
            if (rewards != null && rewards.contains(req.rewardId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 需要收集的总数量
     */
    public int getRequiredCount() {
        return requires.size();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public List<String> getDescription() { return description; }
    public List<RequireEntry> getRequires() { return requires; }
    public CollectionReward getReward() { return reward; }
    public boolean isRepeatable() { return repeatable; }
    public int getSlot() { return slot; }

    /**
     * 收集条件条目
     */
    public static class RequireEntry {
        private final String machineId;
        private final String rewardId;

        public RequireEntry(String machineId, String rewardId) {
            this.machineId = machineId;
            this.rewardId = rewardId;
        }

        public String getMachineId() { return machineId; }
        public String getRewardId() { return rewardId; }
    }

    /**
     * 收集奖励
     */
    public static class CollectionReward {
        private final String item;
        private final int amount;
        private final Map<String, String> components;
        private final List<String> commands;
        private final boolean giveItem;

        public CollectionReward(String item, int amount, Map<String, String> components,
                                List<String> commands, boolean giveItem) {
            this.item = item;
            this.amount = amount;
            this.components = components != null ? components : new java.util.HashMap<>();
            this.commands = commands != null ? commands : new ArrayList<>();
            this.giveItem = giveItem;
        }

        public String getItem() { return item; }
        public int getAmount() { return amount; }
        public Map<String, String> getComponents() { return components; }
        public boolean hasComponents() { return !components.isEmpty(); }
        public List<String> getCommands() { return commands; }
        public boolean hasCommands() { return !commands.isEmpty(); }
        public boolean isGiveItem() { return giveItem; }
    }
}
