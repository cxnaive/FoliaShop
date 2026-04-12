package dev.user.shop.util;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

/**
 * 确定性随机价格工具类
 * 基于玩家UUID + 物品ID + 日期构造种子，实现每天每人对同一物品的固定随机价格
 */
public final class PriceUtil {

    private PriceUtil() {}

    /**
     * 计算确定性随机价格
     * 同一玩家 + 同一物品 + 同一天 → 始终返回相同价格
     *
     * @param playerUuid 玩家UUID
     * @param itemId     物品ID
     * @param minPrice   最低价
     * @param maxPrice   最高价（<= minPrice 时返回 minPrice）
     * @return 当天对该玩家的固定随机价格
     */
    public static double computeDailyPrice(UUID playerUuid, String itemId, double minPrice, double maxPrice) {
        if (maxPrice <= minPrice) return minPrice;

        long epochDay = LocalDate.now().toEpochDay();
        long seed = playerUuid.getLeastSignificantBits() ^ playerUuid.getMostSignificantBits()
                    ^ (long) itemId.hashCode() * 31L
                    ^ epochDay * 17L;

        Random random = new Random(seed);
        double price = minPrice + random.nextDouble() * (maxPrice - minPrice);
        return Math.round(price * 100.0) / 100.0;
    }

    /**
     * 解析价格字符串，支持固定值和范围语法
     *
     * @param priceStr 价格字符串，如 "100" 或 "50~200"
     * @return double[2]: [min, max]，固定价格时 min == max
     */
    public static double[] parsePriceRange(String priceStr) {
        if (priceStr.contains("~")) {
            String[] parts = priceStr.split("~", 2);
            double min = Double.parseDouble(parts[0].trim());
            double max = Double.parseDouble(parts[1].trim());
            return new double[]{min, max};
        }
        double val = Double.parseDouble(priceStr.trim());
        return new double[]{val, val};
    }
}
