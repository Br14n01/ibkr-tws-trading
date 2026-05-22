package com.trading.paper_trade.integration.ibkr;

import com.trading.paper_trade.market.MarketData;
import com.trading.paper_trade.config.IbkrProperties;

import com.ib.client.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IBKRClient {
    private final EClientSocket client;
    private final IBKRListener listener;
    private final IbkrProperties props;
    private int currentOrderId = -1;

    private boolean isConnected = false;

    @Autowired
    @Lazy
    private MarketData marketDataService;

    private AtomicInteger nextReqId = new AtomicInteger(1000);

    public IBKRClient(IBKRListener listener, IbkrProperties props){
        this.listener = listener;
        this.props = props;
        this.client = new EClientSocket(listener, listener.getSignal());
    }

    public void setConnected(boolean connected) {
        this.isConnected = connected;
    }

    public boolean isCurrentlyConnected() {
        return client.isConnected() && isConnected;
    }

    @PreDestroy
    public void shutdown() {
        if (client != null && client.isConnected()) {
            System.out.println("Shutting down: Disconnecting from TWS...");
            client.eDisconnect();
        }
    }

    @PostConstruct
    public void start() {
        if (!client.isConnected()) {
            client.eConnect(props.host(), props.port(), props.clientId());
            if (client.isConnected()) {
                System.out.println("Connected to IBKR Gateway at " + props.host() + ":" + props.port()
                        + " (clientId=" + props.clientId() + ").");
                setConnected(true); // Set our flag to true
                startReaderThread();
                subscribeToPortfolioData();
            }
        }
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
            System.out.println("[IBKR] Disconnected successfully.");
        }
    }

    public EClientSocket getClient() { return client; }

    // We'll use this to keep track of the next valid order ID from TWS
    public void setNextOrderId(int id) { this.currentOrderId = id; }
    public int getNextOrderId() { return currentOrderId++; }

    public void subscribeToPortfolioData() {
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

        int reqId = nextReqId.getAndIncrement();
        Contract contract = new Contract();
        contract.symbol(symbol.trim().toUpperCase());
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");

        client.reqMktData(reqId, contract, "", false, false, null);
        return reqId;
    }
}
