package dev.user.shop.gui;

import dev.user.shop.shop.ShopItem;
import dev.user.shop.shop.ShopManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ShopItemsGUITest {

    @Test
    void nonPurchaseClickTypesNeverReachShopItem() {
        ShopManager.ShopCategory category = mock(ShopManager.ShopCategory.class);
        when(category.getName()).thenReturn("Shop");
        ShopItemsGUI gui = new ShopItemsGUI(null, mock(Player.class), category);
        ShopItem item = mock(ShopItem.class);
        Player player = mock(Player.class);

        for (ClickType click : new ClickType[] {
                ClickType.RIGHT, ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY,
                ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.MIDDLE
        }) {
            gui.handleItemClick(player, item, click);
        }

        verifyNoInteractions(item);
    }
}
