package dev.user.shop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一消息工具类
 * 支持 MiniMessage 格式、可翻译物品、特殊占位符
 */
public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * 占位符类，支持文本、Component、翻译键三种类型
     */
    public static class Placeholder {
        private final String key;
        private final String textValue;
        private final Component componentValue;
        private final String translationKey;
        private final boolean isTranslatable;

        private Placeholder(String key, String textValue, Component componentValue, String translationKey, boolean isTranslatable) {
            this.key = key;
            this.textValue = textValue;
            this.componentValue = componentValue;
            this.translationKey = translationKey;
            this.isTranslatable = isTranslatable;
        }

        /**
         * 创建纯文本占位符
         */
        public static Placeholder text(String key, String value) {
            return new Placeholder(key, value != null ? value : "", null, null, false);
        }

        /**
         * 创建数值占位符
         */
        public static Placeholder number(String key, Number value) {
            return new Placeholder(key, value != null ? value.toString() : "0", null, null, false);
        }

        /**
         * 创建 Component 占位符
         */
        public static Placeholder component(String key, Component value) {
            return new Placeholder(key, null, value != null ? value : Component.empty(), null, false);
        }

        /**
         * 创建翻译键占位符
         */
        public static Placeholder translatable(String key, String translationKey) {
            return new Placeholder(key, null, null, translationKey, true);
        }

        /**
         * 创建物品占位符（自动检测翻译键）
         */
        public static Placeholder item(String key, ItemStack item) {
            if (item == null) {
                return new Placeholder(key, "空气", null, null, false);
            }
            String itemName = ItemUtil.getDisplayName(item);
            // 检测是否是 <lang:...> 格式
            if (itemName.startsWith("<lang:") && itemName.endsWith(">")) {
                String translationKey = itemName.substring(6, itemName.length() - 1);
                return new Placeholder(key, null, null, translationKey, true);
            }
            return new Placeholder(key, itemName, null, null, false);
        }

        public String getKey() { return key; }
        public boolean isTranslatable() { return isTranslatable; }

        /**
         * 获取该占位符对应的 Component
         */
        public Component toComponent() {
            if (isTranslatable && translationKey != null) {
                return Component.translatable(translationKey);
            }
            if (componentValue != null) {
                return componentValue;
            }
            return Component.text(textValue != null ? textValue : "");
        }

        /**
         * 获取该占位符的文本表示（用于纯文本场景）
         */
        public String toText() {
            if (textValue != null) {
                return textValue;
            }
            if (isTranslatable && translationKey != null) {
                return "<lang:" + translationKey + ">";
            }
            // Component 无法直接转换为文本，返回空
            return "";
        }
    }

    /**
     * 构建消息 Component
     * 支持 MiniMessage 模板和特殊占位符
     *
     * @param template MiniMessage 格式模板
     * @param placeholders 占位符列表
     * @return 构建好的 Component
     */
    public static Component buildMessage(String template, Placeholder... placeholders) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }

        // 构建占位符映射
        Map<String, Placeholder> placeholderMap = new HashMap<>();
        for (Placeholder ph : placeholders) {
            placeholderMap.put(ph.getKey(), ph);
        }

        // 检查是否有可翻译的占位符需要特殊处理
        boolean hasTranslatable = placeholderMap.values().stream().anyMatch(Placeholder::isTranslatable);

        if (!hasTranslatable) {
            // 没有可翻译占位符，直接解析 MiniMessage
            String result = template;
            for (Placeholder ph : placeholders) {
                result = result.replace("{" + ph.getKey() + "}", ph.toText());
            }
            return parseMiniMessage(result);
        }

        // 有可翻译占位符，需要分段构建
        // 收集所有可翻译占位符
        List<Placeholder> translatables = new ArrayList<>();
        for (Placeholder ph : placeholders) {
            if (ph.isTranslatable()) {
                translatables.add(ph);
            }
        }

        if (translatables.isEmpty()) {
            // 不应该发生，但保险起见
            return parseMiniMessage(template);
        }

        // 逐个处理可翻译占位符：按模板中出现的顺序分割
        Component result = Component.empty();
        String remaining = template;

        for (Placeholder translatable : translatables) {
            String key = translatable.getKey();
            String pattern = "{" + key + "}";
            int idx = remaining.indexOf(pattern);
            if (idx == -1) continue;

            // 处理占位符前的部分（替换其中的普通占位符）
            String before = remaining.substring(0, idx);
            for (Placeholder ph : placeholders) {
                if (!ph.isTranslatable()) {
                    before = before.replace("{" + ph.getKey() + "}", ph.toText());
                }
            }
            result = result.append(parseMiniMessage(before));

            // 插入可翻译组件
            result = result.append(translatable.toComponent());

            // 剩余部分继续处理
            remaining = remaining.substring(idx + pattern.length());
        }

        // 处理最后一个可翻译占位符之后的部分
        for (Placeholder ph : placeholders) {
            if (!ph.isTranslatable()) {
                remaining = remaining.replace("{" + ph.getKey() + "}", ph.toText());
            }
        }
        result = result.append(parseMiniMessage(remaining));

        return result;
    }

    /**
     * 构建消息（使用 Map 形式传递占位符，向后兼容）
     */
    public static Component buildMessage(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }

        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return parseMiniMessage(result);
    }

    /**
     * 解析 MiniMessage 格式
     */
    public static Component parseMiniMessage(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(message);
        } catch (Exception e) {
            // 如果 MiniMessage 解析失败，尝试 Legacy 格式
            String legacy = convertMiniMessageToLegacy(message);
            return LegacyComponentSerializer.legacySection().deserialize(legacy);
        }
    }

    /**
     * 将 MiniMessage 简单转换为 Legacy 格式（§ 颜色代码）
     * 用于降级处理
     */
    public static String convertMiniMessageToLegacy(String message) {
        if (message == null) return "";

        return message
            .replace("<black>", "§0").replace("</black>", "")
            .replace("<dark_blue>", "§1").replace("</dark_blue>", "")
            .replace("<dark_green>", "§2").replace("</dark_green>", "")
            .replace("<dark_aqua>", "§3").replace("</dark_aqua>", "")
            .replace("<dark_red>", "§4").replace("</dark_red>", "")
            .replace("<dark_purple>", "§5").replace("</dark_purple>", "")
            .replace("<gold>", "§6").replace("</gold>", "")
            .replace("<gray>", "§7").replace("</gray>", "")
            .replace("<dark_gray>", "§8").replace("</dark_gray>", "")
            .replace("<blue>", "§9").replace("</blue>", "")
            .replace("<green>", "§a").replace("</green>", "")
            .replace("<aqua>", "§b").replace("</aqua>", "")
            .replace("<red>", "§c").replace("</red>", "")
            .replace("<light_purple>", "§d").replace("</light_purple>", "")
            .replace("<yellow>", "§e").replace("</yellow>", "")
            .replace("<white>", "§f").replace("</white>", "")
            .replace("<bold>", "§l").replace("</bold>", "")
            .replace("<italic>", "§o").replace("</italic>", "")
            .replace("<underline>", "§n").replace("</underline>", "")
            .replace("<strikethrough>", "§m").replace("</strikethrough>", "")
            .replace("<obfuscated>", "§k").replace("</obfuscated>", "")
            .replace("<reset>", "§r").replace("</reset>", "");
    }

    /**
     * 向玩家发送 Component 消息
     */
    public static void sendMessage(Player player, Component message) {
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    /**
     * 向玩家发送消息（模板形式）
     */
    public static void sendMessage(Player player, String template, Placeholder... placeholders) {
        sendMessage(player, buildMessage(template, placeholders));
    }

    /**
     * 广播 Component 消息
     */
    public static void broadcast(Component message, JavaPlugin plugin) {
        plugin.getServer().broadcast(message);
    }

    /**
     * 广播消息（模板形式）
     */
    public static void broadcast(String template, JavaPlugin plugin, Placeholder... placeholders) {
        broadcast(buildMessage(template, placeholders), plugin);
    }

    /**
     * 创建扭蛋广播消息（带抽奖次数）
     * 兼容原有 createBroadcastComponent 功能
     */
    public static Component createGachaBroadcast(String template, String player, String machine,
                                                  String itemName, int drawsSinceLast) {
        // 提取翻译键（如果是 <lang:...> 格式）
        String translationKey = null;
        if (itemName != null && itemName.startsWith("<lang:") && itemName.endsWith(">")) {
            translationKey = itemName.substring(6, itemName.length() - 1);
        }

        // 替换 {player}, {machine}, {draws}
        String processedTemplate = template
            .replace("{player}", player)
            .replace("{machine}", machine);

        if (drawsSinceLast >= 0) {
            processedTemplate = processedTemplate.replace("{draws}", (drawsSinceLast + 1) + "抽");
        }

        // 按 {item} 分割
        String[] parts = processedTemplate.split("\\{item\\}");
        Component result = Component.empty();

        for (int i = 0; i < parts.length; i++) {
            // 解析 MiniMessage 并追加
            result = result.append(parseMiniMessage(parts[i]));

            // 在分割点之间插入可翻译的物品名称（除了最后一部分）
            if (i < parts.length - 1) {
                if (translationKey != null) {
                    result = result.append(Component.translatable(translationKey));
                } else {
                    result = result.append(Component.text(itemName != null ? itemName : ""));
                }
            }
        }

        return result;
    }

    /**
     * 从 ItemUtil 迁移的辅助方法：提取翻译键
     */
    public static String extractTranslationKey(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return null;
        }
        if (itemName.startsWith("<lang:") && itemName.endsWith(">")) {
            return itemName.substring(6, itemName.length() - 1);
        }
        return null;
    }
}
