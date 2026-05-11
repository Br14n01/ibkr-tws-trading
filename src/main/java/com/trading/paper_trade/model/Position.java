package com.trading.paper_trade.model;

public class Position {
    private String symbol;
    private String secType; // e.g., STK, OPT, CRYPTO
    private double quantity;
    private double marketPrice;
    private double marketValue;
    private double averageCost;
    private double unrealizedPNL;

    // --- CONSTRUCTORS ---
    public Position() {}

    // --- GETTERS AND SETTERS ---
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSecType() { return secType; }
    public void setSecType(String secType) { this.secType = secType; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getMarketPrice() { return marketPrice; }
    public void setMarketPrice(double marketPrice) { this.marketPrice = marketPrice; }

    public double getMarketValue() { return marketValue; }
    public void setMarketValue(double marketValue) { this.marketValue = marketValue; }

    public double getAverageCost() { return averageCost; }
    public void setAverageCost(double averageCost) { this.averageCost = averageCost; }

    public double getUnrealizedPNL() { return unrealizedPNL; }
    public void setUnrealizedPNL(double unrealizedPNL) { this.unrealizedPNL = unrealizedPNL; }
}

