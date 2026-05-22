package com.trading.paper_trade.market;

import com.ib.client.Bar;
import com.trading.paper_trade.integration.ibkr.IBKRClient;

import com.ib.client.Contract;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HistoryService {

    private final IBKRClient ibkrClient;

    public HistoryService(IBKRClient ibkrClient) {
        this.ibkrClient = ibkrClient;
    }

    /**
     * Requests the last 5 trading days of OHLCV data.
     * @param symbol The stock ticker (e.g., "AAPL")
     */

    public void fetch(String symbol) {
        Contract contract = new Contract();
        contract.symbol(symbol.toUpperCase());
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");

        // Historical data is a data request, not an order: draw from the request-id space
        // so it can never collide with (or burn) a TWS order id.
        int reqId = ibkrClient.getNextRequestId();

        // EWrapper.historicalData
        ibkrClient.getClient().reqHistoricalData(
                reqId,
                contract,
                "",           // Querying up to current time
                "1 D",        // Duration
                "15 mins",      // Bar size
                "TRADES",     // Data type
                1,            // Use Regular Trading Hours
                1,            // Date format
                false,        // Keep up to date
                null          // Chart options
        );
    }
}