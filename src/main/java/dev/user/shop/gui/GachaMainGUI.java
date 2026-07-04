package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.gacha.GachaMachine;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GachaMainGUI extends AbstractGUI {

    /** 机器网格可用槽位（第 2~5 行，每行 7 格，共 28 格），按行优先顺序 */
    private static final int[] GRID_SLOTS = new int[28];
    static {
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                GRID_SLOTS[i++] = row * 9 + col; // 10-16, 19-25, 28-34, 37-43
            }
        }
    }

    public GachaMainGUI(FoliaShopPlugin plugin, Player player) {
        super(plugin, player, plugin.getShopConfig().getGUITitle("gacha"), 54);
    }

    @Override
    protected void initialize() {
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        Collection<GachaMachine> all = plugin.getGachaManager().getEnabledMachines();
        // 分离：手动定位(slot!=0) 与 自动定位(slot==0)；自动的按 id 排序保证稳定
        List<GachaMachine> manual = new ArrayList<>();
        List<GachaMachine> auto = new ArrayList<>();
        for (GachaMachine m : all) {
            if (m.getSlot() == 0) auto.add(m);
            else manual.add(m);
        }
        auto.sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));

        Set<Integer> occupied = new HashSet<>();

        // 第一遍：手动定位的机器放到各自 slot（校验是否落在网格内）
        for (GachaMachine machine : manual) {
            int slot = machine.getSlot();
            if (!isGridSlot(slot)) {
                plugin.getLogger().warning("扭蛋机 " + machine.getId() + " 的 slot " + slot
                    + " 不在网格区域(10-16/19-25/28-34/37-43)，改用自动分配");
                auto.add(machine);
                continue;
            }
            if (occupied.contains(slot)) {
                plugin.getLogger().warning("扭蛋机 " + machine.getId() + " 的 slot " + slot
                    + " 与其他机器冲突，改用自动分配");
                auto.add(machine);
                continue;
            }
            placeMachine(slot, machine, occupied);
        }

        // 第二遍：自动定位的机器按网格顺序补位
        int gridIdx = 0;
        for (GachaMachine machine : auto) {
            while (gridIdx < GRID_SLOTS.length && occupied.contains(GRID_SLOTS[gridIdx])) gridIdx++;
            if (gridIdx >= GRID_SLOTS.length) {
                plugin.getLogger().warning("扭蛋机 " + machine.getId() + " 无法分配位置，界面已满（最多 " + GRID_SLOTS.length + " 台）");
                break;
            }
            placeMachine(GRID_SLOTS[gridIdx], machine, occupied);
            gridIdx++;
        }

        // 收集兑换按钮（有收集配置时显示）
        if (plugin.getGachaManager().hasCollections()) {
            ItemStack collBtn = new ItemStack(Material.ENDER_CHEST);
            ItemUtil.setDisplayName(collBtn, "§d§l收集兑换");
            ItemUtil.setLore(collBtn, List.of(
                "§7收集指定奖品组合",
                "§7兑换额外奖励！",
                "",
                "§e点击查看"
            ));
            setItem(48, collBtn, p -> {
                p.closeInventory();
                new GachaCollectionGUI(plugin, p).open();
            });
        }

        // 返回按钮
        addBackButton(49, () -> new MainMenuGUI(plugin, player).open());
    }

    /** 判断槽位是否在机器网格内（第 2~5 行的非边框格） */
    private boolean isGridSlot(int slot) {
        if (slot < 10 || slot > 43) return false;
        int col = slot % 9;
        return col != 0 && col != 8;
    }

    /** 放置一台扭蛋机图标到指定槽位 */
    private void placeMachine(int slot, GachaMachine machine, Set<Integer> occupied) {
        ItemStack icon = machine.createIconItem(plugin);
        ItemUtil.setDisplayName(icon, MessageUtil.convertMiniMessageToLegacy("<yellow><bold>" + machine.getName()));

        List<String> lore = new ArrayList<>();
        for (String descLine : machine.getDescription()) {
            lore.add(MessageUtil.convertMiniMessageToLegacy(descLine));
        }
        lore.add("");
        lore.add("§7每次抽奖: §e" + plugin.getShopConfig().formatCurrency(machine.getCost()));
        if (machine.isBookMode()) {
            lore.add("§7产出: §bAiyatsbus 附魔书");
        } else if (machine.isCePackMode()) {
            lore.add("§7产出: §6CraftEngine 物品");
        } else {
            lore.add("§7奖品数量: §e" + machine.getRewards().size() + " 种");
        }
        lore.add("");
        lore.add("§e点击开始抽奖！");

        ItemUtil.setLore(icon, lore);

        setItem(slot, icon, p -> {
            p.closeInventory();
            new GachaMachineGUI(plugin, p, machine).open();
        });
        occupied.add(slot);
    }
}
