package net.protsenko.fundy.notifier.util;

import net.protsenko.fundy.app.dto.rs.FundingRateData;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class FundingMessageFormatter {

    private FundingMessageFormatter() {
    }

    public static String format(FundingRateData fr,
                                ExchangeType ex,
                                ZoneId zone) {
        String emoji = fr.fundingRate().signum() >= 0 ? "🟥" : "🟢";
        String symbol = fr.instrument();
        String baseAsset = "";
        if (symbol != null) {
            if (symbol.contains("-")) {
                baseAsset = symbol.substring(0, symbol.indexOf('-'));
            } else if (symbol.contains("_")) {
                baseAsset = symbol.substring(0, symbol.indexOf('_'));
            } else if (symbol.length() >= 2) {
                int len = symbol.length();
                baseAsset = symbol.substring(0, len / 2);
            } else {
                baseAsset = symbol;
            }
        }
        String time = Instant.ofEpochMilli(fr.nextFundingTs())
                .atZone(zone)
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        String left = prettyDuration(Duration.between(
                Instant.now(),
                Instant.ofEpochMilli(fr.nextFundingTs())));
        String url = ExchangeLinkResolver.link(ex, symbol);

        return String.format("%s <b>%s</b>  %s  %s (%s)  <a href=\"%s\">%s</a>",
                emoji,
                baseAsset != null ? baseAsset.toUpperCase() : "",
                pct(fr.fundingRate()),
                time,
                left,
                url,
                ex.name()
        );
    }

    public static String prettyDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        return switch ((h > 0 ? 1 : 0) | (m > 0 ? 2 : 0)) {
            case 1 -> String.format("%d ч", h);
            case 2 -> String.format("%d мин", m);
            case 3 -> String.format("%d ч %d мин", h, m);
            default -> "0 мин";
        };
    }

    public static String pct(BigDecimal p) {
        BigDecimal percent = p.multiply(BigDecimal.valueOf(100));
        return percent
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }
}
