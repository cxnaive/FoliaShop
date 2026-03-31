package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.GachaMachine;
import dev.user.shop.gacha.GachaManager;
import dev.user.shop.gacha.GachaReward;
import dev.user.shop.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class GachaTenAnimationGUI extends AbstractGUI {

    private enum AnimationState { PENDING, COMPLETED, CANCELLED }

    private static final int[] DISPLAY_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private final GachaMachine machine;
    private final List<GachaReward> finalRewards;
    private final int finalPityCount;
    private final int triggeredCount;
    private final Map<String, Integer> rewardDrawCounts; // 缓存的显示次数
    private final List<ItemStack> animationItems;
    private final AtomicInteger animationTick = new AtomicInteger(0);
    private final int animationDuration;
    private final Random random;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask animationTask;
    private final AtomicReference<AnimationState> state = new AtomicReference<>(AnimationState.PENDING);

    private int currentSpeed = 1;
    private int speedCounter = 0;
    private boolean isSlowingDown = false;
    private int slowdownStartTick = 0;
    private int revealedCount = 0;

    public GachaTenAnimationGUI(FoliaShopPlugin plugin, Player player, GachaMachine machine, GachaManager.TenGachaResult result) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("gacha-ten-animation"), 36);
        this.machine = machine;
        this.finalRewards = result.rewards();
        this.finalPityCount = result.finalPityCount();
        this.triggeredCount = result.triggeredCount();
        this.rewardDrawCounts = result.rewardDrawCounts();
        this.animationDuration = machine.getAnimationDurationTen() * 20;
        this.random = new Random();
        this.animationItems = machine.getAnimationItems();
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        ItemStack titleItem = new ItemStack(Material.NETHER_STAR);
        ItemUtil.setDisplayName(titleItem, "§e§l10连抽抽奖中...");
        ItemUtil.setLore(titleItem, List.of(
            "§7正在抽取10个奖品...",
            "§7请稍候！"
        ));
        setItem(4, titleItem);

        for (int slot : DISPLAY_SLOTS) {
            inventory.setItem(slot, getRandomAnimationItem());
        }

        startAnimation();
    }

    private ItemStack getRandomAnimationItem() {
        return animationItems.get(random.nextInt(animationItems.size()));
    }

    private void startAnimation() {
        player.sendMessage(plugin.getShopConfig().getComponent("gacha-start"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, 2.0f);
        slowdownStartTick = (int) (animationDuration * 0.6);

        animationTask = player.getScheduler().runAtFixedRate(plugin, task -> {
            // 只检查玩家是否在线，不比较库存（避免引用比较问题）
            // 如果玩家关闭GUI，onClose会被调用，动画会在那里处理
            if (!player.isOnline()) {
                task.cancel();
                return;
            }

            int tick = animationTick.incrementAndGet();
            if (tick >= animationDuration || revealedCount >= 10) {
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
            if (ticksSinceSlowdown < 15) currentSpeed = 1;
            else if (ticksSinceSlowdown < 30) currentSpeed = 2;
            else if (ticksSinceSlowdown < 50) currentSpeed = 3;
            else if (ticksSinceSlowdown < 70) currentSpeed = 4;
            else if (ticksSinceSlowdown < 90) currentSpeed = 5;
            else currentSpeed = 8;
        } else {
            currentSpeed = 1;
        }
    }

    private void updateRollingAnimation() {
        for (int i = revealedCount; i < 10; i++) {
            int slot = DISPLAY_SLOTS[i];
            int remainingTicks = animationDuration - animationTick.get();
            int expectedReveals = 10 - (remainingTicks / (animationDuration / 10));

            if (i < expectedReveals && i == revealedCount && random.nextInt(3) == 0) {
                revealReward(i);
                revealedCount++;
                float pitch = 0.8f + (revealedCount * 0.1f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.8f, pitch);
                break;
            } else {
                inventory.setItem(slot, getRandomAnimationItem());
            }
        }
        playTickSound();
    }

    private void revealReward(int index) {
        GachaReward reward = finalRewards.get(index);
        ItemStack display = reward.getDisplayItem();
        if (display != null) {
            display = display.clone();
            display.setAmount(reward.getAmount());
            reward.setTotalProbability(machine.getTotalProbability());

            List<String> lore = new ArrayList<>();
            lore.add("§7稀有度: " + reward.getRarityColor() + reward.getRarityPercent());
            lore.add("§e已揭示!");
            ItemUtil.addLore(display, lore);

            inventory.setItem(DISPLAY_SLOTS[index], display);
        }
    }

    private void playTickSound() {
        float pitch = Math.min(2.0f, 0.8f + (1.0f / currentSpeed));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 0.3f, pitch);
    }

    private void finishAnimation() {
        for (int i = 0; i < 10; i++) {
            revealFinalReward(i);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.8f, 1.0f);

        showPityMessage();

        player.getScheduler().runDelayed(plugin, t -> {
            if (!state.compareAndSet(AnimationState.PENDING, AnimationState.COMPLETED)) {
                return;
            }
            updatePityCountersAndShowResult();
        }, null, 10L);
    }

    private void updatePityCountersAndShowResult() {
        // 保底计数已在抽奖计算时更新，这里只打开结果界面
        if (player.isOnline()) {
            // 创建 TenGachaResult 并传递缓存的显示次数
            GachaManager.TenGachaResult result = new GachaManager.TenGachaResult(
                finalRewards, finalPityCount, triggeredCount, rewardDrawCounts);
            new GachaTenResultGUI(plugin, player, machine, result).open();
        }
    }

    private void revealFinalReward(int index) {
        GachaReward reward = finalRewards.get(index);
        ItemStack display = reward.getDisplayItem();
        if (display != null) {
            display = display.clone();
            display.setAmount(reward.getAmount());
            reward.setTotalProbability(machine.getTotalProbability());

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7稀有度: " + reward.getRarityColor() + reward.getRarityPercent());
            ItemUtil.addLore(display, lore);

            inventory.setItem(DISPLAY_SLOTS[index], display);
        }
    }

    public void cancelAnimation() {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }
    }

    private void showPityMessage() {
        // 保底触发不再发送提示信息
    }

    @Override
    public void onClose() {
        super.onClose();
        cancelAnimation();

        if (state.get() == AnimationState.PENDING) {
            if (state.compareAndSet(AnimationState.PENDING, AnimationState.COMPLETED)) {
                showPityMessage();
                player.getScheduler().execute(plugin, () -> {
                    updatePityCountersAndShowResult();
                }, null, 1L);
            }
        }
    }
}
