package net.protsenko.fundy.app.controller;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.FundingRateView;
import net.protsenko.fundy.app.dto.FundingRateViewEx;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.mapper.FundingMapper;
import net.protsenko.fundy.app.service.FundingScannerService;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class FundingController {

    private final FundingScannerService scanner;

    @GetMapping("/{exchange}/funding/high")
    public List<FundingRateView> highFundingByExchange(
            @PathVariable ExchangeType exchange,
            @RequestParam(defaultValue = "0.001") double minRate,
            @RequestParam String tz) {

        ZoneId zone = resolveZone(tz);
        return scanner.findHighFundingRates(exchange, minRate).stream()
                .map(fr -> FundingMapper.toView(fr, zone))
                .toList();
    }

    @GetMapping("/funding/high")
    public List<FundingRateViewEx> highFundingAll(
            @RequestParam(defaultValue = "0.001") double minRate,
            @RequestParam String tz) {

        ZoneId zone = resolveZone(tz);
        return scanner.findHighFundingRatesAll(minRate).stream()
                .map(fr -> FundingMapper.toView(fr, zone))
                .toList();
    }

    private ZoneId resolveZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
