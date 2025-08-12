package net.protsenko.fundy.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.rs.FundingRateView;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rq.FundingFilterRequest;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exception.ExchangeException;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingScannerService {
    private final ExchangeClientFactory factory;

    public List<FundingRateView> getFundingOpportunities(FundingFilterRequest req) {
        BigDecimal minFr = req.minFr();

        return req.effectiveExchanges().parallelStream()
                .flatMap(ex -> loadExchangeData(ex, minFr))
                .sorted(Comparator.comparing((FundingRateData r) -> r.fundingRate().abs()).reversed())
                .map(FundingRateView::of)
                .toList();
    }

    private Stream<FundingRateData> loadExchangeData(ExchangeType ex, BigDecimal minFr) {
        try {
            ExchangeClient client = client(ex);

            List<InstrumentData> instruments = client.getInstruments().stream()
                    .filter(i -> i.type() == InstrumentType.PERPETUAL)
                    .toList();

            return client.getFundingRates(instruments).stream()
                    .filter(fr -> fr.fundingRate().abs().compareTo(minFr) >= 0)
                    .map(fr -> new FundingRateData(
                            fr.instrument(),
                            fr.fundingRate(),
                            fr.nextFundingTs()
                    ));
        } catch (Exception e) {
            log.warn("Skip {}: {}", ex, e.getMessage());
            return Stream.empty();
        }
    }

    private ExchangeClient client(ExchangeType exchangeType) {
        ExchangeClient c = factory.getClient(exchangeType);
        if (!c.isEnabled()) throw new ExchangeException("Биржа отключена: " + exchangeType);
        return c;
    }
}
