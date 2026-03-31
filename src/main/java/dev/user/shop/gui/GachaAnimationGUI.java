package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.GachaMachine;
import dev.user.shop.gacha.GachaReward;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class GachaAnimationGUI extends AbstractGUI {

    private enum AnimationState { PENDING, COMPLETED, CANCELLED }

    private static final int[] MIDDLE_ROW_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int CENTER_SLOT = 13;
    private static final int VISIBLE_SLOTS = 5;
    private static final int[] VISIBLE_SLOTS_ARRAY = {11, 12, 13, 14, 15};

    private final GachaMachine machine;
    private final GachaReward finalReward;
    private final boolean isPityTriggered;
    private final List<ItemStack> animationItems;
    private final LinkedList<ItemStack> rollingItems;
    private final AtomicInteger animationTick = new AtomicInteger(0);
    private final int animationDuration;
    private final Random random;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask animationTask;
    private final AtomicReference<AnimationState> state = new AtomicReference<>(AnimationState.PENDING);

    private int currentSpeed = 1;
    private int speedCounter = 0;
    private boolean isSlowingDown = false;
    private int slowdownStartTick = 0;

    public GachaAnimationGUI(FoliaShopPlugin plugin, Player player, GachaMachine machine, GachaMachine.PityResult pityResult) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("gacha-animation"), 27);
        this.machine = machine;
        this.finalReward = pityResult.reward();
        this.isPityTriggered = pityResult.isPityTriggered();
        this.animationDuration = machine.getAnimationDuration() * 20;
        this.random = new Random();
        this.animationItems = machine.getAnimationItems();
        this.rollingItems = new LinkedList<>();
        for (int i = 0; i < VISIBLE_SLOTS + 10; i++) {
            rollingItems.add(getRandomAnimationItem());
        }
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);
        startAnimation();
    }

    private ItemStack getRandomAnimationItem() {
        return animationItems.get(random.nextInt(animationItems.size()));
    }

    private void startAnimation() {
        player.sendMessage(plugin.getShopConfig().getComponent("gacha-start"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, 2.0f);
        slowdownStartTick = (int) (animationDuration * 0.7);

        animationTask = player.getScheduler().runAtFixedRate(plugin, task -> {
            // 只检查玩家是否在线，不比较库存（避免引用比较问题）
            // 如果玩家关闭GUI，onClose会被调用，动画会在那里处理
            if (!player.isOnline()) {
                task.cancel();
                return;
            }

            int tick = animationTick.incrementAndGet();
            if (tick >= animationDuration) {
                task.cancel();
                finishAnimation();
                return;
            }

            updateSpeed(tick);
            speedCounter++;
            if (speedCounter >= currentSpeed) {
                speedCounter = 0;
                updateRollingAnimation();
            }
        }, null, 1L, 1L);
    }

    private void updateSpeed(int tick) {
        if (!isSlowingDown && tick >= slowdownStartTick) {
            isSlowingDown = true;
        }

        if (isSlowingDown) {
            int ticksSinceSlowdown = tick - slowdownStartTick;
            if (ticksSinceSlowdown < 10) currentSpeed = 1;
            else if (ticksSinceSlowdown < 20) currentSpeed = 2;
            else if (ticksSinceSlowdown < 35) currentSpeed = 3;
            else if (ticksSinceSlowdown < 50) currentSpeed = 4;
            else if (ticksSinceSlowdown < 70) currentSpeed = 5;
            else if (ticksSinceSlowdown < 90) currentSpeed = 6;
            else if (ticksSinceSlowdown < 110) currentSpeed = 8;
            else currentSpeed = 10;
        } else {
            currentSpeed = 1;
        }
    }

    private void updateRollingAnimation() {
        if (!rollingItems.isEmpty()) {
            rollingItems.pollFirst();
        }

        int remainingTicks = animationDuration - animationTick.get();
        if (remainingTicks <= 15 && remainingTicks > 5) {
            rollingItems.addLast(finalReward.getDisplayItem());
        } else {
            rollingItems.addLast(getRandomAnimationItem());
        }

        updateVisibleSlots();
        playTickSound();
    }

    private void updateVisibleSlots() {
        int queueSize = rollingItems.size();
        for (int i = 0; i < VISIBLE_SLOTS; i++) {
            int slot = VISIBLE_SLOTS_ARRAY[i];
            int queueIndex = queueSize - VISIBLE_SLOTS + i;
            if (queueIndex >= 0 && queueIndex < queueSize) {
                ItemStack item = rollingItems.get(queueIndex);
                inventory.setItem(slot, item != null ? item.clone() : null);
            } else {
                inventory.setItem(slot, null);
            }
        }
    }

    private void playTickSound() {
        float pitch = Math.min(2.0f, 0.8f + (1.0f / currentSpeed));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 0.5f, pitch);
    }

    private void finishAnimation() {
        if (!state.compareAndSet(AnimationState.PENDING, AnimationState.COMPLETED)) {
            return;
        }

        ItemStack rewardItem = finalReward.getDisplayItem();
        if (rewardItem != null) {
            ItemStack display = rewardItem.clone();
            display.setAmount(finalReward.getAmount());
            finalReward.setTotalProbability(machine.getTotalProbability());

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7稀有度: " + finalReward.getRarityColor() + finalReward.getRarityPercent());
            lore.add("");
            lore.add("§a恭喜获得！");
            ItemUtil.addLore(display, lore);
            inventory.setItem(CENTER_SLOT, display);
        }

        for (int slot : VISIBLE_SLOTS_ARRAY) {
            if (slot != CENTER_SLOT) {
                inventory.setItem(slot, null);
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
        plugin.getServer().getRegionScheduler().runDelayed(plugin, player.getLocation(), t -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 1.0f, 2.0f);
            }
        }, 5L);

        giveRewardAndUpdatePity();
        showResult();
    }

    private void giveRewardAndUpdatePity() {
        giveReward();
        // 保底计数已在抽奖计算时更新，这里只记录抽奖历史
    }

    public void cancelAnimation() {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }
    }

    private void showResult() {
        addCloseButton(22);
        ItemStack againBtn = new ItemStack(Material.NETHER_STAR);
        ItemUtil.setDisplayName(againBtn, "§6§l再抽一次");
        ItemUtil.setLore(againBtn, List.of(
            "§7花费 " + plugin.getShopConfig().formatCurrency(machine.getCost()) + " 再次抽奖"
        ));
        setItem(26, againBtn, p -> {
            p.closeInventory();
            new GachaMachineGUI(plugin, p, machine).open();
        });
    }

    private void giveReward() {
        ItemStack rewardItem = finalReward.getDisplayItem();
        if (rewardItem == null) return;

        ItemStack give = rewardItem.clone();
        give.setAmount(finalReward.getAmount());

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), give);
            player.sendMessage(Component.text("背包已满，物品已掉落在地上！").color(NamedTextColor.YELLOW));
        } else {
            player.getInventory().addItem(give);
        }

        // 使用 Component API 发送抽奖结果消息
        Component resultMessage = plugin.getShopConfig().getItemMessage(
            "gacha-result",
            "item",
            rewardItem,
            Map.of()
        );
        player.sendMessage(resultMessage);

        if (machine.shouldBroadcast(finalReward)) {
            String broadcastTemplate = plugin.getShopConfig().getRawMessage("gacha-broadcast");
            plugin.getGachaManager().getDrawsSinceLastReward(
                player.getUniqueId(), machine.getId(), finalReward.getId(),
                draws -> {
                    String itemNameForBroadcast = ItemUtil.getDisplayName(rewardItem);
                    Component broadcastComponent = MessageUtil.createGachaBroadcast(
                        broadcastTemplate, player.getName(), machine.getName(), itemNameForBroadcast, draws);
                    plugin.getServer().broadcast(broadcastComponent);
                }
            );
        }

        plugin.getGachaManager().logGacha(
            player.getUniqueId(), player.getName(),
            machine.getId(), finalReward, machine.getCost()
        );
    }

    @Override
    public void onClose() {
        super.onClose();
        cancelAnimation();
        // 如果动画未完成，强制完成（finishAnimation 内部有 CAS 保护，不会重复执行）
        if (state.get() == AnimationState.PENDING) {
            finishAnimation();
        }
    }
}
