package dev.user.shop.command;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gui.ShopCategoryGUI;
import dev.user.shop.gui.ShopItemsGUI;
import dev.user.shop.shop.ShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final FoliaShopPlugin plugin;

    public ShopCommand(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getShopConfig().getComponent("player-only"));
            return true;
        }

        if (!plugin.getShopConfig().isShopEnabled()) {
            player.sendMessage(plugin.getShopConfig().getComponent("feature-disabled"));
            return true;
        }

        if (!player.hasPermission("foliashop.shop.use")) {
            player.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
            return true;
        }

        if (args.length > 0) {
            String categoryPath = args[0].toLowerCase();

            if (categoryPath.contains(":")) {
                // 子分类路径: "parent:child"
                String[] parts = categoryPath.split(":", 2);
                String parentId = parts[0];
                String subId = parts[1];

                ShopManager.ShopCategory parentCat = plugin.getShopManager().getCategory(parentId);
                if (parentCat == null) {
                    player.sendMessage(Component.text("分类不存在: " + parentId).color(NamedTextColor.RED));
                    return true;
                }

                ShopManager.SubCategory subCat = plugin.getShopManager().getSubcategory(parentId, subId);
                if (subCat == null) {
                    player.sendMessage(Component.text("子分类不存在: " + categoryPath).color(NamedTextColor.RED));
                    return true;
                }

                new ShopItemsGUI(plugin, player, parentCat, subCat).open();
            } else {
                // 父分类路径 - 直接打开 ShopItemsGUI（会显示子分类+商品）
                var category = plugin.getShopManager().getCategory(categoryPath);
                if (category == null) {
                    player.sendMessage(Component.text("分类不存在: " + categoryPath).color(NamedTextColor.RED));
                    return true;
                }

                new ShopItemsGUI(plugin, player, category).open();
            }
        } else {
            new ShopCategoryGUI(plugin, player).open();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (var category : plugin.getShopManager().getAllCategories()) {
                // 添加父分类ID
                completions.add(category.getId());
                // 添加子分类路径
                if (category.hasSubcategories()) {
                    for (var sub : category.getSubcategories().values()) {
                        completions.add(category.getId() + ":" + sub.getId());
                    }
                }
            }
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .toList();
        }

        return completions;
    }
}
