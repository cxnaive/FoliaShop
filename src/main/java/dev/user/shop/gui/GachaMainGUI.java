package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.GachaMachine;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

public class GachaMainGUI extends AbstractGUI {

    public GachaMainGUI(FoliaShopPlugin plugin, Player player) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("gacha"), 27);
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        Collection<GachaMachine> machines = plugin.getGachaManager().getEnabledMachines();

        int autoSlot = 10;
        for (GachaMachine machine : machines) {
            int slot = machine.getSlot();

            // slot 为 0 时使用自动分配
            if (slot == 0) {
                // 自动分配：跳过边框位置
                while (autoSlot < 17 && (autoSlot % 9 == 0 || autoSlot % 9 == 8)) {
                    autoSlot++;
                }
                if (autoSlot >= 17) {
                    plugin.getLogger().warning("扭蛋机 " + machine.getId() + " 无法自动分配位置，界面已满");
                    continue;
                }
                slot = autoSlot;
                autoSlot += 2;
            } else {
                // 手动配置位置，检查有效性
                if (slot < 0 || slot >= 27) {
                    plugin.getLogger().warning("扭蛋机 " + machine.getId() + " 的 slot " + slot + " 超出范围 (0-26)");
                    continue;
                }
                // 避开边框：第1行(0-8)、第3行(18-26)、第1列(0,9,18)、第9列(8,17,26)
                if (slot < 9 || slot >= 18 || slot % 9 == 0 || slot % 9 == 8) {
                    plugin.getLogger().warning("扭蛋机 " + machine.getId() + " 的 slot " + slot + " 位于边框区域");
                    continue;
                }
            }

            ItemStack icon = machine.createIconItem(plugin);
            ItemUtil.setDisplayName(icon, MessageUtil.convertMiniMessageToLegacy("<yellow><bold>" + machine.getName()));

            java.util.List<String> lore = new java.util.ArrayList<>();
            // 转换 MiniMessage 格式的描述
            for (String descLine : machine.getDescription()) {
                lore.add(MessageUtil.convertMiniMessageToLegacy(descLine));
            }
            lore.add("");
            lore.add("§7每次抽奖: §e" + plugin.getShopConfig().formatCurrency(machine.getCost()));
            lore.add("§7奖品数量: §e" + machine.getRewards().size() + " 种");
            lore.add("");
            lore.add("§e点击开始抽奖！");

            ItemUtil.setLore(icon, lore);

            setItem(slot, icon, p -> {
                p.closeInventory();
                new GachaMachineGUI(plugin, p, machine).open();
            });
        }

        // 收集兑换按钮（有收集配置时显示）
        if (plugin.getGachaManager().hasCollections()) {
            ItemStack collBtn = new ItemStack(Material.ENDER_CHEST);
            ItemUtil.setDisplayName(collBtn, "§d§l收集兑换");
            ItemUtil.setLore(collBtn, java.util.Arrays.asList(
                "§7收集指定奖品组合",
                "§7兑换额外奖励！",
                "",
                "§e点击查看"
            ));

            // 从右往左找最后一个可用位置
            int collSlot = -1;
            for (int i = 16; i >= 10; i--) {
                if (i % 9 == 0 || i % 9 == 8) continue;
                if (inventory.getItem(i) == null) {
                    collSlot = i;
                    break;
                }
            }
            if (collSlot == -1) {
                plugin.getLogger().warning("收集兑换按钮无法分配位置，界面已满");
            } else {
                setItem(collSlot, collBtn, p -> {
                    p.closeInventory();
                    new GachaCollectionGUI(plugin, p).open();
                });
            }
        }

        // 返回按钮
        addBackButton(22, () -> new MainMenuGUI(plugin, player).open());
    }
}
