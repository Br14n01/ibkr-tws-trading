package com.trading.paper_trade.strategy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorsTest {

    private static final List<Double> SERIES = Arrays.asList(
            1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0
    );

    @Test
    void sma5_returnsAverageOfLastFiveCloses() {
        Optional<Double> result = Indicators.sma5(SERIES);
        assertTrue(result.isPresent());
        assertEquals(8.0, result.get(), 1e-9);
    }

    @Test
    void sma_returnsEmptyWhenInsufficientData() {
        assertTrue(Indicators.sma(Arrays.asList(1.0, 2.0, 3.0), 5).isEmpty());
        assertTrue(Indicators.sma(null, 5).isEmpty());
        assertTrue(Indicators.sma(Collections.emptyList(), 5).isEmpty());
    }

    @Test
    void ema5_seedsWithSmaAndRollsForward() {
        Optional<Double> result = Indicators.ema5(SERIES);
        assertTrue(result.isPresent());
        assertEquals(8.0, result.get(), 1e-9);
    }

    @Test
    void ema_returnsEmptyWhenInsufficientData() {
        assertTrue(Indicators.ema(Arrays.asList(1.0, 2.0), 5).isEmpty());
    }

    @Test
    void rsi_wilderSmoothingAtLastBar() {
        List<Double> closes = Arrays.asList(10.0, 12.0, 11.0);
        Optional<Double> result = Indicators.rsi(closes, 2);
        assertTrue(result.isPresent());
        assertEquals(100 - (100 / (1 + 2.0)), result.get(), 1e-9);
    }

    @Test
    void rsi_returns100WhenNoLosses() {
        List<Double> closes = Arrays.asList(10.0, 11.0, 12.0, 13.0);
        Optional<Double> result = Indicators.rsi(closes, 2);
        assertTrue(result.isPresent());
        assertEquals(100.0, result.get(), 1e-9);
    }

    @Test
    void rsi_returnsEmptyWhenInsufficientData() {
        assertTrue(Indicators.rsi14(Arrays.asList(1.0, 2.0)).isEmpty());
        assertTrue(Indicators.rsi(Arrays.asList(1.0, 2.0, 3.0), 5).isEmpty());
    }

    @Test
    void rejectsNullElements() {
        List<Double> withNull = Arrays.asList(1.0, null, 3.0, 4.0, 5.0);
        assertTrue(Indicators.sma5(withNull).isEmpty());
        assertTrue(Indicators.ema5(withNull).isEmpty());
        assertTrue(Indicators.rsi14(withNull).isEmpty());
    }

    @Test
    void convenienceMethodsDelegateToGenericMethods() {
        assertEquals(Indicators.sma(SERIES, 10), Indicators.sma10(SERIES));
        assertEquals(Indicators.sma(SERIES, 20), Indicators.sma20(SERIES));
        assertEquals(Indicators.ema(SERIES, 10), Indicators.ema10(SERIES));
        assertEquals(Indicators.ema(SERIES, 20), Indicators.ema20(SERIES));
    }
}
