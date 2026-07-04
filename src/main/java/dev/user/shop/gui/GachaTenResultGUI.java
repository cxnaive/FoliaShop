package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.GachaMachine;
import dev.user.shop.gacha.GachaManager;
import dev.user.shop.gacha.GachaReward;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GachaTenResultGUI extends AbstractGUI {

    private static final int[] DISPLAY_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private final GachaMachine machine;
    private final List<GachaReward> rewards;
    private final Map<Integer, Boolean> claimedRewards; // 记录已领取的奖品
    private final Map<String, Integer> rewardDrawCountsCache; // 缓存每个奖品的抽奖次数（用于播报）

    public GachaTenResultGUI(FoliaShopPlugin plugin, Player player, GachaMachine machine, GachaManager.TenGachaResult result) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("gacha-ten-result"), 36);
        this.machine = machine;
        this.rewards = result.rewards();
        this.claimedRewards = new HashMap<>();
        // 从 TenGachaResult 获取缓存的显示次数
        this.rewardDrawCountsCache = result.rewardDrawCounts() != null ? result.rewardDrawCounts() : new HashMap<>();
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // 标题
        ItemStack titleItem = new ItemStack(Material.NETHER_STAR);
        ItemUtil.setDisplayName(titleItem, "§e§l10连抽结果");

        // 统计信息
        long rareCount = rewards.stream().filter(r -> machine.shouldBroadcast(r)).count();
        ItemUtil.setLore(titleItem, List.of(
            "§7共获得 " + rewards.size() + " 个奖品",
            "§7稀有奖品: §e" + rareCount + " 个",
            "",
            "§e点击物品领取对应奖品"
        ));
        setItem(4, titleItem);

        // 显示所有奖品
        for (int i = 0; i < rewards.size() && i < DISPLAY_SLOTS.length; i++) {
            final int index = i;
            GachaReward reward = rewards.get(i);
            ItemStack display = createRewardItem(reward, false);

            setItem(DISPLAY_SLOTS[i], display, p -> claimReward(index));
        }

        // 一键领取按钮
        ItemStack claimAllBtn = new ItemStack(Material.CHEST);
        ItemUtil.setDisplayName(claimAllBtn, "§a§l一键领取全部");
        ItemUtil.setLore(claimAllBtn, List.of(
            "§7点击领取所有未领取的奖品",
            "§7背包满了的物品会掉落在地上"
        ));
        setItem(31, claimAllBtn, p -> claimAllRewards());

        // 再抽一次按钮
        ItemStack againBtn = new ItemStack(Material.DIAMOND);
        ItemUtil.setDisplayName(againBtn, "§6§l再抽10次");
        double tenCost = machine.getCost() * 10;
        ItemUtil.setLore(againBtn, List.of(
            "§7花费 " + plugin.getShopConfig().formatCurrency(tenCost) + " 再次10连抽"
        ));
        setItem(35, againBtn, p -> {
            p.closeInventory();
            // 定向书模式：回定向 GUI 让玩家重新放入武器（武器已被上次关闭退还）
            if (TargetedBookMachineGUI.isTargeted(machine)) {
                TargetedBookMachineGUI.openFor(plugin, p, machine);
                return;
            }
            // 普通模式：直接触发10连抽
            new GachaMachineGUI(plugin, p, machine);
            startTenGachaDirectly(p);
        });

        // 返回按钮
        addBackButton(30, () -> TargetedBookMachineGUI.openFor(plugin, player, machine));

        // 播放结果展示音效
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 1.0f, 1.5f);
    }

    private ItemStack createRewardItem(GachaReward reward, boolean claimed) {
        ItemStack display = reward.getDisplayItem();
        if (display == null) {
            display = new ItemStack(Material.CHEST);
        }
        display = display.clone();
        display.setAmount(reward.getAmount());

        // 设置总概率以计算实际概率
        reward.setTotalProbability(machine.getTotalProbability());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7稀有度: " + reward.getRarityColor() + reward.getRarityPercent());
        if (claimed) {
            lore.add("§a✓ 已领取");
            ItemUtil.setDisplayName(display, "§8§m" + ItemUtil.getDisplayName(reward.getDisplayItem()));
        } else {
            lore.add("§e点击领取");
        }
        ItemUtil.addLore(display, lore);

        return display;
    }

    private void claimReward(int index) {
        if (claimedRewards.getOrDefault(index, false)) {
            player.sendMessage("§c这个奖品已经领取过了！");
            return;
        }

        GachaReward reward = rewards.get(index);
        giveReward(reward, index);
        claimedRewards.put(index, true);

        // 更新显示
        ItemStack claimedItem = createRewardItem(reward, true);
        setItem(DISPLAY_SLOTS[index], claimedItem);

        // 播放音效
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    private void claimAllRewards() {
        int claimed = 0;
        int dropped = 0;

        for (int i = 0; i < rewards.size(); i++) {
            if (claimedRewards.getOrDefault(i, false)) {
                continue;
            }

            GachaReward reward = rewards.get(i);
            boolean wasDropped = giveReward(reward, i);

            // 无论物品是放入背包还是掉落在地上，都标记为已领取
            claimedRewards.put(i, true);

            // 更新显示
            ItemStack claimedItem = createRewardItem(reward, true);
            setItem(DISPLAY_SLOTS[i], claimedItem);

            if (wasDropped) {
                dropped++;
            } else {
                claimed++;
            }
        }

        if (claimed > 0) {
            player.sendMessage("§a成功领取 " + claimed + " 个奖品到背包！");
        }
        if (dropped > 0) {
            player.sendMessage("§e" + dropped + " 个奖品因背包已满掉落在地上！");
        }
        if (claimed > 0 || dropped > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.8f, 1.0f);
        }
    }

    /**
     * 给予玩家奖品
     * @param reward 奖品
     * @param index 奖品索引（用于广播缓存）
     * @return true 如果物品掉落在地上，false 如果成功放入背包
     */
    private boolean giveReward(GachaReward reward, int index) {
        ItemStack rewardItem = reward.getDisplayItem();
        if (rewardItem == null) return false;

        ItemStack give = rewardItem.clone();
        give.setAmount(reward.getAmount());

        // 尝试给予物品
        boolean dropped = false;
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), give);
            dropped = true;
        } else {
            player.getInventory().addItem(give);
        }

        // 广播稀有奖品（使用缓存的显示次数，不再查询数据库）
        if (machine.shouldBroadcast(reward)) {
            String itemName = ItemUtil.getDisplayName(rewardItem);
            String broadcastTemplate = plugin.getShopConfig().getRawMessage("gacha-broadcast");

            // 从缓存获取显示次数（在十连抽计算时已确定）
            Integer cachedCount = rewardDrawCountsCache.get(String.valueOf(index));
            int drawCount;
            if (cachedCount != null) {
                // 缓存的是实际间隔次数，createBroadcastComponent 会 +1 显示
                drawCount = cachedCount;
            } else {
                // 如果没有缓存（不应该发生），默认显示1抽
                drawCount = 0;
            }

            Component broadcastComponent = MessageUtil.createGachaBroadcast(
                broadcastTemplate, player.getName(), machine.getName(), itemName, drawCount);
            plugin.getServer().broadcast(broadcastComponent);
        }

        // 记录抽奖
        plugin.getGachaManager().logGacha(
            player.getUniqueId(), player.getName(),
            machine.getId(), reward, machine.getCost()
        );

        return dropped;
    }

    private void startTenGachaDirectly(Player player) {
        double totalCost = machine.getCost() * 10;

        // 先获取保底计数，确保在扣款前获取最新状态
        plugin.getGachaManager().getPityCount(player.getUniqueId(), machine.getId(), pityCount -> {
            if (!player.isOnline()) {
                return;
            }

            // 异步检查余额并扣款
            plugin.getEconomyManager().hasEnoughAsync(player, totalCost, hasEnough -> {
                if (!hasEnough) {
                    player.sendMessage(plugin.getShopConfig().getComponent("insufficient-funds",
                        java.util.Map.of("cost", String.format("%.2f", totalCost),
                                        "currency", plugin.getShopConfig().getCurrencyName())));
                    return;
                }

                player.closeInventory();

                if (!player.isOnline()) {
                    return;
                }

                // 异步扣除金钱
                plugin.getEconomyManager().withdrawAsync(player, totalCost, success -> {
                    if (!success) {
                        player.sendMessage(plugin.getShopConfig().getComponent("insufficient-funds",
                            java.util.Map.of("cost", String.format("%.2f", totalCost),
                                            "currency", plugin.getShopConfig().getCurrencyName())));
                        return;
                    }

                    if (!player.isOnline()) {
                        // 玩家已掉线，退款
                        plugin.getEconomyManager().deposit(player, totalCost);
                        return;
                    }

                    // 执行10连抽（异步查询历史记录）
                    plugin.getGachaManager().performTenGacha(machine, pityCount, player.getUniqueId(), result -> {
                        // 打开10连抽动画GUI（含不足 10 本时的退款处理）
                        GachaTenAnimationGUI.openWithRefund(plugin, player, machine, result, totalCost);
                    });
                });
            });
        });
    }

    @Override
    public void onClose() {
        super.onClose();

        // 检查是否还有未领取的奖品，如果有则自动发放
        int unclaimedCount = 0;
        int droppedCount = 0;

        for (int i = 0; i < rewards.size(); i++) {
            if (claimedRewards.getOrDefault(i, false)) {
                continue;
            }

            GachaReward reward = rewards.get(i);
            boolean wasDropped = giveReward(reward, i);
            claimedRewards.put(i, true);

            unclaimedCount++;
            if (wasDropped) {
                droppedCount++;
            }
        }

        // 如果有未领取的奖品，发送提示消息
        if (unclaimedCount > 0) {
            if (droppedCount > 0) {
                player.sendMessage("§e你关闭了结果界面，§a" + (unclaimedCount - droppedCount) + " §e个奖品已放入背包，§c" + droppedCount + " §e个奖品因背包已满掉落在地上！");
            } else {
                player.sendMessage("§e你关闭了结果界面，§a" + unclaimedCount + " §e个奖品已自动放入背包！");
            }
        }
    }
}
