package dev.user.shop.listener;

import dev.user.shop.FoliaShopPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 全球商店登录提醒监听器
 * 玩家登录时检查待领取物品/收益并发送提醒
 */
public class GlobalShopJoinListener implements Listener {

    private final FoliaShopPlugin plugin;

    public GlobalShopJoinListener(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getShopConfig().isGlobalShopEnabled()) return;
        if (!player.hasPermission("foliashop.globalshop.use")) return;

        // 延迟 2 秒发送提醒（避免被登录消息刷屏）
        player.getScheduler().execute(plugin, () -> {
            if (!player.isOnline()) return;

            plugin.getGlobalShopManager().getReturnManager().getUnclaimedCount(player.getUniqueId(), count -> {
                if (!player.isOnline() || count <= 0) return;
                player.sendMessage("§e你有 §6" + count + " §e件未领取的物品/收益，使用 §6/foliashop globalshop §e查看");
            });
        }, null, 40L); // 40 ticks = 2 秒
    }
}
