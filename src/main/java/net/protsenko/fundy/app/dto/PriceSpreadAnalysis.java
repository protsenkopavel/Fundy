package net.protsenko.fundy.app.dto;

import java.math.BigDecimal;

public record PriceSpreadAnalysis(ExchangeArbitrageData minPriceEx,
                                  ExchangeArbitrageData maxPriceEx,
                                  BigDecimal spread) {}
