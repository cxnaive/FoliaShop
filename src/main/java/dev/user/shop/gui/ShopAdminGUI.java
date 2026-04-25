package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.shop.ShopItem;
import dev.user.shop.shop.ShopManager;
import dev.user.shop.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ShopAdminGUI extends AbstractGUI {

    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 28; // 4行 * 7列（避开边框）

    public ShopAdminGUI(FoliaShopPlugin plugin, Player player) {
        super(plugin, player, "§c§l商店管理", 54);
    }

    @Override
    protected void initialize() {
        fillBorder();
        // 打开管理界面时先从数据库刷新库存（跨服同步）
        plugin.getShopManager().refreshAllStocksFromDatabase(count -> {
            // 在主线程刷新界面
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                loadPage();
            });
        });
    }

    private void loadPage() {
        // 确保页码不会小于0
        if (currentPage < 0) currentPage = 0;

        // 清除之前的物品（保留边框）
        for (int i = 10; i <= 43; i++) {
            if (i % 9 != 0 && i % 9 != 8) {
                inventory.setItem(i, null);
            }
        }

        List<ShopItem> allItems = new ArrayList<>(plugin.getShopManager().getAllItems());
        allItems.sort(Comparator.comparing(ShopItem::getId));

        int totalPages = (allItems.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        if (currentPage >= totalPages) currentPage = Math.max(0, totalPages - 1);

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allItems.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            ShopItem item = allItems.get(i);

            // 跳过边框位置
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }

            setItem(slot, createItemButton(item), p -> openItemEditGUI(p, item));
            slot++;
        }

        // 上一页按钮
        if (currentPage > 0) {
            ItemStack prevBtn = createDecorItem("prev-page", Material.ARROW);
            ItemUtil.setDisplayName(prevBtn, "§e上一页");
            setItem(45, prevBtn, p -> {
                currentPage--;
                loadPage();
            });
        }

        // 下一页按钮
        if (currentPage < totalPages - 1) {
            ItemStack nextBtn = createDecorItem("next-page", Material.ARROW);
            ItemUtil.setDisplayName(nextBtn, "§e下一页");
            setItem(53, nextBtn, p -> {
                currentPage++;
                loadPage();
            });
        }

        // 页码显示
        ItemStack pageInfo = createDecorItem("page-info", Material.PAPER);
        ItemUtil.setDisplayName(pageInfo, "§7页码: §e" + (currentPage + 1) + "§7/§e" + totalPages);
        setItem(49, pageInfo);

        // 关闭按钮
        addCloseButton(52);
    }

    private ItemStack createItemButton(ShopItem item) {
        ItemStack display = item.getDisplayItem();
        if (display == null) {
            display = createDecorItem("empty", Material.BARRIER);
        }
        display = display.clone();

        List<String> lore = new ArrayList<>();
        lore.add("§7ID: §f" + item.getId());
        lore.add("§7分类: §f" + item.getCategory());
        lore.add("§7购买价: §e" + plugin.getShopConfig().formatCurrency(item.getBuyPrice()));
        lore.add("§7出售价: §e" + plugin.getShopConfig().formatCurrency(item.getSellPrice()));
        if (item.hasUnlimitedStock()) {
            lore.add("§7库存: §a无限");
        } else {
            lore.add("§7库存: §e" + item.getStock());
        }
        lore.add("");
        lore.add("§e点击管理库存");

        ItemUtil.addLore(display, lore);
        return display;
    }

    private void openItemEditGUI(Player player, ShopItem item) {
        player.closeInventory();
        new ShopItemEditGUI(plugin, player, item).open();
    }
}
