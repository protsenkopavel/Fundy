package net.protsenko.fundy.app.mapper;

import net.protsenko.fundy.app.dto.FundingRateData;
import net.protsenko.fundy.app.dto.FundingRateView;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class FundingMapper {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_ZONED_DATE_TIME;

    private FundingMapper() {
    }

    public static FundingRateView toView(FundingRateData src, ZoneId zone) {
        BigDecimal percent = src.fundingRate().multiply(BigDecimal.valueOf(100));

        Instant next = Instant.ofEpochMilli(src.nextFundingTimeMs());
        ZonedDateTime nextZdt = next.atZone(zone);

        Duration left = Duration.between(Instant.now(), next);
        if (left.isNegative()) left = Duration.ZERO;

        String countdown = formatDuration(left);

        return new FundingRateView(
                src.instrument(),
                percent,
                src.nextFundingTimeMs(),
                nextZdt.format(ISO_FMT),
                countdown
        );
    }

    private static String formatDuration(Duration d) {
        long hours = d.toHours();
        int minutes = d.toMinutesPart();
        int seconds = d.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
