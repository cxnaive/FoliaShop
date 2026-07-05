package dev.user.shop.listener;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gui.AbstractGUI;
import dev.user.shop.gui.GlobalShopSubmitGUI;
import dev.user.shop.gui.GUIManager;
import dev.user.shop.gui.SellGUI;
import dev.user.shop.gui.ShopItemsGUI;
import dev.user.shop.gui.TargetedBookMachineGUI;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GUIListener implements Listener {

    private final FoliaShopPlugin plugin;

    public GUIListener(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AbstractGUI gui)) {
            return;
        }

        // 处理出售界面的特殊逻辑
        if (gui instanceof SellGUI sellGUI) {
            handleSellGUIClick(event, player, sellGUI);
            return;
        }

        // 处理全球商店上架界面的特殊逻辑（与出售界面相同模式）
        if (gui instanceof GlobalShopSubmitGUI submitGUI) {
            handleGlobalShopSubmitGUIClick(event, player, submitGUI);
            return;
        }

        // 处理定向附魔书界面的特殊逻辑（单物品输入格）
        if (gui instanceof TargetedBookMachineGUI targetGUI) {
            handleTargetedBookClick(event, player, targetGUI);
            return;
        }

        // 处理商店物品界面的点击（需要传递点击类型）
        if (gui instanceof ShopItemsGUI shopItemsGUI) {
            event.setCancelled(true);

            // 先检查是否是按钮点击（返回/关闭等），如果不是则处理物品点击
            if (gui.hasAction(event.getSlot())) {
                gui.handleClick(event.getSlot(), player);
            } else {
                shopItemsGUI.handleItemClick(player, event.getSlot(), event.getClick());
            }
            return;
        }

        // 普通GUI：取消所有点击
        event.setCancelled(true);

        // 处理普通GUI点击
        gui.handleClick(event.getSlot(), player);
    }

    private void handleSellGUIClick(InventoryClickEvent event, Player player, SellGUI sellGUI) {
        Inventory clickedInventory = event.getClickedInventory();
        int slot = event.getSlot();
        InventoryAction action = event.getAction();

        // 点击顶部GUI（出售界面）
        if (clickedInventory == sellGUI.getInventory()) {
            // 点击出售格子
            if (sellGUI.isSellSlot(slot)) {
                // 左键或右键点击出售格子的物品，返回给玩家
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType().isItem()) {
                    // 检查玩家背包空间
                    if (player.getInventory().firstEmpty() == -1) {
                        player.sendMessage("§c背包已满！");
                        event.setCancelled(true);
                        return;
                    }
                    // 正常处理，让物品返回背包
                    event.setCancelled(false);
                }
                return;
            }

            // 点击按钮区域（非出售格子），取消事件并处理
            event.setCancelled(true);
            sellGUI.handleClick(slot, player);
            return;
        }

        // 点击底部背包
        if (clickedInventory == player.getInventory()) {
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.getType().isItem()) {
                event.setCancelled(true);
                return;
            }

            // 检查是否是放入出售格子的操作
            boolean isPutAction = action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||  // Shift+点击
                                 action == InventoryAction.PLACE_ALL ||
                                 action == InventoryAction.PLACE_ONE ||
                                 action == InventoryAction.PLACE_SOME ||
                                 action == InventoryAction.SWAP_WITH_CURSOR;

            if (isPutAction) {
                // 检查是否有空的出售格子
                int emptySlot = -1;
                for (int sellSlot : sellGUI.getSellSlots()) {
                    if (sellGUI.getInventory().getItem(sellSlot) == null) {
                        emptySlot = sellSlot;
                        break;
                    }
                }

                if (emptySlot == -1) {
                    player.sendMessage("§c出售格子已满！");
                    event.setCancelled(true);
                    return;
                }

                // 对于Shift+点击，手动处理
                if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    event.setCancelled(true);
                    ItemStack clone = item.clone();
                    sellGUI.getInventory().setItem(emptySlot, clone);
                    // 从玩家背包移除物品
                    player.getInventory().removeItem(item);
                }
                // 其他操作（普通点击拖放）允许正常处理
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AbstractGUI gui)) return;

        // 出售界面允许拖放到出售格子
        if (gui instanceof SellGUI sellGUI) {
            for (int slot : event.getRawSlots()) {
                if (slot < sellGUI.getInventory().getSize() && !sellGUI.isSellSlot(slot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        // 全球商店上架界面允许拖放到上架格子
        if (gui instanceof GlobalShopSubmitGUI submitGUI) {
            for (int slot : event.getRawSlots()) {
                if (slot < submitGUI.getInventory().getSize() && !submitGUI.isSubmitSlot(slot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        // 定向附魔书界面允许拖放到输入格
        if (gui instanceof TargetedBookMachineGUI targetGUI) {
            for (int slot : event.getRawSlots()) {
                if (slot < targetGUI.getInventory().getSize() && !targetGUI.isInputSlot(slot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        AbstractGUI gui = GUIManager.getOpenGUI(player.getUniqueId());
        if (gui != null) {
            // 如果是出售界面，将格子里的物品返回给玩家
            if (gui instanceof SellGUI sellGUI) {
                returnItemsToPlayer(player, sellGUI);
            }

            // 全球商店上架界面关闭时退回物品
            if (gui instanceof GlobalShopSubmitGUI submitGUI) {
                returnGlobalShopItemsToPlayer(player, submitGUI);
            }

            // 定向附魔书界面关闭时退回输入格的武器（不消耗）
            if (gui instanceof TargetedBookMachineGUI targetGUI) {
                returnTargetedBookInput(player, targetGUI);
            }

            gui.onClose();
        }
    }

    /**
     * 将出售界面的物品返回给玩家
     * 在 Folia 环境下需要在玩家所在区域线程执行
     */
    private void returnItemsToPlayer(Player player, SellGUI sellGUI) {
        // 收集需要返回的物品
        List<ItemStack> itemsToReturn = new ArrayList<>();
        for (int slot : sellGUI.getSellSlots()) {
            ItemStack item = sellGUI.getInventory().getItem(slot);
            if (item != null && item.getType().isItem()) {
                itemsToReturn.add(item);
            }
        }

        if (itemsToReturn.isEmpty()) {
            return;
        }

        // 在玩家区域线程执行物品操作
        player.getScheduler().execute(dev.user.shop.FoliaShopPlugin.getInstance(), () -> {
            for (ItemStack item : itemsToReturn) {
                java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        }, null, 1L);
    }

    private void handleGlobalShopSubmitGUIClick(InventoryClickEvent event, Player player, GlobalShopSubmitGUI submitGUI) {
        Inventory clickedInventory = event.getClickedInventory();
        int slot = event.getSlot();
        InventoryAction action = event.getAction();

        // 点击顶部 GUI
        if (clickedInventory == submitGUI.getInventory()) {
            if (submitGUI.isSubmitSlot(slot)) {
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType().isItem()) {
                    if (player.getInventory().firstEmpty() == -1) {
                        player.sendMessage("§c背包已满！");
                        event.setCancelled(true);
                        return;
                    }
                    event.setCancelled(false);
                }
                return;
            }
            event.setCancelled(true);
            submitGUI.handleClick(slot, player);
            return;
        }

        // 点击底部背包
        if (clickedInventory == player.getInventory()) {
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.getType().isItem()) {
                event.setCancelled(true);
                return;
            }

            boolean isPutAction = action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                                 action == InventoryAction.PLACE_ALL ||
                                 action == InventoryAction.PLACE_ONE ||
                                 action == InventoryAction.PLACE_SOME ||
                                 action == InventoryAction.SWAP_WITH_CURSOR;

            if (isPutAction) {
                int emptySlot = -1;
                for (int submitSlot : submitGUI.getSubmitSlots()) {
                    if (submitGUI.getInventory().getItem(submitSlot) == null) {
                        emptySlot = submitSlot;
                        break;
                    }
                }

                if (emptySlot == -1) {
                    player.sendMessage("§c上架格子已满！");
                    event.setCancelled(true);
                    return;
                }

                if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    event.setCancelled(true);
                    ItemStack clone = item.clone();
                    submitGUI.getInventory().setItem(emptySlot, clone);
                    player.getInventory().removeItem(item);
                }
            }
        }
    }

    private void returnGlobalShopItemsToPlayer(Player player, GlobalShopSubmitGUI submitGUI) {
        List<ItemStack> itemsToReturn = new ArrayList<>();
        for (int slot : submitGUI.getSubmitSlots()) {
            ItemStack item = submitGUI.getInventory().getItem(slot);
            if (item != null && item.getType().isItem()) {
                itemsToReturn.add(item);
                submitGUI.getInventory().setItem(slot, null);  // 立即清空，防止二次 close 重复收集导致双倍退回
            }
        }

        if (itemsToReturn.isEmpty()) return;

        player.getScheduler().execute(dev.user.shop.FoliaShopPlugin.getInstance(), () -> {
            for (ItemStack item : itemsToReturn) {
                java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        }, null, 1L);
    }

    /**
     * 定向附魔书界面的点击处理（仿 GlobalShopSubmitGUI，单输入格）。
     * 输入格允许放入/取出；按钮格 cancel+路由；背包 Shift+点击移入输入格。
     */
    private void handleTargetedBookClick(InventoryClickEvent event, Player player, TargetedBookMachineGUI gui) {
        Inventory clickedInventory = event.getClickedInventory();
        int slot = event.getSlot();
        InventoryAction action = event.getAction();

        // 点击顶部 GUI
        if (clickedInventory == gui.getInventory()) {
            if (gui.isInputSlot(slot)) {
                // 禁止按 Q 丢弃输入格里的武器（DROP_ONE_SLOT/DROP_ALL_SLOT）
                if (action.name().startsWith("DROP")) {
                    event.setCancelled(true);
                    return;
                }
                // 输入格：允许放入/取出（取出时检查背包空间）
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType().isItem()) {
                    if (player.getInventory().firstEmpty() == -1) {
                        player.sendMessage("§c背包已满！");
                        event.setCancelled(true);
                        return;
                    }
                }
                event.setCancelled(false);
                return;
            }
            // 按钮/装饰格
            event.setCancelled(true);
            gui.handleClick(slot, player);
            return;
        }

        // 点击底部背包
        if (clickedInventory == player.getInventory()) {
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.getType().isItem()) {
                event.setCancelled(true);
                return;
            }

            // Shift+点击：把物品移到输入格
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                if (gui.getInventory().getItem(TargetedBookMachineGUI.INPUT_SLOT) != null) {
                    player.sendMessage("§c输入格已有物品，请先取出！");
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                ItemStack clone = item.clone();
                gui.getInventory().setItem(TargetedBookMachineGUI.INPUT_SLOT, clone);
                player.getInventory().removeItem(item);
                return;
            }
            // 其他点击：允许玩家正常整理背包（不 cancel）
        }
    }

    /**
     * 定向附魔书界面关闭时退回输入格的武器（Folia 下需在玩家区域线程执行）
     */
    private void returnTargetedBookInput(Player player, TargetedBookMachineGUI gui) {
        ItemStack item = gui.getInventory().getItem(TargetedBookMachineGUI.INPUT_SLOT);
        if (item == null || !item.getType().isItem()) return;

        player.getScheduler().execute(dev.user.shop.FoliaShopPlugin.getInstance(), () -> {
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }, null, 1L);
    }
}
