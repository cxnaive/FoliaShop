package dev.user.shop.economy;

import dev.user.shop.FoliaShopPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 点券兑换会话管理器
 */
public class ExchangeSessionManager {

    private static final long TIMEOUT_MILLIS = 30_000L; // 30 秒超时

    private final FoliaShopPlugin plugin;
    private final Map<UUID, ExchangeSession> sessions = new ConcurrentHashMap<>();
    private ScheduledTask timeoutTask;

    public ExchangeSessionManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
        startTimeoutChecker();
    }

    private void startTimeoutChecker() {
        timeoutTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (sessions.isEmpty()) return;

            long now = System.currentTimeMillis();
            sessions.entrySet().removeIf(entry -> {
                if (now - entry.getValue().getCreateTime() > TIMEOUT_MILLIS) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                            player.sendMessage("§c兑换超时，已自动取消");
                        });
                    }
                    return true;
                }
                return false;
            });
        }, 20L, 20L); // 每秒检查一次
    }

    public void startSession(Player player) {
        sessions.put(player.getUniqueId(), new ExchangeSession(player.getUniqueId()));
    }

    public ExchangeSession getSession(UUID playerUuid) {
        ExchangeSession session = sessions.get(playerUuid);
        if (session == null) return null;
        // 检查过期
        if (System.currentTimeMillis() - session.getCreateTime() > TIMEOUT_MILLIS) {
            sessions.remove(playerUuid);
            return null;
        }
        return session;
    }

    public void removeSession(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    public boolean hasSession(UUID playerUuid) {
        return getSession(playerUuid) != null;
    }

    public void shutdown() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        sessions.clear();
    }
}
