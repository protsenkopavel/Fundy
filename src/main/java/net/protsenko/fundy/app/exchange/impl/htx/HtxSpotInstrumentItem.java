package net.protsenko.fundy.app.exchange.impl.htx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record HtxSpotInstrumentItem(
        String symbol,
        String baseCurrency,
        String quoteCurrency,
        String state,
        String symbolPartition
) {
    // Helper method to extract base and quote from symbol like "btcusdt"
    public String getBaseCurrency() {
        if (baseCurrency != null) return baseCurrency;
        if (symbol == null || symbol.length() < 3) return null;

        String s = symbol.toLowerCase();
        // Common quote currencies
        String[] quotes = {"usdt", "usdc", "usd", "btc", "eth", "bnb"};

        for (String quote : quotes) {
            if (s.endsWith(quote)) {
                return symbol.substring(0, symbol.length() - quote.length()).toUpperCase();
            }
        }

        return null;
    }

    public String getQuoteCurrency() {
        if (quoteCurrency != null) return quoteCurrency;
        if (symbol == null || symbol.length() < 3) return null;

        String s = symbol.toLowerCase();
        // Common quote currencies
        String[] quotes = {"usdt", "usdc", "usd", "btc", "eth", "bnb"};

        for (String quote : quotes) {
            if (s.endsWith(quote)) {
                return quote.toUpperCase();
            }
        }

        return "USDT"; // default
    }
}