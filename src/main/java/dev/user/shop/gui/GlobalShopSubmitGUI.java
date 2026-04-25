package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.globalshop.GlobalShopSession;
import dev.user.shop.util.ItemDataUtil;
import dev.user.shop.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 全球商店上架页面
 * 玩家放入物品后点击确认，进入聊天价格输入流程
 */
public class GlobalShopSubmitGUI extends AbstractGUI {

    private final List<Integer> submitSlots = new ArrayList<>();

    public GlobalShopSubmitGUI(FoliaShopPlugin plugin, Player player) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("globalshop-submit"), 54);
    }

    @Override
    protected void initialize() {
        fillBorder();

        // 提示信息
        ItemStack info = createDecorItem("info", Material.PAPER);
        ItemUtil.setDisplayName(info, "§e§l上架说明");
        double fee = plugin.getShopConfig().getGlobalShopListingFee();
        String feeText = fee > 0 ? "§7上架费用: §e" + String.format("%.2f", fee) + " " + plugin.getShopConfig().getCurrencyName() : "§7上架费用: §a免费";
        ItemUtil.setLore(info, List.of(
                "§7将物品放入下方格子（每次只能上架一组）",
                "§7点击确认按钮后输入售价",
                "",
                feeText,
                "§7租期: §e" + plugin.getShopConfig().getGlobalShopRentalPeriodDays() + "天"
        ));
        setItem(4, info);

        // 设置可放入物品的格子（第2-5行，避开边框）
        submitSlots.clear();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                submitSlots.add(row * 9 + col);
            }
        }

        // 确认上架按钮
        ItemStack confirm = createDecorItem("confirm", Material.LIME_WOOL);
        ItemUtil.setDisplayName(confirm, "§a§l确认上架");
        ItemUtil.setLore(confirm, List.of("§7点击上架放入的物品", "", "§e输入售价后正式上架"));
        setItem(49, confirm, this::confirmSubmit);

        // 返回按钮
        addBackButton(45, () -> new GlobalShopBrowseGUI(plugin, player).open());

        // 关闭按钮
        addCloseButton(52);
    }

    private void confirmSubmit(Player player) {
        // 检查出售权限
        if (!player.hasPermission("foliashop.globalshop.sell")) {
            player.sendMessage("§c你没有出售物品的权限");
            return;
        }

        // 找到第一个有物品的槽位
        ItemStack firstItem = null;
        int firstSlot = -1;
        for (int slot : submitSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                firstItem = item.clone();
                firstSlot = slot;
                break;
            }
        }

        if (firstItem == null) {
            player.sendMessage("§c请先放入要上架的物品！");
            return;
        }

        // 先序列化物品（在从 GUI 移除之前，避免序列化异常导致物品丢失）
        byte[] itemData;
        try {
            itemData = ItemDataUtil.serializeItem(firstItem);
        } catch (Exception e) {
            player.sendMessage("§c物品序列化失败，请更换物品后重试");
            return;
        }
        String itemKey = ItemUtil.getItemKey(firstItem);
        String displayName = ItemUtil.getDisplayName(firstItem);
        if (displayName == null || displayName.isEmpty()) {
            displayName = itemKey;
        }

        // 序列化成功，从 GUI 中取出物品
        inventory.setItem(firstSlot, null);

        // 创建会话
        plugin.getGlobalShopManager().getSessionManager().startSession(
                player, itemData, itemKey, displayName, firstItem.getAmount());

        // 关闭 GUI，进入聊天价格输入
        player.closeInventory();
        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§6§l上架物品: §f" + displayName + " §7x" + firstItem.getAmount());
        player.sendMessage("§7请输入§e总售价§7（输入'取消'取消）");
        player.sendMessage("§7当前上架费: §e" + (plugin.getShopConfig().getGlobalShopListingFee() > 0
                ? String.format("%.2f", plugin.getShopConfig().getGlobalShopListingFee()) + " " + plugin.getShopConfig().getCurrencyName()
                : "免费"));
        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    public boolean isSubmitSlot(int slot) {
        return submitSlots.contains(slot);
    }

    public List<Integer> getSubmitSlots() {
        return submitSlots;
    }
}
