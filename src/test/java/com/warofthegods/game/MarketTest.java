package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.warofthegods.Config;

class MarketTest {

    @Test
    void onlyFoodAndWoodAreTraded() {
        assertTrue(Market.trades(Resource.FOOD));
        assertTrue(Market.trades(Resource.WOOD));
        assertFalse(Market.trades(Resource.GOLD));
        assertFalse(Market.trades(Resource.FAVOR));
    }

    @Test
    void buyingCostsMoreThanSellingGets() {
        double price = Market.basePrice();
        assertEquals(130, Market.buyCost(price));
        assertEquals(70, Market.sellGain(price));
        assertTrue(Market.buyCost(price) > Market.sellGain(price));
    }

    @Test
    void eachLotTradedMovesThePriceUntilItsBounds() {
        double price = Market.basePrice();
        assertEquals(price + Config.marketPriceStep, Market.afterBuy(price));
        assertEquals(price - Config.marketPriceStep, Market.afterSell(price));
        double sold = price;
        for (int i = 0; i < 100; i++) {
            sold = Market.afterSell(sold);
        }
        assertEquals(Config.marketMinPrice, sold);
        assertTrue(Market.sellGain(sold) >= 1);
        double bought = price;
        for (int i = 0; i < 100; i++) {
            bought = Market.afterBuy(bought);
        }
        assertEquals(Config.marketMaxPrice, bought);
    }

    @Test
    void pricesDriftBackWithoutPassingTheBase() {
        double base = Market.basePrice();
        // A minute brings a price back by the recovery per minute.
        assertEquals(base + 40 - Config.marketRecoveryPerMinute, Market.recover(base + 40, 1200), 1e-9);
        assertEquals(base - 40 + Config.marketRecoveryPerMinute, Market.recover(base - 40, 1200), 1e-9);
        assertEquals(base, Market.recover(base + 40, 1200 * 60), 1e-9);
        assertEquals(base, Market.recover(base - 40, 1200 * 60), 1e-9);
        assertEquals(base + 40, Market.recover(base + 40, 0), 1e-9);
    }
}
