package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.GachaManager;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GachaHistoryGUI extends AbstractGUI {

    private final GachaMachineGUI previousGUI;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public GachaHistoryGUI(FoliaShopPlugin plugin, Player player, GachaMachineGUI previousGUI) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("gacha-history"), 36);
        this.previousGUI = previousGUI;
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // 标题
        ItemStack titleItem = new ItemStack(Material.CLOCK);
        ItemUtil.setDisplayName(titleItem, "§e§l最近20次抽奖记录");
        ItemUtil.setLore(titleItem, List.of(
            "§7显示你最近的抽奖历史",
            "§7按时间倒序排列"
        ));
        setItem(4, titleItem);

        // 加载记录
        loadRecords();

        // 返回按钮
        addBackButton(31, () -> {
            if (previousGUI != null) {
                previousGUI.open();
            } else {
                player.closeInventory();
            }
        });
    }

    private void loadRecords() {
        plugin.getGachaManager().getPlayerGachaRecords(player.getUniqueId(), records -> {
            // 回调可能在异步线程，需要切换到主线程更新GUI
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
                    return;
                }

                if (records.isEmpty()) {
                    // 显示无记录提示
                    ItemStack emptyItem = new ItemStack(Material.BARRIER);
                    ItemUtil.setDisplayName(emptyItem, "§c暂无抽奖记录");
                    ItemUtil.setLore(emptyItem, List.of("§7你还没有进行过抽奖"));
                    setItem(13, emptyItem);
                    return;
                }

                // 显示记录 (从slot 10开始，每行7个，最多20个)
                int[] slots = {10, 11, 12, 13, 14, 15, 16,
                               19, 20, 21, 22, 23, 24, 25,
                               28, 29, 30};
                int slotIndex = 0;

                for (GachaManager.GachaRecord record : records) {
                    if (slotIndex >= slots.length) break;

                    ItemStack displayItem = createRecordItem(record);
                    setItem(slots[slotIndex], displayItem);
                    slotIndex++;
                }
            });
        });
    }

    private ItemStack createRecordItem(GachaManager.GachaRecord record) {
        // 根据物品key尝试获取物品
        ItemStack item = ItemUtil.createItemFromKey(plugin, record.getItemKey());
        if (item == null) {
            item = new ItemStack(Material.CHEST);
        }

        String timeStr = DATE_FORMAT.format(Instant.ofEpochMilli(record.getTimestamp()));
        String machineNameRaw = getMachineDisplayName(record.getMachineId());
        // 解析 MiniMessage 格式（如 <gold>高级扭蛋机）
        String machineName = MessageUtil.convertMiniMessageToLegacy(machineNameRaw);

        // 保持物品原始名称，只在lore中添加信息
        List<String> lore = new ArrayList<>();
        lore.add("§7数量: §f" + record.getAmount() + " 个");
        lore.add("§7时间: §f" + timeStr);
        lore.add("§7扭蛋机: §f" + machineName);
        if (record.isPick()) {
            lore.add("§7来源: §d累抽自选");
        }
        lore.add("§7花费: §e" + plugin.getShopConfig().formatCurrency(record.getCost()));
        lore.add("§7物品ID: §f" + record.getItemKey());
        ItemUtil.setLore(item, lore);

        return item;
    }

    private String getMachineDisplayName(String machineId) {
        var machine = plugin.getGachaManager().getMachine(machineId);
        return machine != null ? machine.getName() : machineId;
    }
}
