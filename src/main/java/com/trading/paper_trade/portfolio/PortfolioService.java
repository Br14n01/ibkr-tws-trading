package com.trading.paper_trade.portfolio;

import com.trading.paper_trade.model.AccountSummary;
import com.trading.paper_trade.model.Position;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class PortfolioService {
    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    private final Map<String, AccountSummary> accounts = new ConcurrentHashMap<>();

    public void updatePosition(Position pos) {
        String key = pos.getSymbol() + "_" + pos.getSecType();
        if (pos.getQuantity() == 0) positions.remove(key);
        else positions.put(key, pos);
    }

    public void updateAccountSummary(String accountId, String tag, String value) {
        accounts.computeIfAbsent(accountId, AccountSummary::new).updateMetric(tag, value);
    }

    public Map<String, Position> getPositions() { return positions; }
    public AccountSummary getSummary(String accountId) {
        return accounts.values().stream().findFirst().orElse(new AccountSummary("Unknown"));
    }
}