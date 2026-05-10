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

    @GetMapping("/trade/buy")
    public String buyStock(
            @RequestParam String symbol,
            @RequestParam int qty,
            @RequestParam(required = false) Double price // Added price parameter
    ) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");

        Order order = new Order();
        order.action("BUY");
        order.totalQuantity(Decimal.get(qty));

        if (price != null) {
            // --- LIMIT ORDER ---
            order.orderType("LMT");
            order.lmtPrice(price);
        } else {
            // --- MARKET ORDER ---
            order.orderType("MKT");
        }

        int id = ibkrClient.getNextOrderId();
        ibkrClient.getClient().placeOrder(id, contract, order);

        String type = (price != null) ? "Limit at $" + price : "Market";
        return "Successfully sent " + type + " order for " + qty + " shares of " + symbol;
    }
}