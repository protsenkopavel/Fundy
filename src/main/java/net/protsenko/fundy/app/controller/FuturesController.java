package net.protsenko.fundy.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rq.InstrumentsRequest;
import net.protsenko.fundy.app.dto.rq.TickersRequest;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.UniverseEntry;
import net.protsenko.fundy.app.service.FuturesService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/futures")
@RequiredArgsConstructor
@Validated
public class FuturesController {
    private final FuturesService futuresService;

    @PostMapping("/instruments")
    public List<UniverseEntry> instruments(@Valid @RequestBody InstrumentsRequest req) {
        return futuresService.getFuturesUniverse(req);
    }

    @PostMapping("/tickers")
    public List<TickerData> tickers(@Valid @RequestBody TickersRequest tickersRequest) {
        return futuresService.getFuturesTickers(tickersRequest);
    }
}