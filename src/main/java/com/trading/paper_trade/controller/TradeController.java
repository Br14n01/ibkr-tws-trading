package com.trading.paper_trade.controller;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.Decimal;
import com.trading.paper_trade.integration.ibkr.IBKRClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeController {

    private final IBKRClient ibkrClient;

    public TradeController(IBKRClient ibkrClient) {
        this.ibkrClient = ibkrClient;
    }

    private String placeOrder(String symbol, int qty, Double price, String action) {
        if (!ibkrClient.getClient().isConnected()) {
            return "Error: TWS is not connected!";
        }

        Contract contract = new Contract();
        contract.symbol(symbol.toUpperCase());
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");

        Order order = new Order();
        order.action(action); // Set to "BUY" or "SELL"
        order.totalQuantity(Decimal.get(qty));

        if (price != null) {
            order.orderType("LMT");
            order.lmtPrice(price);
        } else {
            order.orderType("MKT");
        }

        int id = ibkrClient.getNextOrderId();
        ibkrClient.getClient().placeOrder(id, contract, order);

        String priceType = (price != null ? " @ $" + price : " (Market)");
        return "Order " + id + " sent: " + action + " " + qty + " " + symbol + priceType;
    }

    @GetMapping("/trade/buy")
    public String buyStock(
            @RequestParam String symbol,
            @RequestParam int qty,
            @RequestParam(required = false) Double price // Added price parameter
    ) {
        return placeOrder(symbol, qty, price, "BUY");
    }

    @GetMapping("/trade/sell")
    public String sellStock(
            @RequestParam String symbol,
            @RequestParam int qty,
            @RequestParam(required = false) Double price // Added price parameter
    ) {
        return placeOrder(symbol, qty, price, "SELL");
    }
}