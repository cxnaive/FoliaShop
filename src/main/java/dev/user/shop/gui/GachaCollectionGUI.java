package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.CollectionSet;
import dev.user.shop.gacha.GachaMachine;
import dev.user.shop.gacha.GachaManager;
import dev.user.shop.gacha.GachaReward;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 收集兑换界面
 * 显示玩家在各收集任务中的进度，满足条件后可领取奖励
 */
public class GachaCollectionGUI extends AbstractGUI {

    public GachaCollectionGUI(FoliaShopPlugin plugin, Player player) {
        super(plugin, player, "§e§l收集兑换", 54);
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        Collection<CollectionSet> collections = plugin.getGachaManager().getAllCollections();
        if (collections.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.BARRIER);
            ItemUtil.setDisplayName(emptyItem, "§c暂无收集任务");
            setItem(22, emptyItem);
            addBackButton(49, () -> new GachaMainGUI(plugin, player).open());
            return;
        }

        // 异步加载每个收集任务的进度
        for (CollectionSet collSet : collections) {
            plugin.getGachaManager().getCollectionProgress(player.getUniqueId(), collSet, progress -> {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
                    renderItem(collSet, progress);
                });
            });
        }

        addBackButton(49, () -> new GachaMainGUI(plugin, player).open());
        addCloseButton(52);
    }

    private void renderItem(CollectionSet collSet, Map<String, Set<String>> progress) {
        int slot = collSet.getSlot();
        if (slot > 0 && slot < 45 && slot % 9 != 0 && slot % 9 != 8) {
            // 手动配置的有效位置
        } else {
            // 自动寻找空位
            for (int i = 10; i < 44; i++) {
                if (i % 9 == 0 || i % 9 == 8) continue;
                if (inventory.getItem(i) == null) {
                    slot = i;
                    break;
                }
            }
        }

        if (slot <= 0 || slot >= 45) return;

        final int finalSlot = slot;

        boolean complete = collSet.isComplete(progress);
        int collected = collSet.getCollectedCount(progress);
        int required = collSet.getRequiredCount();

        // 构建图标物品
        ItemStack displayItem;
        String iconKey = collSet.getIcon();
        if (iconKey != null && !iconKey.isEmpty()) {
            ItemStack customIcon = ItemUtil.createItemFromKey(plugin, iconKey);
            if (customIcon != null && customIcon.getType() != Material.AIR) {
                displayItem = customIcon;
            } else {
                displayItem = new ItemStack(complete ? Material.GOLD_BLOCK : Material.IRON_BLOCK);
            }
        } else {
            displayItem = new ItemStack(complete ? Material.GOLD_BLOCK : Material.IRON_BLOCK);
        }

        // name 支持 MiniMessage（通过 legacy 被换显示）
        ItemUtil.setDisplayName(displayItem, (complete ? "§a§l" : "§e§l") + MessageUtil.convertMiniMessageToLegacy(collSet.getName()));

        List<String> lore = new ArrayList<>();
        for (String desc : collSet.getDescription()) {
            lore.add(MessageUtil.convertMiniMessageToLegacy(desc));
        }
        lore.add("");
        lore.add("§7进度: §e" + collected + "§7/§e" + required);

        // 显示具体收集条件
        lore.add("");
        for (CollectionSet.RequireEntry req : collSet.getRequires()) {
            Set<String> rewards = progress.get(req.getMachineId());
            boolean has = rewards != null && rewards.contains(req.getRewardId());
            String status = has ? "§a✔" : "§c✘";

            GachaMachine machine = plugin.getGachaManager().getMachine(req.getMachineId());
            String machineName = machine != null ? MessageUtil.convertMiniMessageToLegacy(machine.getName()) : req.getMachineId();
            String rewardName = getRewardDisplayName(req.getMachineId(), req.getRewardId());
            lore.add("  " + status + " §7[" + machineName + "§7] " + rewardName);
        }

        lore.add("");

        if (complete) {
            // 检查是否已领取
            plugin.getGachaManager().hasClaimedCollection(player.getUniqueId(), collSet.getId(), claimed -> {
                if (!player.isOnline()) return;
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;

                    List<String> finalLore = new ArrayList<>(lore);
                    if (claimed && !collSet.isRepeatable()) {
                        finalLore.add("§c已领取");
                        ItemStack iconItem = displayItem.clone();
                        ItemUtil.setLore(iconItem, finalLore);
                        inventory.setItem(finalSlot, iconItem);
                    } else {
                        finalLore.add("§a§l点击领取奖励！");
                        ItemStack iconItem = displayItem.clone();
                        ItemUtil.setLore(iconItem, finalLore);
                        setItem(finalSlot, iconItem, p -> handleClaim(collSet));
                    }
                });
            });
        } else {
            lore.add("§7收集齐所有物品后可领取");
            ItemUtil.setLore(displayItem, lore);
            inventory.setItem(finalSlot, displayItem);
        }
    }

    private void handleClaim(CollectionSet collSet) {
        plugin.getGachaManager().claimCollection(player.getUniqueId(), collSet.getId(), success -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline()) return;

                if (success) {
                    player.sendMessage(plugin.getShopConfig().getComponent("collect-claim-success",
                        MessageUtil.Placeholder.text("name", collSet.getName())));
                    plugin.getGachaManager().giveCollectionReward(player, collSet);
                    // 刷新界面
                    player.closeInventory();
                    new GachaCollectionGUI(plugin, player).open();
                } else {
                    player.sendMessage(plugin.getShopConfig().getComponent("collect-claim-fail"));
                }
            });
        });
    }

    private String getRewardDisplayName(String machineId, String rewardId) {
        GachaMachine machine = plugin.getGachaManager().getMachine(machineId);
        if (machine == null) return rewardId;
        for (GachaReward reward : machine.getRewards()) {
            if (reward.getId().equals(rewardId)) {
                return reward.getPlainDisplayName();
            }
        }
        return rewardId;
    }
}
