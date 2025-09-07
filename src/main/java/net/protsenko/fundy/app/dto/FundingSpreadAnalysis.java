package net.protsenko.fundy.app.dto;

import java.math.BigDecimal;

public record FundingSpreadAnalysis(ExchangeArbitrageData minFundingEx,
                                    ExchangeArbitrageData maxFundingEx,
                                    BigDecimal spread) {}