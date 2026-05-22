package com.trading.paper_trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the REST trading API, bound from {@code trade.api.*}.
 * Used by {@link SecurityConfig} to build the in-memory user.
 */
@ConfigurationProperties(prefix = "trade.api")
public record TradeApiProperties(String username, String password) {
}
