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
 * 全球商店浏览/购买页面
 */
public class GlobalShopBrowseGUI extends AbstractGUI {

    private int page = 0;
    private static final int ITEMS_PER_PAGE = 28;

    public GlobalShopBrowseGUI(FoliaShopPlugin plugin, Player player) {
        this(plugin, player, 0);
    }

    public GlobalShopBrowseGUI(FoliaShopPlugin plugin, Player player, int page) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("globalshop"), 54);
        this.page = page;
    }

    @Override
    protected void initialize() {
        fillBorder();

        // 异步加载上架列表
        plugin.getGlobalShopManager().browseListings(page, ITEMS_PER_PAGE, listings -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;

                if (listings.isEmpty() && page == 0) {
                    ItemStack emptyInfo = createDecorItem("empty", Material.BARRIER);
                    ItemUtil.setDisplayName(emptyInfo, "§7暂无商品上架");
                    setItem(22, emptyInfo);
                } else {
                    // 填充物品到 GUI 槽位 (避开边框)
                    int[] slots = getContentSlots();
                    for (int i = 0; i < Math.min(listings.size(), slots.length); i++) {
                        GlobalShopListing listing = listings.get(i);
                        ItemStack displayItem = listing.deserializeItem();
                        if (displayItem == null) continue;

                        displayItem = displayItem.clone();
                        displayItem.setAmount(Math.min(listing.getAmount(), 64));

                        List<String> lore = new ArrayList<>();
                        lore.add("");
                        lore.add("§7卖家: §f" + listing.getSellerName());
                        lore.add("§7数量: §f" + listing.getAmount());
                        lore.add("§7售价: §e" + String.format("%.2f", listing.getPrice()) + " " + plugin.getShopConfig().getCurrencyName());

                        long remainingMs = listing.getRemainingTimeMs();
                        if (remainingMs > 0) {
                            long hours = remainingMs / (60 * 60 * 1000);
                            long minutes = (remainingMs % (60 * 60 * 1000)) / (60 * 1000);
                            lore.add("§7剩余时间: §a" + hours + "小时" + minutes + "分钟");
                        } else {
                            lore.add("§7剩余时间: §c即将过期");
                        }
                        lore.add("");
                        lore.add("§a点击购买");

                        ItemUtil.setLore(displayItem, lore);
                        setItem(slots[i], displayItem, p -> handlePurchase(p, listing));
                    }
                }

                // 底部导航
                setupNavigation(listings.size());
            });
        });
    }

    private void handlePurchase(Player player, GlobalShopListing listing) {
        // 执行购买（服务端会处理背包满的情况，溢出物品存入待领取）
        player.closeInventory();
        player.sendMessage("§e正在购买...");

        plugin.getGlobalShopManager().getPurchaseManager().submitPurchase(player, listing.getId(), result -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(result.getMessage());
            });
        });
    }

    private void setupNavigation(int loadedCount) {
        // 返回按钮
        addBackButton(45, () -> new MainMenuGUI(plugin, player).open());

        // 上一页
        if (page > 0) {
            var decoration = plugin.getShopConfig().getGUIDecoration("prev-page");
            ItemStack prevBtn = ItemUtil.createItemFromKey(plugin,
                    decoration != null ? decoration.getMaterial() : "minecraft:arrow");
            if (prevBtn != null) {
                ItemUtil.setDisplayName(prevBtn, decoration != null ? MessageUtil.convertMiniMessageToLegacy(decoration.getName()) : "§e上一页");
                setItem(47, prevBtn, p -> {
                    page--;
                    new GlobalShopBrowseGUI(plugin, player, page).open();
                });
            }
        }

        // 页码指示
        ItemStack pageInfo = createDecorItem("page-info", Material.PAPER);
        ItemUtil.setDisplayName(pageInfo, "§7第 §e" + (page + 1) + " §7页");
        setItem(49, pageInfo);

        // 我的上架
        ItemStack manageBtn = createDecorItem("manage", Material.CHEST);
        ItemUtil.setDisplayName(manageBtn, "§e我的上架");
        ItemUtil.setLore(manageBtn, List.of("§7点击查看你上架的物品"));
        setItem(50, manageBtn, p -> new GlobalShopManageGUI(plugin, p).open());

        // 待领取
        ItemStack returnsBtn = createDecorItem("returns", Material.HOPPER);
        ItemUtil.setDisplayName(returnsBtn, "§6待领取");
        ItemUtil.setLore(returnsBtn, List.of("§7查看待领取的物品和收益"));
        setItem(51, returnsBtn, p -> new GlobalShopReturnsGUI(plugin, p).open());

        // 下一页
        if (loadedCount >= ITEMS_PER_PAGE) {
            var decoration = plugin.getShopConfig().getGUIDecoration("next-page");
            ItemStack nextBtn = ItemUtil.createItemFromKey(plugin,
                    decoration != null ? decoration.getMaterial() : "minecraft:arrow");
            if (nextBtn != null) {
                ItemUtil.setDisplayName(nextBtn, decoration != null ? MessageUtil.convertMiniMessageToLegacy(decoration.getName()) : "§e下一页");
                setItem(53, nextBtn, p -> {
                    page++;
                    new GlobalShopBrowseGUI(plugin, player, page).open();
                });
            }
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
