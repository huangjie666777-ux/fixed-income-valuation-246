package rates246;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Quote-by-quote DV01: bump each quote up and down by 1bp, re-bootstrap the
 * whole curve each time, and report DV01 = (P_down - P_up) / 2 on clean price.
 */
public final class RiskCalculator {
    private static final double ONE_BP = 1e-4;

    private final LocalDate valuationDate;
    private final CurveConfig config;
    private final BondPricer pricer = new BondPricer();

    public RiskCalculator(LocalDate valuationDate, CurveConfig config) {
        this.valuationDate = valuationDate;
        this.config = config == null ? CurveConfig.defaults() : config;
    }

    /** Per-quote DV01 result; on failure, reason is set and dv01 is NaN (never zero-filled). */
    public record Dv01(String quoteId, double dv01, String failureReason) {
        public boolean ok() {
            return failureReason == null;
        }
    }

    public List<Dv01> dv01(List<Quote> quotes, Bond bond, LocalDate settlement) {
        List<Dv01> results = new ArrayList<>();
        for (int i = 0; i < quotes.size(); i++) {
            Quote q = quotes.get(i);
            try {
                double pUp = cleanPriceWithBump(quotes, i, +ONE_BP, bond, settlement);
                double pDown = cleanPriceWithBump(quotes, i, -ONE_BP, bond, settlement);
                results.add(new Dv01(q.id(), (pDown - pUp) / 2.0, null));
            } catch (RuntimeException e) {
                results.add(new Dv01(q.id(), Double.NaN, e.getMessage()));
            }
        }
        return results;
    }

    private double cleanPriceWithBump(List<Quote> quotes, int index, double bump,
                                      Bond bond, LocalDate settlement) {
        List<Quote> bumped = new ArrayList<>(quotes);
        bumped.set(index, bumpQuote(quotes.get(index), bump));
        BootstrapResult result = new CurveBootstrapper(valuationDate, config).bootstrap(bumped);
        return pricer.price(bond, result.curve(), settlement).cleanPrice();
    }

    private static Quote bumpQuote(Quote q, double bump) {
        return switch (q) {
            case Quote.DepositQuote dep -> new Quote.DepositQuote(dep.id(), dep.rate() + bump, dep.maturity());
            case Quote.SwapQuote swap -> new Quote.SwapQuote(swap.id(), swap.rate() + bump, swap.paymentDates());
        };
    }
}
