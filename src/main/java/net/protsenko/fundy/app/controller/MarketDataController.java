package net.protsenko.fundy.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rq.InstrumentsRequest;
import net.protsenko.fundy.app.dto.rq.TickersRequest;
import net.protsenko.fundy.app.dto.rs.ExchangeData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.UniverseEntry;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.service.MarketDataService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/market/data")
@RequiredArgsConstructor
@Validated
public class MarketDataController {
    private final MarketDataService service;

    @PostMapping("/instruments")
    public List<UniverseEntry> instruments(@Valid @RequestBody InstrumentsRequest req) {
        return service.getPerpUniverse(req);
    }

    @PostMapping("/tickers")
    public List<TickerData> tickers(@Valid @RequestBody TickersRequest tickersRequest) {
        return service.getTickers(tickersRequest);
    }

    @GetMapping("/exchanges")
    public List<ExchangeData> exchanges() {
        return Arrays.stream(ExchangeType.values())
                .map(et -> new ExchangeData(et, et.name()))
                .toList();
    }
}