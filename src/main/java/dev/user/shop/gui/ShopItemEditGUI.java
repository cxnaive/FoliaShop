package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.shop.ShopItem;
import dev.user.shop.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopItemEditGUI extends AbstractGUI {

    private final ShopItem shopItem;

    public ShopItemEditGUI(FoliaShopPlugin plugin, Player player, ShopItem shopItem) {
        super(plugin, player, "§c编辑: " + shopItem.getId(), 27);
        this.shopItem = shopItem;
    }

    @Override
    protected void initialize() {
        fillBorder();

        // 物品信息展示
        ItemStack infoItem = shopItem.getDisplayItem();
        if (infoItem == null) {
            infoItem = createDecorItem("empty", Material.BARRIER);
        }
        infoItem = infoItem.clone();
        ItemUtil.setDisplayName(infoItem, "§e§l" + shopItem.getId());
        List<String> lore = new ArrayList<>();
        lore.add("§7当前库存: " + getStockDisplay());
        lore.add("§7购买价格: §e" + plugin.getShopConfig().formatCurrency(shopItem.getBuyPrice()));
        lore.add("§7出售价格: §e" + plugin.getShopConfig().formatCurrency(shopItem.getSellPrice()));
        ItemUtil.setLore(infoItem, lore);
        setItem(4, infoItem);

        // 设置为无限库存
        ItemStack unlimitedBtn = createDecorItem("unlimited", Material.NETHER_STAR);
        ItemUtil.setDisplayName(unlimitedBtn, "§a§l设置为无限库存");
        ItemUtil.setLore(unlimitedBtn, List.of("§7点击将库存设置为无限(-1)"));
        setItem(10, unlimitedBtn, p -> setStock(-1));

        // 库存 -10
        ItemStack minus10Btn = createDecorItem("decrease", Material.RED_WOOL);
        ItemUtil.setDisplayName(minus10Btn, "§c§l-10");
        ItemUtil.setLore(minus10Btn, List.of("§7减少10个库存"));
        setItem(11, minus10Btn, p -> changeStock(-10));

        // 库存 -1
        ItemStack minus1Btn = createDecorItem("decrease", Material.RED_WOOL);
        ItemUtil.setDisplayName(minus1Btn, "§c§l-1");
        ItemUtil.setLore(minus1Btn, List.of("§7减少1个库存"));
        setItem(12, minus1Btn, p -> changeStock(-1));

        // 库存 +1
        ItemStack plus1Btn = createDecorItem("increase", Material.LIME_WOOL);
        ItemUtil.setDisplayName(plus1Btn, "§a§l+1");
        ItemUtil.setLore(plus1Btn, List.of("§7增加1个库存"));
        setItem(14, plus1Btn, p -> changeStock(1));

        // 库存 +10
        ItemStack plus10Btn = createDecorItem("increase", Material.LIME_WOOL);
        ItemUtil.setDisplayName(plus10Btn, "§a§l+10");
        ItemUtil.setLore(plus10Btn, List.of("§7增加10个库存"));
        setItem(15, plus10Btn, p -> changeStock(10));

        // 库存 +64
        ItemStack plus64Btn = createDecorItem("increase", Material.LIME_WOOL);
        ItemUtil.setDisplayName(plus64Btn, "§a§l+64");
        ItemUtil.setLore(plus64Btn, List.of("§7增加64个库存"));
        setItem(16, plus64Btn, p -> changeStock(64));

        // 清空库存按钮
        ItemStack clearStockBtn = createDecorItem("clear-stock", Material.BARRIER);
        ItemUtil.setDisplayName(clearStockBtn, "§c§l清空库存");
        ItemUtil.setLore(clearStockBtn, List.of(
            "§7点击将库存设置为 0",
            "§c警告: 此操作不可撤销！"
        ));
        setItem(21, clearStockBtn, p -> {
            shopItem.setStock(0);
            player.sendMessage("§c已清空 §e" + shopItem.getId() + " §c的库存");
            initialize(); // 刷新界面
        });

        // 从配置文件重置按钮
        ItemStack resetBtn = createDecorItem("reset", Material.BOOK);
        ItemUtil.setDisplayName(resetBtn, "§b§l从配置文件重置");
        ItemUtil.setLore(resetBtn, List.of(
            "§7点击从配置文件重新加载该物品的",
            "§7所有配置（包括价格、库存、分类、每日上限等）",
            "",
            "§c警告: 数据库中的修改将被覆盖！"
        ));
        setItem(22, resetBtn, p -> {
            boolean success = plugin.getShopManager().resetItemFromConfig(shopItem.getId());
            if (success) {
                player.sendMessage("§a已从配置文件重置 §e" + shopItem.getId());
                // 重新获取刷新后的物品
                ShopItem refreshedItem = plugin.getShopManager().getItem(shopItem.getId());
                if (refreshedItem != null) {
                    p.closeInventory();
                    new ShopItemEditGUI(plugin, p, refreshedItem).open();
                } else {
                    p.closeInventory();
                    new ShopAdminGUI(plugin, p).open();
                }
            } else {
                player.sendMessage("§c配置文件中没有找到 §e" + shopItem.getId() + " §c的定义");
            }
        });

        // 删除物品按钮
        ItemStack deleteBtn = createDecorItem("delete", Material.TNT);
        ItemUtil.setDisplayName(deleteBtn, "§4§l删除物品");
        ItemUtil.setLore(deleteBtn, List.of(
            "§7点击从数据库中删除该商店物品",
            "",
            "§c§l危险操作！此操作不可撤销！",
            "§7该物品将从商店中完全移除"
        ));
        setItem(23, deleteBtn, p -> {
            // 打开确认界面
            new ConfirmDeleteGUI(plugin, p, shopItem, () -> {
                plugin.getShopManager().deleteItem(shopItem.getId());
                player.sendMessage("§c已删除商店物品 §e" + shopItem.getId());
                p.closeInventory();
                new ShopAdminGUI(plugin, p).open();
            }).open();
        });

        // 保存按钮
        ItemStack saveBtn = createDecorItem("save", Material.EMERALD_BLOCK);
        ItemUtil.setDisplayName(saveBtn, "§a§l保存并返回");
        ItemUtil.setLore(saveBtn, List.of("§7当前库存: " + getStockDisplay()));
        setItem(25, saveBtn, p -> {
            plugin.getShopManager().updateItemStock(shopItem.getId(), shopItem.getStock(), success -> {
                if (success) {
                    player.sendMessage("§a已保存 §e" + shopItem.getId() + " §a的库存设置: " + getStockDisplay());
                    p.closeInventory();
                    new ShopAdminGUI(plugin, p).open();
                } else {
                    player.sendMessage("§c保存 §e" + shopItem.getId() + " §c的库存失败，请重试！");
                }
            });
        });

        // 返回按钮（不保存）
        addBackButton(18, () -> new ShopAdminGUI(plugin, player).open());
    }

    private String getStockDisplay() {
        if (shopItem.hasUnlimitedStock()) {
            return "§a无限";
        }
        return "§e" + shopItem.getStock();
    }

    private void changeStock(int delta) {
        int currentStock = shopItem.getStock();
        if (currentStock < 0) {
            // 无限库存变为有限
            currentStock = 0;
        }
        int newStock = Math.max(0, currentStock + delta);
        shopItem.setStock(newStock);
        initialize(); // 刷新界面
    }

    private void setStock(int stock) {
        shopItem.setStock(stock);
        initialize(); // 刷新界面
    }
}
