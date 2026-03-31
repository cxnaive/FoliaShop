package dev.user.shop.economy;

import dev.user.shop.FoliaShopPlugin;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * PlayerPoints 点数管理器（软依赖）
 * 处理 PlayerPoints 插件的点数操作
 */
public class PlayerPointsManager {

    private final FoliaShopPlugin plugin;
    private PlayerPointsAPI playerPointsAPI;
    private boolean enabled = false;

    public PlayerPointsManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化 PlayerPoints 支持
     * 检查插件是否存在并可用
     */
    public void init() {
        try {
            // 检查 PlayerPoints 插件是否已加载
            if (plugin.getServer().getPluginManager().getPlugin("PlayerPoints") == null) {
                plugin.getLogger().info("PlayerPoints 插件未找到，点数功能不可用");
                return;
            }

            // 获取 PlayerPoints 实例
            PlayerPoints playerPoints = PlayerPoints.getInstance();
            if (playerPoints == null) {
                plugin.getLogger().info("PlayerPoints 实例未找到，点数功能不可用");
                return;
            }

            // 获取 API
            this.playerPointsAPI = playerPoints.getAPI();
            if (this.playerPointsAPI == null) {
                plugin.getLogger().info("PlayerPoints API 未找到，点数功能不可用");
                return;
            }

            this.enabled = true;
            plugin.getLogger().info("已连接到 PlayerPoints 点数系统");
        } catch (Exception e) {
            plugin.getLogger().info("PlayerPoints 初始化失败: " + e.getMessage());
            this.enabled = false;
        }
    }

    /**
     * 检查 PlayerPoints 是否可用
     */
    public boolean isEnabled() {
        return enabled;
    }

    // ==================== 同步方法 ====================

    /**
     * 获取玩家点数余额（同步）
     * @param player 玩家
     * @return 点数余额
     */
    public int getPoints(Player player) {
        if (!enabled || playerPointsAPI == null) return 0;
        return getPoints(player.getUniqueId());
    }

    /**
     * 获取玩家点数余额（同步）
     * @param playerUuid 玩家UUID
     * @return 点数余额
     */
    public int getPoints(UUID playerUuid) {
        if (!enabled || playerPointsAPI == null) return 0;
        try {
            return playerPointsAPI.look(playerUuid);
        } catch (Exception e) {
            plugin.getLogger().warning("获取点数余额失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 扣除玩家点数（同步）
     * @param player 玩家
     * @param amount 扣除数量
     * @return 是否成功
     */
    public boolean takePoints(Player player, int amount) {
        if (!enabled || playerPointsAPI == null) return false;
        return takePoints(player.getUniqueId(), amount);
    }

    /**
     * 扣除玩家点数（同步）
     * @param playerUuid 玩家UUID
     * @param amount 扣除数量
     * @return 是否成功
     */
    public boolean takePoints(UUID playerUuid, int amount) {
        if (!enabled || playerPointsAPI == null) return false;
        if (amount <= 0) return true;
        try {
            return playerPointsAPI.take(playerUuid, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("扣除点数失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 给予玩家点数（同步）
     * @param player 玩家
     * @param amount 给予数量
     * @return 是否成功
     */
    public boolean givePoints(Player player, int amount) {
        if (!enabled || playerPointsAPI == null) return false;
        return givePoints(player.getUniqueId(), amount);
    }

    /**
     * 给予玩家点数（同步）
     * @param playerUuid 玩家UUID
     * @param amount 给予数量
     * @return 是否成功
     */
    public boolean givePoints(UUID playerUuid, int amount) {
        if (!enabled || playerPointsAPI == null) return false;
        if (amount <= 0) return true;
        try {
            return playerPointsAPI.give(playerUuid, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("给予点数失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 设置玩家点数（同步）
     * @param player 玩家
     * @param amount 设置数量
     * @return 是否成功
     */
    public boolean setPoints(Player player, int amount) {
        if (!enabled || playerPointsAPI == null) return false;
        return setPoints(player.getUniqueId(), amount);
    }

    /**
     * 设置玩家点数（同步）
     * @param playerUuid 玩家UUID
     * @param amount 设置数量
     * @return 是否成功
     */
    public boolean setPoints(UUID playerUuid, int amount) {
        if (!enabled || playerPointsAPI == null) return false;
        try {
            return playerPointsAPI.set(playerUuid, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("设置点数失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 重置玩家点数为0（同步）
     * @param player 玩家
     * @return 是否成功
     */
    public boolean resetPoints(Player player) {
        if (!enabled || playerPointsAPI == null) return false;
        return resetPoints(player.getUniqueId());
    }

    /**
     * 重置玩家点数为0（同步）
     * @param playerUuid 玩家UUID
     * @return 是否成功
     */
    public boolean resetPoints(UUID playerUuid) {
        if (!enabled || playerPointsAPI == null) return false;
        try {
            return playerPointsAPI.reset(playerUuid);
        } catch (Exception e) {
            plugin.getLogger().warning("重置点数失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 玩家间转账（同步）
     * @param source 转出玩家
     * @param target 转入玩家
     * @param amount 转账数量
     * @return 是否成功
     */
    public boolean payPoints(Player source, Player target, int amount) {
        if (!enabled || playerPointsAPI == null) return false;
        return payPoints(source.getUniqueId(), target.getUniqueId(), amount);
    }

    /**
     * 玩家间转账（同步）
     * @param sourceUuid 转出玩家UUID
     * @param targetUuid 转入玩家UUID
     * @param amount 转账数量
     * @return 是否成功
     */
    public boolean payPoints(UUID sourceUuid, UUID targetUuid, int amount) {
        if (!enabled || playerPointsAPI == null) return false;
        if (amount <= 0) return false;
        try {
            return playerPointsAPI.pay(sourceUuid, targetUuid, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("转账失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查玩家是否有足够点数（同步）
     * @param player 玩家
     * @param amount 需要数量
     * @return 是否足够
     */
    public boolean hasEnoughPoints(Player player, int amount) {
        if (!enabled) return false;
        if (amount <= 0) return true;
        return getPoints(player) >= amount;
    }

    /**
     * 检查玩家是否有足够点数（同步）
     * @param playerUuid 玩家UUID
     * @param amount 需要数量
     * @return 是否足够
     */
    public boolean hasEnoughPoints(UUID playerUuid, int amount) {
        if (!enabled) return false;
        if (amount <= 0) return true;
        return getPoints(playerUuid) >= amount;
    }

    // ==================== 异步方法 ====================

    /**
     * 异步获取玩家点数余额
     * @param player 玩家
     * @param callback 回调函数，参数为余额
     */
    public void getPointsAsync(Player player, Consumer<Integer> callback) {
        getPointsAsync(player, callback, null);
    }

    /**
     * 异步获取玩家点数余额
     * @param player 玩家
     * @param callback 成功回调
     * @param errorCallback 错误回调（可为null）
     */
    public void getPointsAsync(Player player, Consumer<Integer> callback, Consumer<Exception> errorCallback) {
        if (!enabled) {
            callback.accept(0);
            return;
        }

        // PlayerPoints 没有原生异步API，使用全局调度器
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            try {
                int result = getPoints(player);
                callback.accept(result);
            } catch (Exception e) {
                plugin.getLogger().warning("异步获取点数失败: " + e.getMessage());
                if (errorCallback != null) {
                    errorCallback.accept(e);
                } else {
                    callback.accept(0);
                }
            }
        });
    }

    /**
     * 异步扣除点数
     * @param player 玩家
     * @param amount 扣除数量
     * @param callback 回调函数，参数为是否成功
     */
    public void takePointsAsync(Player player, int amount, Consumer<Boolean> callback) {
        takePointsAsync(player, amount, callback, null);
    }

    /**
     * 异步扣除点数
     * @param player 玩家
     * @param amount 扣除数量
     * @param callback 成功回调
     * @param errorCallback 错误回调（可为null）
     */
    public void takePointsAsync(Player player, int amount, Consumer<Boolean> callback, Consumer<Exception> errorCallback) {
        if (!enabled) {
            callback.accept(false);
            return;
        }
        if (amount <= 0) {
            callback.accept(true);
            return;
        }

        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            try {
                // 先检查余额
                int balance = getPoints(player);
                if (balance < amount) {
                    callback.accept(false);
                    return;
                }
                // 执行扣除
                boolean result = takePoints(player, amount);
                callback.accept(result);
            } catch (Exception e) {
                plugin.getLogger().warning("异步扣除点数失败: " + e.getMessage());
                if (errorCallback != null) {
                    errorCallback.accept(e);
                } else {
                    callback.accept(false);
                }
            }
        });
    }

    /**
     * 异步给予点数
     * @param player 玩家
     * @param amount 给予数量
     * @param callback 回调函数，参数为是否成功
     */
    public void givePointsAsync(Player player, int amount, Consumer<Boolean> callback) {
        givePointsAsync(player, amount, callback, null);
    }

    /**
     * 异步给予点数
     * @param player 玩家
     * @param amount 给予数量
     * @param callback 成功回调
     * @param errorCallback 错误回调（可为null）
     */
    public void givePointsAsync(Player player, int amount, Consumer<Boolean> callback, Consumer<Exception> errorCallback) {
        if (!enabled) {
            callback.accept(false);
            return;
        }
        if (amount <= 0) {
            callback.accept(true);
            return;
        }

        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            try {
                boolean result = givePoints(player, amount);
                callback.accept(result);
            } catch (Exception e) {
                plugin.getLogger().warning("异步给予点数失败: " + e.getMessage());
                if (errorCallback != null) {
                    errorCallback.accept(e);
                } else {
                    callback.accept(false);
                }
            }
        });
    }

    /**
     * 异步设置点数
     * @param player 玩家
     * @param amount 设置数量
     * @param callback 回调函数，参数为是否成功
     */
    public void setPointsAsync(Player player, int amount, Consumer<Boolean> callback) {
        setPointsAsync(player, amount, callback, null);
    }

    /**
     * 异步设置点数
     * @param player 玩家
     * @param amount 设置数量
     * @param callback 成功回调
     * @param errorCallback 错误回调（可为null）
     */
    public void setPointsAsync(Player player, int amount, Consumer<Boolean> callback, Consumer<Exception> errorCallback) {
        if (!enabled) {
            callback.accept(false);
            return;
        }

        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
            try {
                boolean result = setPoints(player, amount);
                callback.accept(result);
            } catch (Exception e) {
                plugin.getLogger().warning("异步设置点数失败: " + e.getMessage());
                if (errorCallback != null) {
                    errorCallback.accept(e);
                } else {
                    callback.accept(false);
                }
            }
        });
    }

    /**
     * 异步检查是否足够点数
     * @param player 玩家
     * @param amount 需要数量
     * @param callback 回调函数，参数为是否足够
     */
    public void hasEnoughPointsAsync(Player player, int amount, Consumer<Boolean> callback) {
        hasEnoughPointsAsync(player, amount, callback, null);
    }

    /**
     * 异步检查是否足够点数
     * @param player 玩家
     * @param amount 需要数量
     * @param callback 成功回调
     * @param errorCallback 错误回调（可为null）
     */
    public void hasEnoughPointsAsync(Player player, int amount, Consumer<Boolean> callback, Consumer<Exception> errorCallback) {
        if (!enabled || amount <= 0) {
            callback.accept(true);
            return;
        }

        getPointsAsync(player, balance -> {
            callback.accept(balance >= amount);
        }, errorCallback);
    }

    /**
     * 格式化点数显示（带千分位分隔符）
     * @param amount 点数数量
     * @return 格式化后的字符串
     */
    public String format(int amount) {
        return String.format("%,d", amount);
    }

    /**
     * 获取 PlayerPointsAPI 实例（高级用法）
     * @return API 实例，如果未启用则返回 null
     */
    public PlayerPointsAPI getAPI() {
        return playerPointsAPI;
    }
}
