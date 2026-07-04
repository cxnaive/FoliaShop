package dev.user.shop.gui;

import dev.user.shop.FoliaShopPlugin;
import dev.user.shop.util.ItemUtil;
import dev.user.shop.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractGUI implements InventoryHolder {

    protected final FoliaShopPlugin plugin;
    protected final Player player;
    protected final String title;
    protected final int size;
    protected Inventory inventory;
    protected final Map<Integer, GUIAction> actions;

    public interface GUIAction {
        void execute(Player player);
    }

    public AbstractGUI(FoliaShopPlugin plugin, Player player, String title, int size) {
        this.plugin = plugin;
        this.player = player;
        this.title = title;
        this.size = size;
        this.actions = new HashMap<>();
    }

    public void open() {
        Component titleComponent = LegacyComponentSerializer.legacySection().deserialize(title);
        inventory = Bukkit.createInventory(this, size, titleComponent);
        initialize();
        player.openInventory(inventory);
        GUIManager.registerGUI(player.getUniqueId(), this);
    }

    protected abstract void initialize();

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void handleClick(int slot, Player player) {
        GUIAction action = actions.get(slot);
        if (action != null) {
            action.execute(player);
        }
    }

    /**
     * 检查指定槽位是否有绑定的点击动作
     */
    public boolean hasAction(int slot) {
        return actions.containsKey(slot);
    }

    protected void setItem(int slot, ItemStack item, GUIAction action) {
        if (slot >= 0 && slot < size) {
            inventory.setItem(slot, item);
            if (action != null) {
                actions.put(slot, action);
            }
        }
    }

    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    protected void fillBorder(Material material) {
        ItemStack border = ItemUtil.createDecoration(material, " ");
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                if (inventory.getItem(i) == null) {
                    setItem(i, border);
                }
            }
        }
    }

    protected void fillBorder() {
        Material borderMat = getDecorMaterial("border", Material.BLACK_STAINED_GLASS_PANE);
        fillBorder(borderMat);
    }

    protected void addCloseButton(int slot) {
        var decoration = plugin.getShopConfig().getGUIDecoration("close");
        ItemStack closeBtn = ItemUtil.createItemFromKey(plugin, decoration.getMaterial());
        String name = decoration.getName().isEmpty() ? "§c关闭" : MessageUtil.convertMiniMessageToLegacy(decoration.getName());
        ItemUtil.setDisplayName(closeBtn, name);
        setItem(slot, closeBtn, p -> p.closeInventory());
    }

    protected void addBackButton(int slot, Runnable onBack) {
        var decoration = plugin.getShopConfig().getGUIDecoration("back");
        ItemStack backBtn = ItemUtil.createItemFromKey(plugin, decoration.getMaterial());
        String name = decoration.getName().isEmpty() ? "§e返回" : MessageUtil.convertMiniMessageToLegacy(decoration.getName());
        ItemUtil.setDisplayName(backBtn, name);
        setItem(slot, backBtn, p -> onBack.run());
    }

    protected ItemStack createDecorItem(String key, Material fallback) {
        var decor = plugin.getShopConfig().getGUIDecoration(key);
        if (decor != null) {
            return new ItemStack(decor.getBukkitMaterial());
        }
        return new ItemStack(fallback);
    }

    protected Material getDecorMaterial(String key, Material fallback) {
        var decor = plugin.getShopConfig().getGUIDecoration(key);
        if (decor != null) {
            return decor.getBukkitMaterial();
        }
        return fallback;
    }

    protected ItemStack createActionItem(String materialKey, String name, String actionKey, String actionValue) {
        ItemStack item = ItemUtil.createItemFromKey(plugin, materialKey);
        ItemUtil.setDisplayName(item, name);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(plugin, actionKey), PersistentDataType.STRING, actionValue);
            item.setItemMeta(meta);
        }

        return item;
    }

    public void onClose() {
        GUIManager.unregisterGUI(player.getUniqueId());
    }
}
