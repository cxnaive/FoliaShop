package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.shop.ShopItem;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.PriceUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SellGUI extends AbstractGUI {

    private final List<Integer> sellSlots;

    public SellGUI(FoliaShopPlugin plugin, Player player) {
        super(plugin, player, "§8出售物品", 54);
        this.sellSlots = new ArrayList<>();
    }

    @Override
    protected void initialize() {
        // 填充边框
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // 显示提示信息
        ItemStack info = new ItemStack(Material.PAPER);
        ItemUtil.setDisplayName(info, "§e§l出售说明");

        ItemUtil.setLore(info, List.of(
            "§7将物品放入下方格子",
            "§7点击确认出售按钮出售",
            "",
            "§7系统只会收购商店中有收购价的物品"
        ));
        setItem(4, info);

        // 设置可放入物品的格子 (第2-5行，避开边框和按钮区域)
        // 第6行(45-53)留给导航按钮
        sellSlots.clear();
        for (int row = 1; row <= 4; row++) {  // 第2-5行
            for (int col = 1; col <= 7; col++) {  // 第2-8列
                int slot = row * 9 + col;  // 10-16, 19-25, 28-34, 37-43
                sellSlots.add(slot);
            }
        }

        // 确认出售按钮 (底部中间 slot 49)
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemUtil.setDisplayName(confirm, "§a§l确认出售");
        ItemUtil.setLore(confirm, List.of(
            "§7点击出售格子中的所有物品",
            ""
        ));
        setItem(49, confirm, this::confirmSell);

        // 返回按钮 (底部右侧 slot 52)
        addBackButton(52, () -> new ShopCategoryGUI(plugin, player).open());
    }

    private void confirmSell(Player player) {
        if (!plugin.getShopConfig().isSellSystemEnabled()) {
            player.sendMessage("§c系统回收功能已关闭！");
            return;
        }

        // 第一阶段：计算总价值并收集所有可售条目
        List<SellEntry> entries = new ArrayList<>();
        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        for (int slot : sellSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            SellPriceResult result = getSellPrice(item);
            if (result.price <= 0) continue;

            int amount = item.getAmount();
            double reward = result.price * amount;
            String itemKey = ItemUtil.getItemKey(item);

            SellEntry entry = new SellEntry(slot, item.clone(), reward, result.source, result.shopItemId, itemKey, result.category);
            entries.add(entry);

            if (entry.category != null) {
                categoryTotals.merge(entry.category, reward, Double::sum);
            }
        }

        if (entries.isEmpty()) {
            player.sendMessage("§c没有可以出售的物品！");
            return;
        }

        // 快照完成：清空可售格子的物品，然后关闭 GUI
        for (SellEntry entry : entries) {
            inventory.setItem(entry.slot, null);
        }
        player.closeInventory();
        player.sendMessage("§e正在处理出售请求...");

        // 第二阶段：异步预留分类出售额度
        plugin.getShopManager().tryReserveCategorySellAmountsAsync(
            player.getUniqueId(), categoryTotals,
            reservedAmounts -> processReserveResult(player, entries, reservedAmounts, categoryTotals));
    }

    /**
     * 异步预留回调：截断 + 返还被截断物品 + 提交存款
     */
    private void processReserveResult(Player player, List<SellEntry> entries,
                                       Map<String, Double> reservedAmounts,
                                       Map<String, Double> categoryTotals) {
        if (!player.isOnline()) {
            // 玩家已离线，回滚所有预留额度
            Map<String, Double> toRollback = new HashMap<>();
            for (Map.Entry<String, Double> entry : reservedAmounts.entrySet()) {
                if (entry.getValue() > 0) {
                    toRollback.put(entry.getKey(), entry.getValue());
                }
            }
            if (!toRollback.isEmpty()) {
                plugin.getShopManager().rollbackCategorySellAmounts(player.getUniqueId(), toRollback);
            }
            plugin.getLogger().warning("玩家 " + player.getName() + " 在出售处理期间下线，已回滚预留额度");
            return;
        }

        // 第三阶段：贪心拆分 — 按顺序尽量多卖，超限时拆分堆叠
        Map<String, Double> categoryAccumulated = new HashMap<>();
        List<SellEntry> sellEntries = new ArrayList<>();

        for (SellEntry entry : entries) {
            if (entry.category == null || !reservedAmounts.containsKey(entry.category)) {
                sellEntries.add(entry);
                continue;
            }
            double budget = reservedAmounts.get(entry.category);
            double accumulated = categoryAccumulated.getOrDefault(entry.category, 0.0);
            double remaining = budget - accumulated;

            if (remaining <= 0) {
                returnItemToPlayer(player, entry.originalItem);
                continue;
            }

            double pricePerUnit = entry.reward / entry.originalItem.getAmount();

            if (entry.reward <= remaining) {
                sellEntries.add(entry);
                categoryAccumulated.merge(entry.category, entry.reward, Double::sum);
            } else {
                int maxAffordable = (int) (remaining / pricePerUnit);
                if (maxAffordable <= 0) {
                    returnItemToPlayer(player, entry.originalItem);
                    continue;
                }
                ItemStack soldItem = entry.originalItem.clone();
                soldItem.setAmount(maxAffordable);
                double actualReward = pricePerUnit * maxAffordable;

                SellEntry splitEntry = new SellEntry(entry.slot, soldItem, actualReward,
                    entry.source, entry.shopItemId, entry.itemKey, entry.category);
                sellEntries.add(splitEntry);
                categoryAccumulated.merge(entry.category, actualReward, Double::sum);

                int returnAmount = entry.originalItem.getAmount() - maxAffordable;
                if (returnAmount > 0) {
                    ItemStack returnItem = entry.originalItem.clone();
                    returnItem.setAmount(returnAmount);
                    returnItemToPlayer(player, returnItem);
                }
            }
        }

        // 释放未使用的预留额度
        Map<String, Double> excessReserved = new HashMap<>();
        for (Map.Entry<String, Double> reserved : reservedAmounts.entrySet()) {
            double used = categoryAccumulated.getOrDefault(reserved.getKey(), 0.0);
            if (reserved.getValue() > used) {
                excessReserved.put(reserved.getKey(), reserved.getValue() - used);
            }
        }
        if (!excessReserved.isEmpty()) {
            plugin.getShopManager().rollbackCategorySellAmounts(player.getUniqueId(), excessReserved);
        }

        // 返还被截断的物品（贪心循环中已处理拆分，此处无需额外操作）

        if (sellEntries.isEmpty()) {
            player.sendMessage("§c今日出售额度已用完，明天再来吧！");
            return;
        }

        // 第四阶段：执行异步存款
        double totalReward = sellEntries.stream().mapToDouble(e -> e.reward).sum();
        int totalItems = sellEntries.stream().mapToInt(e -> e.originalItem.getAmount()).sum();

        plugin.getEconomyManager().depositAsync(player, totalReward, success -> {
            if (!player.isOnline()) return;

            if (!success) {
                for (SellEntry entry : sellEntries) {
                    returnItemToPlayer(player, entry.originalItem);
                }
                player.sendMessage(Component.text("经济系统错误，出售已取消，物品已返还！").color(NamedTextColor.RED));
                rollbackSellReserve(player, categoryAccumulated);
                return;
            }

            Component sellMessage = plugin.getShopConfig().getComponent("sell-success-batch",
                Map.of("count", String.valueOf(sellEntries.size()),
                       "total", String.valueOf(totalItems),
                       "reward", String.format("%.2f", totalReward),
                       "currency", plugin.getShopConfig().getCurrencyName()));
            player.sendMessage(sellMessage);

            if (plugin.getShopConfig().isAddStockOnSell()) {
                for (SellEntry entry : sellEntries) {
                    if (entry.shopItemId != null && !entry.shopItemId.isEmpty()) {
                        plugin.getShopManager().atomicAddStock(entry.shopItemId, entry.originalItem.getAmount());
                    }
                }
            }

            for (SellEntry entry : sellEntries) {
                plugin.getShopManager().logTransaction(
                    player.getUniqueId(), player.getName(),
                    entry.itemKey != null ? entry.itemKey : "unknown",
                    entry.itemKey != null ? entry.itemKey : "unknown",
                    entry.originalItem.getAmount(),
                    entry.reward,
                    "SELL"
                );
            }
        });
    }

    /**
     * 回退已预留的分类出售额度（出售失败时调用）
     */
    private void rollbackSellReserve(Player player, Map<String, Double> categoryAccumulated) {
        Map<String, Double> toRollback = new HashMap<>();
        for (Map.Entry<String, Double> entry : categoryAccumulated.entrySet()) {
            if (entry.getValue() > 0) {
                toRollback.put(entry.getKey(), entry.getValue());
            }
        }
        if (!toRollback.isEmpty()) {
            plugin.getShopManager().rollbackCategorySellAmounts(player.getUniqueId(), toRollback);
        }
    }

    /**
     * 将物品返还给玩家（背包满了则掉落）
     */
    private void returnItemToPlayer(Player player, ItemStack item) {
        player.getScheduler().execute(plugin, () -> {
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }, null, 1L);
    }

    /**
     * 出售条目辅助类
     */
    private static class SellEntry {
        final int slot;
        final ItemStack originalItem;
        final double reward;
        final String source;
        final String shopItemId;
        final String itemKey;
        final String category;

        SellEntry(int slot, ItemStack originalItem, double reward, String source, String shopItemId, String itemKey, String category) {
            this.slot = slot;
            this.originalItem = originalItem;
            this.reward = reward;
            this.source = source;
            this.shopItemId = shopItemId;
            this.itemKey = itemKey;
            this.category = category;
        }
    }

    /**
     * 获取物品的回收价格（仅匹配商店物品）
     */
    private SellPriceResult getSellPrice(ItemStack item) {
        ShopItem shopItem = plugin.getShopManager().findShopItemByStack(item);
        if (shopItem != null && shopItem.canSell()) {
            double price = shopItem.hasRandomSellPrice()
                ? PriceUtil.computeDailyPrice(player.getUniqueId(), shopItem.getId(), shopItem.getSellPrice(), shopItem.getSellPriceMax())
                : shopItem.getSellPrice();
            return new SellPriceResult(price, "商店", shopItem.getId(), shopItem.getCategory());
        }
        return new SellPriceResult(0, null);
    }

    /**
     * 价格结果辅助类
     */
    private static class SellPriceResult {
        final double price;
        final String source;
        final String shopItemId;
        final String category;

        SellPriceResult(double price, String source, String shopItemId, String category) {
            this.price = price;
            this.source = source;
            this.shopItemId = shopItemId;
            this.category = category;
        }

        SellPriceResult(double price, String source) {
            this(price, source, null, null);
        }
    }

    public boolean isSellSlot(int slot) {
        return sellSlots.contains(slot);
    }

    public List<Integer> getSellSlots() {
        return sellSlots;
    }
}
