package net.protsenko.fundy.app.exchange.impl.bingx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BingxSpotInstrumentItem(
        String symbol,
        String minQty,
        String maxQty,
        String minNotional,
        String maxNotional,
        Integer status,
        String tickSize,
        String stepSize,
        Boolean apiStateSell,
        Boolean apiStateBuy,
        Long timeOnline,
        Long offTime,
        Long maintainTime,
        String displayName
) {
    // Helper method to extract base and quote from symbol like "BTC-USDT"
    public String getBaseCurrency() {
        if (symbol == null || symbol.length() < 3) return null;

        String s = symbol.toUpperCase();
        // Common quote currencies
        String[] quotes = {"USDT", "USDC", "USD", "BTC", "ETH", "BNB"};

        for (String quote : quotes) {
            if (s.endsWith("-" + quote)) {
                return symbol.substring(0, symbol.length() - quote.length() - 1);
            }
        }

        return null;
    }

    public String getQuoteCurrency() {
        if (symbol == null || symbol.length() < 3) return null;

        String s = symbol.toUpperCase();
        // Common quote currencies
        String[] quotes = {"USDT", "USDC", "USD", "BTC", "ETH", "BNB"};

        for (String quote : quotes) {
            if (s.endsWith("-" + quote)) {
                return quote;
            }
        }

        return "USDT"; // default
    }

    public String getState() {
        return status != null ? status.toString() : "0";
    }
}