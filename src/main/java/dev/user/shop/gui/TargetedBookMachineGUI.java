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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定向附魔书扭蛋机 GUI（书模式 + target-filter）。
 * <p>
 * 多一个输入格（slot 13），玩家放入武器/工具后，抽书前按该物品过滤掉不适用的附魔，
 * 保证出的书一定能在该武器上用。武器本身不消耗，关闭 GUI 时由 GUIListener 退还。
 * 复用现有动画/结果/发放链路（合成 GachaReward）。
 */
public class TargetedBookMachineGUI extends AbstractGUI {

    public static final int INPUT_SLOT = 13;

    private final GachaMachine machine;

    public TargetedBookMachineGUI(FoliaShopPlugin plugin, Player player, GachaMachine machine) {
        super(plugin, player, MessageUtil.convertMiniMessageToLegacy(machine.getName()), 27);
        this.machine = machine;
    }

    /**
     * 判断机器是否为定向附魔书模式（书模式 + target-filter）。路由判断的唯一来源。
     */
    public static boolean isTargeted(GachaMachine machine) {
        return machine.isBookMode() && machine.getEnchantPool() != null && machine.getEnchantPool().isTargetFilter();
    }

    /**
     * 按机器类型打开对应的机器 GUI：定向书模式(target-filter)开本类，否则开普通 GachaMachineGUI。
     * 供扭蛋中心、动画/结果 GUI 的「再抽」按钮统一调用。
     */
    public static void openFor(FoliaShopPlugin plugin, Player player, GachaMachine machine) {
        if (isTargeted(machine)) {
            new TargetedBookMachineGUI(plugin, player, machine).open();
        } else {
            new GachaMachineGUI(plugin, player, machine).open();
        }
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // 信息（slot 4）
        ItemStack info = new ItemStack(Material.PAPER);
        ItemUtil.setDisplayName(info, "§e§l定向附魔书");
        double singleCost = machine.getCost();
        double tenCost = machine.getCost() * 10;
        ItemUtil.setLore(info, List.of(
            "§7把要附魔的武器/工具放入中间格子",
            "§7抽出的书必定适用于该物品",
            "",
            "§a单抽: §e" + plugin.getShopConfig().formatCurrency(singleCost),
            "§b十连: §e" + plugin.getShopConfig().formatCurrency(tenCost)
        ));
        setItem(4, info);

        // 输入格 slot 13：保持空，由玩家放入（listener 放行）

        // 单抽按钮（slot 11）
        ItemStack singleBtn = new ItemStack(Material.EMERALD);
        ItemUtil.setDisplayName(singleBtn, "§a§l单抽 §7(定向)");
        ItemUtil.setLore(singleBtn, List.of("§7花费 §e" + plugin.getShopConfig().formatCurrency(singleCost) + " §7抽 1 本适用附魔书"));
        setItem(11, singleBtn, this::startGacha);

        // 十连按钮（slot 15）
        ItemStack tenBtn = new ItemStack(Material.DIAMOND);
        ItemUtil.setDisplayName(tenBtn, "§b§l十连抽 §7(定向)");
        ItemUtil.setLore(tenBtn, List.of("§7花费 §e" + plugin.getShopConfig().formatCurrency(tenCost) + " §7抽 10 本适用附魔书"));
        setItem(15, tenBtn, this::startTenGacha);

        // 预览按钮（slot 20）
        ItemStack previewBtn = new ItemStack(Material.BOOK);
        ItemUtil.setDisplayName(previewBtn, "§e附魔池预览");
        ItemUtil.setLore(previewBtn, List.of("§7查看附魔池（实际抽取会按放入物品过滤）"));
        setItem(20, previewBtn, p -> new GachaPreviewGUI(plugin, p, machine).open());

        // 返回按钮（slot 22）
        addBackButton(22, () -> new GachaMainGUI(plugin, player).open());
    }

    /** listener 用来判断点击是否落在输入格 */
    public boolean isInputSlot(int slot) {
        return slot == INPUT_SLOT;
    }

    public List<Integer> getInputSlots() {
        return List.of(INPUT_SLOT);
    }

    private void startGacha(Player player) {
        ItemStack target = inventory.getItem(INPUT_SLOT);
        if (target == null || target.getType().isAir()) {
            player.sendMessage("§c请先放入要定向的武器/工具！");
            return;
        }
        final ItemStack targetClone = target.clone();
        double cost = machine.getCost();

        executeGachaWithPayment(player, cost, () -> {
            List<GachaReward> books = plugin.getGachaManager().drawBookRewards(machine, 1, targetClone);
            if (books.isEmpty()) {
                plugin.getEconomyManager().deposit(player, cost);
                player.sendMessage("§c该物品没有可定向的附魔，已退还花费。");
                return;
            }
            GachaMachine.PityResult result = new GachaMachine.PityResult(books.get(0), false);
            new GachaAnimationGUI(plugin, player, machine, result).open();
        });
    }

    private void startTenGacha(Player player) {
        ItemStack target = inventory.getItem(INPUT_SLOT);
        if (target == null || target.getType().isAir()) {
            player.sendMessage("§c请先放入要定向的武器/工具！");
            return;
        }
        final ItemStack targetClone = target.clone();
        double totalCost = machine.getCost() * 10;

        executeGachaWithPayment(player, totalCost, () -> {
            // 直接抽 10 本（带定向过滤），不走 performTenGacha（它不知道输入物品）
            List<GachaReward> books = plugin.getGachaManager().drawBookRewards(machine, 10, targetClone);
            GachaManager.TenGachaResult result = new GachaManager.TenGachaResult(books, 0, 0, new HashMap<>());
            GachaTenAnimationGUI.openWithRefund(plugin, player, machine, result, totalCost);
        });
    }

    /**
     * 扣费通用流程（与 GachaMachineGUI 一致）：余额检查 → 扣款 → 成功回调。
     * 注意：余额足够后会 closeInventory，会触发 GUIListener 退还输入格的武器（玩家不丢失武器）。
     */
    private void executeGachaWithPayment(Player player, double cost, Runnable onSuccess) {
        plugin.getEconomyManager().hasEnoughAsync(player, cost, hasEnough -> {
            if (!hasEnough) {
                player.sendMessage(plugin.getShopConfig().getComponent("insufficient-funds",
                    Map.of("cost", String.format("%.2f", cost),
                           "currency", plugin.getShopConfig().getCurrencyName())));
                return;
            }
            player.closeInventory();
            if (!player.isOnline()) return;

            plugin.getEconomyManager().withdrawAsync(player, cost, success -> {
                if (!success) {
                    player.sendMessage(plugin.getShopConfig().getComponent("insufficient-funds",
                        Map.of("cost", String.format("%.2f", cost),
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
