package net.protsenko.fundy.app.controller;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.ArbitrageData;
import net.protsenko.fundy.app.dto.ArbitrageFilter;
import net.protsenko.fundy.app.service.ArbitrageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class ArbitrageController {
    private final ArbitrageService arbitrageService;

    @PostMapping("/arbitrage")
    public List<ArbitrageData> findArbitrageOpportunities(@RequestBody ArbitrageFilter filter) {
        return arbitrageService.findArbitrageOpportunities(filter);
    }
}
