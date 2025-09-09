package net.protsenko.fundy.app.exchange.impl.htx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record HtxSpotTickerItem(
        String symbol,
        String open,
        String high,
        String low,
        String close,
        String vol,
        String amount,
        String count,
        String bid,
        String bidSize,
        String ask,
        String askSize
) {
}