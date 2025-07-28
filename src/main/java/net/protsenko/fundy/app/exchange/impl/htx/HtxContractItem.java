package net.protsenko.fundy.app.exchange.impl.htx;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HtxContractItem(
        String symbol,
        @JsonProperty("contract_code")
        String contractCode,
        @JsonProperty("contract_size")
        double contractSize,
        @JsonProperty("price_tick")
        double priceTick,
        @JsonProperty("contract_status")
        int contractStatus,
        @JsonProperty("trade_partition")
        String tradePartition,
        @JsonProperty("business_type")
        String businessType,
        @JsonProperty("contract_type")
        String contractType
) {
}
