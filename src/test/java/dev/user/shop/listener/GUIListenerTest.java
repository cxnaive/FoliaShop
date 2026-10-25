package dev.user.shop.listener;

import dev.user.shop.gui.GachaMachineGUI;
import dev.user.shop.gui.SellGUI;
import dev.user.shop.gui.ShopItemsGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class GUIListenerTest {

    private final GUIListener listener = new GUIListener(null);
    private final Player player = mock(Player.class);
    private final Inventory top = mock(Inventory.class);
    private final PlayerInventory bottom = mock(PlayerInventory.class);

    @Test
    void playerInventorySlotMatchingShopItemDoesNotPurchase() {
        ShopItemsGUI gui = mock(ShopItemsGUI.class);
        when(top.getHolder()).thenReturn(gui);
        InventoryClickEvent event = click(bottom, 64, 10, ClickType.SHIFT_LEFT);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(gui, never()).hasAction(anyInt());
        verify(gui, never()).handleItemClick(any(), anyInt(), any());
        verify(gui, never()).handleClick(anyInt(), any());
    }

    @Test
    void topShopItemStillDispatchesPurchaseClick() {
        ShopItemsGUI gui = mock(ShopItemsGUI.class);
        when(top.getHolder()).thenReturn(gui);
        InventoryClickEvent event = click(top, 10, 10, ClickType.LEFT);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(gui).handleItemClick(player, 10, ClickType.LEFT);
    }

    @Test
    void playerInventorySlotMatchingGachaButtonDoesNotRunAction() {
        GachaMachineGUI gui = mock(GachaMachineGUI.class);
        when(top.getHolder()).thenReturn(gui);
        InventoryClickEvent event = click(bottom, 67, 13, ClickType.LEFT);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(gui, never()).handleClick(anyInt(), any());
    }

    @Test
    void topGachaButtonStillRunsAction() {
        GachaMachineGUI gui = mock(GachaMachineGUI.class);
        when(top.getHolder()).thenReturn(gui);
        InventoryClickEvent event = click(top, 13, 13, ClickType.LEFT);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(gui).handleClick(13, player);
    }

    @Test
    void sellGuiStillHandlesPlayerInventoryClicksSeparately() {
        SellGUI gui = mock(SellGUI.class);
        when(top.getHolder()).thenReturn(gui);
        when(gui.getInventory()).thenReturn(top);
        when(player.getInventory()).thenReturn(bottom);
        InventoryClickEvent event = click(bottom, 64, 10, ClickType.SHIFT_LEFT);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(event).getCurrentItem();
    }

    private InventoryClickEvent click(Inventory clicked, int rawSlot, int localSlot, ClickType clickType) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(top);
        when(event.getClickedInventory()).thenReturn(clicked);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getSlot()).thenReturn(localSlot);
        when(event.getClick()).thenReturn(clickType);
        return event;
    }
}
