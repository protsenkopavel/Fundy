package net.protsenko.fundy.app.dto;

import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;

public record ExchangeArbitrageData(String canonicalKey,
                                     ExchangeType exchange,
                                     BigDecimal price,
                                     BigDecimal fundingRate,
                                     Long nextFundingTs,
                                     String link) {}
