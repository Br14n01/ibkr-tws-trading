package com.trading.paper_trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the IBKR TWS / Gateway socket, bound from {@code ibkr.*}.
 * Replaces the previously hardcoded {@code eConnect("127.0.0.1", 7497, 1)} call.
 */
@ConfigurationProperties(prefix = "ibkr")
public record IbkrProperties(String host, int port, int clientId) {

    public IbkrProperties {
        if (host == null || host.isBlank()) {
            host = "127.0.0.1";
        }
        if (port <= 0) {
            port = 7497;
        }
        if (clientId < 0) {
            clientId = 1;
        }
    }
}
