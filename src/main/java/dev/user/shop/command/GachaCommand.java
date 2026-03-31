package dev.user.shop.command;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.GachaMachine;
import dev.user.shop.gui.GachaMachineGUI;
import dev.user.shop.gui.GachaMainGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GachaCommand implements CommandExecutor, TabCompleter {

    private final FoliaShopPlugin plugin;

    public GachaCommand(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getShopConfig().getComponent("player-only"));
            return true;
        }

        if (!plugin.getShopConfig().isGachaEnabled()) {
            player.sendMessage(plugin.getShopConfig().getComponent("feature-disabled"));
            return true;
        }

        if (!player.hasPermission("foliashop.gacha.use")) {
            player.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
            return true;
        }

        if (args.length > 0) {
            String machineId = args[0].toLowerCase();
            GachaMachine machine = plugin.getGachaManager().getMachine(machineId);
            if (machine != null) {
                if (!machine.isEnabled()) {
                    player.sendMessage(Component.text("该扭蛋机已禁用: " + machineId).color(NamedTextColor.RED));
                } else {
                    new GachaMachineGUI(plugin, player, machine).open();
                }
            } else {
                player.sendMessage(Component.text("扭蛋机不存在: " + machineId).color(NamedTextColor.RED));
                player.sendMessage(Component.text("可用的扭蛋机: " + String.join(", ",
                    plugin.getGachaManager().getEnabledMachines().stream()
                        .map(GachaMachine::getId)
                        .toList())).color(NamedTextColor.YELLOW));
            }
        } else {
            new GachaMainGUI(plugin, player).open();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 返回已启用的扭蛋机ID
            for (GachaMachine machine : plugin.getGachaManager().getEnabledMachines()) {
                completions.add(machine.getId());
            }
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }

        return completions;
    }
}
