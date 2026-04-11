package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.globalshop.GlobalShopListing;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 全球商店 - 我的上架管理页面
 */
public class GlobalShopManageGUI extends AbstractGUI {

    private int page = 0;
    private static final int ITEMS_PER_PAGE = 28;

    public GlobalShopManageGUI(FoliaShopPlugin plugin, Player player) {
        this(plugin, player, 0);
    }

    public GlobalShopManageGUI(FoliaShopPlugin plugin, Player player, int page) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("globalshop-manage"), 54);
        this.page = page;
    }

    @Override
    protected void initialize() {
        fillBorder();

        plugin.getGlobalShopManager().getMyListings(player.getUniqueId(), page, ITEMS_PER_PAGE, listings -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;

                if (listings.isEmpty() && page == 0) {
                    ItemStack emptyInfo = new ItemStack(Material.BARRIER);
                    ItemUtil.setDisplayName(emptyInfo, "§7暂无上架物品");
                    setItem(22, emptyInfo);
                } else {
                    int[] slots = getContentSlots();
                    for (int i = 0; i < Math.min(listings.size(), slots.length); i++) {
                        GlobalShopListing listing = listings.get(i);
                        ItemStack displayItem = listing.deserializeItem();
                        if (displayItem == null) continue;

                        displayItem = displayItem.clone();
                        displayItem.setAmount(Math.min(listing.getAmount(), 64));

                        List<String> lore = new ArrayList<>();
                        lore.add("");
                        lore.add("§7售价: §e" + String.format("%.2f", listing.getPrice()) + " " + plugin.getShopConfig().getCurrencyName());

                        long remainingMs = listing.getRemainingTimeMs();
                        if (remainingMs > 0) {
                            long days = remainingMs / (24 * 60 * 60 * 1000);
                            long hours = (remainingMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
                            lore.add("§7剩余时间: §a" + days + "天" + hours + "小时");
                        } else {
                            lore.add("§7状态: §c即将过期");
                        }
                        lore.add("");
                        lore.add("§c点击取消上架");

                        ItemUtil.setLore(displayItem, lore);
                        setItem(slots[i], displayItem, p -> handleCancel(p, listing));
                    }
                }

                setupNavigation(listings.size());
            });
        });
    }

    private void handleCancel(Player player, GlobalShopListing listing) {
        player.closeInventory();
        plugin.getGlobalShopManager().cancelListing(player, listing.getId(), success -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline()) return;
                // 刷新页面
                new GlobalShopManageGUI(plugin, player, page).open();
            });
        });
    }

    private void setupNavigation(int loadedCount) {
        // 返回按钮
        addBackButton(45, () -> new GlobalShopBrowseGUI(plugin, player).open());

        // 上架新物品
        ItemStack submitBtn = new ItemStack(Material.LIME_DYE);
        ItemUtil.setDisplayName(submitBtn, "§a§l上架新物品");
        ItemUtil.setLore(submitBtn, List.of("§7点击上架新物品"));
        setItem(48, submitBtn, p -> new GlobalShopSubmitGUI(plugin, p).open());

        // 页码指示
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemUtil.setDisplayName(pageInfo, "§7第 §e" + (page + 1) + " §7页");
        setItem(49, pageInfo);

        // 上一页
        if (page > 0) {
            var decoration = plugin.getShopConfig().getGUIDecoration("prev-page");
            ItemStack prevBtn = ItemUtil.createItemFromKey(plugin, decoration.getMaterial());
            ItemUtil.setDisplayName(prevBtn, MessageUtil.convertMiniMessageToLegacy(decoration.getName()));
            setItem(47, prevBtn, p -> new GlobalShopManageGUI(plugin, p, page - 1).open());
        }

        // 下一页
        if (loadedCount >= ITEMS_PER_PAGE) {
            var decoration = plugin.getShopConfig().getGUIDecoration("next-page");
            ItemStack nextBtn = ItemUtil.createItemFromKey(plugin, decoration.getMaterial());
            ItemUtil.setDisplayName(nextBtn, MessageUtil.convertMiniMessageToLegacy(decoration.getName()));
            setItem(53, nextBtn, p -> new GlobalShopManageGUI(plugin, p, page + 1).open());
        }

        // 关闭按钮
        addCloseButton(52);
    }

    private int[] getContentSlots() {
        int[] slots = new int[28];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[idx++] = row * 9 + col;
            }
        }
        return slots;
    }
}
