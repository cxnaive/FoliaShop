package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.shop.ShopItem;
import dev.user.shop.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 删除确认对话框
 */
public class ConfirmDeleteGUI extends AbstractGUI {

    private final ShopItem shopItem;
    private final Runnable onConfirm;

    public ConfirmDeleteGUI(FoliaShopPlugin plugin, Player player, ShopItem shopItem, Runnable onConfirm) {
        super(plugin, player, "§4§l确认删除", 27);
        this.shopItem = shopItem;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void initialize() {
        fillBorder();

        // 警告图标
        ItemStack warningItem = createDecorItem("delete", Material.TNT);
        ItemUtil.setDisplayName(warningItem, "§4§l警告: 即将删除物品");
        ItemUtil.setLore(warningItem, List.of(
            "",
            "§7物品ID: §e" + shopItem.getId(),
            "§7物品: §e" + shopItem.getItemKey(),
            "",
            "§c此操作将永久删除该商店物品！",
            "§c该物品将从商店中完全移除！",
            "§c此操作不可撤销！",
            "",
            "§7请在下方选择操作"
        ));
        setItem(13, warningItem);

        // 确认删除按钮
        ItemStack confirmBtn = createDecorItem("confirm", Material.LIME_WOOL);
        ItemUtil.setDisplayName(confirmBtn, "§a§l确认删除");
        ItemUtil.setLore(confirmBtn, List.of(
            "§7点击确认删除该物品",
            "",
            "§c§l警告: 此操作不可撤销！"
        ));
        setItem(11, confirmBtn, p -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
        });

        // 取消按钮
        ItemStack cancelBtn = createDecorItem("cancel", Material.RED_WOOL);
        ItemUtil.setDisplayName(cancelBtn, "§c§l取消");
        ItemUtil.setLore(cancelBtn, List.of(
            "§7点击取消删除操作",
            "",
            "§7返回物品编辑界面"
        ));
        setItem(15, cancelBtn, p -> {
            p.closeInventory();
            new ShopItemEditGUI(plugin, p, shopItem).open();
        });
    }
}
