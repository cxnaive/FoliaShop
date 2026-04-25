package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MainMenuGUI extends AbstractGUI {

    public MainMenuGUI(FoliaShopPlugin plugin, Player player) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("main-menu"), 27);
    }

    @Override
    protected void initialize() {
        fillBorder();

        // 商店按钮（需要功能启用且有权限）
        if (plugin.getShopConfig().isShopEnabled() && player.hasPermission("foliashop.shop.use")) {
            ItemStack shopBtn = createDecorItem("shop", Material.EMERALD);
            ItemUtil.setDisplayName(shopBtn, "§a§l系统商店");
            ItemUtil.setLore(shopBtn, java.util.Arrays.asList(
                "§7点击打开系统商店",
                "",
                "§e购买和出售各种物品"
            ));
            setItem(11, shopBtn, p -> {
                p.closeInventory();
                new ShopCategoryGUI(plugin, p).open();
            });
        }

        // 扭蛋按钮（需要功能启用且有权限）
        if (plugin.getShopConfig().isGachaEnabled() && player.hasPermission("foliashop.gacha.use")) {
            ItemStack gachaBtn = createDecorItem("gacha", Material.NETHER_STAR);
            ItemUtil.setDisplayName(gachaBtn, "§6§l扭蛋中心");
            ItemUtil.setLore(gachaBtn, java.util.Arrays.asList(
                "§7点击打开扭蛋中心",
                "",
                "§e试试你的手气！"
            ));
            setItem(15, gachaBtn, p -> {
                p.closeInventory();
                new GachaMainGUI(plugin, p).open();
            });
        }

        // 交易记录按钮
        ItemStack historyBtn = createDecorItem("history", Material.BOOK);
        ItemUtil.setDisplayName(historyBtn, "§b§l交易记录");
        ItemUtil.setLore(historyBtn, java.util.Arrays.asList(
            "§7查看最近的交易记录",
            "",
            "§e点击查询"
        ));
        setItem(13, historyBtn, p -> {
            p.closeInventory();
            new TransactionHistoryGUI(plugin, p, this).open();
        });

        // 点券兑换按钮（仅当兑换功能启用且 PlayerPoints 可用时显示）
        if (plugin.getShopConfig().isExchangeEnabled() && plugin.getPlayerPointsManager().isEnabled()) {
            ItemStack exchangeBtn = createDecorItem("exchange", Material.SUNFLOWER);
            ItemUtil.setDisplayName(exchangeBtn, "§e§l点券兑换");
            List<String> lore = new ArrayList<>();
            lore.add("§7将点券兑换为" + plugin.getShopConfig().getCurrencyName());
            lore.add("§7汇率: §e1 §7点券 = §a" + String.format("%.0f", plugin.getShopConfig().getExchangeRate()) + " " + plugin.getShopConfig().getCurrencyName());

            // 异步加载点券余额
            final ItemStack btn = exchangeBtn;
            plugin.getPlayerPointsManager().getPointsAsync(player, points -> {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
                    lore.add("§7你的点券: §e" + points);
                    lore.add("");
                    lore.add("§a点击进行兑换");
                    ItemUtil.setLore(btn, new ArrayList<>(lore));
                    inventory.setItem(21, btn);
                });
            });
            ItemUtil.setLore(exchangeBtn, lore);
            setItem(21, exchangeBtn, p -> {
                p.closeInventory();
                plugin.getExchangeSessionManager().startSession(p);
                p.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━");
                p.sendMessage("§6§l点券兑换");
                p.sendMessage("§7请输入要兑换的§e点券数量§7（输入'取消'取消）");
                p.sendMessage("§7当前汇率: §e1 §7点券 = §a" + String.format("%.0f", plugin.getShopConfig().getExchangeRate()) + " " + plugin.getShopConfig().getCurrencyName());
                p.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━");
            });
        }

        // 全球商店按钮（仅当功能启用且有权限时显示）
        if (plugin.getShopConfig().isGlobalShopEnabled() && player.hasPermission("foliashop.globalshop.use")) {
            ItemStack globalShopBtn = createDecorItem("globalshop", Material.ENDER_CHEST);
            ItemUtil.setDisplayName(globalShopBtn, "§d§l全球商店");
            ItemUtil.setLore(globalShopBtn, java.util.Arrays.asList(
                "§7玩家间自由交易市场",
                "",
                "§e浏览/上架/购买物品"
            ));
            setItem(20, globalShopBtn, p -> {
                p.closeInventory();
                new GlobalShopBrowseGUI(plugin, p).open();
            });
        }

        // 关闭按钮
        addCloseButton(22);
    }
}
