package com.trading.paper_trade.strategy;

import java.util.List;
import java.util.Optional;

/**
 * Stateless technical indicators over a chronological series of close prices.
 * <p>
 * All methods expect {@code closes} in time order (oldest first, newest last)
 * and return the indicator value for the <em>last</em> bar in the series.
 */
public class Indicators {

    private Indicators() {
    }

    public static Optional<Double> sma5(List<Double> closes) {
        return sma(closes, 5);
    }

    public static Optional<Double> sma10(List<Double> closes) {
        return sma(closes, 10);
    }

    public static Optional<Double> sma20(List<Double> closes) {
        return sma(closes, 20);
    }

    public static Optional<Double> ema5(List<Double> closes) {
        return ema(closes, 5);
    }

    public static Optional<Double> ema10(List<Double> closes) {
        return ema(closes, 10);
    }

    public static Optional<Double> ema20(List<Double> closes) {
        return ema(closes, 20);
    }

    public static Optional<Double> rsi14(List<Double> closes) {
        return rsi(closes, 14);
    }

    /**
     * Simple moving average of the last {@code period} closes.
     * Requires {@code closes.size() >= period}.
     */
    public static Optional<Double> sma(List<Double> closes, int period) {
        if (isValidInput(closes, period)) {
            double sum = 0;
            int start = closes.size() - period;
            for (int i = start; i < closes.size(); i++) {
                sum += closes.get(i);
            }
            return Optional.of(sum / period);
        }
        return Optional.empty();
    }

    /**
     * Exponential moving average at the last bar.
     * <p>
     * Uses multiplier {@code alpha = 2 / (period + 1)}. The first EMA is seeded
     * with the SMA of the first {@code period} closes, then rolled forward to the end.
     * Requires {@code closes.size() >= period}.
     */
    public static Optional<Double> ema(List<Double> closes, int period) {
        if (isValidInput(closes, period)) {
            double sum = 0;
            for (int i = 0; i < period; i++) {
                sum += closes.get(i);
            }

            double ema = sum / period;
            double multiplier = 2.0 / (period + 1);
            for (int i = period; i < closes.size(); i++) {
                ema = (closes.get(i) - ema) * multiplier + ema;
            }
            return Optional.of(ema);
        }
        return Optional.empty();
    }

    /**
     * Wilder-smoothed RSI at the last bar.
     * <p>
     * Initial average gain/loss uses the first {@code period} price changes;
     * subsequent bars use Wilder smoothing:
     * {@code avg = (prev * (period - 1) + value) / period}.
     * Requires {@code closes.size() >= period + 1}.
     */
    public static Optional<Double> rsi(List<Double> closes, int period) {
        if (closes == null || closes.isEmpty() || period <= 0 || closes.size() < period + 1) {
            return Optional.empty();
        }
        if (hasNullElement(closes)) {
            return Optional.empty();
        }

        double gainSum = 0;
        double lossSum = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            gainSum += Math.max(change, 0);
            lossSum += Math.max(-change, 0);
        }

        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        for (int i = period + 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        return Optional.of(toRsi(avgGain, avgLoss));
    }

    private static boolean isValidInput(List<Double> closes, int period) {
        if (closes == null || closes.isEmpty() || period <= 0 || closes.size() < period) {
            return false;
        }
        return !hasNullElement(closes);
    }

    private static boolean hasNullElement(List<Double> closes) {
        for (Double close : closes) {
            if (close == null) {
                return true;
            }
        }
        return false;
    }

    private static double toRsi(double avgGain, double avgLoss) {
        if (avgLoss == 0) {
            return avgGain == 0 ? 0 : 100;
        }
        if (avgGain == 0) {
            return 0;
        }
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
}
