package dev.user.shop.shop;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopItem {

    private final String id;
    private final String itemKey;
    private ItemStack displayItem;
    private double buyPrice;
    private double buyPriceMax; // 购买价上限，> buyPrice 时启用随机价格
    private double sellPrice;
    private double sellPriceMax; // 出售价上限，> sellPrice 时启用随机价格
    private int buyPoints; // 购买所需点券，0表示不需要
    private final AtomicInteger stock;
    private String category;
    private int slot;
    private boolean enabled;
    private int dailyLimit; // 每日购买限额，0表示无限制
    private int playerLimit; // 每个玩家终身购买限额，0表示无限制
    private Map<String, String> components; // NBT 组件配置
    private List<String> commands; // 购买后执行的命令
    private List<String> conditions; // 购买条件
    private boolean giveItem; // 是否给予物品（默认true）
    private boolean autoFill; // 是否为 namespace 自动填充物品（仅内存，不写 DB）

    public ShopItem(String id, String itemKey, double buyPrice, double sellPrice, int stock, String category, int slot) {
        this(id, itemKey, buyPrice, sellPrice, 0, stock, category, slot, 0, null);
    }

    public ShopItem(String id, String itemKey, double buyPrice, double sellPrice, int stock, String category, int slot, int dailyLimit) {
        this(id, itemKey, buyPrice, sellPrice, 0, stock, category, slot, dailyLimit, null);
    }

    public ShopItem(String id, String itemKey, double buyPrice, double sellPrice, int buyPoints, int stock, String category, int slot, int dailyLimit, Map<String, String> components) {
        this.id = id;
        this.itemKey = itemKey;
        this.buyPrice = buyPrice;
        this.buyPriceMax = buyPrice; // 默认固定价格
        this.sellPrice = sellPrice;
        this.sellPriceMax = sellPrice; // 默认固定价格
        this.buyPoints = buyPoints;
        this.stock = new AtomicInteger(stock);
        this.category = category;
        this.slot = slot;
        this.enabled = true;
        this.dailyLimit = dailyLimit;
        this.components = components != null ? components : new HashMap<>();
        this.commands = new ArrayList<>();
        this.conditions = new ArrayList<>();
        this.giveItem = true;
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getItemKey() { return itemKey; }

    public ItemStack getDisplayItem() { return displayItem; }
    public void setDisplayItem(ItemStack displayItem) { this.displayItem = displayItem; }

    public double getBuyPrice() { return buyPrice; }
    public void setBuyPrice(double buyPrice) { this.buyPrice = buyPrice; }

    public double getBuyPriceMax() { return buyPriceMax; }
    public void setBuyPriceMax(double buyPriceMax) { this.buyPriceMax = buyPriceMax; }
    public boolean hasRandomBuyPrice() { return buyPriceMax > buyPrice && buyPrice > 0; }

    public double getSellPrice() { return sellPrice; }
    public void setSellPrice(double sellPrice) { this.sellPrice = sellPrice; }

    public double getSellPriceMax() { return sellPriceMax; }
    public void setSellPriceMax(double sellPriceMax) { this.sellPriceMax = sellPriceMax; }
    public boolean hasRandomSellPrice() { return sellPriceMax > sellPrice && sellPrice > 0; }

    public int getStock() { return stock.get(); }
    public void setStock(int stock) { this.stock.set(stock); }
    public boolean hasUnlimitedStock() { return stock.get() < 0; }
    public boolean isInStock(int amount) { int s = stock.get(); return s < 0 || s >= amount; }
    public void reduceStock(int amount) {
        stock.getAndUpdate(s -> s > 0 ? Math.max(0, s - amount) : s);
    }
    public void addStock(int amount) {
        stock.getAndUpdate(s -> s >= 0 ? s + amount : s);
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean canBuy() { return enabled && (buyPrice > 0 || buyPoints > 0); }
    public boolean canSell() { return enabled && sellPrice > 0; }

    public int getBuyPoints() { return buyPoints; }
    public void setBuyPoints(int buyPoints) { this.buyPoints = buyPoints; }
    public boolean requiresPoints() { return buyPoints > 0; }

    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }
    public boolean hasDailyLimit() { return dailyLimit > 0; }

    public int getPlayerLimit() { return playerLimit; }
    public void setPlayerLimit(int playerLimit) { this.playerLimit = playerLimit; }
    public boolean hasPlayerLimit() { return playerLimit > 0; }

    public Map<String, String> getComponents() { return components; }
    public void setComponents(Map<String, String> components) { this.components = components != null ? components : new HashMap<>(); }
    public boolean hasComponents() { return components != null && !components.isEmpty(); }

    public List<String> getCommands() { return commands; }
    public void setCommands(List<String> commands) { this.commands = commands != null ? commands : new ArrayList<>(); }
    public boolean hasCommands() { return commands != null && !commands.isEmpty(); }

    public List<String> getConditions() { return conditions; }
    public void setConditions(List<String> conditions) { this.conditions = conditions != null ? conditions : new ArrayList<>(); }
    public boolean hasConditions() { return conditions != null && !conditions.isEmpty(); }

    public boolean isGiveItem() { return giveItem; }
    public void setGiveItem(boolean giveItem) { this.giveItem = giveItem; }

    public boolean isAutoFill() { return autoFill; }
    public void setAutoFill(boolean autoFill) { this.autoFill = autoFill; }
}
