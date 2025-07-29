package net.protsenko.fundy.notifier.util;

import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.TradingInstrument;
import net.protsenko.fundy.app.exchange.ExchangeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class FundingMessageFormatter {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private FundingMessageFormatter() {}

    public static String format(FundingRateData fr,
                                ExchangeType ex,
                                ZoneId zone) {

        BigDecimal percent = fr.fundingRate().multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP); // 2 знака

        String sign = percent.signum() >= 0 ? "🟥" : "🟢";

        String timePart = "";
        long ms = fr.nextFundingTimeMs();
        if (ms > 0) {
            Instant next = Instant.ofEpochMilli(ms);
            ZonedDateTime zdt = next.atZone(zone);
            Duration left = Duration.between(Instant.now(), next);
            if (left.isNegative()) left = Duration.ZERO;
            timePart = "  %s (%s осталось)".formatted(
                    zdt.format(TIME_FMT),
                    prettyDuration(left)
            );
        }

        String url = ExchangeLinkResolver.link(ex, fr.instrument());
        String exchangeHtml = url.isBlank()
                ? ex.name()
                : "<a href=\"" + url + "\">" + ex.name() + "</a>";

        // Пример строки:
        // 🟢 <a href="...">BYBIT</a> · NEWT -0.85%  08:00 (06:41 осталось)
        return "%s %s · %s %s%%%s"
                .formatted(
                        sign,
                        exchangeHtml,
                        fr.instrument().baseAsset(),
                        percent.stripTrailingZeros().toPlainString(),
                        timePart
                );
    }

    public static String prettyDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        if (h > 0) return String.format("%02d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }
}
