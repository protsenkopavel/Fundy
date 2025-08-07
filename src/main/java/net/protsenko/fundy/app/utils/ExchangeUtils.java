package net.protsenko.fundy.app.utils;

import java.math.BigDecimal;

public final class ExchangeUtils {
    private ExchangeUtils() {
    }

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public static BigDecimal bd(String s) {
        if (blank(s)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public static long l(String s) {
        if (blank(s)) return 0L;
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }
}
