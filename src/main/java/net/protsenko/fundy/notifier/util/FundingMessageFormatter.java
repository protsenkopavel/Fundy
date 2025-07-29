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

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private FundingMessageFormatter() {}

    public static String format(FundingRateData fr,
                                ExchangeType ex,
                                ZoneId zone) {

        BigDecimal pct = fr.fundingRate()
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        String emoji = pct.signum() >= 0 ? "🟥" : "🟢";

        String timeBlock = buildTimeBlock(fr.nextFundingTimeMs(), zone);

        String url = ExchangeLinkResolver.link(ex, fr.instrument());

        /* пример:
           🟢 <a href="…">BYBIT</a> · KNC -0.54%  19:00ч (3 ч 53 м осталось)
         */
        return "%s <a href=\"%s\">%s</a> · %s %s%%%s"
                .formatted(
                        emoji,
                        url,
                        ex.name(),
                        fr.instrument().baseAsset(),
                        pct.stripTrailingZeros().toPlainString(),
                        timeBlock
                );
    }

    /* ---------- helpers ---------- */

    private static String buildTimeBlock(long nextMs, ZoneId zone) {
        if (nextMs <= 0) return "";
        ZonedDateTime next = Instant.ofEpochMilli(nextMs).atZone(zone);
        Duration left = Duration.between(Instant.now(), next);
        if (left.isNegative()) left = Duration.ZERO;

        return "  %sч (%s осталось)"
                .formatted(next.format(HH_MM), prettyDuration(left));
    }

    /** «3 ч 53 м», «25 м», «5 ч» */
    public static String prettyDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        if (h > 0 && m > 0) return "%d ч %d м".formatted(h, m);
        if (h > 0)          return "%d ч".formatted(h);
        return "%d м".formatted(m);
    }
}
