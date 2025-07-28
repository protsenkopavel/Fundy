package net.protsenko.fundy.app.controller;

import lombok.RequiredArgsConstructor;
import net.protsenko.fundy.app.dto.TickerData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.dto.TradingInstrumentRequest;
import net.protsenko.fundy.app.exchange.ExchangeType;
import net.protsenko.fundy.app.service.MarketDataService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Validated
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/{exchange}/instruments")
    public List<TradingInstrument> getInstruments(@PathVariable ExchangeType exchange) {
        return marketDataService.getAvailableInstruments(exchange);
    }

    @GetMapping("/{exchange}/ticker")
    public TickerData getTicker(
            @PathVariable ExchangeType exchange,
            @RequestParam String base,
            @RequestParam String quote
    ) {
        return marketDataService.getTicker(exchange, base, quote);
    }

    @PostMapping("/{exchange}/tickers")
    public List<TickerData> getTickers(
            @PathVariable ExchangeType exchange,
            @RequestBody List<TradingInstrumentRequest> body
    ) {
        return marketDataService.getTickers(exchange, body);
    }
}