package net.protsenko.fundy.app.controller;

import net.protsenko.fundy.app.dto.rs.ExchangeData;
import net.protsenko.fundy.app.exchange.ExchangeType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    @GetMapping("/exchanges")
    public List<ExchangeData> exchanges() {
        return Arrays.stream(ExchangeType.values())
                .map(et -> new ExchangeData(et, et.name()))
                .toList();
    }
}