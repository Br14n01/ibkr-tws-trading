package com.trading.paper_trade.shell;

import com.trading.paper_trade.integration.ibkr.IBKRClient;
import com.trading.paper_trade.model.Position;
import com.trading.paper_trade.model.AccountSummary;
import com.trading.paper_trade.portfolio.PortfolioService;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.Decimal;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import org.jline.terminal.Terminal;

import java.util.Map;

@ShellComponent
public class TradingShell {

    private final IBKRClient ibkrClient;
    private final Terminal terminal;
    private final PortfolioService portfolioService;

    public TradingShell(Terminal terminal,
                        PortfolioService portfolioService,
                        IBKRClient ibkrClient) {
        this.terminal = terminal;
        this.portfolioService = portfolioService;
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

    @ShellMethod(key = "disconnect", value = "Disconnect from IBKR TWS")
    public String disconnect() {
        ibkrClient.disconnect();
        return "Disconnected from TWS.";
    }

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
}