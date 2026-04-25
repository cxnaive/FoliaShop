package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.shop.ShopManager;
import dev.user.shop.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TransactionHistoryGUI extends AbstractGUI {

    private final AbstractGUI parentGUI;

    public TransactionHistoryGUI(FoliaShopPlugin plugin, Player player, AbstractGUI parentGUI) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("transaction-history"), 36);
        this.parentGUI = parentGUI;
    }

    @Override
    protected void initialize() {
        fillBorder();

        // 标题
        ItemStack titleItem = createDecorItem("history", Material.BOOK);
        ItemUtil.setDisplayName(titleItem, "§6§l交易记录");
        ItemUtil.setLore(titleItem, List.of(
            "§7显示最近20条交易记录",
            "",
            "§a绿色 §7= 购买",
            "§c红色 §7= 出售"
        ));
        setItem(4, titleItem);

        // 异步加载交易记录
        plugin.getShopManager().getPlayerTransactions(player.getUniqueId(), records -> {
            if (records.isEmpty()) {
                ItemStack emptyItem = createDecorItem("empty", Material.BARRIER);
                ItemUtil.setDisplayName(emptyItem, "§c暂无交易记录");
                setItem(13, emptyItem);
                return;
            }

            int slot = 9;
            for (ShopManager.TransactionRecord record : records) {
                if (slot >= 27) break; // 只显示前18条（留出底部空间）

                ItemStack item = createRecordItem(record);
                setItem(slot++, item);
            }
        });

        // 返回按钮
        addBackButton(31, () -> {
            if (parentGUI != null) {
                parentGUI.open();
            } else {
                new MainMenuGUI(plugin, player).open();
            }
        });

        // 关闭按钮
        addCloseButton(35);
    }

    private ItemStack createRecordItem(ShopManager.TransactionRecord record) {
        Material material = record.isBuy() ? getDecorMaterial("buy-icon", Material.LIME_STAINED_GLASS_PANE) :
                           record.isSell() ? getDecorMaterial("sell-icon", Material.RED_STAINED_GLASS_PANE) :
                           getDecorMaterial("other-icon", Material.GRAY_STAINED_GLASS_PANE);

        ItemStack item = new ItemStack(material);

        String typeStr = record.isBuy() ? "§a购买" : record.isSell() ? "§c出售" : "§7未知";
        String itemName = record.getItemKey();
        // 简化物品ID显示
        if (itemName.startsWith("minecraft:")) {
            itemName = itemName.substring(10);
        }

        ItemUtil.setDisplayName(item, typeStr + " §7" + itemName);
        ItemUtil.setLore(item, List.of(
            "§7时间: §f" + record.getFormattedTime(),
            "§7数量: §f" + record.getAmount(),
            "§7金额: §e" + plugin.getShopConfig().formatCurrency(record.getPrice()),
            ""
        ));

        return item;
    }
}
