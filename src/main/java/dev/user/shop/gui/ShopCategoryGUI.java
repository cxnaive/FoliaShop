package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.shop.ShopManager;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopCategoryGUI extends AbstractGUI {

    private int page = 0;
    private final List<ShopManager.ShopCategory> categories;

    public ShopCategoryGUI(FoliaShopPlugin plugin, Player player) {
        super(plugin, player, plugin.getShopConfig().getShopTitle(), 54);
        this.categories = new ArrayList<>(plugin.getShopManager().getVisibleCategories());
    }

    @Override
    protected void initialize() {
        // 确保页码不会小于0
        if (page < 0) page = 0;

        fillBorder();

        // 每页显示的分类数量 (排除边框和导航按钮)
        int itemsPerPage = 28; // 4行 x 7列
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, categories.size());

        // 显示当前页的分类
        int slot = 10; // 从第2行第2列开始
        for (int i = startIndex; i < endIndex; i++) {
            ShopManager.ShopCategory category = categories.get(i);

            ItemStack icon = ItemUtil.createItemFromKey(plugin, category.getIcon());
            ItemUtil.setDisplayName(icon, MessageUtil.convertMiniMessageToLegacy("<yellow><bold>" + category.getName()));

            // 计算该分类下的商品数量（包含子分类）
            long itemCount = plugin.getShopManager().getItemsByCategoryPath(category.getId()).size();

            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7商品数量: §e" + itemCount);
            if (category.hasSubcategories()) {
                lore.add("§7子分类: §e" + category.getSubcategories().size() + " 个");
            }
            lore.add("");
            lore.add("§e点击查看");
            ItemUtil.setLore(icon, lore);

            // 确保slot在有效范围内且不是边框
            while (slot < 53 && (slot % 9 == 0 || slot % 9 == 8 || slot < 9 || slot > 44)) {
                slot++;
            }

            if (slot < 53) {
                final int currentSlot = slot;
                setItem(currentSlot, icon, p -> {
                    p.closeInventory();
                    new ShopItemsGUI(plugin, p, category).open();
                });
                slot++;
            }
        }

        // 计算总页数
        int totalPages = (categories.size() + itemsPerPage - 1) / itemsPerPage;

        // 动态布局底部按钮（居中显示）
        // 底部可用槽位: 45-53 (第6行中间7个格子)
        java.util.List<java.util.function.Consumer<Integer>> buttonSetters = new ArrayList<>();

        // 1. 上一页按钮（如果有）
        if (page > 0) {
            buttonSetters.add(targetSlot -> {
                ItemStack prevBtn = createDecorItem("prev-page", Material.ARROW);
                ItemUtil.setDisplayName(prevBtn, "§e§l上一页");
                ItemUtil.setLore(prevBtn, java.util.List.of("§7点击返回上一页"));
                setItem(targetSlot, prevBtn, p -> {
                    page--;
                    inventory.clear();
                    actions.clear();
                    initialize();
                });
            });
        }

        // 2. 交易记录按钮
        buttonSetters.add(targetSlot -> {
            ItemStack historyBtn = createDecorItem("history", Material.BOOK);
            ItemUtil.setDisplayName(historyBtn, "§b§l交易记录");
            ItemUtil.setLore(historyBtn, java.util.List.of(
                "§7点击查看最近20条交易记录",
                "",
                "§a绿色 §7= 购买",
                "§c红色 §7= 出售"
            ));
            setItem(targetSlot, historyBtn, p -> {
                p.closeInventory();
                new TransactionHistoryGUI(plugin, p, this).open();
            });
        });

        // 3. 关闭按钮
        buttonSetters.add(targetSlot -> {
            ItemStack closeBtn = createDecorItem("close", Material.BARRIER);
            ItemUtil.setDisplayName(closeBtn, "§c§l关闭");
            setItem(targetSlot, closeBtn, Player::closeInventory);
        });

        // 4. 返回主菜单按钮
        buttonSetters.add(targetSlot -> {
            ItemStack backBtn = createDecorItem("back", Material.ARROW);
            ItemUtil.setDisplayName(backBtn, "§e§l返回主菜单");
            ItemUtil.setLore(backBtn, java.util.List.of("§7点击返回主菜单"));
            setItem(targetSlot, backBtn, p -> {
                p.closeInventory();
                new MainMenuGUI(plugin, p).open();
            });
        });

        // 5. 页码指示器
        buttonSetters.add(targetSlot -> {
            ItemStack pageInfo = createDecorItem("page-info", Material.PAPER);
            ItemUtil.setDisplayName(pageInfo, "§e§l第 " + (page + 1) + "/" + totalPages + " 页");
            ItemUtil.setLore(pageInfo, java.util.List.of("§7共 " + categories.size() + " 个分类"));
            setItem(targetSlot, pageInfo, null);
        });

        // 6. 点券兑换按钮（如果启用）
        if (plugin.getShopConfig().isExchangeEnabled() && plugin.getPlayerPointsManager().isEnabled()) {
            buttonSetters.add(targetSlot -> {
                ItemStack exchangeBtn = createDecorItem("exchange", Material.SUNFLOWER);
                ItemUtil.setDisplayName(exchangeBtn, "§e§l点券兑换");
                List<String> lore = new ArrayList<>();
                lore.add("§7将点券兑换为" + plugin.getShopConfig().getCurrencyName());
                lore.add("§7汇率: §e1 §7点券 = §a" + String.format("%.0f", plugin.getShopConfig().getExchangeRate()) + " " + plugin.getShopConfig().getCurrencyName());

                // 异步加载点券余额
                final ItemStack btn = exchangeBtn;
                final int exchangeSlot = targetSlot;
                plugin.getPlayerPointsManager().getPointsAsync(player, points -> {
                    plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                        if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
                        lore.add("§7你的点券: §e" + points);
                        lore.add("");
                        lore.add("§a点击进行兑换");
                        ItemUtil.setLore(btn, new ArrayList<>(lore));
                        inventory.setItem(exchangeSlot, btn);
                    });
                });
                ItemUtil.setLore(exchangeBtn, lore);
                setItem(targetSlot, exchangeBtn, p -> {
                    p.closeInventory();
                    plugin.getExchangeSessionManager().startSession(p);
                    p.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    p.sendMessage("§6§l点券兑换");
                    p.sendMessage("§7请输入要兑换的§e点券数量§7（输入'取消'取消）");
                    p.sendMessage("§7当前汇率: §e1 §7点券 = §a" + String.format("%.0f", plugin.getShopConfig().getExchangeRate()) + " " + plugin.getShopConfig().getCurrencyName());
                    p.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━");
                });
            });
        }

        // 7. 出售按钮（如果启用）
        if (plugin.getShopConfig().isAllowSell() && plugin.getShopConfig().isSellSystemEnabled()) {
            buttonSetters.add(targetSlot -> {
                ItemStack sellBtn = createDecorItem("sell", Material.GOLD_INGOT);
                ItemUtil.setDisplayName(sellBtn, "§6§l出售物品");

                ItemUtil.setLore(sellBtn, java.util.Arrays.asList(
                    "§7点击出售背包中的物品"
                ));
                setItem(targetSlot, sellBtn, p -> {
                    p.closeInventory();
                    new SellGUI(plugin, p).open();
                });
            });
        }

        // 8. 下一页按钮（如果有）
        if (endIndex < categories.size()) {
            buttonSetters.add(targetSlot -> {
                ItemStack nextBtn = createDecorItem("next-page", Material.ARROW);
                ItemUtil.setDisplayName(nextBtn, "§e§l下一页");
                ItemUtil.setLore(nextBtn, java.util.List.of("§7点击查看更多分类"));
                setItem(targetSlot, nextBtn, p -> {
                    page++;
                    inventory.clear();
                    actions.clear();
                    initialize();
                });
            });
        }

        // 计算居中起始位置
        // 底部可用槽位: 45-53 共9个，但中间7个(46-52)用于按钮
        int buttonCount = buttonSetters.size();
        int totalWidth = 7; // 中间可用区域 46-52
        int baseSlot = 46 + (totalWidth - buttonCount) / 2;
        // 奇数居中，偶数偏向右边
        int startSlot = (buttonCount % 2 == 0) ? baseSlot + 1 : baseSlot;

        // 放置按钮
        for (int i = 0; i < buttonCount; i++) {
            buttonSetters.get(i).accept(startSlot + i);
        }
    }
}
