package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.craftengine.CraftEnginePackManager;
import dev.user.shop.enchant.AiyatsbusEnchantManager;
import dev.user.shop.gacha.EnchantBookPool;
import dev.user.shop.gacha.GachaMachine;
import dev.user.shop.gacha.GachaReward;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class GachaPreviewGUI extends AbstractGUI {

    private final GachaMachine machine;
    private final List<GachaReward> sortedRewards;
    private final boolean hasAdminPermission;
    private int page = 0;

    public GachaPreviewGUI(FoliaShopPlugin plugin, Player player, GachaMachine machine) {
        super(plugin, player, "§8奖品预览 - " + MessageUtil.convertMiniMessageToLegacy(machine.getName()), 54);
        this.machine = machine;
        this.hasAdminPermission = player.hasPermission("foliashop.admin");
        // 按获奖难度从高到低排序（概率从低到高）
        this.sortedRewards = machine.getRewards().stream()
            .sorted(Comparator.comparingDouble(GachaReward::getProbability))
            .collect(Collectors.toList());
    }

    @Override
    protected void initialize() {
        // 确保页码不会小于0
        if (page < 0) page = 0;

        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // 清除之前的物品（10-43号槽位）
        for (int i = 10; i <= 43; i++) {
            if (i % 9 != 0 && i % 9 != 8) {
                inventory.setItem(i, null);
            }
        }

        // 附魔书模式：展示附魔池样本书，而非 rewards 列表
        if (machine.isBookMode()) {
            initializeBookPreview();
            return;
        }

        // CE pack 模式：分页展示物品池网格
        if (machine.isCePackMode()) {
            initializeCePackPreview();
            return;
        }

        List<GachaReward> rewards = sortedRewards;
        int itemsPerPage = 28; // 4行 * 7列
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, rewards.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            GachaReward reward = rewards.get(i);
            ItemStack display = reward.getDisplayItem();
            if (display == null) continue;

            ItemStack item = display.clone();
            item.setAmount(reward.getAmount());

            // 设置总概率以计算实际概率
            reward.setTotalProbability(machine.getTotalProbability());

            List<String> lore = new ArrayList<>();
            lore.add("");

            // 只有admin权限才显示概率和稀有度
            if (hasAdminPermission) {
                lore.add("§7概率: §e" + String.format("%.2f", reward.getActualProbability() * 100) + "%");
                lore.add("§7稀有度: " + reward.getRarityColor() + reward.getRarityPercent());
                if (reward.shouldBroadcast()) {
                    lore.add("§6★ 稀有奖品");
                }
            }

            lore.add("§7数量: §e" + reward.getAmount());

            ItemUtil.addLore(item, lore);

            // 跳过边框位置
            while (slot % 9 == 0 || slot % 9 == 8 || slot < 9 || slot > 44) {
                slot++;
            }

            setItem(slot, item);
            slot++;
        }

        // 上一页按钮
        if (page > 0) {
            ItemStack prevBtn = ItemUtil.createItemFromKey(plugin,
                plugin.getShopConfig().getGUIDecoration("prev-page").getMaterial());
            ItemUtil.setDisplayName(prevBtn, "§e上一页");
            setItem(45, prevBtn, p -> {
                page--;
                initialize();
            });
        }

        // 下一页按钮
        if (endIndex < rewards.size()) {
            ItemStack nextBtn = ItemUtil.createItemFromKey(plugin,
                plugin.getShopConfig().getGUIDecoration("next-page").getMaterial());
            ItemUtil.setDisplayName(nextBtn, "§e下一页");
            setItem(53, nextBtn, p -> {
                page++;
                initialize();
            });
        }

        // 页码显示
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemUtil.setDisplayName(pageInfo, "§e第 " + (page + 1) + " 页");
        setItem(49, pageInfo);

        // 返回按钮
        addBackButton(48, () -> new GachaMachineGUI(plugin, player, machine).open());
    }

    /**
     * 附魔书模式预览：展示加载期预生成的样本书（附魔池里各附魔各 1 本，满级）
     */
    /**
     * 附魔书模式预览：按品质档展示（每档一格，显示中选概率 + 附魔数；管理员可见附魔清单）。
     * 概率来自 AiyatsbusEnchantManager.getPoolInfo，与实际抽奖共用档位构建逻辑。
     */
    private void initializeBookPreview() {
        EnchantBookPool pool = machine.getEnchantPool();
        List<AiyatsbusEnchantManager.TierInfo> tiers = plugin.getAiyatsbusEnchantManager().getPoolInfo(pool);
        String levelDesc = levelDesc(pool);

        if (tiers.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemUtil.setDisplayName(empty, "§c附魔池为空");
            ItemUtil.setLore(empty, List.of("§7未匹配到任何可用附魔", "§7请检查 rarities/groups/exclude 配置"));
            setItem(13, empty);
        } else {
            int slot = 10;
            for (AiyatsbusEnchantManager.TierInfo tier : tiers) {
                while (slot <= 44 && (slot % 9 == 0 || slot % 9 == 8)) slot++;
                if (slot > 44) break;
                setItem(slot, createTierItem(tier, levelDesc));
                slot++;
            }
        }

        // 标题信息
        ItemStack info = new ItemStack(Material.PAPER);
        ItemUtil.setDisplayName(info, "§e附魔书池预览");
        ItemUtil.setLore(info, List.of(
            "§7单抽 1 本，10 连抽 10 本",
            "§7品质按衰减公式加权 · " + levelDesc,
            hasAdminPermission ? "§7管理员可见各档附魔清单" : "§7悬停查看各品质中选概率"
        ));
        setItem(49, info);

        // 返回按钮
        addBackButton(48, () -> new GachaMachineGUI(plugin, player, machine).open());
    }

    private ItemStack createTierItem(AiyatsbusEnchantManager.TierInfo tier, String levelDesc) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        String color = rarityColor(tier.rarityKey);
        ItemUtil.setDisplayName(item, color + tier.rarityName + " §7附魔");

        List<String> lore = new ArrayList<>();
        lore.add("§7中选概率: §e" + String.format("%.2f", tier.probability * 100) + "%");
        lore.add("§7该档附魔: §f" + tier.enchantCount + " 种");
        lore.add("§7出货等级: §f" + levelDesc);
        if (hasAdminPermission && !tier.enchantNames.isEmpty()) {
            lore.add("§7附魔清单:");
            int show = Math.min(tier.enchantNames.size(), 14);
            for (int i = 0; i < show; i++) {
                lore.add("§8- " + tier.enchantNames.get(i));
            }
            if (tier.enchantNames.size() > 14) {
                lore.add("§8- ...等共 " + tier.enchantNames.size() + " 种");
            }
        }
        ItemUtil.setLore(item, lore);
        return item;
    }

    /** 品质 key → §颜色（近似 Aiyatsbus rarity.yml 配色） */
    private String rarityColor(String key) {
        return switch (key == null ? "" : key.toLowerCase()) {
            case "common" -> "§f";      // 汉白玉
            case "uncommon" -> "§a";    // 毛绿
            case "rare" -> "§b";        // 霁青
            case "epic" -> "§d";        // 夹竹桃红
            case "legendary" -> "§6";   // 淡橘橙
            case "splendid" -> "§e";    // 油菜花黄
            case "curse" -> "§c";       // 鹤顶红
            case "artifact" -> "§d";    // 粉团花红
            default -> "§7";
        };
    }

    /** 等级模式 → 中文描述 */
    private String levelDesc(EnchantBookPool pool) {
        if (pool == null) return "未知";
        return switch (pool.getLevelMode()) {
            case "max" -> "恒为满级";
            case "fixed" -> "固定等级 " + pool.getLevelFixed();
            case "decay" -> "低等级更常见";
            default -> "随机等级";
        };
    }

    /**
     * CE pack 模式预览：分页（28/页）展示物品池网格，每件显示中选概率。
     */
    private void initializeCePackPreview() {
        List<CraftEnginePackManager.PoolEntryInfo> entries =
            plugin.getCraftEnginePackManager().getPoolInfo(machine.getCePackPool());

        int itemsPerPage = 28;
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) itemsPerPage));
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, entries.size());

        if (entries.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemUtil.setDisplayName(empty, "§c物品池为空");
            ItemUtil.setLore(empty, List.of("§7未匹配到任何 CE 物品", "§7请检查 packs/tags/items/exclude 配置"));
            setItem(13, empty);
        } else {
            int slot = 10;
            for (int i = startIndex; i < endIndex; i++) {
                CraftEnginePackManager.PoolEntryInfo info = entries.get(i);
                ItemStack display = null;
                try {
                    display = info.customItem.buildItemStack(1);
                } catch (Throwable ignored) {
                    // 跳过构造失败的物品
                }
                if (display == null) continue;
                while (slot <= 44 && (slot % 9 == 0 || slot % 9 == 8)) slot++;
                if (slot > 44) break;
                ItemStack item = display.clone();
                ItemUtil.addLore(item, List.of(
                    "",
                    "§7中选概率: §e" + String.format("%.2f", info.probability * 100) + "%",
                    "§8" + info.key
                ));
                setItem(slot, item);
                slot++;
            }
        }

        // 上一页
        if (page > 0) {
            ItemStack prevBtn = ItemUtil.createItemFromKey(plugin,
                plugin.getShopConfig().getGUIDecoration("prev-page").getMaterial());
            ItemUtil.setDisplayName(prevBtn, "§e上一页");
            setItem(45, prevBtn, p -> { page--; initialize(); });
        }
        // 下一页
        if (endIndex < entries.size()) {
            ItemStack nextBtn = ItemUtil.createItemFromKey(plugin,
                plugin.getShopConfig().getGUIDecoration("next-page").getMaterial());
            ItemUtil.setDisplayName(nextBtn, "§e下一页");
            setItem(53, nextBtn, p -> { page++; initialize(); });
        }
        // 页码
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemUtil.setDisplayName(pageInfo, "§e第 " + (page + 1) + " / " + totalPages + " 页");
        ItemUtil.setLore(pageInfo, List.of("§7共 " + entries.size() + " 件物品"));
        setItem(49, pageInfo);

        // 返回按钮
        addBackButton(48, () -> new GachaMachineGUI(plugin, player, machine).open());
    }
}
