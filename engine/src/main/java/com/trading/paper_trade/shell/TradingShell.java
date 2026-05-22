package com.trading.paper_trade.shell;

import com.trading.paper_trade.integration.ibkr.IBKRClient;
import com.trading.paper_trade.model.Position;
import com.trading.paper_trade.model.AccountSummary;
import com.trading.paper_trade.portfolio.PortfolioService;
import com.trading.paper_trade.market.MarketData;
import com.trading.paper_trade.market.HistoryService;
import com.trading.paper_trade.order.OrderService;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import org.jline.terminal.Terminal;

import java.util.List;
import java.util.Map;

@ShellComponent
public class TradingShell {

    private final IBKRClient ibkrClient;
    private final Terminal terminal;
    private final PortfolioService portfolioService;
    private final MarketData marketDataService;
    private final HistoryService historyService;
    private final OrderService orderService;

    public TradingShell(Terminal terminal,
                        PortfolioService portfolioService,
                        @Lazy IBKRClient ibkrClient,
                        MarketData marketDataService,
                        HistoryService historyService,
                        OrderService orderService) {
        this.terminal = terminal;
        this.portfolioService = portfolioService;
        this.ibkrClient = ibkrClient;
        this.marketDataService = marketDataService;
        this.historyService = historyService;
        this.orderService = orderService;
    }

    @ShellMethod(key = "status", value = "Check connection status")
    public String status() {
        boolean connected = ibkrClient.getClient().isConnected();
        return connected ? "CONNECTED to IBKR" : "DISCONNECTED - use 'connect' to retry";
    }

    // Connection
    @ShellMethod(key = "connect", value = "Manually reconnect to TWS")
    public String reconnect() {
        if (ibkrClient.getClient().isConnected()) return "Already connected.";
        ibkrClient.start(); // Reuse your start logic
        return "Reconnection attempt initiated...";
    }

    @ShellMethod(key = "disconnect", value = "Disconnect from IBKR TWS")
    public String disconnect() {
        ibkrClient.disconnect();
        return "Disconnected from TWS.";
    }

    // Functions
    @ShellMethod(key = "portfolio", value = "View TWS Account Details")
    public void viewPortfolio() {
        AccountSummary summary = portfolioService.getSummary("");
        Map<String, Position> positions = portfolioService.getPositions();

        terminal.writer().println("\n" + "\u001B[1m" + "=== TWS ACCOUNT SUMMARY ===" + "\u001B[0m");
        terminal.writer().printf("Account: %-15s | Net Liq: $%s\n", summary.getAccountId(), summary.getMetric("NetLiquidation"));
        terminal.writer().printf("Cash:    %-15s | Leverage: %s\n", summary.getMetric("TotalCashValue"), summary.getMetric("Leverage"));
        terminal.writer().printf("Buying Power: $%s\n", summary.getMetric("BuyingPower"));

        if (positions.isEmpty()) {
            terminal.writer().println("\nNo open positions.");
        } else {
            terminal.writer().println("\n" + "\u001B[1m" + "--- OPEN POSITIONS ---" + "\u001B[0m");
            terminal.writer().printf("%-10s | %-6s | %-12s | %-12s | %-10s\n", "Symbol", "Qty", "Price", "Avg Cost", "Unrealized");
            positions.values().forEach(p -> {
                String color = p.getUnrealizedPNL() >= 0 ? "\u001B[32m" : "\u001B[31m";
                terminal.writer().printf("%-10s | %-6.0f | $%-11.2f | $%-11.2f | %s$%-10.2f\u001B[0m\n",
                        p.getSymbol(), p.getQuantity(), p.getMarketPrice(), p.getAverageCost(), color, p.getUnrealizedPNL());
            });
        }
        terminal.writer().println("====================================\n");
        terminal.flush();
    }

    @ShellMethod(key = "orders", value = "View all open orders")
    public void viewOrders() {
        if (!ibkrClient.getClient().isConnected()) {
            terminal.writer().println("Error: TWS not connected.");
            terminal.flush();
            return;
        }

        List<OrderService.OpenOrderResult> orders = orderService.fetchOpenOrders();
        terminal.writer().println();
        terminal.writer().println("\u001B[1m=== OPEN ORDERS ===\u001B[0m");

        if (orders.isEmpty()) {
            terminal.writer().println("No open orders.");
            terminal.writer().println("===================\n");
            terminal.flush();
            return;
        }

        terminal.writer().printf("%-8s | %-8s | %-8s | %-6s | %-6s | %-8s | %-10s | %-12s | %-7s | %-8s%n",
                "OrderId", "Symbol", "Type", "Side", "Qty", "LmtPx", "Status", "Exchange", "SecType", "Account");
        orders.forEach(o -> terminal.writer().printf(
                "%-8d | %-8s | %-8s | %-6s | %-6s | %-8s | %-10s | %-12s | %-7s | %-8s%n",
                o.orderId(),
                dash(o.symbol()),
                dash(o.orderType()),
                dash(o.action()),
                dash(o.quantity()),
                formatPrice(o.lmtPrice()),
                dash(o.status()),
                dash(o.exchange()),
                dash(o.secType()),
                dash(o.account())
        ));
        terminal.writer().println("===================\n");
        terminal.flush();
    }

    @ShellMethod(key = "buy", value = "Place a buy order")
    public void buy(String symbol, int qty, @ShellOption(defaultValue = ShellOption.NULL) Double price) {
        OrderService.PlaceOrderResult result = orderService.placeOrder(symbol, qty, price, "BUY");
        terminal.writer().println(result.message());
        terminal.flush();
    }

    @ShellMethod(key = "sell", value = "Place a sell order")
    public void sell(String symbol, int qty, @ShellOption(defaultValue = ShellOption.NULL) Double price) {
        OrderService.PlaceOrderResult result = orderService.placeOrder(symbol, qty, price, "SELL");
        terminal.writer().println(result.message());
        terminal.flush();
    }

    @ShellMethod(key = "cancel", value = "Cancel an open order by id")
    public void cancel(int orderId) {
        OrderService.CancelOrderResult result = orderService.cancelOrder(orderId);
        terminal.writer().println(result.message());
        terminal.flush();
    }

    @ShellMethod(key = "market", value = "Subscribe (once) and show last price when available")
    public String price(String symbol) {
        if (!ibkrClient.getClient().isConnected()) {
            return "Error: TWS not connected.";
        }

        double p = marketDataService.getPrice(symbol);
        if (p == 0.0) {
            return "No last price yet for "
                    + symbol.toUpperCase()
                    + "; subscription requested—wait briefly for delayed data (typically ~15 min behind).";
        }
        return symbol.toUpperCase() + " Last Price: $" + p;
    }

    @ShellMethod(key = "hist", value = "Get OHLCV")
    public String getHistory(String symbol) {
        if (!ibkrClient.getClient().isConnected()) {
            return "Error: TWS not connected.";
        }

        historyService.fetch(symbol);
        return "Fetching last 5 days of history for " + symbol.toUpperCase() + "...";
    }

    private static String dash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private static String formatPrice(Double value) {
        if (value == null || value.isNaN() || value == 0.0) {
            return "-";
        }
        return String.format("%.4f", value);
    }
}