package dev.user.shop.globalshop;

import dev.user.shop.FoliaShopPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全球商店上架会话管理器
 * 管理玩家聊天价格输入的会话，30s 超时自动退回物品
 */
public class GlobalShopSessionManager {

    private static final long TIMEOUT_MILLIS = 30_000L;

    private final FoliaShopPlugin plugin;
    private final Map<UUID, GlobalShopSession> sessions = new ConcurrentHashMap<>();
    private ScheduledTask timeoutTask;

    public GlobalShopSessionManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
        startTimeoutChecker();
    }

    private void startTimeoutChecker() {
        timeoutTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (sessions.isEmpty()) return;

            long now = System.currentTimeMillis();
            sessions.entrySet().removeIf(entry -> {
                if (now - entry.getValue().getCreateTime() > TIMEOUT_MILLIS) {
                    handleTimeout(entry.getValue());
                    return true;
                }
                return false;
            });
        }, 20L, 20L);
    }

    private void handleTimeout(GlobalShopSession session) {
        // 取出物品数据并标记消费，防止重复退回
        if (!session.markConsumed()) return;

        Player player = Bukkit.getPlayer(session.getPlayerUuid());
        if (player == null || !player.isOnline()) {
            // 玩家离线：物品直接存入待领取（防丢）
            plugin.getGlobalShopManager().createReturnEntryForOffline(
                    session.getPlayerUuid(), session.getItemData(), null, null, session.getAmount());
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            if (!player.isOnline()) {
                // 调度执行期间玩家已下线：物品存入待领取（防丢）
                plugin.getGlobalShopManager().createReturnEntryForOffline(
                        session.getPlayerUuid(), session.getItemData(), null, null, session.getAmount());
                return;
            }
            // 退回物品到玩家背包
            org.bukkit.inventory.ItemStack item = dev.user.shop.util.ItemDataUtil.deserializeItem(session.getItemData());
            if (item == null) {
                // 反序列化失败：保留原始数据存入待领取（防丢）
                plugin.getGlobalShopManager().createReturnEntryForOffline(
                        session.getPlayerUuid(), session.getItemData(), null, null, session.getAmount());
                plugin.getLogger().warning("[全球商店] 会话超时退回物品反序列化失败，已存入待领取: " + session.getPlayerUuid());
                return;
            }
            item.setAmount(session.getAmount());
            java.util.Collection<org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(item).values();
            if (!leftover.isEmpty()) {
                int leftoverCount = leftover.stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
                if (leftoverCount > 0) {
                    plugin.getGlobalShopManager().createReturnEntryForOffline(
                            player.getUniqueId(), session.getItemData(), null, null, leftoverCount);
                }
                plugin.getLogger().warning("[全球商店] 会话超时退回物品背包部分满，" + leftoverCount + "/" + session.getAmount() + " 存入待领取: " + player.getName());
            }
            player.sendMessage("§c上架操作超时，物品已退回到你的背包");
        });
    }

    public void startSession(Player player, byte[] itemData, String itemKey,
                             String itemDisplayName, int amount) {
        // 检查并退回旧会话的物品（防止覆盖丢失）
        GlobalShopSession oldSession = sessions.get(player.getUniqueId());
        if (oldSession != null && oldSession.markConsumed()) {
            handleTimeout(oldSession);
        }
        sessions.put(player.getUniqueId(), new GlobalShopSession(
                player.getUniqueId(), itemData, itemKey, itemDisplayName, amount));
    }

    public GlobalShopSession getSession(UUID playerUuid) {
        GlobalShopSession session = sessions.get(playerUuid);
        if (session == null) return null;
        if (System.currentTimeMillis() - session.getCreateTime() > TIMEOUT_MILLIS) {
            sessions.remove(playerUuid);
            handleTimeout(session);
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
        // 退回所有活跃会话的物品
        for (Map.Entry<UUID, GlobalShopSession> entry : sessions.entrySet()) {
            handleTimeout(entry.getValue());
        }
        sessions.clear();
    }
}
