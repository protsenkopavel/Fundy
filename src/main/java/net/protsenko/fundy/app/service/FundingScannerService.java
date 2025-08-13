package net.protsenko.fundy.app.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.fundy.app.dto.InstrumentType;
import net.protsenko.fundy.app.dto.rq.FundingFilterRequest;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.dto.rs.FundingRateView;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.exchange.ExchangeClient;
import net.protsenko.fundy.app.exchange.ExchangeClientFactory;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
public class FundingScannerService extends BaseExchangeService {

    private final UniverseService universeService;

    public FundingScannerService(ExchangeClientFactory factory, UniverseService universeService) {
        super(factory);
        this.universeService = universeService;
    }

    public List<FundingRateView> getFundingOpportunities(FundingFilterRequest req) {
        BigDecimal minFr = req.minFr();

        Map<String, Map<ExchangeType, String>> uni = universeService.perpUniverse(req.effectiveExchanges());

        return across(req.effectiveExchanges(), c -> loadExchangeData(c, minFr, uni))
                .sorted(Comparator.comparing((FundingRateData r) -> r.fundingRate().abs()).reversed())
                .map(FundingRateView::of)
                .toList();
    }

    private InstrumentData makeInstr(String canonicalKey, String nativeSymbol, ExchangeType ex) {
        String[] p = canonicalKey.split("/");
        String base = p.length > 0 ? p[0] : "";
        String quote = p.length > 1 ? p[1] : "USDT";
        return new InstrumentData(base, quote, InstrumentType.PERPETUAL, nativeSymbol, ex);
    }

    private Stream<FundingRateData> loadExchangeData(ExchangeClient client,
                                                     BigDecimal minFr,
                                                     Map<String, Map<ExchangeType, String>> uni) {
        try {
            ExchangeType ex = client.getExchangeType();

            List<InstrumentData> instruments = uni.entrySet().stream()
                    .map(e -> {
                        String nativeSymbol = e.getValue().get(ex);
                        return nativeSymbol == null ? null : makeInstr(e.getKey(), nativeSymbol, ex);
                    })
                    .filter(Objects::nonNull)
                    .toList();

            return client.getFundingRates(instruments).stream()
                    .filter(fr -> fr.fundingRate().abs().compareTo(minFr) >= 0);
        } catch (Exception e) {
            log.warn("Skip {}: {}", client.getExchangeType(), e.getMessage());
            return Stream.empty();
        }
    }
}
