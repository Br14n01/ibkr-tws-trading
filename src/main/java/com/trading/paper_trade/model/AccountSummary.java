package com.trading.paper_trade.model;

import java.util.concurrent.ConcurrentHashMap;

public class AccountSummary {
    private final String accountId;
    // Map of Tag (e.g., NetLiquidation) to Value (e.g., 100000.00)
    private final ConcurrentHashMap<String, String> metrics = new ConcurrentHashMap<>();

    public AccountSummary(String accountId) {
        this.accountId = accountId;
    }

    public void updateMetric(String tag, String value) {
        metrics.put(tag, value);
    }

    public String getMetric(String tag) {
        return metrics.getOrDefault(tag, "0.00");
    }

    public String getAccountId() { return accountId; }
}