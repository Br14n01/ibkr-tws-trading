package com.trading.paper_trade.order;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.trading.paper_trade.integration.ibkr.IBKRClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class OrderService {
    private static final long OPEN_ORDERS_TIMEOUT_MS = 10_000;

    private final IBKRClient ibkrClient;
    private final Object openOrdersLock = new Object();
    private final List<OpenOrderResult> openOrdersBuffer = new ArrayList<>();
    private volatile CountDownLatch openOrdersLatch;

    /** Serializes reqAllOpenOrders: its callbacks carry no request id, so only one may be in flight. */
    private final ReentrantLock openOrdersRequestLock = new ReentrantLock();

    /** Latest TWS status per order id, updated asynchronously via {@link #onOrderStatus}. */
    private final Map<Integer, String> lastStatusByOrderId = new ConcurrentHashMap<>();

    public OrderService(@Lazy IBKRClient ibkrClient){
        this.ibkrClient = ibkrClient;
    }

    public record PlaceOrderResult(
            String symbol,
            int quantity,
            Double price,
            String action,
            int orderId,
            boolean success,
            String message
    ) {}

    public record OpenOrderResult(
            int orderId,
            long permId,
            String status,
            String action,
            String orderType,
            String quantity,
            Double lmtPrice,
            String symbol,
            String secType,
            String exchange,
            String currency,
            String account
    ) {}

    public PlaceOrderResult placeOrder(String symbol, int qty, Double price, String action) {
        // Validate here too: the shell calls this directly and bypasses the
        // controller's request validation, and we must never forward a bad order to IBKR.
        if (symbol == null || symbol.isBlank()) {
            return new PlaceOrderResult(null, qty, price, action, -1, false, "Error: symbol is required.");
        }
        if (qty <= 0) {
            return new PlaceOrderResult(symbol, qty, price, action, -1, false, "Error: quantity must be a positive whole number.");
        }
        if (price != null && price <= 0) {
            return new PlaceOrderResult(symbol, qty, price, action, -1, false, "Error: price must be positive when provided.");
        }

        if (!ibkrClient.getClient().isConnected()) {
            return new PlaceOrderResult(null, qty, price, action, -1, false, "Error: TWS is not connected!");
        }

        String normalizedSymbol = symbol.toUpperCase();
        Contract contract = new Contract();
        contract.symbol(normalizedSymbol);
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
        if (id < 0) {
            return new PlaceOrderResult(normalizedSymbol, qty, price, action, -1, false,
                    "Error: no valid order id from TWS yet. Wait for the connection to finish initializing.");
        }

        lastStatusByOrderId.put(id, "PendingSubmit");
        ibkrClient.getClient().placeOrder(id, contract, order);

        // NOTE: placeOrder is fire-and-forget — this confirms the order was SUBMITTED, not filled
        // or accepted. The real outcome arrives asynchronously via orderStatus()/error().
        String message = "Order " + id + " submitted (pending confirmation): " + action + " " + qty + " "
                + normalizedSymbol + (price != null ? " @ $" + price : " (Market)");
        return new PlaceOrderResult(normalizedSymbol, qty, price, action, id, true, message);
    }

    /** Called from the IB listener's orderStatus callback. Records the latest status per order. */
    public void onOrderStatus(int orderId, String status) {
        if (status != null && !status.isBlank()) {
            lastStatusByOrderId.put(orderId, status);
        }
    }

    /** Last known status for an order id, or {@code "UNKNOWN"} if none has been received. */
    public String getLastStatus(int orderId) {
        return lastStatusByOrderId.getOrDefault(orderId, "UNKNOWN");
    }

    public List<OpenOrderResult> fetchOpenOrders() {
        if (!ibkrClient.getClient().isConnected()) {
            return List.of();
        }

        // The IB openOrder/openOrderEnd callbacks carry no request id, so two concurrent
        // reqAllOpenOrders calls would interleave into the same buffer. Serialize them so
        // each caller gets a clean, consistent snapshot.
        openOrdersRequestLock.lock();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            synchronized (openOrdersLock) {
                openOrdersBuffer.clear();
                openOrdersLatch = latch;
            }

            ibkrClient.getClient().reqAllOpenOrders();

            try {
                latch.await(OPEN_ORDERS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            synchronized (openOrdersLock) {
                openOrdersLatch = null;
                return List.copyOf(openOrdersBuffer);
            }
        } finally {
            openOrdersRequestLock.unlock();
        }
    }

    public void onOpenOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        if (openOrdersLatch == null) {
            return;
        }

        OpenOrderResult row = new OpenOrderResult(
                orderId,
                order != null ? order.permId() : 0L,
                orderState != null ? orderState.getStatus() : "",
                order != null ? order.getAction() : "",
                order != null ? order.getOrderType() : "",
                toQuantity(order != null ? order.totalQuantity() : null),
                order != null ? order.lmtPrice() : null,
                contract != null ? contract.symbol() : "",
                contract != null ? contract.getSecType() : "",
                contract != null ? contract.exchange() : "",
                contract != null ? contract.currency() : "",
                order != null ? order.account() : ""
        );

        synchronized (openOrdersLock) {
            openOrdersBuffer.add(row);
        }
    }

    public void onOpenOrderEnd() {
        CountDownLatch latch = openOrdersLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    private static String toQuantity(Decimal decimal) {
        if (decimal == null) {
            return "";
        }
        try {
            return decimal.value().stripTrailingZeros().toPlainString();
        } catch (RuntimeException ignored) {
            return decimal.toString();
        }
    }

}
