package dev.user.shop.listener;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.economy.ExchangeSession;
import dev.user.shop.economy.ExchangeSessionManager;
import dev.user.shop.economy.PlayerPointsManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 点券兑换聊天监听器
 * 监听玩家聊天输入，处理点券兑换金额
 */
public class ExchangeChatListener implements Listener {

    private final FoliaShopPlugin plugin;

    public ExchangeChatListener(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ExchangeSessionManager sessionManager = plugin.getExchangeSessionManager();

        ExchangeSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) return;

        // 取消事件，防止消息发送给其他玩家
        event.setCancelled(true);
        event.viewers().clear();

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // 处理取消
        if (message.equalsIgnoreCase("取消") || message.equalsIgnoreCase("cancel")) {
            sessionManager.removeSession(player.getUniqueId());
            player.sendMessage("§c已取消兑换");
            return;
        }

        // 解析金额
        int points;
        try {
            points = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            player.sendMessage("§c请输入有效的正整数");
            player.sendMessage("§7输入要兑换的点券数量，或输入'取消'取消");
            return;
        }

        if (points <= 0) {
            player.sendMessage("§c兑换数量必须大于 0");
            player.sendMessage("§7输入要兑换的点券数量，或输入'取消'取消");
            return;
        }

        // 移除 session（一次性使用）
        sessionManager.removeSession(player.getUniqueId());

        // 执行兑换（在全局区域线程）
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            if (!player.isOnline()) return;
            handleExchange(player, points);
        });
    }

    /**
     * 监听旧版聊天事件（兼容性，仅取消事件）
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLegacyPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ExchangeSessionManager sessionManager = plugin.getExchangeSessionManager();

        if (!sessionManager.hasSession(player.getUniqueId())) return;

        event.setCancelled(true);
        event.getRecipients().clear();
    }

    /**
     * 玩家退出时清理 session
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getExchangeSessionManager().removeSession(event.getPlayer().getUniqueId());
    }

    /**
     * 执行兑换逻辑
     * 先扣点券，扣成功后发金币，发金币失败则回滚点券
     */
    private void handleExchange(Player player, int points) {
        PlayerPointsManager ppManager = plugin.getPlayerPointsManager();

        // 1. 检查点券余额
        if (!ppManager.hasEnoughPoints(player, points)) {
            int balance = ppManager.getPoints(player);
            player.sendMessage("§c点券不足！你只有 §e" + balance + " §c点券");
            return;
        }

        // 2. 扣除点券（先扣）
        boolean taken = ppManager.takePoints(player, points);
        if (!taken) {
            player.sendMessage("§c扣除点券失败，请稍后再试");
            return;
        }

        // 3. 计算并发放金币
        double rate = plugin.getShopConfig().getExchangeRate();
        double coins = points * rate;

        boolean deposited = plugin.getEconomyManager().deposit(player, coins);
        if (!deposited) {
            // 发金币失败，回滚点券
            boolean rolledBack = ppManager.givePoints(player, points);
            if (!rolledBack) {
                plugin.getLogger().warning("点券兑换回滚失败！玩家: " + player.getName() + "，点券: " + points);
            }
            player.sendMessage("§c兑换失败，请稍后再试");
            return;
        }

        // 4. 成功
        String currencyName = plugin.getShopConfig().getCurrencyName();
        player.sendMessage("§a§l兑换成功！");
        player.sendMessage("§7消耗 §e" + points + " §7点券 → 获得 §e" + String.format("%.0f", coins) + " " + currencyName);

        plugin.getLogger().info("[点券兑换] " + player.getName() + ": " + points + " 点券 → " + coins + " " + currencyName);
    }
}
