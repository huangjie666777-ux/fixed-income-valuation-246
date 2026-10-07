package rates246;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** ACT/365F day count: actual days between dates over 365. */
public final class DayCount {
    private DayCount() {}

    public static long days(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end);
    }

    /** Year fraction ACT/365F. Rejects non-positive periods (zero denominator). */
    public static double yearFraction(LocalDate start, LocalDate end) {
        long d = days(start, end);
        if (d <= 0) {
            throw new IllegalArgumentException("non-positive period: " + start + " -> " + end);
        }
        return d / 365.0;
    }
}
