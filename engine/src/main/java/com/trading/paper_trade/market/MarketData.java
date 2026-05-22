package com.trading.paper_trade.market;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.trading.paper_trade.integration.ibkr.IBKRClient;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds latest prices from IBKR market data callbacks.
 * Symbols are keyed in uppercase ({@link #addSubscription}/{@link #updatePrice}).
 */
@Service
public class MarketData {

    private final IBKRClient ibkrClient;
    private final Map<Integer, String> tickerIdToSymbol = new ConcurrentHashMap<>();
    private final Map<String, Double> lastPriceBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Integer> symbolToTickerId = new ConcurrentHashMap<>();

    public MarketData(@Lazy IBKRClient ibkrClient) {
        this.ibkrClient = ibkrClient;
    }

    /**
     * Ensures market data subscription exists for symbol, then returns cached last price.
     * Returns 0.0 until the listener receives a tick and stores it via {@link #updatePrice}.
     */
    public double getPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0.0;
        }

        String normalized = normalize(symbol);
        ensureSubscription(normalized);
        return lastPriceBySymbol.getOrDefault(normalized, 0.0);
    }

    private void ensureSubscription(String symbol) {
        symbolToTickerId.computeIfAbsent(symbol, key -> {
            int reqId = ibkrClient.requestMarketData(symbol);
            if (reqId >= 0) {
                tickerIdToSymbol.put(reqId, symbol);
                return reqId;
            }
            return null;
        });
    }

    public String getSymbolById(int tickerId) {
        return tickerIdToSymbol.get(tickerId);
    }

    public void updatePrice(String symbol, double price) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        lastPriceBySymbol.put(normalize(symbol), price);
    }

    private static String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
