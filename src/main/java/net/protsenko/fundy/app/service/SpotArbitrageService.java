package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.domain.CanonicalInstrument;
import net.protsenko.fundy.app.dto.rq.SpotArbitrageRequest;
import net.protsenko.fundy.app.dto.rs.DepositWithdrawStatus;
import net.protsenko.fundy.app.dto.rs.SpotArbitrageData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.WithdrawalDepositStatus;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.exchange.SpotExchangeClient;
import net.protsenko.fundy.app.utils.ExchangeLinkResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotArbitrageService {

    private final ExchangeClientFactory exchangeClientFactory;
    private final SpotService spotService;
    private final UniverseService universeService;

    public List<SpotArbitrageData> getSpotArbitrageOpportunities(SpotArbitrageRequest request) {
        Set<ExchangeType> exchanges = request.effectiveExchanges();
        BigDecimal minSpread = request.effectiveMinSpread();
        BigDecimal maxSpread = request.effectiveMaxSpread();
        BigDecimal minVolume = request.effectiveMinVolume();

        Map<String, Map<ExchangeType, TickerData>> priceData = spotService.collectSpotPriceData(exchanges);
        Map<String, Map<ExchangeType, String>> universe = universeService.spotUniverse(exchanges);

        return priceData.entrySet().parallelStream()
                .filter(entry -> {
                    Map<ExchangeType, TickerData> instrumentPrices = entry.getValue();
                    return instrumentPrices != null && instrumentPrices.size() >= 2;
                })
                .map(entry -> {
                    String canonicalKey = entry.getKey();
                    Map<ExchangeType, TickerData> instrumentPrices = entry.getValue();
                    Map<ExchangeType, String> nativeSymbols = universe.get(canonicalKey);
                    return analyzeSpotArbitrageOpportunity(canonicalKey, instrumentPrices, nativeSymbols, exchanges);
                })
                .filter(Objects::nonNull)
                .filter(opportunity -> opportunity.priceSpread().compareTo(minSpread) >= 0)
                .filter(opportunity -> opportunity.priceSpread().compareTo(maxSpread) <= 0)
                .filter(opportunity -> opportunity.buyVolume24h().compareTo(minVolume) >= 0)
                .filter(opportunity -> opportunity.sellVolume24h().compareTo(minVolume) >= 0)
                .sorted((a, b) -> b.priceSpread().compareTo(a.priceSpread()))
                .collect(Collectors.toList());
    }

    private SpotArbitrageData analyzeSpotArbitrageOpportunity(
            String canonicalKey,
            Map<ExchangeType, TickerData> prices,
            Map<ExchangeType, String> nativeSymbols,
            Set<ExchangeType> targetExchanges) {

        if (prices.size() < 2) return null;

        String coin = extractCoinFromCanonicalKey(canonicalKey);
        if (coin == null) return null;

        ExchangePrice buyOpportunity = findBestBuyOpportunity(coin, prices, targetExchanges);
        if (buyOpportunity == null) return null;

        ExchangePrice sellOpportunity = findBestSellOpportunity(coin, prices, targetExchanges);
        if (sellOpportunity == null) return null;

        if (buyOpportunity.exchange == sellOpportunity.exchange) return null;

        BigDecimal spread = calculatePriceSpread(buyOpportunity.price, sellOpportunity.price);

        Map<ExchangeType, String> links = new EnumMap<>(ExchangeType.class);
        String[] parts = canonicalKey.split("/");
        CanonicalInstrument instrument = new CanonicalInstrument(parts[0], parts[1]);

        if (nativeSymbols != null && nativeSymbols.containsKey(buyOpportunity.exchange)) {
            String buyLink = ExchangeLinkResolver.spotLink(buyOpportunity.exchange,
                    nativeSymbols.get(buyOpportunity.exchange), instrument.quote());
            links.put(buyOpportunity.exchange, buyLink);
        }

        if (nativeSymbols != null && nativeSymbols.containsKey(sellOpportunity.exchange)) {
            String sellLink = ExchangeLinkResolver.spotLink(sellOpportunity.exchange,
                    nativeSymbols.get(sellOpportunity.exchange), instrument.quote());
            links.put(sellOpportunity.exchange, sellLink);
        }

        return new SpotArbitrageData(
                instrument,
                buyOpportunity.exchange,
                buyOpportunity.withdrawStatus,
                sellOpportunity.exchange,
                sellOpportunity.depositStatus,
                spread,
                buyOpportunity.price,
                sellOpportunity.price,
                buyOpportunity.volume24h,
                sellOpportunity.volume24h,
                links
        );
    }

    private ExchangePrice findBestBuyOpportunity(String coin,
                                                  Map<ExchangeType, TickerData> prices,
                                                  Set<ExchangeType> targetExchanges) {
        return prices.entrySet().stream()
                .filter(entry -> targetExchanges.contains(entry.getKey()))
                .map(entry -> {
                    ExchangeType exchange = entry.getKey();
                    TickerData ticker = entry.getValue();

                    DepositWithdrawStatus withdrawStatus = getWithdrawalStatus(exchange, coin);

                    return new ExchangePrice(
                            exchange,
                            ticker.lastPrice(),
                            ticker.volume24h(),
                            withdrawStatus,
                            DepositWithdrawStatus.ENABLED
                    );
                })
                .filter(ep -> ep.withdrawStatus == DepositWithdrawStatus.ENABLED
                        || ep.withdrawStatus == DepositWithdrawStatus.UNKNOWN)
                .min(Comparator.comparing(ep -> ep.price))
                .orElse(null);
    }

    private ExchangePrice findBestSellOpportunity(String coin,
                                                   Map<ExchangeType, TickerData> prices,
                                                   Set<ExchangeType> targetExchanges) {
        return prices.entrySet().stream()
                .filter(entry -> targetExchanges.contains(entry.getKey()))
                .map(entry -> {
                    ExchangeType exchange = entry.getKey();
                    TickerData ticker = entry.getValue();

                    DepositWithdrawStatus depositStatus = getDepositStatus(exchange, coin);

                    return new ExchangePrice(
                            exchange,
                            ticker.lastPrice(),
                            ticker.volume24h(),
                            DepositWithdrawStatus.ENABLED,
                            depositStatus
                    );
                })
                .filter(ep -> ep.depositStatus == DepositWithdrawStatus.ENABLED
                        || ep.depositStatus == DepositWithdrawStatus.UNKNOWN)
                .max(Comparator.comparing(ep -> ep.price))
                .orElse(null);
    }

    private DepositWithdrawStatus getWithdrawalStatus(ExchangeType exchange, String asset) {
        try {
            SpotExchangeClient client = exchangeClientFactory.getSpotClient(exchange);
            if (client != null) {
                WithdrawalDepositStatus status = client.getWithdrawalDepositStatus(asset);
                return status.withdrawStatus();
            }
        } catch (Exception e) {
            log.warn("Failed to get withdrawal status for {} on {}: {}", asset, exchange, e.getMessage());
        }
        return DepositWithdrawStatus.UNKNOWN;
    }

    private DepositWithdrawStatus getDepositStatus(ExchangeType exchange, String asset) {
        try {
            SpotExchangeClient client = exchangeClientFactory.getSpotClient(exchange);
            if (client != null) {
                WithdrawalDepositStatus status = client.getWithdrawalDepositStatus(asset);
                return status.depositStatus();
            }
        } catch (Exception e) {
            log.warn("Failed to get deposit status for {} on {}: {}", asset, exchange, e.getMessage());
        }
        return DepositWithdrawStatus.UNKNOWN;
    }

    private BigDecimal calculatePriceSpread(BigDecimal buyPrice, BigDecimal sellPrice) {
        if (buyPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return sellPrice.subtract(buyPrice)
                .divide(buyPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private String extractCoinFromCanonicalKey(String canonicalKey) {
        if (canonicalKey == null || !canonicalKey.contains("/")) return null;
        return canonicalKey.split("/")[0];
    }

    private record ExchangePrice(ExchangeType exchange, BigDecimal price, BigDecimal volume24h,
                                  DepositWithdrawStatus withdrawStatus, DepositWithdrawStatus depositStatus) {
    }
}