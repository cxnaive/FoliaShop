package dev.user.shop.globalshop;

/**
 * 全球商店购买结果
 */
public class GlobalShopPurchaseResult {

    private final boolean success;
    private final String message;
    private final String itemKey;
    private final int amount;
    private final double cost;
    private final double taxAmount;

    public GlobalShopPurchaseResult(boolean success, String message, String itemKey,
                                    int amount, double cost, double taxAmount) {
        this.success = success;
        this.message = message;
        this.itemKey = itemKey;
        this.amount = amount;
        this.cost = cost;
        this.taxAmount = taxAmount;
    }

    public static GlobalShopPurchaseResult success(String message, String itemKey, int amount, double cost, double taxAmount) {
        return new GlobalShopPurchaseResult(true, message, itemKey, amount, cost, taxAmount);
    }

    public static GlobalShopPurchaseResult fail(String message) {
        return new GlobalShopPurchaseResult(false, message, null, 0, 0, 0);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getItemKey() { return itemKey; }
    public int getAmount() { return amount; }
    public double getCost() { return cost; }
    public double getTaxAmount() { return taxAmount; }
}
