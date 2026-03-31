package dev.user.shop.gacha;

import dev.user.shop.FoliaShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扭蛋机展示实体管理器
 * 管理绑定方块上方的 Item Display 展示实体
 */
public class GachaDisplayManager {

    private final FoliaShopPlugin plugin;
    // Map<WorldUUID, Map<BlockPos, DisplayEntityUUID>>
    private final Map<UUID, Map<BlockPos, UUID>> displayEntities = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public GachaDisplayManager(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 标记管理器已加载完成
     */
    public void setLoaded() {
        this.loaded = true;
    }

    /**
     * 检查管理器是否已加载完成
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * 为绑定创建展示实体（必须在区域线程执行）
     * @param binding 方块绑定
     * @return 展示实体UUID，创建失败返回null
     */
    public UUID createDisplay(GachaBlockBinding binding) {
        // 检查是否启用展示实体（全局设置）
        if (!plugin.getShopConfig().isDisplayEntityEnabled()) {
            return null;
        }

        World world = Bukkit.getWorld(binding.getWorldUuid());
        if (world == null) {
            return null;
        }

        GachaMachine machine = plugin.getGachaManager().getMachine(binding.getMachineId());
        if (machine == null) {
            return null;
        }

        // 检查该扭蛋机是否启用展示实体
        DisplayEntityConfig effectiveConfig = getEffectiveConfig(machine);
        if (!effectiveConfig.isEnabled()) {
            plugin.getLogger().info("扭蛋机 " + machine.getId() + " 的展示实体已禁用");
            return null;
        }

        // 计算展示位置（方块上方配置的高度）
        float heightOffset = plugin.getShopConfig().getDisplayEntityHeightOffset();
        Location location = new Location(
            world,
            binding.getPosition().getBlockX() + 0.5,
            binding.getPosition().getBlockY() + heightOffset,
            binding.getPosition().getBlockZ() + 0.5
        );

        // Folia: 确保在区域线程创建实体
        if (!Bukkit.isOwnedByCurrentRegion(location)) {
            // 调度到正确的区域线程
            final GachaBlockBinding finalBinding = binding;
            final GachaMachine finalMachine = machine;
            plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
                createDisplayInternal(finalBinding, finalMachine, location);
            });
            return null; // 异步创建，无法立即返回UUID
        }

        return createDisplayInternal(binding, machine, location);
    }

    /**
     * 获取有效配置（全局配置 + 扭蛋机特定配置）
     */
    private DisplayEntityConfig getEffectiveConfig(GachaMachine machine) {
        DisplayEntityConfig defaultConfig = DisplayEntityConfig.fromShopConfig(plugin.getShopConfig());
        return machine.hasDisplayConfig()
            ? machine.getDisplayConfig().mergeWithParent(defaultConfig)
            : defaultConfig;
    }

    /**
     * 内部方法：实际创建展示实体（必须在正确的区域线程执行）
     */
    private UUID createDisplayInternal(GachaBlockBinding binding, GachaMachine machine, Location location) {
        // 获取有效配置
        DisplayEntityConfig effectiveConfig = getEffectiveConfig(machine);

        // 重新计算 location 的 Y 坐标（使用正确的高度偏移）
        Location spawnLocation = new Location(
            location.getWorld(),
            location.getX(),
            binding.getPosition().getBlockY() + effectiveConfig.getHeightOffset(),
            location.getZ()
        );

        // 创建 Item Display
        ItemDisplay display = (ItemDisplay) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ITEM_DISPLAY);

        // 设置展示物品
        ItemStack icon = machine.createIconItem(plugin);
        display.setItemStack(icon);

        // 设置 billboard 模式
        display.setBillboard(effectiveConfig.isFacePlayer() ? Display.Billboard.CENTER : Display.Billboard.FIXED);

        // 设置变换（缩放和初始旋转）
        float rotationYRad = (float) Math.toRadians(effectiveConfig.getRotationY());
        Transformation transformation = new Transformation(
            new Vector3f(0, 0, 0),
            new AxisAngle4f(rotationYRad, 0, 1, 0),
            new Vector3f(effectiveConfig.getScale(), effectiveConfig.getScale(), effectiveConfig.getScale()),
            new AxisAngle4f(0, 0, 0, 1)
        );
        display.setTransformation(transformation);

        // 设置阴影
        display.setShadowRadius(effectiveConfig.getShadowRadius());
        display.setShadowStrength(effectiveConfig.getShadowStrength());

        // 设置视图范围
        display.setViewRange(effectiveConfig.getViewRange());

        // 设置发光
        if (effectiveConfig.isGlowing()) {
            String glowColor = effectiveConfig.getGlowColor();
            if (glowColor != null && !glowColor.isEmpty()) {
                try {
                    display.setGlowColorOverride(org.bukkit.Color.fromRGB(
                        Integer.parseInt(glowColor.replace("#", ""), 16)
                    ));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("无效的发光颜色: " + glowColor + "，使用默认白色");
                    display.setGlowing(true);
                }
            } else {
                display.setGlowing(true);
            }
        }

        UUID displayUuid = display.getUniqueId();

        // 保存到内存
        BlockPos blockPos = new BlockPos(
            binding.getPosition().getBlockX(),
            binding.getPosition().getBlockY(),
            binding.getPosition().getBlockZ()
        );
        displayEntities
            .computeIfAbsent(binding.getWorldUuid(), k -> new ConcurrentHashMap<>())
            .put(blockPos, displayUuid);

        // 异步保存到数据库并设置 outdated = false
        saveDisplayUuidAndClearOutdated(binding.getId(), displayUuid);

        // 启动过时检测任务（1秒1次）
        startOutdatedCheckTask(display, binding.getId(), binding.getWorldUuid(), blockPos, machine);

        // 启动悬浮动画（如果启用）
        if (effectiveConfig.isFloatingAnimation()) {
            startInterpolationAnimation(display, binding.getId(), effectiveConfig);
        }

        // 启动粒子效果（如果启用）
        ParticleEffectConfig particleConfig = effectiveConfig.getParticleEffect();
        if (particleConfig != null && particleConfig.getType() != ParticleEffectConfig.EffectType.NONE) {
            startParticleEffect(display, particleConfig);
        }

        return displayUuid;
    }

    /**
     * 移除展示实体（必须在区域线程执行）
     * @param binding 方块绑定
     */
    public void removeDisplay(GachaBlockBinding binding) {
        UUID displayUuid = binding.getDisplayEntityUuid();

        // 从内存移除
        Map<BlockPos, UUID> worldDisplays = displayEntities.get(binding.getWorldUuid());
        if (worldDisplays != null) {
            worldDisplays.values().remove(binding.getDisplayEntityUuid());
            if (worldDisplays.isEmpty()) {
                displayEntities.remove(binding.getWorldUuid());
            }
        }

        // 从世界移除实体
        if (displayUuid != null) {
            World world = Bukkit.getWorld(binding.getWorldUuid());
            if (world != null) {
                Entity entity = Bukkit.getEntity(displayUuid);
                if (entity != null && entity.getType() == EntityType.ITEM_DISPLAY) {
                    // Folia: 确保在正确的区域线程
                    if (Bukkit.isOwnedByCurrentRegion(entity)) {
                        entity.remove();
                    } else {
                        // 调度到正确的区域线程
                        Location loc = entity.getLocation();
                        plugin.getServer().getRegionScheduler().execute(plugin, loc, entity::remove);
                    }
                }
            }
        }

        // 清除数据库中的UUID
        clearDisplayUuid(binding.getId());
    }

    /**
     * 根据方块位置获取展示实体
     */
    public ItemDisplay getDisplayByBlock(UUID worldUuid, int x, int y, int z) {
        Map<BlockPos, UUID> worldDisplays = displayEntities.get(worldUuid);
        if (worldDisplays == null) {
            return null;
        }

        UUID displayUuid = worldDisplays.get(new BlockPos(x, y, z));
        if (displayUuid == null) {
            return null;
        }

        Entity entity = Bukkit.getEntity(displayUuid);
        if (entity instanceof ItemDisplay display && entity.isValid()) {
            return display;
        }

        return null;
    }

    /**
     * 加载展示实体管理器（服务器启动时调用）
     * 从数据库加载现有的展示实体UUID到内存，以便能正确删除旧实体
     */
    public void loadAllDisplays() {
        plugin.getLogger().info("加载展示实体管理器，从数据库恢复现有实体记录...");

        // 从数据库加载所有绑定（包括display_entity_uuid）
        plugin.getGachaBlockManager().getAllBindings(bindings -> {
            int loadedCount = 0;
            for (GachaBlockBinding binding : bindings) {
                if (binding.getDisplayEntityUuid() != null) {
                    // 将已存在的展示实体UUID加载到内存
                    displayEntities
                        .computeIfAbsent(binding.getWorldUuid(), k -> new ConcurrentHashMap<>())
                        .put(new BlockPos(
                            binding.getPosition().getBlockX(),
                            binding.getPosition().getBlockY(),
                            binding.getPosition().getBlockZ()
                        ), binding.getDisplayEntityUuid());
                    loadedCount++;
                }
            }
            plugin.getLogger().info("已从数据库加载 " + loadedCount + " 个展示实体记录到内存");
            setLoaded();
        });
    }

    /**
     * 重新加载展示实体（reload 时调用）
     * 设置所有绑定的 outdated 标志，由各个实体的检测任务自行重建
     */
    public void reload() {
        // 检查是否启用展示实体
        if (!plugin.getShopConfig().isDisplayEntityEnabled()) {
            plugin.getLogger().info("展示实体已禁用，跳过加载");
            return;
        }

        // 设置所有绑定的 outdated = true，实体检测任务会自动重建
        setAllOutdated();
        plugin.getLogger().info("已设置所有展示实体为过时状态，将由检测任务自动重建");
    }

    /**
     * 设置所有绑定为过时状态
     */
    private void setAllOutdated() {
        plugin.getDatabaseQueue().submit("setAllOutdated", conn -> {
            String sql = "UPDATE gacha_block_bindings SET outdated = TRUE";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int affected = ps.executeUpdate();
                plugin.getLogger().info("已标记 " + affected + " 个绑定为过时状态");
            }
            return null;
        });
    }

    /**
     * 保存展示实体UUID到数据库并清除过时标志
     */
    private void saveDisplayUuidAndClearOutdated(int bindingId, UUID displayUuid) {
        plugin.getDatabaseQueue().submit("saveDisplayUuid", conn -> {
            String sql = "UPDATE gacha_block_bindings SET display_entity_uuid = ?, outdated = FALSE WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, displayUuid.toString());
                ps.setInt(2, bindingId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 清除数据库中的展示实体UUID
     */
    private void clearDisplayUuid(int bindingId) {
        plugin.getDatabaseQueue().submit("clearDisplayUuid", conn -> {
            String sql = "UPDATE gacha_block_bindings SET display_entity_uuid = NULL WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, bindingId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 区块加载时恢复展示实体（必须在区域线程执行）
     */
    public void onChunkLoad(UUID worldUuid, int chunkX, int chunkZ) {
        // 检查是否启用展示实体
        if (!plugin.getShopConfig().isDisplayEntityEnabled()) {
            return;
        }

        World world = Bukkit.getWorld(worldUuid);
        if (world == null) return;

        // 检查管理器是否已加载完成，如果没有则延迟执行
        if (!isLoaded()) {
            plugin.getServer().getRegionScheduler().runDelayed(plugin, world, chunkX << 4, chunkZ << 4, task -> {
                onChunkLoad(worldUuid, chunkX, chunkZ);
            }, 5L);
            return;
        }

        // 获取该世界的绑定
        Map<BlockVector, String> worldBindings = plugin.getGachaBlockManager().getBlockBindingsForWorld(worldUuid);
        if (worldBindings == null) return;

        // 检查该区块内是否有绑定的方块需要创建展示实体
        float heightOffset = plugin.getShopConfig().getDisplayEntityHeightOffset();
        for (Map.Entry<BlockVector, String> entry : worldBindings.entrySet()) {
            BlockVector pos = entry.getKey();
            String machineId = entry.getValue();

            int blockChunkX = pos.getBlockX() >> 4;
            int blockChunkZ = pos.getBlockZ() >> 4;

            if (blockChunkX == chunkX && blockChunkZ == chunkZ) {
                final BlockVector finalPos = pos;
                final String finalMachineId = machineId;
                final float finalHeightOffset = heightOffset;
                final Location location = new Location(
                    world,
                    finalPos.getBlockX() + 0.5,
                    finalPos.getBlockY() + finalHeightOffset,
                    finalPos.getBlockZ() + 0.5
                );

                // 异步获取绑定信息，回调后调度回区域线程执行实体操作
                plugin.getGachaBlockManager().getBinding(worldUuid, finalPos, binding -> {
                    if (binding == null) return;

                    // 调度回区域线程执行实体操作
                    plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
                        // 检查是否已有展示实体，如果有则删除（可能是残留的）
                        BlockPos blockPos = new BlockPos(finalPos.getBlockX(), finalPos.getBlockY(), finalPos.getBlockZ());
                        Map<BlockPos, UUID> worldDisplays = displayEntities.get(worldUuid);
                        if (worldDisplays != null) {
                            UUID oldUuid = worldDisplays.remove(blockPos);
                            if (oldUuid != null) {
                                Entity oldEntity = Bukkit.getEntity(oldUuid);
                                if (oldEntity != null) {
                                    oldEntity.remove();
                                }
                            }
                            if (worldDisplays.isEmpty()) {
                                displayEntities.remove(worldUuid);
                            }
                        }

                        GachaMachine machine = plugin.getGachaManager().getMachine(finalMachineId);
                        if (machine != null) {
                            // 检查该扭蛋机是否启用展示实体
                            DisplayEntityConfig effectiveConfig = getEffectiveConfig(machine);
                            if (effectiveConfig.isEnabled()) {
                                createDisplayInternal(binding, machine, location);
                            }
                        }
                    });
                });
            }
        }
    }

    /**
     * 简单的位置记录类
     */
    public record BlockPos(int x, int y, int z) {}

    /**
     * 启动展示实体的插值动画
     * 使用 Display Entity 自带的插值功能实现流畅动画
     * @param display 展示实体
     * @param bindingId 绑定ID（用于标识任务）
     * @param machineConfig 扭蛋机展示实体配置（可为null）
     */
    private void startInterpolationAnimation(ItemDisplay display, int bindingId, DisplayEntityConfig config) {
        float scale = config.getScale();
        float baseRotationY = (float) Math.toRadians(config.getRotationY());
        float amplitude = config.getFloatAmplitude();
        int period = Math.max(1, config.getAnimationPeriod());
        float rotationPerPeriod = config.getFloatSpeed() * 0.05f * period;

        // 设置初始变换
        display.setTransformation(new Transformation(
            new Vector3f(0, 0, 0),                       // 位移
            new AxisAngle4f(baseRotationY, 0, 1, 0),    // 左旋转：Y轴初始角度
            new Vector3f(scale, scale, scale),          // 缩放
            new AxisAngle4f(0, 0, 0, 1)                 // 右旋转
        ));

        // 设置插值参数，duration = period 让客户端在周期内平滑过渡
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(period);

        // Folia: 使用 EntityScheduler 的 runAtFixedRate
        // 每 period tick 更新一次目标变换，让客户端进行插值
        display.getScheduler().runAtFixedRate(plugin, task -> {
            if (!display.isValid() || display.isDead()) {
                task.cancel();
                return;
            }

            // 从绝对时间计算旋转角度，避免累积四元数乘法导致的浮点漂移
            long time = System.currentTimeMillis() / 50; // 转换为tick
            float totalRotation = baseRotationY + rotationPerPeriod * (time / period);
            Quaternionf newRot = new Quaternionf().rotateY(totalRotation);

            // 计算上下浮动（基于时间）
            double yOffset = Math.sin(time * 0.1) * amplitude;

            // 应用新的变换
            Transformation newTrans = new Transformation(
                new Vector3f(0, (float) yOffset, 0),        // Y轴位移（上下浮动）
                newRot,                                     // 左旋转：基于绝对时间的Y轴旋转
                new Vector3f(scale, scale, scale),          // 保持固定缩放
                new Quaternionf(0, 0, 0, 1)                 // 右旋转保持不变
            );

            // 设置插值参数（每次都要设置，确保客户端知道插值时间）
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(period);
            display.setTransformation(newTrans);

        }, null, 1L, period); // 每 period tick 更新一次
    }

    /**
     * 启动过时检测任务（1秒1次）
     * 当检测到数据库中 outdated = true 时，删除并重建实体
     */
    private void startOutdatedCheckTask(ItemDisplay display, int bindingId, UUID worldUuid, BlockPos blockPos, GachaMachine machine) {
        display.getScheduler().runAtFixedRate(plugin, task -> {
            if (!display.isValid() || display.isDead()) {
                task.cancel();
                return;
            }

            // 提交到数据库队列异步检查过时状态
            plugin.getDatabaseQueue().submit("checkOutdated", conn -> {
                String sql = "SELECT outdated FROM gacha_block_bindings WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, bindingId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        return rs.getBoolean("outdated");
                    }
                }
                return false;
            }, isOutdated -> {
                if (isOutdated) {
                    // 取消检测任务
                    task.cancel();

                    final Location location = display.getLocation();
                    final World world = location.getWorld();
                    if (world == null) return;

                    // 异步获取绑定信息，然后调度回区域线程重建
                    plugin.getGachaBlockManager().getBinding(worldUuid,
                        new BlockVector(blockPos.x, blockPos.y, blockPos.z),
                        binding -> {
                            if (binding == null) return;

                            // 重新获取最新的 machine（reload 后可能已更新）
                            GachaMachine currentMachine = plugin.getGachaManager().getMachine(binding.getMachineId());
                            if (currentMachine == null) {
                                plugin.getLogger().warning("[reload] 无法找到扭蛋机: " + binding.getMachineId());
                                return;
                            }

                            // 调度到区域线程删除旧实体并根据配置决定是否重建
                            plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
                                // 从内存移除旧记录
                                Map<BlockPos, UUID> worldDisplays = displayEntities.get(worldUuid);
                                if (worldDisplays != null) {
                                    worldDisplays.remove(blockPos);
                                }

                                // 删除旧实体
                                display.remove();

                                // 检查新配置是否启用展示实体
                                DisplayEntityConfig newConfig = getEffectiveConfig(currentMachine);
                                if (newConfig.isEnabled()) {
                                    // 创建新实体（使用最新的 machine 配置）
                                    createDisplayInternal(binding, currentMachine, location);
                                    plugin.getLogger().info("[reload] 展示实体已重建: " + binding.getMachineId());
                                } else {
                                    plugin.getLogger().info("[reload] 展示实体已禁用，不重建: " + binding.getMachineId());
                                }
                            });
                        });
                }
            }, error -> {
                plugin.getLogger().warning("检查过时状态失败: " + error.getMessage());
            });
        }, null, 20L, 20L); // 延迟1秒后开始，每1秒(20tick)检测一次
    }

    /**
     * 启动粒子效果任务
     * @param display 展示实体
     * @param particleConfig 粒子效果配置
     */
    private void startParticleEffect(ItemDisplay display, ParticleEffectConfig particleConfig) {
        // 创建粒子配置的副本，每个实体有自己的状态
        ParticleEffectConfig config = new ParticleEffectConfig(
            particleConfig.getType(),
            particleConfig.getDensity(),
            particleConfig.getRadius(),
            particleConfig.getSpeed(),
            particleConfig.getCustomColor()
        );

        display.getScheduler().runAtFixedRate(plugin, task -> {
            if (!display.isValid() || display.isDead()) {
                task.cancel();
                return;
            }

            // 每tick更新粒子效果
            boolean shouldContinue = config.tick(display);
            if (!shouldContinue) {
                task.cancel();
            }
        }, null, 1L, 1L); // 每tick更新一次
    }
}
