package net.protsenko.fundy.app.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rq.FundingFilterRequest;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.FundingRateView;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class FundingScannerService extends BaseExchangeService {

    public FundingScannerService(ExchangeClientFactory factory) {
        super(factory);
    }

    public List<FundingRateView> getFundingOpportunities(FundingFilterRequest req) {
        BigDecimal minFr = req.minFr();

        return across(req.effectiveExchanges(), c -> loadExchangeData(c, minFr))
                .sorted(Comparator.comparing((FundingRateData r) -> r.fundingRate().abs()).reversed())
                .map(FundingRateView::of)
                .toList();
    }

    private Stream<FundingRateData> loadExchangeData(ExchangeClient client, BigDecimal minFr) {
        try {
            List<InstrumentData> instruments = client.getInstruments().stream()
                    .filter(i -> i.type() == InstrumentType.PERPETUAL)
                    .toList();

            return client.getFundingRates(instruments).stream()
                    .filter(fr -> fr.fundingRate().abs().compareTo(minFr) >= 0);
        } catch (Exception e) {
            log.warn("Skip {}: {}", client.getExchangeType(), e.getMessage());
            return Stream.empty();
        }
    }
}
