package net.protsenko.fundy.app.service;

import net.protsenko.fundy.app.domain.FundingSpread;
import net.protsenko.fundy.app.domain.PriceSpread;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ArbitrageCalculator {

    public List<PriceSpread> calculatePriceSpreads(Map<ExchangeType, TickerData> prices) {
        List<PriceSpread> spreads = new ArrayList<>();
        List<ExchangeType> exchanges = new ArrayList<>(prices.keySet());

        for (int i = 0; i < exchanges.size(); i++) {
            for (int j = i + 1; j < exchanges.size(); j++) {
                ExchangeType ex1 = exchanges.get(i);
                ExchangeType ex2 = exchanges.get(j);

                BigDecimal price1 = prices.get(ex1).lastPrice();
                BigDecimal price2 = prices.get(ex2).lastPrice();

                if (price1 != null && price2 != null && price1.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal spread = price1.subtract(price2).divide(price1, 6, RoundingMode.HALF_UP);
                    spreads.add(new PriceSpread(ex1, ex2, spread));
                }
            }
        }

        return spreads;
    }

    public List<FundingSpread> calculateFundingSpreads(Map<ExchangeType, FundingRateData> fundingRates) {
        if (fundingRates == null || fundingRates.size() < 2) return new ArrayList<>();

        List<FundingSpread> spreads = new ArrayList<>();
        List<ExchangeType> exchanges = new ArrayList<>(fundingRates.keySet());

        for (int i = 0; i < exchanges.size(); i++) {
            for (int j = i + 1; j < exchanges.size(); j++) {
                ExchangeType ex1 = exchanges.get(i);
                ExchangeType ex2 = exchanges.get(j);

                BigDecimal rate1 = fundingRates.get(ex1).fundingRate();
                BigDecimal rate2 = fundingRates.get(ex2).fundingRate();

                if (rate1 != null && rate2 != null) {
                    BigDecimal spread = rate2.subtract(rate1);
                    spreads.add(new FundingSpread(ex1, ex2, spread));
                }
            }
        }

        return spreads;
    }

    public BigDecimal calculatePriceSpreadForDirection(
            ExchangeType longEx, ExchangeType shortEx, PriceSpread priceSpread) {

        ExchangeType higherPriceEx, lowerPriceEx;
        if (priceSpread.spread().compareTo(BigDecimal.ZERO) > 0) {
            higherPriceEx = priceSpread.ex1();
            lowerPriceEx = priceSpread.ex2();
        } else {
            higherPriceEx = priceSpread.ex2();
            lowerPriceEx = priceSpread.ex1();
        }

        if (shortEx.equals(higherPriceEx) && longEx.equals(lowerPriceEx)) {
            return priceSpread.spread().abs();
        }

        return priceSpread.spread().abs().negate();
    }

    public BigDecimal calculateFundingSpreadForDirection(
            ExchangeType longEx, ExchangeType shortEx, FundingSpread fundingSpread) {

        BigDecimal rate1, rate2;

        if (longEx.equals(fundingSpread.ex1())) {
            rate1 = BigDecimal.ZERO;
            rate2 = fundingSpread.spread();
        } else {
            rate1 = fundingSpread.spread().negate();
            rate2 = BigDecimal.ZERO;
        }

        BigDecimal longFundingRate = longEx.equals(fundingSpread.ex1()) ? rate1 : rate2;
        BigDecimal shortFundingRate = shortEx.equals(fundingSpread.ex1()) ? rate1 : rate2;

        BigDecimal longFundingPayment, shortFundingPayment;

        if (longFundingRate.compareTo(BigDecimal.ZERO) > 0) {
            longFundingPayment = longFundingRate.negate();
        } else {
            longFundingPayment = longFundingRate.negate();
        }

        if (shortFundingRate.compareTo(BigDecimal.ZERO) > 0) {
            shortFundingPayment = shortFundingRate;
        } else {
            shortFundingPayment = shortFundingRate;
        }

        return longFundingPayment.add(shortFundingPayment);
    }

    public boolean isSameExchangePair(PriceSpread priceSpread, FundingSpread fundingSpread) {
        return (priceSpread.ex1().equals(fundingSpread.ex1()) && priceSpread.ex2().equals(fundingSpread.ex2())) ||
                (priceSpread.ex1().equals(fundingSpread.ex2()) && priceSpread.ex2().equals(fundingSpread.ex1()));
    }

    public BigDecimal calculateCombinedSpread(net.protsenko.fundy.app.dto.rs.ArbitrageData data) {
        return data.priceSpread().abs().add(data.fundingSpread().abs());
    }
}