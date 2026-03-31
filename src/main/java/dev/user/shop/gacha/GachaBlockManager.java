package dev.user.shop.gacha;

import dev.user.shop.FoliaShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 扭蛋机方块绑定管理器
 */
public class GachaBlockManager {

    private final FoliaShopPlugin plugin;
    // Map<WorldUUID, Map<Position, MachineId>>
    private final Map<UUID, Map<BlockVector, String>> blockBindings = new ConcurrentHashMap<>();

    public GachaBlockManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
        loadBindingsAsync();
    }

    /**
     * 重新加载方块绑定（用于导入后刷新）
     */
    public void reload() {
        loadBindingsAsync();
    }

    /**
     * 从数据库异步加载所有绑定（不阻塞）
     */
    private void loadBindingsAsync() {
        plugin.getDatabaseQueue().submit("loadBindings", conn -> {
            List<GachaBlockBinding> bindings = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM gacha_block_bindings")) {
                while (rs.next()) {
                    bindings.add(createBindingFromResultSet(rs));
                }
            }
            return bindings;
        }, bindings -> {
            // 原子替换：先构建新 map，再一次性替换，避免中间状态
            Map<UUID, Map<BlockVector, String>> newMap = new ConcurrentHashMap<>();
            for (GachaBlockBinding binding : bindings) {
                newMap
                    .computeIfAbsent(binding.getWorldUuid(), k -> new ConcurrentHashMap<>())
                    .put(binding.getPosition(), binding.getMachineId());
            }
            blockBindings.clear();
            blockBindings.putAll(newMap);
            plugin.getLogger().info("已加载 " + getTotalBindingCount() + " 个扭蛋机方块绑定");
        }, error -> {
            plugin.getLogger().severe("加载扭蛋机方块绑定失败: " + error.getMessage());
        });
    }

    /**
     * 获取方块绑定的扭蛋机ID
     * @return 扭蛋机ID，未绑定返回 null
     */
    public String getMachineByBlock(Block block) {
        return getMachineByBlock(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * 获取方块绑定的扭蛋机ID
     * @return 扭蛋机ID，未绑定返回 null
     */
    public String getMachineByBlock(UUID worldUuid, int x, int y, int z) {
        Map<BlockVector, String> worldBindings = blockBindings.get(worldUuid);
        if (worldBindings == null) {
            return null;
        }
        return worldBindings.get(new BlockVector(x, y, z));
    }

    /**
     * 检查方块是否已绑定
     */
    public boolean isBlockBound(Block block) {
        return getMachineByBlock(block) != null;
    }

    /**
     * 检查方块是否已绑定
     */
    public boolean isBlockBound(UUID worldUuid, int x, int y, int z) {
        return getMachineByBlock(worldUuid, x, y, z) != null;
    }

    /**
     * 绑定方块到扭蛋机（异步）
     * @param block 目标方块
     * @param machineId 扭蛋机ID
     * @param player 操作玩家
     * @param callback 回调函数，参数：是否成功，错误信息（失败时）
     */
    public void bindBlock(Block block, String machineId, Player player, Consumer<BindResult> callback) {
        UUID worldUuid = block.getWorld().getUID();
        BlockVector pos = new BlockVector(block.getX(), block.getY(), block.getZ());

        // 检查是否已绑定
        if (isBlockBound(block)) {
            String existingMachine = getMachineByBlock(block);
            callback.accept(new BindResult(false, "该方块已绑定到扭蛋机 '" + existingMachine + "'，请先解绑"));
            return;
        }

        // 异步保存到数据库
        plugin.getDatabaseQueue().submit("bindGachaBlock", conn -> {
            String sql;
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();

            if (isMySQL) {
                sql = "INSERT INTO gacha_block_bindings (world_uuid, block_x, block_y, block_z, machine_id, created_by, created_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO gacha_block_bindings (world_uuid, block_x, block_y, block_z, machine_id, created_by, created_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?)";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, worldUuid.toString());
                ps.setInt(2, block.getX());
                ps.setInt(3, block.getY());
                ps.setInt(4, block.getZ());
                ps.setString(5, machineId);
                ps.setString(6, player.getUniqueId().toString());
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();

                // 获取生成的ID
                int bindingId = -1;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        bindingId = rs.getInt(1);
                    }
                }

                // 更新内存
                blockBindings
                    .computeIfAbsent(worldUuid, k -> new ConcurrentHashMap<>())
                    .put(pos, machineId);

                return bindingId;
            }
        }, bindingId -> {
            // 创建展示实体
            if (bindingId != null && bindingId > 0) {
                GachaBlockBinding binding = new GachaBlockBinding(
                    bindingId, worldUuid, pos, machineId,
                    player.getUniqueId(), System.currentTimeMillis()
                );
                plugin.getGachaDisplayManager().createDisplay(binding);
            }
            callback.accept(new BindResult(true, null));
        },
        error -> callback.accept(new BindResult(false, "数据库错误: " + error.getMessage())));
    }

    /**
     * 解绑方块（异步）
     * @param block 目标方块
     * @param callback 回调函数，参数：是否成功，错误信息（失败时）
     */
    public void unbindBlock(Block block, Consumer<BindResult> callback) {
        UUID worldUuid = block.getWorld().getUID();
        BlockVector pos = new BlockVector(block.getX(), block.getY(), block.getZ());

        // 检查是否已绑定（内存检查）
        String existingMachine = getMachineByBlock(block);
        if (existingMachine == null) {
            callback.accept(new BindResult(false, "该方块未绑定任何扭蛋机"));
            return;
        }

        // 异步查询绑定信息并删除
        getBinding(worldUuid, pos, binding -> {
            if (binding == null) {
                callback.accept(new BindResult(false, "无法获取绑定信息"));
                return;
            }

            // 异步从数据库删除
            plugin.getDatabaseQueue().submit("unbindGachaBlock", conn -> {
                String sql = "DELETE FROM gacha_block_bindings WHERE world_uuid = ? AND block_x = ? AND block_y = ? AND block_z = ?";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, worldUuid.toString());
                    ps.setInt(2, block.getX());
                    ps.setInt(3, block.getY());
                    ps.setInt(4, block.getZ());
                    int affected = ps.executeUpdate();

                    if (affected > 0) {
                        // 更新内存
                        Map<BlockVector, String> worldBindings = blockBindings.get(worldUuid);
                        if (worldBindings != null) {
                            worldBindings.remove(pos);
                            if (worldBindings.isEmpty()) {
                                blockBindings.remove(worldUuid);
                            }
                        }
                        return true;
                    }
                    return false;
                }
            }, success -> {
                // 调度到区域线程移除展示实体（范围删除 Y 轴 cleanupRange 格内所有 ItemDisplay）
                World world = Bukkit.getWorld(binding.getWorldUuid());

                if (world != null) {
                    Location baseLoc = new Location(
                        world,
                        binding.getPosition().getBlockX() + 0.5,
                        binding.getPosition().getBlockY(),
                        binding.getPosition().getBlockZ() + 0.5
                    );
                    float cleanupRange = plugin.getShopConfig().getDisplayEntityCleanupRange();
                    double minY = baseLoc.getY();
                    double maxY = baseLoc.getY() + cleanupRange;
                    double centerX = baseLoc.getX();
                    double centerZ = baseLoc.getZ();

                    plugin.getServer().getRegionScheduler().execute(plugin, baseLoc, () -> {
                        // 1. 先清理绑定的 ItemDisplay（从内存和数据库中移除）
                        plugin.getGachaDisplayManager().removeDisplay(binding);

                        // 2. 清理范围内的所有 ItemDisplay（包括遗留的）
                        for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                            Location loc = display.getLocation();
                            // Y轴范围判断
                            if (loc.getY() >= minY && loc.getY() <= maxY) {
                                // X/Z 范围限制（确保是同一方块区域）
                                if (Math.abs(loc.getX() - centerX) < 1.0 &&
                                    Math.abs(loc.getZ() - centerZ) < 1.0) {
                                    display.remove();
                                }
                            }
                        }
                    });
                }
                callback.accept(new BindResult(true, existingMachine));
            },
            error -> callback.accept(new BindResult(false, "数据库错误: " + error.getMessage())));
        });
    }

    /**
     * 获取所有绑定列表（异步）
     * @param callback 回调函数，参数为绑定列表
     */
    public void getAllBindings(Consumer<List<GachaBlockBinding>> callback) {
        plugin.getDatabaseQueue().submit("getAllBindings", conn -> {
            List<GachaBlockBinding> result = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM gacha_block_bindings ORDER BY world_uuid, block_x, block_y, block_z")) {
                while (rs.next()) {
                    result.add(createBindingFromResultSet(rs));
                }
            }
            return result;
        }, callback, error -> {
            plugin.getLogger().warning("查询绑定列表失败: " + error.getMessage());
            callback.accept(new ArrayList<>());
        });
    }

    /**
     * 获取指定扭蛋机的绑定列表（异步）
     * @param machineId 扭蛋机ID
     * @param callback 回调函数，参数为绑定列表
     */
    public void getBindingsByMachine(String machineId, Consumer<List<GachaBlockBinding>> callback) {
        plugin.getDatabaseQueue().submit("getBindingsByMachine", conn -> {
            List<GachaBlockBinding> result = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM gacha_block_bindings WHERE machine_id = ? ORDER BY world_uuid, block_x, block_y, block_z")) {
                ps.setString(1, machineId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    result.add(createBindingFromResultSet(rs));
                }
            }
            return result;
        }, callback, error -> {
            plugin.getLogger().warning("查询绑定列表失败: " + error.getMessage());
            callback.accept(new ArrayList<>());
        });
    }

    private GachaBlockBinding createBindingFromResultSet(ResultSet rs) throws SQLException {
        String displayUuidStr = rs.getString("display_entity_uuid");
        return new GachaBlockBinding(
            rs.getInt("id"),
            UUID.fromString(rs.getString("world_uuid")),
            new BlockVector(rs.getInt("block_x"), rs.getInt("block_y"), rs.getInt("block_z")),
            rs.getString("machine_id"),
            rs.getString("created_by") != null ? UUID.fromString(rs.getString("created_by")) : null,
            rs.getLong("created_at"),
            displayUuidStr != null ? UUID.fromString(displayUuidStr) : null,
            rs.getBoolean("outdated")
        );
    }

    /**
     * 获取世界的方块绑定
     * @return Map<Position, MachineId>，如果没有返回 null
     */
    public Map<BlockVector, String> getBlockBindingsForWorld(UUID worldUuid) {
        return blockBindings.get(worldUuid);
    }

    /**
     * 获取指定位置的绑定信息（异步）
     * @param worldUuid 世界UUID
     * @param pos 方块位置
     * @param callback 回调函数，参数为绑定信息（可能为null）
     */
    public void getBinding(UUID worldUuid, BlockVector pos, Consumer<GachaBlockBinding> callback) {
        plugin.getDatabaseQueue().submit("getBinding", conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM gacha_block_bindings WHERE world_uuid = ? AND block_x = ? AND block_y = ? AND block_z = ?")) {
                ps.setString(1, worldUuid.toString());
                ps.setInt(2, pos.getBlockX());
                ps.setInt(3, pos.getBlockY());
                ps.setInt(4, pos.getBlockZ());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return createBindingFromResultSet(rs);
                }
            }
            return null;
        }, callback, error -> {
            plugin.getLogger().warning("查询绑定信息失败: " + error.getMessage());
            callback.accept(null);
        });
    }

    /**
     * 获取绑定总数
     */
    public int getTotalBindingCount() {
        return blockBindings.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * 获取世界名称（安全）
     */
    public String getWorldName(UUID worldUuid) {
        World world = Bukkit.getWorld(worldUuid);
        return world != null ? world.getName() : worldUuid.toString().substring(0, 8);
    }

    /**
     * 绑定操作结果
     */
    public record BindResult(boolean success, String message) {
    }

    /**
     * 简单的位置记录类（用于GachaDisplayManager）
     */
    public record BlockPos(int x, int y, int z) {
    }
}
