package net.protsenko.fundy.app.exchange;

public interface ExchangeClient extends
        BaseExchangeClient,
        SpotExchangeOperations,
        FuturesExchangeOperations,
        FundingExchangeOperations {
}
