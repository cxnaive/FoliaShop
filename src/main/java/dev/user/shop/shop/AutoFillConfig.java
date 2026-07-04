package dev.user.shop.shop;

import dev.user.shop.gacha.CraftEnginePackPool;
import dev.user.shop.util.PriceUtil;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 商店分类/子分类的 CraftEngine namespace 自动填充配置。
 * <p>
 * 持有一个 {@link CraftEnginePackPool}（packs/tags/items/exclude，复用扭蛋的解析）作为物品来源，
 * 以及一组 defaults（价格/库存/限额）应用到该 namespace 下的每件物品。
 * buy-price 支持 "min~max"（每日随机区间，由 PriceUtil 实现）；sell-price 默认 0=不可回售。
 */
public class AutoFillConfig {

    private final CraftEnginePackPool pool;
    private final double buyPriceMin;
    private final double buyPriceMax;
    private final double sellPrice;
    private final int stock;
    private final int dailyLimit;
    private final int playerLimit;

    public AutoFillConfig(CraftEnginePackPool pool, double buyPriceMin, double buyPriceMax,
                          double sellPrice, int stock, int dailyLimit, int playerLimit) {
        this.pool = pool;
        this.buyPriceMin = buyPriceMin;
        this.buyPriceMax = buyPriceMax;
        this.sellPrice = sellPrice;
        this.stock = stock;
        this.dailyLimit = dailyLimit;
        this.playerLimit = playerLimit;
    }

    /**
     * 从分类/子分类配置节解析。需要同时含 auto-fill 段（来源）才生效；defaults 段可选（有默认值）。
     * @return 配置；无 auto-fill 段返回 null
     */
    public static AutoFillConfig fromConfig(ConfigurationSection catSection) {
        if (catSection == null) return null;
        ConfigurationSection autoFillSection = catSection.getConfigurationSection("auto-fill");
        if (autoFillSection == null) return null;

        CraftEnginePackPool pool = CraftEnginePackPool.fromConfig(autoFillSection);
        if (pool == null) return null;

        double buyMin = 100, buyMax = 100, sell = 0;
        int stock = -1, daily = 0, player = 0;
        ConfigurationSection defaults = catSection.getConfigurationSection("defaults");
        if (defaults != null) {
            Object bp = defaults.get("buy-price");
            if (bp instanceof String s && s.contains("~")) {
                double[] range = PriceUtil.parsePriceRange(s);
                buyMin = range[0];
                buyMax = range[1];
            } else {
                buyMin = defaults.getDouble("buy-price", 100);
                buyMax = buyMin;
            }
            Object sp = defaults.get("sell-price");
            if (sp instanceof String s && s.contains("~")) {
                double[] range = PriceUtil.parsePriceRange(s);
                sell = range[0]; // 回售价取区间下限（不搞随机卖价，简化）
            } else {
                sell = defaults.getDouble("sell-price", 0);
            }
            stock = defaults.getInt("stock", -1);
            daily = defaults.getInt("daily-limit", 0);
            player = defaults.getInt("player-limit", 0);
        }
        return new AutoFillConfig(pool, buyMin, buyMax, sell, stock, daily, player);
    }

    public CraftEnginePackPool getPool() { return pool; }
    public double getBuyPriceMin() { return buyPriceMin; }
    public double getBuyPriceMax() { return buyPriceMax; }
    /** 是否启用每日随机区间价（max>min 且 min>0） */
    public boolean hasRandomBuyPrice() { return buyPriceMax > buyPriceMin && buyPriceMin > 0; }
    public double getSellPrice() { return sellPrice; }
    public int getStock() { return stock; }
    public int getDailyLimit() { return dailyLimit; }
    public int getPlayerLimit() { return playerLimit; }
}
