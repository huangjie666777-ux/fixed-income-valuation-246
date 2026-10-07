package rates246;

import java.time.LocalDate;
import java.util.List;

/**
 * Fixed-rate bond: issue date, strictly increasing coupon dates (last is
 * maturity), positive notional, non-negative annual coupon. Accrual and
 * payment share the same dates; no ex-dividend period.
 */
public record Bond(String id, LocalDate issueDate, List<LocalDate> couponDates,
                   double notional, double annualCoupon) {
    public Bond {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("bond id must be non-empty");
        }
        if (issueDate == null) {
            throw new IllegalArgumentException("bond " + id + ": issue date must not be null");
        }
        if (couponDates == null || couponDates.isEmpty()) {
            throw new IllegalArgumentException("bond " + id + ": coupon dates must be non-empty");
        }
        couponDates = List.copyOf(couponDates);
        for (int i = 0; i < couponDates.size(); i++) {
            LocalDate d = couponDates.get(i);
            if (d == null) {
                throw new IllegalArgumentException("bond " + id + ": null coupon date");
            }
            if (!d.isAfter(issueDate)) {
                throw new IllegalArgumentException("bond " + id + ": coupon date must be after issue date");
            }
            if (i > 0 && !d.isAfter(couponDates.get(i - 1))) {
                throw new IllegalArgumentException("bond " + id + ": coupon dates must be strictly increasing");
            }
        }
        if (!Double.isFinite(notional) || notional <= 0) {
            throw new IllegalArgumentException("bond " + id + ": notional must be positive and finite");
        }
        if (!Double.isFinite(annualCoupon) || annualCoupon < 0) {
            throw new IllegalArgumentException("bond " + id + ": coupon must be non-negative and finite");
        }
    }

    public LocalDate maturity() {
        return couponDates.get(couponDates.size() - 1);
    }
}
