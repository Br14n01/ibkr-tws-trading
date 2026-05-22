package com.trading.paper_trade.controller;

import com.trading.paper_trade.integration.ibkr.IBKRClient;
import com.trading.paper_trade.order.OrderService;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    // Placing an order changes state, so it must be POST (never GET): GET requests
    // can be triggered by prefetch, crawlers, history, or CSRF, and would leak the
    // order params into access logs.
    @PostMapping("/trade/buy")
    public String buyStock(
            @RequestParam @NotBlank String symbol,
            @RequestParam @Min(1) int qty,
            @RequestParam(required = false) @Positive Double price
    ) {
        return orderService.placeOrder(symbol, qty, price, "BUY").message();
    }

    @PostMapping("/trade/sell")
    public String sellStock(
            @RequestParam @NotBlank String symbol,
            @RequestParam @Min(1) int qty,
            @RequestParam(required = false) @Positive Double price
    ) {
        return orderService.placeOrder(symbol, qty, price, "SELL").message();
    }

    @PostMapping("/trade/cancel")
    public String cancelOrder(@RequestParam @Min(0) int orderId) {
        return orderService.cancelOrder(orderId).message();
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