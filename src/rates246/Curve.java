package rates246;

import java.time.LocalDate;
import java.util.List;

/**
 * Discount curve: D(valuation) = 1, time axis in ACT/365F years,
 * log D linearly interpolated between adjacent nodes.
 */
public final class Curve {
    private final LocalDate valuationDate;
    private final double[] times;
    private final double[] logDfs;

    Curve(LocalDate valuationDate, List<LocalDate> nodeDates, List<Double> dfs) {
        this.valuationDate = valuationDate;
        int n = nodeDates.size();
        this.times = new double[n + 1];
        this.logDfs = new double[n + 1];
        for (int i = 0; i < n; i++) {
            double t = DayCount.yearFraction(valuationDate, nodeDates.get(i));
            double d = dfs.get(i);
            if (!Double.isFinite(d) || d <= 0) {
                throw new IllegalArgumentException("discount factor must be positive and finite at " + nodeDates.get(i));
            }
            if (t <= times[i]) {
                throw new IllegalArgumentException("curve nodes must be strictly increasing");
            }
            times[i + 1] = t;
            logDfs[i + 1] = Math.log(d);
        }
    }

    public LocalDate valuationDate() {
        return valuationDate;
    }

    public LocalDate endDate() {
        return valuationDate.plusDays(Math.round(times[times.length - 1] * 365));
    }

    public double maxTime() {
        return times[times.length - 1];
    }

    public double timeOf(LocalDate date) {
        return DayCount.days(valuationDate, date) / 365.0;
    }

    /** Discount factor at a time in years. Rejects queries outside the curve range. */
    public double discountFactor(double t) {
        if (!Double.isFinite(t)) {
            throw new IllegalArgumentException("time must be finite");
        }
        if (t < 0.0 || t > maxTime()) {
            throw new IllegalArgumentException("time " + t + " outside curve range [0, " + maxTime() + "]");
        }
        return Math.exp(logDf(t));
    }

    public double discountFactor(LocalDate date) {
        if (date.isBefore(valuationDate)) {
            throw new IllegalArgumentException("date " + date + " before valuation date " + valuationDate);
        }
        return discountFactor(timeOf(date));
    }

    /** Log-linear interpolation of D between adjacent nodes. */
    double logDf(double t) {
        if (t <= 0.0) {
            return 0.0;
        }
        int n = times.length;
        for (int i = 1; i < n; i++) {
            if (t <= times[i]) {
                double w = (t - times[i - 1]) / (times[i] - times[i - 1]);
                return logDfs[i - 1] + w * (logDfs[i] - logDfs[i - 1]);
            }
        }
        return logDfs[n - 1];
    }
}
