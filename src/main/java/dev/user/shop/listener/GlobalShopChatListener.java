package dev.user.shop.listener;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.globalshop.GlobalShopSession;
import dev.user.shop.globalshop.GlobalShopSessionManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 全球商店聊天监听器
 * 拦截聊天事件获取上架价格输入
 */
public class GlobalShopChatListener implements Listener {

    private final FoliaShopPlugin plugin;

    public GlobalShopChatListener(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        GlobalShopSessionManager sessionManager = plugin.getGlobalShopManager().getSessionManager();

        GlobalShopSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) return;

        // 取消事件，防止消息发送给其他玩家
        event.setCancelled(true);
        event.viewers().clear();

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // 处理取消
        if (message.equalsIgnoreCase("取消") || message.equalsIgnoreCase("cancel")) {
            sessionManager.removeSession(player.getUniqueId());
            // 退回物品
            if (session.markConsumed()) {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    if (!player.isOnline()) return;
                    org.bukkit.inventory.ItemStack item = dev.user.shop.util.ItemDataUtil.deserializeItem(session.getItemData());
                    if (item != null) {
                        item.setAmount(session.getAmount());
                        player.getInventory().addItem(item);
                    }
                    player.sendMessage("§c已取消上架，物品已退回");
                });
            }
            return;
        }

        // 解析价格
        double price;
        try {
            price = Double.parseDouble(message);
        } catch (NumberFormatException e) {
            player.sendMessage("§c请输入有效的数字");
            player.sendMessage("§7输入总售价，或输入'取消'取消");
            return;
        }

        if (price <= 0) {
            player.sendMessage("§c价格必须大于 0");
            player.sendMessage("§7输入总售价，或输入'取消'取消");
            return;
        }

        if (price > 999_999_999_999.99) {
            player.sendMessage("§c价格超出上限");
            player.sendMessage("§7输入总售价，或输入'取消'取消");
            return;
        }

        if (Double.isInfinite(price) || Double.isNaN(price)) {
            player.sendMessage("§c请输入有效的数字");
            player.sendMessage("§7输入总售价，或输入'取消'取消");
            return;
        }

        // 移除 session
        sessionManager.removeSession(player.getUniqueId());

        // 执行上架（无论玩家是否在线，createListing 内部处理离线退回）
        plugin.getGlobalShopManager().createListing(player, session, price, success -> {});
    }

    /**
     * 监听旧版聊天事件（兼容性）
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLegacyPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getGlobalShopManager().getSessionManager().hasSession(player.getUniqueId())) return;

        event.setCancelled(true);
        event.getRecipients().clear();
    }

    /**
     * 玩家退出时清理 session 并退回物品
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GlobalShopSessionManager sessionManager = plugin.getGlobalShopManager().getSessionManager();
        GlobalShopSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session != null) {
            sessionManager.removeSession(event.getPlayer().getUniqueId());
            // 原子标记并退回物品，防止超时处理器重复处理
            if (session.markConsumed()) {
                org.bukkit.entity.Player player = event.getPlayer();
                org.bukkit.inventory.ItemStack item = dev.user.shop.util.ItemDataUtil.deserializeItem(session.getItemData());
                if (item != null) {
                    item.setAmount(session.getAmount());
                    java.util.Collection<org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(item).values();
                    if (!leftover.isEmpty()) {
                        int leftoverCount = leftover.stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
                        if (leftoverCount > 0) {
                            plugin.getGlobalShopManager().createReturnEntryForOffline(
                                    player.getUniqueId(), session.getItemData(), null, null, leftoverCount);
                        }
                        plugin.getLogger().warning("[全球商店] 玩家退出退回物品背包部分满，" + leftoverCount + "/" + session.getAmount() + " 存入待领取: " + player.getName());
                    }
                }
            }
        }
    }
}
