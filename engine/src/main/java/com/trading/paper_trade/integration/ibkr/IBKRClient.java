package com.trading.paper_trade.integration.ibkr;

import com.trading.paper_trade.market.MarketData;
import com.trading.paper_trade.config.IbkrProperties;

import com.ib.client.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IBKRClient {
    private final EClientSocket client;
    private final IBKRListener listener;
    private final IbkrProperties props;

    /** Next order id, seeded by TWS via {@link #setNextOrderId}. Orders use this space only. */
    private final AtomicInteger nextOrderId = new AtomicInteger(-1);

    /** Request ids for data calls (market data, historical data). Kept separate from order ids. */
    private final AtomicInteger nextReqId = new AtomicInteger(1000);

    /** Guards against re-subscribing to portfolio data multiple times on a single connection. */
    private final AtomicBoolean portfolioSubscribed = new AtomicBoolean(false);

    @Autowired
    @Lazy
    private MarketData marketDataService;

    public IBKRClient(IBKRListener listener, IbkrProperties props){
        this.listener = listener;
        this.props = props;
        this.client = new EClientSocket(listener, listener.getSignal());
    }

    @PreDestroy
    public void shutdown() {
        if (client != null && client.isConnected()) {
            System.out.println("Shutting down: Disconnecting from TWS...");
            client.eDisconnect();
            portfolioSubscribed.set(false);
        }
    }

    @PostConstruct
    public void start() {
        if (client.isConnected()) {
            return;
        }

        client.eConnect(props.host(), props.port(), props.clientId());
        if (!client.isConnected()) {
            System.err.println("[IBKR] Connection to " + props.host() + ":" + props.port()
                    + " failed. Is TWS/Gateway running with the API enabled? Use 'connect' to retry.");
            return;
        }

        System.out.println("Connected to IBKR Gateway at " + props.host() + ":" + props.port()
                + " (clientId=" + props.clientId() + ").");

        // The reader MUST be started for ANY message (including the handshake and the first
        // nextValidId) to be processed. Portfolio subscriptions are deferred until TWS signals
        // readiness via nextValidId() -> subscribeToPortfolioData(), rather than fired here
        // synchronously before the connection is actually usable.
        portfolioSubscribed.set(false);
        startReaderThread();
    }

    private void startReaderThread() {
        // JAVA 25: Using a Virtual Thread for the background message reader.
        // This is much more efficient than old-school platform threads.
        Thread.ofVirtual().start(() -> {
            try {
                var reader = new EReader(client, listener.getSignal());
                reader.start();
                while (client.isConnected()) {
                    listener.getSignal().waitForSignal();
                    reader.processMsgs();
                }
            } catch (Exception e) {
                System.err.println("Socket Reader Error: " + e.getMessage());
            }
        });
    }

    public void disconnect() {
        if (client != null && client.isConnected()) {
            client.eDisconnect();
            portfolioSubscribed.set(false);
            System.out.println("[IBKR] Disconnected successfully.");
        }
    }

    /** Called from the listener when TWS reports the socket closed (server-initiated drop). */
    public void onConnectionClosed() {
        portfolioSubscribed.set(false);
    }

    public EClientSocket getClient() { return client; }

    // Order ids: seeded by TWS' nextValidId callback, then handed out atomically per order.
    public void setNextOrderId(int id) { nextOrderId.set(id); }
    public int getNextOrderId() { return nextOrderId.getAndIncrement(); }

    /** Monotonic request id for non-order data requests (historical data, etc.). */
    public int getNextRequestId() { return nextReqId.getAndIncrement(); }

    public void subscribeToPortfolioData() {
        if (!client.isConnected()) {
            return;
        }
        // Only subscribe once per connection; nextValidId can fire again and we don't want
        // duplicate reqAccountSummary/reqAccountUpdates calls.
        if (!portfolioSubscribed.compareAndSet(false, true)) {
            return;
        }

        // 1. Get high-level account metrics
        String tags = "AccountType,NetLiquidation,TotalCashValue,SettledCash,BuyingPower,EquityWithLoanValue,GrossPositionValue,ExcessLiquidity,Leverage";
        client.reqAccountSummary(9001, "All", tags);

        // 2. Get specific asset positions
        client.reqAccountUpdates(true, "");
    }

    /**
     * Sends reqMktData for the symbol and returns request id used by IBKR.
     * Returns -1 when the client is disconnected or the symbol is invalid.
     */
    public int requestMarketData(String symbol) {
        if (!client.isConnected() || symbol == null || symbol.isBlank()) {
            return -1;
        }

        client.reqMarketDataType(3);

        int reqId = getNextRequestId();
        Contract contract = new Contract();
        contract.symbol(symbol.trim().toUpperCase());
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");

        client.reqMktData(reqId, contract, "", false, false, null);
        return reqId;
    }
}
