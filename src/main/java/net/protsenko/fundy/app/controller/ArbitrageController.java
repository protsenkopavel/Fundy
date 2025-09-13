package net.protsenko.fundy.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rq.FuturesArbitrageRequest;
import net.protsenko.fundy.app.dto.rq.SpotArbitrageRequest;
import net.protsenko.fundy.app.dto.rq.SpotFuturesArbitrageRequest;
import net.protsenko.fundy.app.dto.rs.FuturesArbitrageData;
import net.protsenko.fundy.app.dto.rs.SpotArbitrageData;
import net.protsenko.fundy.app.dto.rs.SpotFuturesArbitrageData;
import net.protsenko.fundy.app.service.FuturesArbitrageScannerService;
import net.protsenko.fundy.app.service.SpotArbitrageService;
import net.protsenko.fundy.app.service.SpotFuturesArbitrageService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/arbitrage")
@RequiredArgsConstructor
@Validated
public class ArbitrageController {

    private final SpotArbitrageService spotArbitrageService;
    private final FuturesArbitrageScannerService futuresArbitrageScannerService;
    private final SpotFuturesArbitrageService spotFuturesArbitrageService;

    @PostMapping("/spot/opportunities")
    public List<SpotArbitrageData> getSpotArbitrageOpportunities(@Valid @RequestBody SpotArbitrageRequest request) {
        return spotArbitrageService.getSpotArbitrageOpportunities(request);
    }

    @PostMapping("/futures/opportunities")
    public List<FuturesArbitrageData> getFuturesArbitrageOpportunities(
            @Valid @RequestBody FuturesArbitrageRequest request) {
        return futuresArbitrageScannerService.getArbitrageOpportunities(request);
    }

    @PostMapping("/spot-futures/opportunities")
    public List<SpotFuturesArbitrageData> getSpotFuturesArbitrageOpportunities(
            @Valid @RequestBody SpotFuturesArbitrageRequest request) {
        return spotFuturesArbitrageService.getSpotFuturesArbitrageOpportunities(request);
    }
}