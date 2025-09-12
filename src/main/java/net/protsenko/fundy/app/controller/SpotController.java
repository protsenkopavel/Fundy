package net.protsenko.fundy.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rq.InstrumentsRequest;
import net.protsenko.fundy.app.dto.rq.TickersRequest;
import net.protsenko.fundy.app.dto.rs.TickerData;
import net.protsenko.fundy.app.dto.rs.UniverseEntry;
import net.protsenko.fundy.app.service.SpotService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/spot")
@RequiredArgsConstructor
@Validated
public class SpotController {
    private final SpotService spotService;

    @PostMapping("/instruments")
    public List<UniverseEntry> instruments(@Valid @RequestBody InstrumentsRequest req) {
        return spotService.getSpotUniverse(req);
    }

    @PostMapping("/tickers")
    public List<TickerData> tickers(@Valid @RequestBody TickersRequest tickersRequest) {
        return spotService.getSpotTickers(tickersRequest);
    }
}