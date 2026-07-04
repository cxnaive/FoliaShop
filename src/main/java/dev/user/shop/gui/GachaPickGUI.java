package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.GachaMachine;
import dev.user.shop.gacha.GachaManager;
import dev.user.shop.gacha.GachaReward;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 累抽自选奖品界面
 * 玩家可以从扭蛋机奖品池中选择一个奖品
 */
public class GachaPickGUI extends AbstractGUI {

    private final GachaMachine machine;
    private GachaManager.MilepostInfo milepostInfo;
    private int page = 0;

    public GachaPickGUI(FoliaShopPlugin plugin, Player player, GachaMachine machine, GachaManager.MilepostInfo milepostInfo) {
        super(plugin, player, "§d§l自选奖品 - " + MessageUtil.convertMiniMessageToLegacy(machine.getName()), 54);
        this.machine = machine;
        this.milepostInfo = milepostInfo;
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        List<GachaReward> rewards = machine.getRewards();
        int itemsPerPage = 28; // 7 * 4 格内容区域
        int totalPages = Math.max(1, (int) Math.ceil((double) rewards.size() / itemsPerPage));

        if (page >= totalPages) page = 0;

        // 顶部信息栏 (slot 4)
        ItemStack infoItem = new ItemStack(Material.NETHER_STAR);
        ItemUtil.setDisplayName(infoItem, "§d§l可用自选次数: §e§l" + milepostInfo.getAvailablePicks());
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7累计抽奖: §e" + milepostInfo.getTotalDraws() + " §7次");
        infoLore.add("§7每 §e" + milepostInfo.getInterval() + " §7次获得1次自选");
        if (milepostInfo.hasMaxPicks()) {
            infoLore.add("§7上限: §e" + milepostInfo.getMaxPicks() + " §7次");
        }
        infoLore.add("§7已使用: §e" + milepostInfo.getUsedPicks() + " §7次");
        ItemUtil.setLore(infoItem, infoLore);
        setItem(4, infoItem);

        // 渲染奖品列表
        int startIdx = page * itemsPerPage;
        int slot = 10;
        for (int i = startIdx; i < Math.min(startIdx + itemsPerPage, rewards.size()); i++) {
            GachaReward reward = rewards.get(i);

            ItemStack displayItem = reward.getDisplayItem();
            if (displayItem != null) {
                displayItem = displayItem.clone();
                displayItem.setAmount(Math.min(reward.getAmount(), 64));
            } else {
                displayItem = new ItemStack(Material.STONE);
            }

            // 构建lore
            List<String> lore = new ArrayList<>();
            lore.add("§7稀有度: " + reward.getRarityColor() + reward.getRarityPercent());
            lore.add("§7数量: §f" + reward.getAmount());
            lore.add("");
            lore.add("§a§l点击选择此奖品！");

            ItemUtil.setLore(displayItem, lore);

            // 跳过边框位置
            while (slot < 44 && (slot % 9 == 0 || slot % 9 == 8)) {
                slot++;
            }
            if (slot >= 44) break;

            final int rewardIndex = i;
            setItem(slot, displayItem, p -> handlePick(rewardIndex));
            slot++;
        }

        // 分页按钮
        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemUtil.setDisplayName(prevBtn, "§a上一页");
            setItem(45, prevBtn, p -> {
                page--;
                refresh();
            });
        }

        if (page < totalPages - 1) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemUtil.setDisplayName(nextBtn, "§a下一页");
            setItem(53, nextBtn, p -> {
                page++;
                refresh();
            });
        }

        // 返回按钮
        addBackButton(49, () -> TargetedBookMachineGUI.openFor(plugin, player, machine));
    }

    private void handlePick(int rewardIndex) {
        List<GachaReward> rewards = machine.getRewards();
        if (rewardIndex < 0 || rewardIndex >= rewards.size()) return;

        GachaReward reward = rewards.get(rewardIndex);

        plugin.getGachaManager().usePick(
            player.getUniqueId(), machine.getId(), reward, player.getName(),
            success -> {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    if (!player.isOnline()) return;

                    if (success) {
                        // 发放物品
                        ItemStack giveItem = reward.getDisplayItem();
                        if (giveItem != null) {
                            giveItem = giveItem.clone();
                            giveItem.setAmount(reward.getAmount());
                            if (player.getInventory().firstEmpty() == -1) {
                                player.getWorld().dropItemNaturally(player.getLocation(), giveItem);
                                player.sendMessage("§e背包已满，物品已掉落在地上！");
                            } else {
                                player.getInventory().addItem(giveItem);
                            }
                        }

                        player.sendMessage("§a§l[自选] §7你选择了 §f" + reward.getPlainDisplayName());
                        player.closeInventory();

                        // 刷新扭蛋机界面
                        TargetedBookMachineGUI.openFor(plugin, player, machine);
                    } else {
                        player.sendMessage("§c自选失败，可用次数不足或发生错误");
                        player.closeInventory();
                        TargetedBookMachineGUI.openFor(plugin, player, machine);
                    }
                });
            }
        );
    }

    private void refresh() {
        plugin.getGachaManager().getMilepostProgress(player.getUniqueId(), machine.getId(), info -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
                this.milepostInfo = info;
                inventory.clear();
                actions.clear();
                initialize();
                player.updateInventory();
            });
        });
    }
}
