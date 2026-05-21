package com.trading.paper_trade.controller;

import com.trading.paper_trade.integration.ibkr.IBKRClient;
import com.trading.paper_trade.order.OrderService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TradeController {

    private final IBKRClient ibkrClient;
    private final OrderService orderService;

    public TradeController(IBKRClient ibkrClient, OrderService orderService) {
        this.ibkrClient = ibkrClient;
        this.orderService = orderService;
    }

    @GetMapping("/trade/buy")
    public String buyStock(
            @RequestParam String symbol,
            @RequestParam int qty,
            @RequestParam(required = false) Double price // Added price parameter
    ) {
        return orderService.placeOrder(symbol, qty, price, "BUY").message();
    }

    @GetMapping("/trade/sell")
    public String sellStock(
            @RequestParam String symbol,
            @RequestParam int qty,
            @RequestParam(required = false) Double price // Added price parameter
    ) {
        return orderService.placeOrder(symbol, qty, price, "SELL").message();
    }

    /**
     * Returns all open orders (async IB snapshot via {@link OrderService#fetchOpenOrders()}).
     */
    @GetMapping("/trade/orders")
    public ResponseEntity<List<OrderService.OpenOrderResult>> getOpenOrders() {
        if (!ibkrClient.getClient().isConnected()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(orderService.fetchOpenOrders());
    }
}