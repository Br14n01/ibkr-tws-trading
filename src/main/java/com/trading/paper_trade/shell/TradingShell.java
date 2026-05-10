package com.trading.paper_trade.shell;

import com.trading.paper_trade.integration.ibkr.IBKRClient;
import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.Decimal;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class TradingShell {

    private final IBKRClient ibkrClient;

    public TradingShell(IBKRClient ibkrClient) {
        this.ibkrClient = ibkrClient;
    }

    @ShellMethod(key = "buy", value = "Place a buy order: buy --symbol AAPL --qty 10 --price 150.0")
    public String buy(
            String symbol,
            int qty,
            @ShellOption(defaultValue = ShellOption.NULL) Double price) {

        if (!ibkrClient.getClient().isConnected()) {
            return "Error: TWS is not connected!";
        }

        // --- Reuse your logic from the Controller ---
        Contract contract = new Contract();
        contract.symbol(symbol.toUpperCase());
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");

        Order order = new Order();
        order.action("BUY");
        order.totalQuantity(Decimal.get(qty));

        if (price != null) {
            order.orderType("LMT");
            order.lmtPrice(price);
        } else {
            order.orderType("MKT");
        }

        int id = ibkrClient.getNextOrderId();
        ibkrClient.getClient().placeOrder(id, contract, order);

        return "Order " + id + " sent: BUY " + qty + " " + symbol + (price != null ? " @ " + price : " (Market)");
    }

    @ShellMethod(key = "status", value = "Check connection status")
    public String status() {
        boolean connected = ibkrClient.getClient().isConnected();
        return connected ? "CONNECTED to IBKR" : "DISCONNECTED - use 'connect' to retry";
    }

    @ShellMethod(key = "connect", value = "Manually reconnect to TWS")
    public String reconnect() {
        if (ibkrClient.getClient().isConnected()) return "Already connected.";
        ibkrClient.start(); // Reuse your start logic
        return "Reconnection attempt initiated...";
    }
}