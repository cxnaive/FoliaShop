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

        String mode = plugin.getShopConfig().getSellSystemMode();
        String modeDesc = switch (mode) {
            case "SHOP_ONLY" -> "§7当前模式: §a仅回收商店物品";
            case "CONFIG_ONLY" -> "§7当前模式: §a仅回收系统定义物品";
            case "ALL" -> "§7当前模式: §a回收商店+系统定义物品";
            default -> "§7当前模式: §a商店物品";
        };

        ItemUtil.setLore(info, List.of(
            "§7将物品放入下方格子",
            "§7点击确认出售按钮出售",
            "",
            modeDesc,
            "§e系统只会收购有收购价格的物品"
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
        // 检查系统回收是否启用
        if (!plugin.getShopConfig().isSellSystemEnabled()) {
            player.sendMessage("§c系统回收功能已关闭！");
            return;
        }

        double totalReward = 0;
        String mode = plugin.getShopConfig().getSellSystemMode();

        // 第一阶段：计算总价值并验证所有物品（不修改物品）
        List<SellEntry> entriesToSell = new ArrayList<>();
        for (int slot : sellSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            // 获取物品的回收价格
            SellPriceResult result = getSellPrice(item, mode);
            if (result.price <= 0) continue;

            int amount = item.getAmount();
            double reward = result.price * amount;

            // 获取物品的实际ID用于记录
            String itemKey = ItemUtil.getItemKey(item);

            entriesToSell.add(new SellEntry(slot, item.clone(), reward, result.source, result.shopItemId, itemKey));
            totalReward += reward;
        }

        if (entriesToSell.isEmpty() || totalReward <= 0) {
            player.sendMessage("§c没有可以出售的物品！");
            return;
        }

        // 第二阶段：执行出售（原子性操作）
        // 先清除所有要出售的物品格子
        for (SellEntry entry : entriesToSell) {
            // 双重检查物品是否还在且未改变
            ItemStack currentItem = inventory.getItem(entry.slot);
            if (currentItem == null || !currentItem.isSimilar(entry.originalItem)) {
                player.sendMessage("§c出售失败：物品在确认期间发生变化，请重新放入物品！");
                return;
            }
            inventory.setItem(entry.slot, null);
        }

        // 异步给予金钱
        final double finalTotalReward = totalReward;
        final List<SellEntry> finalEntriesToSell = new ArrayList<>(entriesToSell);
        final int totalItems = entriesToSell.stream().mapToInt(e -> e.originalItem.getAmount()).sum();

        plugin.getEconomyManager().depositAsync(player, totalReward, success -> {
            if (!success) {
                // 退款失败，将物品还给玩家
                for (SellEntry entry : finalEntriesToSell) {
                    returnItemToPlayer(player, entry.originalItem);
                }
                player.sendMessage(Component.text("经济系统错误，出售已取消，物品已返还！").color(NamedTextColor.RED));
                return;
            }

            // 发送消息 - 批量出售显示总数量和物品种类
            Component sellMessage = plugin.getShopConfig().getComponent("sell-success-batch",
                Map.of("count", String.valueOf(finalEntriesToSell.size()),
                       "total", String.valueOf(totalItems),
                       "reward", String.format("%.2f", finalTotalReward),
                       "currency", plugin.getShopConfig().getCurrencyName()));
            player.sendMessage(sellMessage);

            // 增加商店库存（如果是商店物品且配置允许）
            if (plugin.getShopConfig().isAddStockOnSell()) {
                for (SellEntry entry : finalEntriesToSell) {
                    if (entry.shopItemId != null && !entry.shopItemId.isEmpty()) {
                        plugin.getShopManager().atomicAddStock(entry.shopItemId, entry.originalItem.getAmount());
                    }
                }
            }

            // 记录交易（使用实际物品ID）
            for (SellEntry entry : finalEntriesToSell) {
                plugin.getShopManager().logTransaction(
                    player.getUniqueId(), player.getName(),
                    entry.itemKey != null ? entry.itemKey : "unknown",
                    entry.itemKey != null ? entry.itemKey : "unknown",
                    entry.originalItem.getAmount(),
                    entry.reward,
                    "SELL"
                );
            }

            player.closeInventory();
        });
    }

    /**
     * 将物品返还给玩家（背包满了则掉落）
     */
    private void returnItemToPlayer(Player player, ItemStack item) {
        java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    /**
     * 出售条目辅助类
     */
    private static class SellEntry {
        final int slot;
        final ItemStack originalItem;
        final double reward;
        final String source;
        final String shopItemId; // 关联的商店商品ID（如果是商店物品）
        final String itemKey; // 物品的实际ID（用于交易记录）

        SellEntry(int slot, ItemStack originalItem, double reward, String source, String shopItemId, String itemKey) {
            this.slot = slot;
            this.originalItem = originalItem;
            this.reward = reward;
            this.source = source;
            this.shopItemId = shopItemId;
            this.itemKey = itemKey;
        }
    }

    /**
     * 获取物品的回收价格
     * @param item 物品
     * @param mode 回收模式 (SHOP_ONLY, CONFIG_ONLY, ALL)
     * @return 价格结果
     */
    private SellPriceResult getSellPrice(ItemStack item, String mode) {
        String itemKey = ItemUtil.getItemKey(item);

        // SHOP_ONLY 或 ALL 模式：检查商店物品
        if (mode.equals("SHOP_ONLY") || mode.equals("ALL")) {
            ShopItem shopItem = plugin.getShopManager().findShopItemByStack(item);
            if (shopItem != null && shopItem.canSell()) {
                double price = shopItem.hasRandomSellPrice()
                    ? PriceUtil.computeDailyPrice(player.getUniqueId(), shopItem.getId(), shopItem.getSellPrice(), shopItem.getSellPriceMax())
                    : shopItem.getSellPrice();
                return new SellPriceResult(price, "商店", shopItem.getId());
            }
            // 如果是SHOP_ONLY模式且没有找到商店物品，直接返回0
            if (mode.equals("SHOP_ONLY")) {
                return new SellPriceResult(0, null);
            }
        }

        // CONFIG_ONLY 或 ALL 模式：检查自定义回收物品
        if (mode.equals("CONFIG_ONLY") || mode.equals("ALL")) {
            double price = plugin.getShopConfig().getCustomSellPrice(itemKey);
            if (price > 0) {
                return new SellPriceResult(price, "系统回收");
            }
        }

        return new SellPriceResult(0, null);
    }

    /**
     * 价格结果辅助类
     */
    private static class SellPriceResult {
        final double price;
        final String source;
        final String shopItemId; // 关联的商店商品ID

        SellPriceResult(double price, String source, String shopItemId) {
            this.price = price;
            this.source = source;
            this.shopItemId = shopItemId;
        }

        SellPriceResult(double price, String source) {
            this(price, source, null);
        }
    }

    public boolean isSellSlot(int slot) {
        return sellSlots.contains(slot);
    }

    public List<Integer> getSellSlots() {
        return sellSlots;
    }
}