package rates246;

import java.time.LocalDate;
import java.util.List;

/** A market quote with a unique id. Rates are decimals (0.05 = 5%). */
public sealed interface Quote permits Quote.DepositQuote, Quote.SwapQuote {
    String id();
    double rate();
    /** End (maturity / last payment) date of the instrument. */
    LocalDate endDate();

    static void checkCommon(String id, double rate) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("quote id must be non-empty");
        }
        if (!Double.isFinite(rate)) {
            throw new IllegalArgumentException("quote " + id + ": rate must be finite");
        }
    }

    /** Deposit from valuation date to maturity: D = 1 / (1 + r * alpha). */
    record DepositQuote(String id, double rate, LocalDate maturity) implements Quote {
        public DepositQuote {
            checkCommon(id, rate);
            if (maturity == null) {
                throw new IllegalArgumentException("quote " + id + ": maturity must not be null");
            }
        }

        @Override
        public LocalDate endDate() {
            return maturity;
        }
    }

    /** Fixed-for-float swap starting at valuation date with increasing payment dates. */
    record SwapQuote(String id, double rate, List<LocalDate> paymentDates) implements Quote {
        public SwapQuote {
            checkCommon(id, rate);
            if (paymentDates == null || paymentDates.isEmpty()) {
                throw new IllegalArgumentException("quote " + id + ": payment dates must be non-empty");
            }
            paymentDates = List.copyOf(paymentDates);
            for (int i = 0; i < paymentDates.size(); i++) {
                LocalDate d = paymentDates.get(i);
                if (d == null) {
                    throw new IllegalArgumentException("quote " + id + ": null payment date");
                }
                if (i > 0 && !d.isAfter(paymentDates.get(i - 1))) {
                    throw new IllegalArgumentException("quote " + id + ": payment dates must be strictly increasing");
                }
            }
        }

        @Override
        public LocalDate endDate() {
            return paymentDates.get(paymentDates.size() - 1);
        }
    }
}
