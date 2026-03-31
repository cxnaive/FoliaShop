package dev.user.shop.listener;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gui.GachaMachineGUI;
import dev.user.shop.gui.GachaPreviewGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 方块交互监听器 - 处理扭蛋机绑定方块的点击
 */
public class BlockInteractListener implements Listener {

    private final FoliaShopPlugin plugin;

    public BlockInteractListener(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只处理左键和右键点击方块
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player player = event.getPlayer();

        // 检查该方块是否绑定了扭蛋机
        String machineId = plugin.getGachaBlockManager().getMachineByBlock(block);
        if (machineId == null) {
            return; // 未绑定，不处理
        }

        // 检查扭蛋机是否存在
        var machine = plugin.getGachaManager().getMachine(machineId);
        if (machine == null) {
            player.sendMessage(Component.text("错误：该方块绑定的扭蛋机 '" + machineId + "' 不存在！").color(NamedTextColor.RED));
            return;
        }

        // 取消原事件，防止破坏方块或打开容器（即使扭蛋禁用也要保护方块）
        event.setCancelled(true);

        // 检查扭蛋功能是否启用
        if (!plugin.getShopConfig().isGachaEnabled()) {
            player.sendMessage(plugin.getShopConfig().getComponent("feature-disabled"));
            return;
        }

        // 检查该扭蛋机是否启用
        if (!machine.isEnabled()) {
            player.sendMessage(Component.text("该扭蛋机已禁用").color(NamedTextColor.RED));
            return;
        }

        // 检查权限
        if (!player.hasPermission("foliashop.gacha.use")) {
            player.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
            return;
        }

        // 左键：预览奖品
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            new GachaPreviewGUI(plugin, player, machine).open();
        }
        // 右键：打开抽奖界面
        else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            new GachaMachineGUI(plugin, player, machine).open();
        }
    }
}
