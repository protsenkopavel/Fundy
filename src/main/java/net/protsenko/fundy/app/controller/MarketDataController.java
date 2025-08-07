package net.protsenko.fundy.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rq.InstrumentsRequest;
import net.protsenko.fundy.app.dto.rq.TickersRequest;
import net.protsenko.fundy.app.dto.rs.InstrumentData;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.service.MarketDataService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/data")
@RequiredArgsConstructor
@Validated
public class MarketDataController {
    private final MarketDataService service;

    @PostMapping("/instruments")
    public List<InstrumentData> instruments(@Valid @RequestBody InstrumentsRequest req) {
        return service.getAvailableInstruments(req);
    }

    @PostMapping("/tickers")
    public List<TickerData> tickers(@Valid @RequestBody TickersRequest req) {
        return service.getTickers(req);
    }
}