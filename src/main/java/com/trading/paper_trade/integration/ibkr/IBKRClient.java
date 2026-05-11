package com.trading.paper_trade.integration.ibkr;

import com.ib.client.*;
import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;

@Service
public class IBKRClient {
    private final EClientSocket client;
    private final IBKRListener listener;
    private int currentOrderId = -1;

    private boolean isConnected = false;

    public IBKRClient(IBKRListener listener){
        this.listener = listener;
        this.client = new EClientSocket(listener, listener.getSignal());
    }

    public void setConnected(boolean connected) {
        this.isConnected = connected;
    }

    public boolean isCurrentlyConnected() {
        return client.isConnected() && isConnected;
    }

    @PostConstruct
    public void start() {
        if (!client.isConnected()) {
            client.eConnect("127.0.0.1", 7497, 2);
            if (client.isConnected()) {
                System.out.println("Connected to IBKR Gateway.");
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
}
