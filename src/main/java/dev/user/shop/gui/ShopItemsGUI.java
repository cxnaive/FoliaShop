package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.shop.ShopItem;
import dev.user.shop.shop.ShopManager;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class ShopItemsGUI extends AbstractGUI {

    private final ShopManager.ShopCategory category;
    private final ShopManager.SubCategory subcategory;
    private final java.util.Map<Integer, ShopItem> slotToItem;
    private final java.util.Map<Integer, ShopManager.SubCategory> slotToSubcategory;

    public ShopItemsGUI(FoliaShopPlugin plugin, Player player, ShopManager.ShopCategory category) {
        super(plugin, player, MessageUtil.convertMiniMessageToLegacy(category.getName()), 54);
        this.category = category;
        this.subcategory = null;
        this.slotToItem = new java.util.HashMap<>();
        this.slotToSubcategory = new java.util.HashMap<>();
    }

    /**
     * 子分类构造函数
     */
    public ShopItemsGUI(FoliaShopPlugin plugin, Player player, ShopManager.ShopCategory parentCategory, ShopManager.SubCategory subcategory) {
        super(plugin, player, MessageUtil.convertMiniMessageToLegacy(subcategory.getName()), 54);
        this.category = parentCategory;
        this.subcategory = subcategory;
        this.slotToItem = new java.util.HashMap<>();
        this.slotToSubcategory = new java.util.HashMap<>();
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);
        slotToItem.clear();
        slotToSubcategory.clear();

        // 如果在父分类层级，显示子分类
        if (subcategory == null && category.hasSubcategories()) {
            for (ShopManager.SubCategory sub : category.getSubcategories().values()) {
                ItemStack subIcon = createSubcategoryDisplay(sub);
                int slot = sub.getSlot();

                if (slot > 0 && slot < 45 && slot % 9 != 0 && slot % 9 != 8) {
                    setItem(slot, subIcon, p -> {
                        p.closeInventory();
                        new ShopItemsGUI(plugin, p, category, sub).open();
                    });
                    slotToSubcategory.put(slot, sub);
                } else {
                    // 自动寻找空位
                    for (int i = 10; i < 44; i++) {
                        if (i % 9 == 0 || i % 9 == 8) continue;
                        if (inventory.getItem(i) == null) {
                            setItem(i, subIcon, p -> {
                                p.closeInventory();
                                new ShopItemsGUI(plugin, p, category, sub).open();
                            });
                            slotToSubcategory.put(i, sub);
                            break;
                        }
                    }
                }
            }
        }

        // 加载商品
        List<ShopItem> items;
        if (subcategory != null) {
            items = plugin.getShopManager().getItemsByCategoryPath(category.getId() + ":" + subcategory.getId());
        } else {
            items = plugin.getShopManager().getItemsByCategory(category.getId());
        }

        for (ShopItem shopItem : items) {
            if (!shopItem.isEnabled()) continue;

            ItemStack item = createItemDisplay(shopItem);
            if (item == null) continue;

            int slot = shopItem.getSlot();
            if (slot > 0 && slot < 45 && slot % 9 != 0 && slot % 9 != 8 && inventory.getItem(slot) == null) {
                setItem(slot, item);  // 不设置action，让GUIListener处理点击
                slotToItem.put(slot, shopItem);
            } else {
                // 自动寻找空位
                for (int i = 10; i < 44; i++) {
                    if (i % 9 == 0 || i % 9 == 8) continue;
                    if (inventory.getItem(i) == null) {
                        setItem(i, item);  // 不设置action，让GUIListener处理点击
                        slotToItem.put(i, shopItem);
                        break;
                    }
                }
            }
        }

        // 返回按钮
        if (subcategory != null) {
            addBackButton(49, () -> new ShopItemsGUI(plugin, player, category).open());
        } else {
            addBackButton(49, () -> new ShopCategoryGUI(plugin, player).open());
        }

        // 关闭按钮
        addCloseButton(52);
    }

    /**
     * 创建子分类显示物品
     */
    private ItemStack createSubcategoryDisplay(ShopManager.SubCategory sub) {
        ItemStack icon = ItemUtil.createItemFromKey(plugin, sub.getIcon());
        if (icon == null) icon = new ItemStack(Material.CHEST);

        ItemUtil.setDisplayName(icon, "§e§l📁 " + sub.getName());

        // 统计子分类下的商品数量
        long itemCount = plugin.getShopManager().getItemsByCategoryPath(category.getId() + ":" + sub.getId()).size();

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7商品数量: §e" + itemCount);
        lore.add("");
        lore.add("§e点击查看商品");
        ItemUtil.setLore(icon, lore);

        return icon;
    }

    /**
     * 通过slot处理物品点击 - 由GUIListener调用
     */
    public void handleItemClick(Player player, int slot, ClickType clickType) {
        ShopItem shopItem = slotToItem.get(slot);
        if (shopItem == null) return;
        handleItemClick(player, shopItem, clickType);
    }

    /**
     * 处理物品点击 - 由GUIListener调用，保存了点击事件信息
     * 左键购买1个，Shift+左键购买64个，右键和Shift+右键不处理
     */
    public void handleItemClick(Player player, ShopItem shopItem, ClickType clickType) {
        // 右键和Shift+右键不处理
        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            return;
        }

        // 购买
        if (!shopItem.canBuy()) {
            player.sendMessage(Component.text("该物品不可购买！").color(NamedTextColor.RED));
            return;
        }
        int amount = (clickType == ClickType.SHIFT_LEFT) ? 64 : 1;
        buyItem(player, shopItem, amount);
    }

    /**
     * 兼容旧的处理方法
     */
    private void handleItemClick(Player player, ShopItem shopItem) {
        // 默认购买1个
        buyItem(player, shopItem, 1);
    }

    private void buyItem(Player player, ShopItem shopItem, int amount) {
        // 限制单次最大购买数量
        final int finalAmount = Math.min(amount, 64);

        // 检查背包空间
        ItemStack item = shopItem.getDisplayItem().clone();
        item.setAmount(finalAmount);

        int canFit = calculateCanFit(player, item);
        if (canFit < finalAmount) {
            player.sendMessage(Component.text("背包空间不足！最多还能放入 " + canFit + " 个").color(NamedTextColor.RED));
            return;
        }

        // 提交购买请求（限额检查在 PurchaseManager 中统一处理）
        submitPurchase(player, shopItem, finalAmount);
    }

    private void submitPurchase(Player player, ShopItem shopItem, int amount) {
        // 提交到 PurchaseManager 处理
        plugin.getPurchaseManager().submitPurchase(player, shopItem, amount, result -> {
            if (result.success) {
                // 根据支付方式选择不同的消息模板
                String messageKey;
                Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("amount", String.valueOf(result.amount));

                boolean hasCost = result.cost > 0;
                boolean hasPoints = result.points > 0;

                if (hasCost && hasPoints) {
                    messageKey = "purchase-success-mixed";
                    placeholders.put("cost", String.format("%.2f", result.cost));
                    placeholders.put("currency", plugin.getShopConfig().getCurrencyName());
                    placeholders.put("points", String.valueOf(result.points));
                } else if (hasPoints) {
                    messageKey = "purchase-success-points";
                    placeholders.put("points", String.valueOf(result.points));
                } else {
                    messageKey = "purchase-success";
                    placeholders.put("cost", String.format("%.2f", result.cost));
                    placeholders.put("currency", plugin.getShopConfig().getCurrencyName());
                }

                Component successMessage = plugin.getShopConfig().getItemMessage(
                    messageKey,
                    "item",
                    shopItem.getDisplayItem(),
                    placeholders
                );
                player.sendMessage(successMessage);
                // 刷新GUI显示
                refreshItemDisplay(shopItem);
            } else {
                player.sendMessage(Component.text(result.message).color(NamedTextColor.RED));
            }
        });
    }

    /**
     * 刷新指定商品的显示（更新库存等信息）
     */
    private void refreshItemDisplay(ShopItem shopItem) {
        // 找到该商品所在的槽位
        for (java.util.Map.Entry<Integer, ShopItem> entry : slotToItem.entrySet()) {
            if (entry.getValue().getId().equals(shopItem.getId())) {
                int slot = entry.getKey();
                // 重新创建物品显示
                ItemStack newDisplay = createItemDisplay(shopItem);
                inventory.setItem(slot, newDisplay);
                break;
            }
        }
    }

    /**
     * 创建商品的显示物品
     */
    private ItemStack createItemDisplay(ShopItem shopItem) {
        ItemStack displayItem = shopItem.getDisplayItem();
        if (displayItem == null) return new ItemStack(Material.BARRIER);

        ItemStack item = displayItem.clone();
        java.util.List<String> shopLore = new java.util.ArrayList<>();

        // 价格信息
        if (shopItem.canBuy()) {
            StringBuilder priceStr = new StringBuilder();
            if (shopItem.getBuyPrice() > 0) {
                priceStr.append("§e").append(plugin.getShopConfig().formatCurrency(shopItem.getBuyPrice()));
            }
            if (shopItem.requiresPoints()) {
                if (priceStr.length() > 0) priceStr.append(" §7+ ");
                priceStr.append("§e").append(shopItem.getBuyPoints()).append(" 点券");
            }
            shopLore.add("§7购买价格: " + priceStr.toString());
        }
        // 只在系统回收启用时显示出售价格
        if (shopItem.canSell() && plugin.getShopConfig().isSellSystemEnabled()) {
            shopLore.add("§7回收价格: §e" + plugin.getShopConfig().formatCurrency(shopItem.getSellPrice()));
        }

        // 库存信息
        if (shopItem.hasUnlimitedStock()) {
            shopLore.add("§7库存: §a无限");
        } else {
            shopLore.add("§7库存: §e" + shopItem.getStock());
        }

        // 每日限额信息
        if (shopItem.hasDailyLimit()) {
            shopLore.add("§7每日限额: §e" + shopItem.getDailyLimit() + " 个");
        }

        // 终身限额信息
        if (shopItem.hasPlayerLimit()) {
            shopLore.add("§7终身限额: §e" + shopItem.getPlayerLimit() + " 个");
        }

        shopLore.add("");

        // 操作提示 - 只显示购买操作
        if (shopItem.canBuy()) {
            shopLore.add("§e左键 §7购买 1 个");
            shopLore.add("§eShift+左键 §7购买 64 个");
        }

        // 保留原有lore，在末尾添加商店信息
        addShopLore(item, shopLore);
        return item;
    }

    /**
     * 在物品原有lore基础上添加商店信息
     */
    private void addShopLore(ItemStack item, java.util.List<String> shopLore) {
        ItemUtil.addLore(item, shopLore);
    }

    private void sellItem(Player player, ShopItem shopItem, int amount) {
        // 计算玩家拥有的该物品数量
        int hasAmount = countItems(player, shopItem.getDisplayItem());

        if (hasAmount == 0) {
            player.sendMessage(Component.text("你没有该物品！").color(NamedTextColor.RED));
            return;
        }

        // 如果是Shift+右键，出售全部
        if (amount == Integer.MAX_VALUE) {
            amount = hasAmount;
        }

        // 限制出售数量不超过拥有的数量
        amount = Math.min(amount, hasAmount);

        // 移除物品
        int removed = removeItems(player, shopItem.getDisplayItem(), amount);
        if (removed == 0) {
            player.sendMessage(Component.text("物品移除失败！").color(NamedTextColor.RED));
            return;
        }

        double totalReward = shopItem.getSellPrice() * removed;
        final int finalRemoved = removed;

        // 异步给予金钱
        plugin.getEconomyManager().depositAsync(player, totalReward, success -> {
            if (!success) {
                // 给予金钱失败，返还物品
                returnItemsToPlayer(player, shopItem.getDisplayItem(), finalRemoved);
                player.sendMessage(Component.text("经济系统错误，出售已取消，物品已返还！").color(NamedTextColor.RED));
                return;
            }

            // 增加库存（如果是有限库存且配置允许）
            if (!shopItem.hasUnlimitedStock() && plugin.getShopConfig().isAddStockOnSell()) {
                plugin.getShopManager().atomicAddStock(shopItem.getId(), finalRemoved);
            }

            // 记录交易
            plugin.getShopManager().logTransaction(
                player.getUniqueId(), player.getName(),
                shopItem.getId(), shopItem.getItemKey(),
                finalRemoved, totalReward, "SELL"
            );

            // 发送消息（使用 Component API）
            Component sellMessage = plugin.getShopConfig().getItemMessage(
                "sell-success",
                "item",
                shopItem.getDisplayItem(),
                Map.of(
                    "amount", String.valueOf(finalRemoved),
                    "reward", String.format("%.2f", totalReward),
                    "currency", plugin.getShopConfig().getCurrencyName()
                )
            );
            player.sendMessage(sellMessage);
        });
    }

    /**
     * 返还物品给玩家（背包满了则掉落）
     */
    private void returnItemsToPlayer(Player player, ItemStack template, int amount) {
        ItemStack toReturn = template.clone();
        toReturn.setAmount(amount);

        java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toReturn);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    /**
     * 计算背包可以容纳指定物品的数量
     */
    private int calculateCanFit(Player player, ItemStack item) {
        int maxStackSize = item.getMaxStackSize();
        int amountNeeded = item.getAmount();
        int canFit = 0;

        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (canFit >= amountNeeded) break;

            if (invItem == null || invItem.getType() == Material.AIR) {
                canFit += maxStackSize;
            } else if (invItem.isSimilar(item)) {
                int space = maxStackSize - invItem.getAmount();
                if (space > 0) {
                    canFit += space;
                }
            }
        }

        return Math.min(canFit, amountNeeded);
    }

    /**
     * 统计玩家拥有的指定物品数量
     */
    private int countItems(Player player, ItemStack target) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.isSimilar(target)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * 从玩家背包移除指定数量的物品
     * @return 实际移除的数量
     */
    private int removeItems(Player player, ItemStack target, int amountToRemove) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int i = 0; i < contents.length && removed < amountToRemove; i++) {
            ItemStack item = contents[i];
            if (item != null && item.isSimilar(target)) {
                int toRemove = Math.min(item.getAmount(), amountToRemove - removed);
                if (toRemove >= item.getAmount()) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - toRemove);
                }
                removed += toRemove;
            }
        }

        return removed;
    }
}
