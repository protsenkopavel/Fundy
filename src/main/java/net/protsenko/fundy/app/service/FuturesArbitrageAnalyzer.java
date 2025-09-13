package net.protsenko.fundy.app.service;

import net.protsenko.fundy.app.domain.*;
import net.protsenko.fundy.app.dto.rq.FuturesArbitrageRequest;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class FuturesArbitrageAnalyzer {

    private final FuturesArbitrageCalculator calculator;

    public FuturesArbitrageAnalyzer(FuturesArbitrageCalculator calculator) {
        this.calculator = calculator;
    }

    public ArbitrageOpportunity findBestArbitrageOpportunity(
            List<PriceSpread> priceSpreads,
            List<FundingSpread> fundingSpreads,
            FuturesArbitrageRequest req) {

        return findCombinedArbitrage(priceSpreads, fundingSpreads, req);
    }

    private ArbitrageOpportunity findCombinedArbitrage(
            List<PriceSpread> priceSpreads,
            List<FundingSpread> fundingSpreads,
            FuturesArbitrageRequest req) {

        ArbitrageOpportunity best = null;
        BigDecimal bestScore = BigDecimal.ZERO;

        for (PriceSpread priceSpread : priceSpreads) {
            for (FundingSpread fundingSpread : fundingSpreads) {
                if (!calculator.isSameExchangePair(priceSpread, fundingSpread)) continue;

                BigDecimal priceArbSpread = priceSpread.spread().abs();
                BigDecimal fundingArbSpread = fundingSpread.spread().abs();

                if (priceArbSpread.compareTo(req.minPr()) < 0 ||
                        fundingArbSpread.compareTo(req.minFr()) < 0 ||
                        priceArbSpread.compareTo(req.maxPr()) > 0 ||
                        fundingArbSpread.compareTo(req.maxFr()) > 0) continue;

                ArbitrageCandidate direction1 = evaluateDirection(
                        priceSpread.ex1(), priceSpread.ex2(), priceSpread, fundingSpread);
                ArbitrageCandidate direction2 = evaluateDirection(
                        priceSpread.ex2(), priceSpread.ex1(), priceSpread, fundingSpread);

                ArbitrageCandidate betterDirection = direction1.score().compareTo(direction2.score()) > 0 ?
                        direction1 : direction2;

                if (betterDirection.score().compareTo(bestScore) > 0) {
                    best = new ArbitrageOpportunity(
                            betterDirection.longEx(), betterDirection.shortEx(),
                            betterDirection.priceSpread(), betterDirection.fundingSpread(),
                            ArbitrageType.COMBINED
                    );
                    bestScore = betterDirection.score();
                }
            }
        }

        return best;
    }

    private ArbitrageCandidate evaluateDirection(
            ExchangeType longEx, ExchangeType shortEx,
            PriceSpread priceSpread, FundingSpread fundingSpread) {

        BigDecimal priceArbSpread = calculator.calculatePriceSpreadForDirection(longEx, shortEx, priceSpread);
        BigDecimal fundingArbSpread = calculator.calculateFundingSpreadForDirection(longEx, shortEx, fundingSpread);

        BigDecimal annualizedFunding = fundingArbSpread.multiply(BigDecimal.valueOf(8760));
        BigDecimal score = priceArbSpread.add(annualizedFunding);

        return new ArbitrageCandidate(
                longEx, shortEx, priceArbSpread, fundingArbSpread, score, ArbitrageType.COMBINED
        );
    }
}