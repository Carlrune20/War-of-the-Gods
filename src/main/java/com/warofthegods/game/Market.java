package com.warofthegods.game;

import com.warofthegods.Config;

/**
 * The market's prices, as in Age of Empires: food and wood are bought and sold for gold a lot at a time, buying a
 * lot paying a fee over its price and selling one getting as much under it. Every lot a team buys puts that price up
 * for the team, every lot it sells puts it down, so trading much of one thing soon stops paying; while the team leaves
 * a price alone it drifts back to where it started. Prices are kept as gold a lot, and {@link Team} keeps each team's.
 */
public final class Market {

    /** What can be traded at a market for gold. */
    public static final Resource[] GOODS = { Resource.FOOD, Resource.WOOD };

    private Market() {}

    /** Whether the resource can be bought and sold at a market. */
    public static boolean trades(Resource resource) {
        return resource == Resource.FOOD || resource == Resource.WOOD;
    }

    /** The price a lot starts at, and goes back to. */
    public static double basePrice() {
        return Math.max(Config.marketMinPrice, Math.min(Config.marketMaxPrice, Config.marketBasePrice));
    }

    /** Gold buying a lot costs at the price. */
    public static long buyCost(double price) {
        return Math.max(1, Math.round(price * (100 + Config.marketFee) / 100));
    }

    /** Gold selling a lot gets at the price. */
    public static long sellGain(double price) {
        return Math.max(1, Math.round(price * (100 - Config.marketFee) / 100));
    }

    /** The price once a lot has been bought at it. */
    public static double afterBuy(double price) {
        return Math.min(Config.marketMaxPrice, price + Config.marketPriceStep);
    }

    /** The price once a lot has been sold at it. */
    public static double afterSell(double price) {
        return Math.max(Config.marketMinPrice, price - Config.marketPriceStep);
    }

    /** The price after so many ticks without trading, drifting back towards the base price without passing it. */
    public static double recover(double price, long ticks) {
        double base = basePrice();
        double drift = Config.marketRecoveryPerMinute * Math.max(0, ticks) / 1200.0;
        return price > base ? Math.max(base, price - drift) : Math.min(base, price + drift);
    }
}
