package com.trading.paper_trade.integration.ibkr;

import com.trading.paper_trade.model.AccountSummary;
import com.trading.paper_trade.model.Position;
import com.trading.paper_trade.portfolio.PortfolioService;
import com.trading.paper_trade.market.MarketData;
import com.trading.paper_trade.market.HistoryService;
import com.trading.paper_trade.order.OrderService;
import com.trading.paper_trade.strategy.Indicators;

import com.ib.client.*;
import com.ib.client.protobuf.*;

import org.jline.reader.LineReader;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.jline.terminal.Terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
// ... import all other required IB classes

@Component
public class IBKRListener implements EWrapper {
    private final EJavaSignal signal = new EJavaSignal();
    private final IBKRClient ibkrClient;

    private final Terminal terminal;
    private final LineReader lineReader;

    private final PortfolioService portfolioService;
    private final MarketData marketDataService;
    private final HistoryService historyService;
    private final OrderService orderService;

    /** Close prices per historical request ID (append order = oldest → newest). */
    private final Map<Integer, List<Double>> historicalClosesByReqId = new ConcurrentHashMap<>();


    public IBKRListener(@Lazy IBKRClient ibkrClient,
                        @Lazy Terminal terminal,
                        @Lazy LineReader lineReader,
                        PortfolioService portfolioService,
                        MarketData marketDataService,
                        @Lazy HistoryService historyService,
                        @Lazy OrderService orderService){
        this.ibkrClient = ibkrClient;
        this.terminal = terminal;
        this.lineReader = lineReader;
        this.portfolioService = portfolioService;
        this.marketDataService = marketDataService;
        this.historyService = historyService;
        this.orderService = orderService;
    }

    public EReaderSignal getSignal() {
        return signal;
    }

    // IMPORTANT: This is the method the Client calls to set the ID
    @Override
    public void nextValidId(int orderId) {
        // nextValidId is TWS' "connection is ready" signal. Seed the order id, then kick off
        // portfolio subscriptions here (rather than synchronously during connect, before the
        // reader thread is processing messages). subscribeToPortfolioData() is idempotent.
        System.out.println("Next Valid ID: " + orderId);
        ibkrClient.setNextOrderId(orderId);
        ibkrClient.subscribeToPortfolioData();
    }

    // You must implement all EWrapper methods (even if empty)
    // Use Alt+Insert -> Implement Methods in IntelliJ to fill the rest.

    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttrib attribs) {
        String symbol = marketDataService.getSymbolById(tickerId);
        if (symbol == null) return;

        // Field 68 is "Delayed Last", Field 4 is "Live Last"
        if (field == 68 || field == 4) {
            marketDataService.updatePrice(symbol, price);
        }
    }

    @Override
    public void tickSize(int i, int i1, Decimal decimal) {

    }

    @Override
    public void tickOptionComputation(int i, int i1, int i2, double v, double v1, double v2, double v3, double v4, double v5, double v6, double v7) {

    }

    @Override
    public void tickGeneric(int i, int i1, double v) {

    }

    @Override
    public void tickString(int i, int i1, String s) {

    }

    @Override
    public void tickEFP(int i, int i1, double v, String s, double v1, int i2, String s1, double v2, double v3) {

    }

    @Override
    public void orderStatus(int orderId, String status, Decimal filled, Decimal remaining,
                            double avgFillPrice, long permId, int parentId, double lastFillPrice,
                            int clientId, String whyHeld, double mktCapPrice) {
        String filledText = decimalText(filled);
        String remainingText = decimalText(remaining);

        // TWS re-emits the same orderStatus on every internal refresh; only print real changes.
        if (orderService.onOrderStatus(orderId, status, filledText, remainingText)) {
            printAbovePrompt(String.format(
                    "\033[36m[Order %d] %s — filled %s, remaining %s%s\033[0m",
                    orderId,
                    status,
                    filledText,
                    remainingText,
                    avgFillPrice > 0 ? String.format(" @ avg $%.2f", avgFillPrice) : ""));
        }
    }

    @Override
    public void openOrder(int i, Contract contract, Order order, OrderState orderState) {
        orderService.onOpenOrder(i, contract, order, orderState);
    }

    @Override
    public void openOrderEnd() {
        orderService.onOpenOrderEnd();
    }

    @Override
    public void updateAccountValue(String s, String s1, String s2, String s3) {

    }

    @Override
    public void updatePortfolio(Contract contract, Decimal position, double marketPrice,
                                double marketValue, double averageCost, double unrealizedPNL,
                                double realizedPNL, String accountName) {
        Position pos = new Position();
        pos.setSymbol(contract.symbol());
        pos.setSecType(contract.getSecType());
        pos.setQuantity(position.value().doubleValue());
        pos.setMarketPrice(marketPrice);
        pos.setAverageCost(averageCost);
        pos.setUnrealizedPNL(unrealizedPNL);
        portfolioService.updatePosition(pos);
    }

    @Override
    public void updateAccountTime(String s) {

    }

    @Override
    public void accountDownloadEnd(String s) {

    }

    @Override
    public void contractDetails(int i, ContractDetails contractDetails) {

    }

    @Override
    public void bondContractDetails(int i, ContractDetails contractDetails) {

    }

    @Override
    public void contractDetailsEnd(int i) {

    }

    @Override
    public void execDetails(int i, Contract contract, Execution execution) {

    }

    @Override
    public void execDetailsEnd(int i) {

    }

    @Override
    public void updateMktDepth(int i, int i1, int i2, int i3, double v, Decimal decimal) {

    }

    @Override
    public void updateMktDepthL2(int i, int i1, String s, int i2, int i3, double v, Decimal decimal, boolean b) {

    }

    @Override
    public void updateNewsBulletin(int i, int i1, String s, String s1) {

    }

    @Override
    public void managedAccounts(String s) {

    }

    @Override
    public void receiveFA(int i, String s) {

    }
    /**
     * Prints a line above the interactive shell prompt without leaving the prompt garbled.
     * Safe to call from IB callback threads and before the line reader is actively reading.
     */
    private void printAbovePrompt(String ansiLine) {
        try {
            terminal.writer().print("\r\033[2K");   // clear the current prompt line
            terminal.writer().println(ansiLine);
            try {
                lineReader.callWidget(LineReader.REDRAW_LINE);
                lineReader.callWidget(LineReader.REDISPLAY);
            } catch (RuntimeException notReading) {
                // line reader isn't in an active read (e.g. during startup) — line is already printed
            }
            terminal.flush();
        } catch (Exception ex) {
            System.err.println(ansiLine);
        }
    }

    /** Renders an IB {@link Decimal} as a plain number, tolerant of nulls / unset values. */
    private static String decimalText(Decimal d) {
        if (d == null) {
            return "0";
        }
        try {
            return d.value().stripTrailingZeros().toPlainString();
        } catch (RuntimeException e) {
            return d.toString();
        }
    }

    /** Formats numeric indicator Optional for stdout (value or "-" if undefined). */
    private static String formatIndicator(Optional<Double> value) {
        return value.map(d -> String.format("%.4f", d)).orElse("-");
    }

    private static void printHistoricalIndicatorsSnapshot(int reqId, List<Double> closes) {
        System.out.printf(
                "[Indicators req=%d n=%d] SMA5=%s SMA10=%s SMA20=%s | EMA5=%s EMA10=%s EMA20=%s | RSI14=%s%n",
                reqId,
                closes.size(),
                formatIndicator(Indicators.sma5(closes)),
                formatIndicator(Indicators.sma10(closes)),
                formatIndicator(Indicators.sma20(closes)),
                formatIndicator(Indicators.ema5(closes)),
                formatIndicator(Indicators.ema10(closes)),
                formatIndicator(Indicators.ema20(closes)),
                formatIndicator(Indicators.rsi14(closes))
        );
    }
    
    @Override
    public void historicalData(int reqId, Bar bar) {
        System.out.println("HistoricalData:  " + EWrapperMsgGenerator.historicalData(reqId, bar.time(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume(), bar.count(), bar.wap()));

        List<Double> closes = historicalClosesByReqId.computeIfAbsent(reqId, ignored -> new ArrayList<>());
        closes.add(bar.close());
    }

    @Override
    public void scannerParameters(String s) {

    }

    @Override
    public void scannerData(int i, int i1, ContractDetails contractDetails, String s, String s1, String s2, String s3) {

    }

    @Override
    public void scannerDataEnd(int i) {

    }

    @Override
    public void realtimeBar(int i, long l, double v, double v1, double v2, double v3, Decimal decimal, Decimal decimal1, int i1) {

    }

    @Override
    public void currentTime(long l) {

    }

    @Override
    public void fundamentalData(int i, String s) {

    }

    @Override
    public void deltaNeutralValidation(int i, DeltaNeutralContract deltaNeutralContract) {

    }

    @Override
    public void tickSnapshotEnd(int i) {

    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
        // This confirms TWS switched to Delayed mode for your request
        String typeStr = (marketDataType == 3) ? "DELAYED" : (marketDataType ==  1) ? "LIVE" : "OTHER";
        System.out.println("\r\n[IBKR] Market Data Type for ID " + reqId + ": " + typeStr);
    }

    @Override
    public void commissionAndFeesReport(CommissionAndFeesReport commissionAndFeesReport) {

    }

    @Override
    public void position(String s, Contract contract, Decimal decimal, double v) {

    }

    @Override
    public void positionEnd() {

    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
        portfolioService.updateAccountSummary(account, tag, value);
    }

    @Override
    public void accountSummaryEnd(int i) {

    }

    @Override
    public void verifyMessageAPI(String s) {

    }

    @Override
    public void verifyCompleted(boolean b, String s) {

    }

    @Override
    public void verifyAndAuthMessageAPI(String s, String s1) {

    }

    @Override
    public void verifyAndAuthCompleted(boolean b, String s) {

    }

    @Override
    public void displayGroupList(int i, String s) {

    }

    @Override
    public void displayGroupUpdated(int i, String s) {

    }

    @Override
    public void error(Exception e) {
        // Previously swallowed silently. Surface low-level transport/parse exceptions.
        printAbovePrompt("\033[31m[IBKR] Connection error: " + e.getMessage() + "\033[0m");
    }

    @Override
    public void error(String s) {
        // Previously swallowed silently.
        printAbovePrompt("\033[31m[IBKR] " + s + "\033[0m");
    }

    @Override
    public void error(int id, long l, int errorCode, String errorMsg, String advancedOrderRejectionJson) {
        // 2104, 2106, 2158 are just "Data farm connection is OK" status messages.
        if (errorCode == 2104 || errorCode == 2106 || errorCode == 2158) return;

        printAbovePrompt("\033[33m[IBKR] (" + errorCode + ") " + errorMsg
                + (id >= 0 ? " [id=" + id + "]" : "") + "\033[0m");
    }

    @Override
    public void connectionClosed() {
        // Previously empty, so a dropped connection left stale subscription state behind.
        ibkrClient.onConnectionClosed();
        printAbovePrompt("\033[31m[IBKR] Connection to TWS closed.\033[0m");
    }

    @Override
    public void connectAck() {

    }

    @Override
    public void positionMulti(int i, String s, String s1, Contract contract, Decimal decimal, double v) {

    }

    @Override
    public void positionMultiEnd(int i) {

    }

    @Override
    public void accountUpdateMulti(int i, String s, String s1, String s2, String s3, String s4) {

    }

    @Override
    public void accountUpdateMultiEnd(int i) {

    }

    @Override
    public void securityDefinitionOptionalParameter(int i, String s, int i1, String s1, String s2, Set<String> set, Set<Double> set1) {

    }

    @Override
    public void securityDefinitionOptionalParameterEnd(int i) {

    }

    @Override
    public void softDollarTiers(int i, SoftDollarTier[] softDollarTiers) {

    }

    @Override
    public void familyCodes(FamilyCode[] familyCodes) {

    }

    @Override
    public void symbolSamples(int i, ContractDescription[] contractDescriptions) {

    }

    @Override
    public void historicalDataEnd(int reqId, String start, String end) {
        List<Double> closes = historicalClosesByReqId.remove(reqId);
        if (closes != null && !closes.isEmpty()) {
            printHistoricalIndicatorsSnapshot(reqId, closes);
        }
    }

    @Override
    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {

    }

    @Override
    public void tickNews(int i, long l, String s, String s1, String s2, String s3) {

    }

    @Override
    public void smartComponents(int i, Map<Integer, Map.Entry<String, Character>> map) {

    }

    @Override
    public void tickReqParams(int i, double v, String s, int i1) {

    }

    @Override
    public void newsProviders(NewsProvider[] newsProviders) {

    }

    @Override
    public void newsArticle(int i, int i1, String s) {

    }

    @Override
    public void historicalNews(int i, String s, String s1, String s2, String s3) {

    }

    @Override
    public void historicalNewsEnd(int i, boolean b) {

    }

    @Override
    public void headTimestamp(int i, String s) {

    }

    @Override
    public void histogramData(int i, List<HistogramEntry> list) {

    }

    @Override
    public void historicalDataUpdate(int i, Bar bar) {

    }

    @Override
    public void rerouteMktDataReq(int i, int i1, String s) {

    }

    @Override
    public void rerouteMktDepthReq(int i, int i1, String s) {

    }

    @Override
    public void marketRule(int i, PriceIncrement[] priceIncrements) {

    }

    @Override
    public void pnl(int i, double v, double v1, double v2) {

    }

    @Override
    public void pnlSingle(int i, Decimal decimal, double v, double v1, double v2, double v3) {

    }

    @Override
    public void historicalTicks(int i, List<HistoricalTick> list, boolean b) {

    }

    @Override
    public void historicalTicksBidAsk(int i, List<HistoricalTickBidAsk> list, boolean b) {

    }

    @Override
    public void historicalTicksLast(int i, List<HistoricalTickLast> list, boolean b) {

    }

    @Override
    public void tickByTickAllLast(int i, int i1, long l, double v, Decimal decimal, TickAttribLast tickAttribLast, String s, String s1) {

    }

    @Override
    public void tickByTickBidAsk(int i, long l, double v, double v1, Decimal decimal, Decimal decimal1, TickAttribBidAsk tickAttribBidAsk) {

    }

    @Override
    public void tickByTickMidPoint(int i, long l, double v) {

    }

    @Override
    public void orderBound(long l, int i, int i1) {

    }

    @Override
    public void completedOrder(Contract contract, Order order, OrderState orderState) {

    }

    @Override
    public void completedOrdersEnd() {

    }

    @Override
    public void replaceFAEnd(int i, String s) {

    }

    @Override
    public void wshMetaData(int i, String s) {

    }

    @Override
    public void wshEventData(int i, String s) {

    }

    @Override
    public void historicalSchedule(int i, String s, String s1, String s2, List<HistoricalSession> list) {

    }

    @Override
    public void userInfo(int i, String s) {

    }

    @Override
    public void currentTimeInMillis(long l) {

    }

    @Override
    public void orderStatusProtoBuf(OrderStatusProto.OrderStatus orderStatus) {

    }

    @Override
    public void openOrderProtoBuf(OpenOrderProto.OpenOrder openOrder) {

    }

    @Override
    public void openOrdersEndProtoBuf(OpenOrdersEndProto.OpenOrdersEnd openOrdersEnd) {

    }

    @Override
    public void errorProtoBuf(ErrorMessageProto.ErrorMessage errorMessage) {

    }

    @Override
    public void execDetailsProtoBuf(ExecutionDetailsProto.ExecutionDetails executionDetails) {

    }

    @Override
    public void execDetailsEndProtoBuf(ExecutionDetailsEndProto.ExecutionDetailsEnd executionDetailsEnd) {

    }
}