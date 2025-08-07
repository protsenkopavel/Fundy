package net.protsenko.fundy.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.rq.FundingFilterRequest;
import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.service.FundingScannerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/funding")
@RequiredArgsConstructor
public class FundingController {
    private final FundingScannerService service;

    @PostMapping("/opportunities")
    public List<FundingRateData> getFundingOpportunities(@Valid @RequestBody FundingFilterRequest req) {
        return service.getFundingOpportunities(req);
    }
}