package net.protsenko.fundy.notifier.util;

import net.protsenko.fundy.app.dto.FundingRateData;
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
        String time = Instant.ofEpochMilli(fr.nextFundingTimeMs())
                .atZone(zone)
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        String left = prettyDuration(Duration.between(
                Instant.now(),
                Instant.ofEpochMilli(fr.nextFundingTimeMs())));

        String url = ExchangeLinkResolver.link(ex, fr.instrument());

        return "%s <b>%s</b>  %s  %s (%s)  <a href=\"%s\">%s</a>"
                .formatted(emoji,
                        fr.instrument().baseAsset(),
                        pct(fr.fundingRate()),
                        time,
                        left,
                        url,
                        ex.name());
    }

    /* ---------- helpers ---------- */

    public static String prettyDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        return switch ((h > 0 ? 1 : 0) | (m > 0 ? 2 : 0)) {
            case 1 -> "%d ч".formatted(h);
            case 2 -> "%d мин".formatted(m);
            case 3 -> "%d ч %d мин".formatted(h, m);
            default -> "0 мин";
        };
    }

    public static String pct(BigDecimal p) {
        return p.setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }
}
