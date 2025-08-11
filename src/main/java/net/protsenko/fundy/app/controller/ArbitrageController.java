package net.protsenko.fundy.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rq.ArbitrageFilterRequest;
import net.protsenko.fundy.app.dto.rs.ArbitrageData;
import net.protsenko.fundy.app.service.ArbitrageScannerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/arbitrage")
@RequiredArgsConstructor
public class ArbitrageController {
    private final ArbitrageScannerService service;

    @PostMapping("/opportunities")
    public List<ArbitrageData> getArbitrageOpportunities(@Valid @RequestBody ArbitrageFilterRequest req) {
        return service.getArbitrageOpportunities(req);
    }
}
