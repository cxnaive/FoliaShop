package dev.user.shop.command;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.GachaBlockBinding;
import dev.user.shop.gui.MainMenuGUI;
import dev.user.shop.gui.ShopAdminGUI;
import dev.user.shop.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FoliaShopCommand implements CommandExecutor, TabCompleter {

    private final FoliaShopPlugin plugin;

    public FoliaShopCommand(FoliaShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
                return true;
            }

            if (!player.hasPermission("foliashop.use")) {
                player.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                return true;
            }

            new MainMenuGUI(plugin, player).open();
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> {
                if (!sender.hasPermission("foliashop.admin")) {
                    sender.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(plugin.getShopConfig().getComponent("config-reloaded"));
            }
            case "shop" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
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
                new dev.user.shop.gui.ShopCategoryGUI(plugin, player).open();
            }
            case "sell" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
                    return true;
                }
                if (!plugin.getShopConfig().isAllowSell() || !plugin.getShopConfig().isSellSystemEnabled()) {
                    player.sendMessage("§c系统回收功能未启用！");
                    return true;
                }
                if (!player.hasPermission("foliashop.shop.sell")) {
                    player.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                new dev.user.shop.gui.SellGUI(plugin, player).open();
            }
            case "gacha" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
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
                new dev.user.shop.gui.GachaMainGUI(plugin, player).open();
            }
            case "globalshop" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
                    return true;
                }
                if (!plugin.getShopConfig().isGlobalShopEnabled()) {
                    player.sendMessage(plugin.getShopConfig().getComponent("feature-disabled"));
                    return true;
                }
                if (!player.hasPermission("foliashop.globalshop.use")) {
                    player.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                if (args.length >= 2) {
                    String subArg = args[1].toLowerCase();
                    switch (subArg) {
                        case "sell", "submit" -> new dev.user.shop.gui.GlobalShopSubmitGUI(plugin, player).open();
                        case "manage", "mylistings" -> new dev.user.shop.gui.GlobalShopManageGUI(plugin, player).open();
                        case "returns", "claim" -> new dev.user.shop.gui.GlobalShopReturnsGUI(plugin, player).open();
                        default -> new dev.user.shop.gui.GlobalShopBrowseGUI(plugin, player).open();
                    }
                } else {
                    new dev.user.shop.gui.GlobalShopBrowseGUI(plugin, player).open();
                }
            }
            case "pick" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
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
                if (args.length < 2) {
                    player.sendMessage("§c用法: /foliashop pick <扭蛋机ID>");
                    return true;
                }
                handleGachaPick(player, args[1]);
            }
            case "collect" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
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
                if (!plugin.getGachaManager().hasCollections()) {
                    player.sendMessage(plugin.getShopConfig().getComponent("collect-empty"));
                    return true;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("claim")) {
                    handleCollectClaim(player, args[2]);
                } else {
                    new dev.user.shop.gui.GachaCollectionGUI(plugin, player).open();
                }
            }
            case "admin" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
                    return true;
                }
                if (!player.hasPermission("foliashop.admin")) {
                    player.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                new ShopAdminGUI(plugin, player).open();
            }
            case "reset" -> {
                if (!sender.hasPermission("foliashop.admin")) {
                    sender.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                plugin.getShopManager().reloadFromConfig();
                sender.sendMessage("§a已清空数据库并从配置文件重新加载商店商品！");
            }
            case "clean" -> {
                if (!sender.hasPermission("foliashop.admin")) {
                    sender.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                handleCleanCommand(sender, args);
            }
            case "bindblock" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
                    return true;
                }
                if (!player.hasPermission("foliashop.admin")) {
                    player.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                handleBindBlockCommand(player, args);
            }
            case "unbindblock" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("此命令只能由玩家执行。").color(NamedTextColor.RED));
                    return true;
                }
                if (!player.hasPermission("foliashop.admin")) {
                    player.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                handleUnbindBlockCommand(player);
            }
            case "listblocks" -> {
                if (!sender.hasPermission("foliashop.admin")) {
                    sender.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                handleListBlocksCommand(sender, args);
            }
            case "exportshop" -> {
                if (!sender.hasPermission("foliashop.admin")) {
                    sender.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                handleExportShopCommand(sender);
            }
            case "stats" -> {
                if (!sender.hasPermission("foliashop.admin")) {
                    sender.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                handleStatsCommand(sender, args);
            }
            case "export" -> {
                if (!sender.hasPermission("foliashop.admin")) {
                    sender.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                handleExportCommand(sender, args);
            }
            case "import" -> {
                if (!sender.hasPermission("foliashop.admin")) {
                    sender.sendMessage(plugin.getShopConfig().getComponent("no-permission"));
                    return true;
                }
                handleImportCommand(sender, args);
            }
            case "help" -> sendHelp(sender);
            default -> sender.sendMessage("§c未知命令。使用 /foliashop help 查看帮助。");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("help");
            if (sender.hasPermission("foliashop.use")) {
                completions.add("shop");
                completions.add("sell");
                completions.add("gacha");
                completions.add("collect");
                completions.add("pick");
                completions.add("globalshop");
            }
            if (sender.hasPermission("foliashop.admin")) {
                completions.add("reload");
                completions.add("admin");
                completions.add("reset");
                completions.add("clean");
                completions.add("bindblock");
                completions.add("unbindblock");
                completions.add("listblocks");
                completions.add("exportshop");
                completions.add("export");
                completions.add("import");
                completions.add("stats");
            }
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }

        // globalshop 命令的参数补全
        if (args.length == 2 && args[0].equalsIgnoreCase("globalshop") && sender.hasPermission("foliashop.globalshop.use")) {
            return List.of("sell", "manage", "returns").stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
        }

        // collect 命令的参数补全
        if (args[0].equalsIgnoreCase("collect") && sender.hasPermission("foliashop.gacha.use")) {
            if (args.length == 2) {
                return List.of("claim").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("claim")) {
                return plugin.getGachaManager().getAllCollections().stream()
                    .map(c -> c.getId())
                    .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
            }
        }

        // pick 命令的参数补全（扭蛋机ID）
        if (args[0].equalsIgnoreCase("pick") && sender.hasPermission("foliashop.gacha.use")) {
            if (args.length == 2) {
                return plugin.getGachaManager().getEnabledMachines().stream()
                    .filter(m -> m.isMilepostEnabled())
                    .map(m -> m.getId())
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }

        // clean 命令的参数补全
        if (args.length == 2 && args[0].equalsIgnoreCase("clean")) {
            if (sender.hasPermission("foliashop.admin")) {
                return List.of("5", "10", "30").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
            }
        }

        // bindblock 命令的参数补全（扭蛋机ID）
        if (args.length == 2 && args[0].equalsIgnoreCase("bindblock")) {
            if (sender.hasPermission("foliashop.admin")) {
                return plugin.getGachaManager().getAllMachines().stream()
                    .map(machine -> machine.getId())
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }

        // listblocks 命令的参数补全（扭蛋机ID，可选）
        if (args.length == 2 && args[0].equalsIgnoreCase("listblocks")) {
            if (sender.hasPermission("foliashop.admin")) {
                return plugin.getGachaManager().getAllMachines().stream()
                    .map(machine -> machine.getId())
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }

        // export 命令的参数补全
        if (args.length == 2 && args[0].equalsIgnoreCase("export")) {
            if (sender.hasPermission("foliashop.admin")) {
                return List.of("full", "config", "state").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }

        // import 命令的参数补全
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            if (sender.hasPermission("foliashop.admin")) {
                List<String> files = plugin.getBackupManager().listBackups().stream()
                    .map(f -> f.getName().replace(".sql", ""))
                    .limit(10)
                    .toList();
                return files.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("import")) {
            if (sender.hasPermission("foliashop.admin")) {
                return List.of("replace", "merge").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
            }
        }

        // stats 命令的参数补全
        if (args[0].equalsIgnoreCase("stats") && sender.hasPermission("foliashop.admin")) {
            if (args.length == 2) {
                // 第二个参数：玩家名（可选，输入 - 表示所有玩家）或扭蛋机ID
                List<String> suggestions = new ArrayList<>();
                suggestions.add("-"); // 表示所有玩家
                // 添加在线玩家名
                suggestions.addAll(plugin.getServer().getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .toList());
                return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
            if (args.length == 3) {
                // 第三个参数：扭蛋机ID（如果第二个是 - 或玩家名）
                return plugin.getGachaManager().getAllMachines().stream()
                    .map(m -> m.getId())
                    .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
            }
            if (args.length == 4) {
                // 第四个参数：奖品ID
                String machineId = args[2];
                var machine = plugin.getGachaManager().getMachine(machineId);
                if (machine != null) {
                    return machine.getRewards().stream()
                        .map(r -> r.getId())
                        .filter(id -> id.toLowerCase().startsWith(args[3].toLowerCase()))
                        .toList();
                }
            }
        }

        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== FoliaShop 帮助 ==========");
        sender.sendMessage("§e/foliashop §7- 打开主菜单");
        sender.sendMessage("§e/foliashop shop §7- 打开商店");
        sender.sendMessage("§e/foliashop sell §7- 打开出售界面");
        sender.sendMessage("§e/foliashop gacha §7- 打开扭蛋");
        sender.sendMessage("§e/foliashop globalshop §7- 打开全球商店");
        sender.sendMessage("§e/foliashop globalshop sell §7- 上架物品");
        sender.sendMessage("§e/foliashop globalshop manage §7- 我的上架");
        sender.sendMessage("§e/foliashop globalshop returns §7- 待领取物品");
        sender.sendMessage("§e/foliashop collect §7- 打开收集兑换");
        sender.sendMessage("§e/foliashop collect claim <id> §7- 领取收集奖励");
        if (sender.hasPermission("foliashop.admin")) {
            sender.sendMessage("§e/foliashop reload §7- 重载配置");
            sender.sendMessage("§e/foliashop admin §7- 打开商店管理界面");
            sender.sendMessage("§e/foliashop reset §7- 清空数据库并从配置重新加载");
            sender.sendMessage("§e/foliashop clean <天数> §7- 清理旧数据 (5/10/30)");
        }
        sender.sendMessage("§e/shop §7- 打开商店");
        sender.sendMessage("§e/gacha §7- 打开扭蛋");
        if (sender.hasPermission("foliashop.admin")) {
            sender.sendMessage("§e/foliashop bindblock <machineId> §7- 绑定看向的方块到扭蛋机");
            sender.sendMessage("§e/foliashop unbindblock §7- 解绑看向的方块");
            sender.sendMessage("§e/foliashop listblocks [machineId] §7- 列出方块绑定");
            sender.sendMessage("§e/foliashop exportshop §7- 导出商店数据到 backup_shop.yml");
            sender.sendMessage("§e/foliashop export [full|config|state] §7- 导出数据库备份");
            sender.sendMessage("§e/foliashop import <文件名> [replace|merge] §7- 从备份恢复数据库");
            sender.sendMessage("§e/foliashop stats [-|<玩家名>] <machineId> <rewardId> §7- 查询奖品统计");
        }
        sender.sendMessage("§6==================================");
    }

    private void handleCollectClaim(Player player, String collectionId) {
        var collSet = plugin.getGachaManager().getCollection(collectionId);
        if (collSet == null) {
            player.sendMessage(plugin.getShopConfig().getComponent("collect-not-found",
                MessageUtil.Placeholder.text("id", collectionId)));
            return;
        }

        player.sendMessage(plugin.getShopConfig().getComponent("collect-checking"));

        plugin.getGachaManager().getCollectionProgress(player.getUniqueId(), collSet, progress -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline()) return;

                if (!collSet.isComplete(progress)) {
                    int collected = collSet.getCollectedCount(progress);
                    int required = collSet.getRequiredCount();
                    player.sendMessage(plugin.getShopConfig().getComponent("collect-incomplete",
                        MessageUtil.Placeholder.text("collected", String.valueOf(collected)),
                        MessageUtil.Placeholder.text("required", String.valueOf(required))));
                    return;
                }

                plugin.getGachaManager().claimCollection(player.getUniqueId(), collectionId, success -> {
                    plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                        if (!player.isOnline()) return;

                        if (success) {
                            player.sendMessage(plugin.getShopConfig().getComponent("collect-claim-success",
                                MessageUtil.Placeholder.text("name", collSet.getName())));
                            plugin.getGachaManager().giveCollectionReward(player, collSet);
                        } else {
                            player.sendMessage(plugin.getShopConfig().getComponent("collect-claim-fail"));
                        }
                    });
                });
            });
        });
    }

    private void handleCleanCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /foliashop clean <天数>");
            sender.sendMessage("§7可用天数: 5, 10, 30 (清理多少天以前的数据)");
            return;
        }

        int days;
        try {
            days = Integer.parseInt(args[1]);
            if (days != 5 && days != 10 && days != 30) {
                sender.sendMessage("§c错误: 天数必须是 5, 10 或 30");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c错误: 天数必须是数字 (5, 10, 30)");
            return;
        }

        sender.sendMessage("§e正在清理 " + days + " 天以前的数据，请稍候...");

        final int finalDays = days;

        // 清理商店相关数据
        plugin.getShopManager().cleanupOldData(days, result -> {
            int deletedTransactions = result[0];
            int deletedDailyLimits = result[1];
            int deletedCategorySellLimits = result.length > 2 ? result[2] : 0;

            // 清理扭蛋记录
            plugin.getGachaManager().cleanupOldRecords(finalDays, deletedGacha -> {
                sender.sendMessage("§a===== 数据清理完成 =====");
                sender.sendMessage("§7清理范围: §e" + finalDays + " 天以前的数据");
                sender.sendMessage("§7交易记录: §e" + deletedTransactions + " §7条已删除");
                sender.sendMessage("§7过期购买计数: §e" + deletedDailyLimits + " §7条已删除");
                if (deletedCategorySellLimits > 0) {
                    sender.sendMessage("§7过期出售限额: §e" + deletedCategorySellLimits + " §7条已删除");
                }
                sender.sendMessage("§7抽奖记录: §e" + deletedGacha + " §7条已删除");
                sender.sendMessage("§a========================");
            });
        });
    }

    private void handleBindBlockCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /foliashop bindblock <machineId>");
            player.sendMessage("§7可用扭蛋机: §e" + String.join(", ",
                plugin.getGachaManager().getAllMachines().stream()
                    .map(m -> m.getId())
                    .toList()));
            return;
        }

        String machineId = args[1].toLowerCase();

        // 检查扭蛋机是否存在
        if (plugin.getGachaManager().getMachine(machineId) == null) {
            player.sendMessage("§c错误：扭蛋机 '" + machineId + "' 不存在！");
            return;
        }

        // 获取玩家看向的方块（10格内）
        Block targetBlock = getTargetBlock(player, 10);
        if (targetBlock == null) {
            player.sendMessage("§c请看向10格内的方块！");
            return;
        }

        // 检查是否已绑定
        if (plugin.getGachaBlockManager().isBlockBound(targetBlock)) {
            String existingMachine = plugin.getGachaBlockManager().getMachineByBlock(targetBlock);
            player.sendMessage("§c该方块已绑定到扭蛋机 '" + existingMachine + "'，请先解绑！");
            return;
        }

        // 执行绑定
        player.sendMessage("§e正在绑定...");
        plugin.getGachaBlockManager().bindBlock(targetBlock, machineId, player, result -> {
            if (result.success()) {
                player.sendMessage("§a✔ 成功将方块绑定到扭蛋机 '" + machineId + "'！");
                player.sendMessage("§7左键点击方块：预览奖品");
                player.sendMessage("§7右键点击方块：打开抽奖界面");
            } else {
                player.sendMessage("§c✘ 绑定失败: " + result.message());
            }
        });
    }

    private void handleUnbindBlockCommand(Player player) {
        // 获取玩家看向的方块（10格内）
        Block targetBlock = getTargetBlock(player, 10);
        if (targetBlock == null) {
            player.sendMessage("§c请看向10格内的方块！");
            return;
        }

        // 检查是否已绑定
        String existingMachine = plugin.getGachaBlockManager().getMachineByBlock(targetBlock);
        if (existingMachine == null) {
            player.sendMessage("§c该方块未绑定任何扭蛋机！");
            return;
        }

        // 执行解绑
        player.sendMessage("§e正在解绑...");
        plugin.getGachaBlockManager().unbindBlock(targetBlock, result -> {
            if (result.success()) {
                player.sendMessage("§a✔ 成功解绑方块！原绑定扭蛋机: '" + result.message() + "'");
            } else {
                player.sendMessage("§c✘ 解绑失败: " + result.message());
            }
        });
    }

    private void handleListBlocksCommand(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String machineId = args[1].toLowerCase();
            if (plugin.getGachaManager().getMachine(machineId) == null) {
                sender.sendMessage("§c错误：扭蛋机 '" + machineId + "' 不存在！");
                return;
            }
            String title = "扭蛋机 '" + machineId + "' 的方块绑定列表";
            plugin.getGachaBlockManager().getBindingsByMachine(machineId, bindings -> {
                sendBindingsList(sender, title, bindings);
            });
        } else {
            String title = "所有扭蛋机方块绑定列表";
            plugin.getGachaBlockManager().getAllBindings(bindings -> {
                sendBindingsList(sender, title, bindings);
            });
        }
    }

    private void sendBindingsList(CommandSender sender, String title, List<GachaBlockBinding> bindings) {
        sender.sendMessage("§6========== " + title + " ==========");
        sender.sendMessage("§7共 " + bindings.size() + " 个绑定");

        if (bindings.isEmpty()) {
            sender.sendMessage("§7暂无绑定");
        } else {
            int index = 1;
            for (GachaBlockBinding binding : bindings) {
                String worldName = plugin.getGachaBlockManager().getWorldName(binding.getWorldUuid());
                sender.sendMessage("§e" + index + ". §7扭蛋机: §f" + binding.getMachineId() +
                    " §7世界: §f" + worldName +
                    " §7坐标: §f" + binding.getPosition().getBlockX() + "," +
                    binding.getPosition().getBlockY() + "," +
                    binding.getPosition().getBlockZ());
                index++;
                if (index > 20) {
                    sender.sendMessage("§7... 还有 " + (bindings.size() - 20) + " 个绑定未显示");
                    break;
                }
            }
        }
        sender.sendMessage("§6==================================");
    }

    private void handleExportShopCommand(CommandSender sender) {
        sender.sendMessage("§e正在导出商店数据到 backup_shop.yml，请稍候...");

        plugin.getShopManager().exportToYaml(count -> {
            if (count > 0) {
                sender.sendMessage("§a✔ 成功导出 " + count + " 个商品到 backup_shop.yml");
                sender.sendMessage("§7文件位置: §e" + plugin.getDataFolder().getAbsolutePath() + "/backup_shop.yml");
            } else {
                sender.sendMessage("§c✘ 导出失败，请查看控制台日志");
            }
        });
    }

    private void handleStatsCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法: /foliashop stats [-|<玩家名>] <machineId> <rewardId>");
            sender.sendMessage("§7- §7表示查询所有玩家");
            sender.sendMessage("§7示例: /foliashop stats - normal diamond");
            sender.sendMessage("§7示例: /foliashop stats Steve premium legendary_sword");
            return;
        }

        String playerArg = args[1];
        String machineId = args[2];
        String rewardId = args[3];

        // 检查扭蛋机是否存在
        var machine = plugin.getGachaManager().getMachine(machineId);
        if (machine == null) {
            sender.sendMessage("§c错误：扭蛋机 '" + machineId + "' 不存在！");
            sender.sendMessage("§7可用扭蛋机: §e" + String.join(", ",
                plugin.getGachaManager().getAllMachines().stream()
                    .map(m -> m.getId())
                    .toList()));
            return;
        }

        // 检查奖品是否存在
        boolean rewardExists = machine.getRewards().stream()
            .anyMatch(r -> r.getId().equals(rewardId));
        if (!rewardExists) {
            sender.sendMessage("§c错误：奖品 '" + rewardId + "' 不存在于扭蛋机 '" + machineId + "'！");
            sender.sendMessage("§7可用奖品: §e" + String.join(", ",
                machine.getRewards().stream()
                    .map(r -> r.getId())
                    .toList()));
            return;
        }

        // 解析玩家
        UUID playerUuid = null;
        String playerName = null;
        if (!playerArg.equals("-")) {
            // 查找玩家
            Player targetPlayer = plugin.getServer().getPlayerExact(playerArg);
            if (targetPlayer == null) {
                // 尝试从离线玩家查找
                sender.sendMessage("§e正在查询离线玩家数据...");
                // 使用玩家名作为显示名，UUID设为null表示查询不到具体玩家
                playerName = playerArg;
            } else {
                playerUuid = targetPlayer.getUniqueId();
                playerName = targetPlayer.getName();
            }
        }

        String finalPlayerName = playerName;
        UUID finalPlayerUuid = playerUuid;

        sender.sendMessage("§e正在查询统计信息，请稍候...");

        // 执行查询
        plugin.getGachaManager().getRewardStats(playerUuid, machineId, rewardId, stats -> {
            sender.sendMessage("§6========== 抽奖统计 ==========");
            if (finalPlayerName == null) {
                sender.sendMessage("§7玩家: §e所有玩家");
            } else {
                sender.sendMessage("§7玩家: §e" + finalPlayerName +
                    (finalPlayerUuid == null ? " §7(离线，可能无数据)" : ""));
            }
            sender.sendMessage("§7扭蛋机: §e" + machine.getName() + " §7(" + machineId + ")");

            // 获取奖品名称
            String rewardName = machine.getRewards().stream()
                .filter(r -> r.getId().equals(rewardId))
                .findFirst()
                .map(r -> r.getDisplayName() != null ? r.getDisplayName() : r.getItemKey())
                .orElse(rewardId);
            sender.sendMessage("§7奖品: §e" + rewardName + " §7(" + rewardId + ")");
            sender.sendMessage(stats.getFormattedStats());
            sender.sendMessage("§6==============================");
        });
    }

    /**
     * 获取玩家看向的方块
     * @param player 玩家
     * @param maxDistance 最大距离
     * @return 目标方块，未找到返回 null
     */
    private Block getTargetBlock(Player player, int maxDistance) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            maxDistance,
            FluidCollisionMode.NEVER,
            true
        );
        return result != null ? result.getHitBlock() : null;
    }

    private void handleExportCommand(CommandSender sender, String[] args) {
        String type = args.length >= 2 ? args[1].toLowerCase() : "config";

        String[] tables;
        switch (type) {
            case "full" -> {
                tables = new String[]{
                    "shop_items", "gacha_block_bindings",
                    "player_item_limits", "gacha_pity", "daily_limits",
                    "transactions", "gacha_records"
                };
            }
            case "config" -> {
                tables = new String[]{"shop_items", "gacha_block_bindings"};
            }
            case "state" -> {
                tables = new String[]{
                    "shop_items", "gacha_block_bindings",
                    "player_item_limits", "gacha_pity", "daily_limits"
                };
            }
            default -> {
                sender.sendMessage("§c用法: /foliashop export [full|config|state]");
                sender.sendMessage("§7full - 导出所有数据（包含日志）");
                sender.sendMessage("§7config - 只导出配置（商品、方块绑定）");
                sender.sendMessage("§7state - 导出配置和玩家状态（不含日志）");
                return;
            }
        }

        sender.sendMessage("§e正在导出数据库备份，请稍候...");

        plugin.getBackupManager().exportToSql(tables, file -> {
            if (file != null) {
                sender.sendMessage("§a✔ 备份成功: §e" + file.getName());
                sender.sendMessage("§7位置: " + file.getAbsolutePath());
            } else {
                sender.sendMessage("§c✘ 备份失败，请查看控制台日志");
            }
        });
    }

    private void handleImportCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /foliashop import <文件名> [replace|merge]");
            sender.sendMessage("§7文件名不需要包含 .sql 后缀");
            sender.sendMessage("§7replace - 清空现有数据后导入");
            sender.sendMessage("§7merge - 保留现有数据，跳过冲突");
            sender.sendMessage("§e可用备份:");
            List<File> backups = plugin.getBackupManager().listBackups();
            if (backups.isEmpty()) {
                sender.sendMessage("§7  暂无备份文件");
            } else {
                int index = 1;
                for (File f : backups) {
                    sender.sendMessage("§7  " + index + ". §e" + f.getName());
                    if (index++ >= 5) break;
                }
                if (backups.size() > 5) {
                    sender.sendMessage("§7  ... 还有 " + (backups.size() - 5) + " 个");
                }
            }
            return;
        }

        String fileName = args[1];
        if (!fileName.endsWith(".sql")) {
            fileName += ".sql";
        }

        File backupFile = new File(plugin.getBackupManager().getBackupDir(), fileName);
        if (!backupFile.exists()) {
            sender.sendMessage("§c备份文件不存在: " + fileName);
            return;
        }

        dev.user.shop.database.BackupManager.ImportMode mode = dev.user.shop.database.BackupManager.ImportMode.REPLACE;
        if (args.length >= 3) {
            try {
                mode = dev.user.shop.database.BackupManager.ImportMode.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§c无效的导入模式，使用 replace 或 merge");
                return;
            }
        }

        String finalFileName = fileName;
        if (mode == dev.user.shop.database.BackupManager.ImportMode.REPLACE) {
            sender.sendMessage("§c⚠ 警告: 这将清空现有数据并导入备份！");
            sender.sendMessage("§e正在导入: §7" + finalFileName);
        } else {
            sender.sendMessage("§e正在合并导入: §7" + finalFileName);
        }

        plugin.getBackupManager().importFromSql(backupFile, mode, rows -> {
            if (rows >= 0) {
                sender.sendMessage("§a✔ 导入成功，共导入 §e" + rows + " §a条记录");

                // 重新加载管理器以刷新内存数据
                if (plugin.getShopManager() != null) {
                    plugin.getShopManager().reload();
                }
                if (plugin.getGachaBlockManager() != null) {
                    plugin.getGachaBlockManager().reload();
                }

                sender.sendMessage("§7已重新加载商店和扭蛋数据");
            } else {
                sender.sendMessage("§c✘ 导入失败，请查看控制台日志");
            }
        });
    }

    /**
     * 处理自选命令
     */
    private void handleGachaPick(Player player, String machineId) {
        dev.user.shop.gacha.GachaMachine machine = plugin.getGachaManager().getMachine(machineId);
        if (machine == null) {
            player.sendMessage("§c扭蛋机 '" + machineId + "' 不存在");
            return;
        }
        if (!machine.isEnabled()) {
            player.sendMessage("§c扭蛋机 '" + machineId + "' 未启用");
            return;
        }
        if (!machine.isMilepostEnabled()) {
            player.sendMessage("§c扭蛋机 '" + machineId + "' 未启用累抽自选功能");
            return;
        }

        plugin.getGachaManager().getMilepostProgress(player.getUniqueId(), machineId, info -> {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline()) return;
                if (!info.hasAvailable()) {
                    player.sendMessage("§c自选次数不足，继续抽奖积攒次数吧！");
                    player.sendMessage("§7当前进度: §e" + info.getTotalDraws() + "§7/§e" + info.getInterval() + " §7次 | 可用自选: §e" + info.getAvailablePicks());
                    return;
                }
                new dev.user.shop.gui.GachaPickGUI(plugin, player, machine, info).open();
            });
        });
    }
}
