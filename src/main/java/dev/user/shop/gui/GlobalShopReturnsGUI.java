package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.globalshop.GlobalShopReturnEntry;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 全球商店待领取页面
 * 显示待领取的物品和收益
 */
public class GlobalShopReturnsGUI extends AbstractGUI {

    private int page = 0;
    private static final int ITEMS_PER_PAGE = 28;

    public GlobalShopReturnsGUI(FoliaShopPlugin plugin, Player player) {
        this(plugin, player, 0);
    }

    public GlobalShopReturnsGUI(FoliaShopPlugin plugin, Player player, int page) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("globalshop-returns"), 54);
        this.page = page;
    }

    @Override
    protected void initialize() {
        fillBorder();

        plugin.getGlobalShopManager().getReturnManager().getUnclaimedReturns(player.getUniqueId(), entries -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;

                if (entries.isEmpty()) {
                    ItemStack emptyInfo = createDecorItem("empty", Material.BARRIER);
                    ItemUtil.setDisplayName(emptyInfo, "§7暂无待领取物品");
                    setItem(22, emptyInfo);
                } else {
                    // 分页
                    int totalPages = (entries.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
                    if (page >= totalPages) page = Math.max(0, totalPages - 1);

                    int startIdx = page * ITEMS_PER_PAGE;
                    int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());

                    int[] slots = getContentSlots();
                    for (int i = startIdx; i < endIdx; i++) {
                        GlobalShopReturnEntry entry = entries.get(i);
                        ItemStack displayItem = createDisplayItem(entry);
                        int slotIdx = i - startIdx;
                        if (slotIdx < slots.length) {
                            setItem(slots[slotIdx], displayItem, p -> handleClaim(p, entry));
                        }
                    }
                }

                setupNavigation(entries.size());
            });
        });
    }

    private ItemStack createDisplayItem(GlobalShopReturnEntry entry) {
        ItemStack display;
        if (entry.hasItem()) {
            // 有物品的条目
            display = entry.deserializeItem();
            if (display != null) {
                display = display.clone();
                display.setAmount(Math.min(entry.getAmount(), 64));
            } else {
                display = createDecorItem("empty", Material.BARRIER);
            }
        } else {
            // 纯收益条目
            display = createDecorItem("money", Material.SUNFLOWER);
            ItemUtil.setDisplayName(display, "§6§l出售收益");
        }

        List<String> lore = new ArrayList<>();
        if (entry.hasItem()) {
            lore.add("§7数量: §f" + entry.getAmount());
        }
        if (entry.hasEarnings()) {
            lore.add("§7收益: §e" + String.format("%.2f", entry.getEarnings()) + " " + plugin.getShopConfig().getCurrencyName());
        }

        String reasonText = switch (entry.getReason()) {
            case "SOLD" -> "§a出售收益";
            case "EXPIRED" -> "§c过期退回";
            case "CANCELLED" -> "§e取消退回";
            case "PURCHASE" -> "§b购买物品";
            default -> entry.getReason();
        };
        lore.add("§7类型: " + reasonText);
        lore.add("");
        lore.add("§a点击领取");

        ItemUtil.setLore(display, lore);
        return display;
    }

    private void handleClaim(Player player, GlobalShopReturnEntry entry) {
        if (entry.hasItem()) {
            // 领取物品（同时领取该条目关联的收益）
            plugin.getGlobalShopManager().getReturnManager().claimItem(
                    new dev.user.shop.globalshop.GlobalShopReturnManager.PlayerContext(player.getUniqueId()),
                    entry.getId(),
                    outcome -> {
                        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (outcome.success()) {
                                StringBuilder msg = new StringBuilder("§a已领取");
                                if (entry.hasItem()) msg.append("物品");
                                if (outcome.earnings() > 0) {
                                    if (entry.hasItem()) msg.append("和");
                                    msg.append("§e").append(String.format("%.2f", outcome.earnings())).append(" ").append(plugin.getShopConfig().getCurrencyName()).append("§a收益");
                                }
                                player.sendMessage(msg.toString());
                            }
                            // 刷新页面
                            new GlobalShopReturnsGUI(plugin, player, page).open();
                        });
                    });
        } else if (entry.hasEarnings()) {
            // 纯收益条目，仅领取该条收益（"领取全部收益"按钮使用 claimAllEarnings）
            plugin.getGlobalShopManager().getReturnManager().claimItem(
                    new dev.user.shop.globalshop.GlobalShopReturnManager.PlayerContext(player.getUniqueId()),
                    entry.getId(),
                    outcome -> {
                        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (outcome.success()) {
                                player.sendMessage("§a已领取 §e" + String.format("%.2f", outcome.earnings()) + " " + plugin.getShopConfig().getCurrencyName() + " §a收益");
                            }
                            new GlobalShopReturnsGUI(plugin, player, page).open();
                        });
                    });
        }
    }

    private void setupNavigation(int totalEntries) {
        // 返回按钮
        addBackButton(45, () -> new GlobalShopBrowseGUI(plugin, player).open());

        // 领取全部收益按钮
        ItemStack claimAllBtn = createDecorItem("claim-all", Material.GOLD_INGOT);
        ItemUtil.setDisplayName(claimAllBtn, "§6§l领取全部收益");
        ItemUtil.setLore(claimAllBtn, List.of("§7一键领取所有待领取收益"));
        setItem(48, claimAllBtn, p -> {
            plugin.getGlobalShopManager().getReturnManager().claimAllEarnings(p.getUniqueId(), total -> {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    if (!p.isOnline()) return;
                    if (total > 0) {
                        p.sendMessage("§a已领取 §e" + String.format("%.2f", total) + " " + plugin.getShopConfig().getCurrencyName() + " §a收益");
                    } else {
                        p.sendMessage("§c暂无可领取的收益");
                    }
                    new GlobalShopReturnsGUI(plugin, p, 0).open();
                });
            });
        });

        // 页码指示
        ItemStack pageInfo = createDecorItem("page-info", Material.PAPER);
        ItemUtil.setDisplayName(pageInfo, "§7第 §e" + (page + 1) + " §7页");
        setItem(49, pageInfo);

        // 上一页
        if (page > 0) {
            var decoration = plugin.getShopConfig().getGUIDecoration("prev-page");
            ItemStack prevBtn = ItemUtil.createItemFromKey(plugin, decoration.getMaterial());
            ItemUtil.setDisplayName(prevBtn, MessageUtil.convertMiniMessageToLegacy(decoration.getName()));
            setItem(47, prevBtn, p -> new GlobalShopReturnsGUI(plugin, p, page - 1).open());
        }

        // 下一页
        int totalPages = (totalEntries + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        if (page < totalPages - 1) {
            var decoration = plugin.getShopConfig().getGUIDecoration("next-page");
            ItemStack nextBtn = ItemUtil.createItemFromKey(plugin, decoration.getMaterial());
            ItemUtil.setDisplayName(nextBtn, MessageUtil.convertMiniMessageToLegacy(decoration.getName()));
            setItem(53, nextBtn, p -> new GlobalShopReturnsGUI(plugin, p, page + 1).open());
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
