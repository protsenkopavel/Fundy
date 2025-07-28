package net.protsenko.fundy.app.exchange.impl.kucoin;

import java.util.List;

public record KucoinContractsResponse(
        String code,
        List<KucoinContractItem> data
) {
}
