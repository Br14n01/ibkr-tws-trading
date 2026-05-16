package com.trading.paper_trade.market;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to store and manage incoming market data from IBKR.
 * Handles the translation between Ticker IDs and Symbols.
 */
@Service
public class MarketData {

    // Maps tickerId (integer) -> symbol (String)
    private final Map<Integer, String> idToSymbolMap = new ConcurrentHashMap<>();

    // Maps symbol (String) -> lastPrice (Double)
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();

    /**
     * Registers a new subscription request.
     * @param reqId The ID sent to IBKR
     * @param symbol The stock symbol (e.g., AAPL)
     */
    public void addSubscription(int reqId, String symbol) {
        idToSymbolMap.put(reqId, symbol.toUpperCase());
    }

    /**
     * Retrieves the symbol associated with a specific IBKR tickerId.
     */
    public String getSymbolById(int reqId) {
        return idToSymbolMap.get(reqId);
    }

    /**
     * Updates the internal cache with the latest price.
     */
    public void updatePrice(String symbol, double price) {
        if (symbol != null && price > 0) {
            lastPrices.put(symbol.toUpperCase(), price);
        }
    }

    /**
     * Gets the latest stored price for a symbol.
     * Returns 0.0 if no data has been received yet.
     */
    public Double getPrice(String symbol) {
        return lastPrices.getOrDefault(symbol.toUpperCase(), 0.0);
    }

    /**
     * Returns the full map of current prices (useful for a 'prices' command).
     */
    public Map<String, Double> getAllPrices() {
        return lastPrices;
    }
}