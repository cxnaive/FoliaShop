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

public class GachaMachineGUI extends AbstractGUI {

    private final GachaMachine machine;

    public GachaMachineGUI(FoliaShopPlugin plugin, Player player, GachaMachine machine) {
        super(plugin, player, MessageUtil.convertMiniMessageToLegacy(machine.getName()), 27);
        this.machine = machine;
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // 扭蛋机图标
        ItemStack icon = machine.createIconItem(plugin);
        ItemUtil.setDisplayName(icon, MessageUtil.convertMiniMessageToLegacy(machine.getName()));

        // 添加 description 到 lore
        List<String> iconLore = new ArrayList<>();
        for (String desc : machine.getDescription()) {
            iconLore.add(MessageUtil.convertMiniMessageToLegacy(desc));
        }
        ItemUtil.setLore(icon, iconLore);

        setItem(4, icon);

        // 抽奖按钮
        ItemStack rollBtn = new ItemStack(Material.NETHER_STAR);
        ItemUtil.setDisplayName(rollBtn, "§6§l开始抽奖");
        ItemUtil.setLore(rollBtn, List.of(
            "§7每次抽奖花费:",
            "§e" + plugin.getShopConfig().formatCurrency(machine.getCost()),
            "",
            "§e点击开始抽奖！"
        ));
        setItem(13, rollBtn, this::startGacha);

        // 预览奖品按钮
        ItemStack previewBtn = new ItemStack(Material.BOOK);
        ItemUtil.setDisplayName(previewBtn, "§b§l奖品预览");
        ItemUtil.setLore(previewBtn, List.of(
            "§7点击查看所有可能获得的奖品",
            ""
        ));
        setItem(11, previewBtn, p -> {
            p.closeInventory();
            new GachaPreviewGUI(plugin, p, machine).open();
        });

        // 10连抽按钮
        ItemStack tenRollBtn = new ItemStack(Material.DIAMOND);
        ItemUtil.setDisplayName(tenRollBtn, "§b§l10连抽");
        double tenCost = machine.getCost() * 10;
        ItemUtil.setLore(tenRollBtn, List.of(
            "§7连续抽奖10次，获得10个奖品",
            "§7花费:",
            "§e" + plugin.getShopConfig().formatCurrency(tenCost),
            "",
            "§e点击开始10连抽！"
        ));
        setItem(15, tenRollBtn, this::startTenGacha);

        // 累抽自选按钮（仅当启用时显示）
        if (machine.isMilepostEnabled()) {
            renderMilepostButton();
        }

        // 历史记录按钮
        ItemStack historyBtn = new ItemStack(Material.CLOCK);
        ItemUtil.setDisplayName(historyBtn, "§7§l抽奖记录");
        ItemUtil.setLore(historyBtn, List.of(
            "§7查看你的抽奖历史（最近20次）",
            ""
        ));
        setItem(26, historyBtn, p -> {
            p.closeInventory();
            new GachaHistoryGUI(plugin, p, this).open();
        });

        // 返回按钮
        addBackButton(22, () -> new GachaMainGUI(plugin, player).open());
    }

    private void renderMilepostButton() {
        // 先放一个占位按钮，异步加载完成后更新
        ItemStack placeholder = new ItemStack(Material.ITEM_FRAME);
        ItemUtil.setDisplayName(placeholder, "§7§l自选领取");
        ItemUtil.setLore(placeholder, List.of("§7加载中..."));
        setItem(20, placeholder, p -> p.sendMessage("§c正在加载，请稍候..."));

        plugin.getGachaManager().getMilepostProgress(player.getUniqueId(), machine.getId(), info -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;

                ItemStack btn;
                if (info.hasAvailable()) {
                    btn = new ItemStack(Material.GLOW_ITEM_FRAME);
                    ItemUtil.setDisplayName(btn, "§d§l自选领取");
                } else {
                    btn = new ItemStack(Material.ITEM_FRAME);
                    ItemUtil.setDisplayName(btn, "§7§l自选领取");
                }

                List<String> lore = new ArrayList<>();
                lore.add("§7累计抽奖: §e" + info.getTotalDraws() + " §7次");
                lore.add("§7每 §e" + info.getInterval() + " §7次获得1次自选");
                if (info.hasMaxPicks()) {
                    lore.add("§7自选上限: §e" + info.getMaxPicks() + " §7次");
                }
                lore.add("§7已使用: §e" + info.getUsedPicks() + " §7次");
                lore.add("§7可用次数: §e" + info.getAvailablePicks());
                lore.add("");
                if (info.hasAvailable()) {
                    lore.add("§a§l点击选择奖品！");
                } else if (info.hasMaxPicks() && info.getUsedPicks() >= info.getMaxPicks()) {
                    lore.add("§c已达自选上限");
                } else {
                    int nextAt = info.getInterval() - (info.getTotalDraws() % info.getInterval());
                    if (nextAt == info.getInterval()) nextAt = info.getInterval();
                    lore.add("§7再抽 §e" + nextAt + " §7次可获得自选");
                }

                ItemUtil.setLore(btn, lore);

                if (info.hasAvailable()) {
                    setItem(20, btn, p -> {
                        p.closeInventory();
                        new GachaPickGUI(plugin, p, machine, info).open();
                    });
                } else {
                    setItem(20, btn, p -> p.sendMessage("§7自选次数不足，继续抽奖积攒次数吧！"));
                }
            });
        });
    }

    private void startGacha(Player player) {
        double cost = machine.getCost();

        executeGachaWithPayment(player, cost, () -> {
            // 附魔书模式：抽 1 本书（不参与软保底/历史），包装后复用单抽动画 GUI
            if (machine.isBookMode()) {
                List<GachaReward> books = plugin.getGachaManager().drawBookRewards(machine, 1);
                if (books.isEmpty()) {
                    plugin.getEconomyManager().deposit(player, cost);
                    player.sendMessage("§c附魔书抽取失败，已退还花费。请检查 Aiyatsbus 插件与附魔池配置。");
                    return;
                }
                GachaMachine.PityResult result = new GachaMachine.PityResult(books.get(0), false);
                new GachaAnimationGUI(plugin, player, machine, result).open();
                return;
            }

            // CE pack 模式：抽 1 件物品（不参与软保底/历史），包装后复用单抽动画 GUI
            if (machine.isCePackMode()) {
                List<GachaReward> items = plugin.getGachaManager().drawCePackRewards(machine, 1);
                if (items.isEmpty()) {
                    plugin.getEconomyManager().deposit(player, cost);
                    player.sendMessage("§c物品抽取失败，已退还花费。请检查 CraftEngine 与 pack-pool 配置。");
                    return;
                }
                GachaMachine.PityResult result = new GachaMachine.PityResult(items.get(0), false);
                new GachaAnimationGUI(plugin, player, machine, result).open();
                return;
            }

            // 获取保底计数并抽奖
            plugin.getGachaManager().getPityCount(player.getUniqueId(), machine.getId(), pityCount -> {
                if (!player.isOnline()) return;

                // 使用软保底抽奖
                GachaMachine.PityResult result = machine.rollWithPity(pityCount);

                // 立即更新保底计数，确保后续抽奖基于最新状态
                // 注意：即使玩家提前关闭界面，保底计数也不会回滚
                plugin.getGachaManager().updatePityCount(
                    player.getUniqueId(),
                    machine.getId(),
                    machine.isPityTarget(result.reward())
                );

                // 打开动画GUI
                new GachaAnimationGUI(plugin, player, machine, result).open();
            });
        });
    }

    private void startTenGacha(Player player) {
        double totalCost = machine.getCost() * 10;

        executeGachaWithPayment(player, totalCost, () -> {
            // 获取保底计数并进行10连抽（异步查询历史记录）
            plugin.getGachaManager().getPityCount(player.getUniqueId(), machine.getId(), pityCount -> {
                if (!player.isOnline()) return;

                // 执行10连抽（异步查询历史并计算显示次数）
                plugin.getGachaManager().performTenGacha(machine, pityCount, player.getUniqueId(), result -> {
                    if (!player.isOnline()) return;

                    // 打开10连抽动画GUI（含不足 10 本时的退款处理）
                    GachaTenAnimationGUI.openWithRefund(plugin, player, machine, result, totalCost);
                });
            });
        });
    }

    /**
     * 执行扭蛋的通用支付和验证流程
     */
    private void executeGachaWithPayment(Player player, double cost, Runnable onSuccess) {
        plugin.getEconomyManager().hasEnoughAsync(player, cost, hasEnough -> {
            if (!hasEnough) {
                player.sendMessage(plugin.getShopConfig().getComponent("insufficient-funds",
                    java.util.Map.of("cost", String.format("%.2f", cost),
                                    "currency", plugin.getShopConfig().getCurrencyName())));
                return;
            }

            player.closeInventory();
            if (!player.isOnline()) return;

            plugin.getEconomyManager().withdrawAsync(player, cost, success -> {
                if (!success) {
                    player.sendMessage(plugin.getShopConfig().getComponent("insufficient-funds",
                        java.util.Map.of("cost", String.format("%.2f", cost),
                                        "currency", plugin.getShopConfig().getCurrencyName())));
                    return;
                }

                if (!player.isOnline()) {
                    plugin.getEconomyManager().deposit(player, cost);
                    return;
                }

                onSuccess.run();
            });
        });
    }
}
