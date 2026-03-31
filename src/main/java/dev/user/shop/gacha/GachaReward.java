package dev.user.shop.gacha;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class GachaReward {

    private final String id;
    private final String itemKey;
    private final int amount;
    private final double probability;
    private final String displayName;
    private final boolean broadcast;
    private ItemStack displayItem;
    private Map<String, String> components; // NBT 组件配置
    private double totalProbability; // 用于计算实际概率

    public GachaReward(String id, String itemKey, int amount, double probability, String displayName, boolean broadcast) {
        this(id, itemKey, amount, probability, displayName, broadcast, null);
    }

    public GachaReward(String id, String itemKey, int amount, double probability, String displayName, boolean broadcast, Map<String, String> components) {
        this.id = id;
        this.itemKey = itemKey;
        this.amount = amount;
        this.probability = probability;
        this.displayName = displayName;
        this.broadcast = broadcast;
        this.components = components != null ? components : new HashMap<>();
    }

    // Getters
    public String getId() { return id; }
    public String getItemKey() { return itemKey; }
    public int getAmount() { return amount; }
    public double getProbability() { return probability; }
    public String getDisplayName() { return displayName; }
    public boolean shouldBroadcast() { return broadcast; }

    public ItemStack getDisplayItem() { return displayItem; }
    public void setDisplayItem(ItemStack displayItem) { this.displayItem = displayItem; }

    public Map<String, String> getComponents() { return components; }
    public void setComponents(Map<String, String> components) { this.components = components != null ? components : new HashMap<>(); }
    public boolean hasComponents() { return components != null && !components.isEmpty(); }

    /**
     * 设置总概率（用于计算实际概率）
     */
    public void setTotalProbability(double totalProbability) { this.totalProbability = totalProbability; }

    /**
     * 获取实际概率（归一化后的概率）
     * @return 实际概率（0-1之间）
     */
    public double getActualProbability() {
        if (totalProbability <= 0) {
            return probability; // 如果没有设置总概率，返回原始概率
        }
        return probability / totalProbability;
    }

    /**
     * 获取稀有度等级（基于概率）
     */
    public int getRarityLevel() {
        if (probability <= 0.02) return 5; // 传说
        if (probability <= 0.05) return 4; // 史诗
        if (probability <= 0.10) return 3; // 稀有
        if (probability <= 0.20) return 2; // 精良
        return 1; // 普通
    }

    /**
     * 获取稀有度百分比显示（使用实际概率）
     */
    public String getRarityPercent() {
        return String.format("%.1f%%", getActualProbability() * 100);
    }

    public String getRarityColor() {
        return switch (getRarityLevel()) {
            case 5 -> "§6"; // 金色
            case 4 -> "§5"; // 紫色
            case 3 -> "§9"; // 蓝色
            case 2 -> "§a"; // 绿色
            default -> "§f"; // 白色
        };
    }

    /**
     * 获取显示名称的 Component 对象（兼容 MiniMessage 和传统颜色代码）
     * 配置中可以使用：<gold>物品名 或 §6物品名
     * 如果没有配置 displayName，从物品获取
     */
    public Component getDisplayNameComponent() {
        // 如果没有配置 displayName，从 displayItem 获取
        if (displayName == null || displayName.isEmpty()) {
            if (displayItem != null) {
                // 使用 ItemUtil.getDisplayName() 获取本地化名称
                String itemDisplayName = dev.user.shop.util.ItemUtil.getDisplayName(displayItem);
                // 如果是 <lang:...> 格式，需要特殊处理
                if (itemDisplayName.startsWith("<lang:") && itemDisplayName.endsWith(">") && itemDisplayName.length() > 7) {
                    String translationKey = itemDisplayName.substring(6, itemDisplayName.length() - 1);
                    return Component.translatable(translationKey);
                }
                // 否则作为普通文本
                return Component.text(itemDisplayName);
            }
            return Component.text(itemKey);
        }
        // 如果包含传统颜色代码 §，使用 LegacyComponentSerializer
        if (displayName.contains("§")) {
            return LegacyComponentSerializer.legacySection().deserialize(displayName);
        }
        // 否则尝试使用 MiniMessage
        try {
            return MiniMessage.miniMessage().deserialize(displayName);
        } catch (Exception e) {
            // 解析失败返回纯文本
            return Component.text(displayName);
        }
    }

    /**
     * 获取纯文本显示名称（去除颜色代码）
     */
    public String getPlainDisplayName() {
        Component component = getDisplayNameComponent();
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }
}
