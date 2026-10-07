package rates246;

import java.time.LocalDate;
import java.util.List;

/** Self-contained test runner (no JUnit dependency). */
public final class TestRunner {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        testDepositBootstrap();
        testSwapBootstrapAndInterpolation();
        testNegativeRatesAndDfAboveOne();
        testDuplicateEndpointsRejected();
        testInvalidInputsRejected();
        testOutOfRangeQueryRejected();
        testBondPricing();
        testAccruedZeroOnCouponDate();
        testSettlementValidation();
        testDv01();
        testDv01FailureRecorded();
        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void testDepositBootstrap() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        LocalDate mat = LocalDate.of(2026, 7, 6);
        double r = 0.03;
        BootstrapResult res = new CurveBootstrapper(val, CurveConfig.defaults())
                .bootstrap(List.of(new Quote.DepositQuote("D1", r, mat)));
        double alpha = DayCount.yearFraction(val, mat);
        assertClose(1.0 / (1 + r * alpha), res.curve().discountFactor(mat), 1e-12, "deposit DF");
        assertClose(1.0, res.curve().discountFactor(val), 1e-15, "DF at valuation");
        assertClose(r, res.repricings().get(0).repricedRate(), 1e-10, "deposit repricing");
    }

    static void testSwapBootstrapAndInterpolation() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        LocalDate y1 = LocalDate.of(2027, 1, 5);
        LocalDate y2 = LocalDate.of(2028, 1, 5);
        double s2 = 0.04;
        BootstrapResult res = new CurveBootstrapper(val, CurveConfig.defaults())
                .bootstrap(List.of(
                        new Quote.SwapQuote("S1", 0.035, List.of(y1)),
                        new Quote.SwapQuote("S2", s2, List.of(y1, y2))));
        Curve c = res.curve();
        double a1 = DayCount.yearFraction(val, y1);
        double a2 = DayCount.yearFraction(y1, y2);
        double d1 = c.discountFactor(y1);
        double d2 = c.discountFactor(y2);
        assertClose(1.0 - d2, s2 * (a1 * d1 + a2 * d2), 1e-10, "swap par identity");
        // log-linear interpolation at midpoint
        LocalDate mid = LocalDate.of(2027, 7, 6);
        double t = c.timeOf(mid);
        double t1 = c.timeOf(y1);
        double t2 = c.timeOf(y2);
        double expected = Math.exp(Math.log(d1) + (t - t1) / (t2 - t1) * (Math.log(d2) - Math.log(d1)));
        assertClose(expected, c.discountFactor(mid), 1e-12, "log-linear interpolation");
    }

    static void testNegativeRatesAndDfAboveOne() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        BootstrapResult res = new CurveBootstrapper(val, CurveConfig.defaults())
                .bootstrap(List.of(
                        new Quote.DepositQuote("Dneg", -0.005, LocalDate.of(2026, 7, 6)),
                        new Quote.DepositQuote("Dpos", 0.02, LocalDate.of(2027, 1, 5))));
        double d = res.curve().discountFactor(LocalDate.of(2026, 7, 6));
        check(d > 1.0, "negative rate implies D > 1");
        check(d > 0 && Double.isFinite(d), "D positive finite");
    }

    static void testDuplicateEndpointsRejected() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        LocalDate mat = LocalDate.of(2026, 7, 6);
        expectThrow(() -> new CurveBootstrapper(val, CurveConfig.defaults()).bootstrap(List.of(
                new Quote.DepositQuote("A", 0.02, mat),
                new Quote.DepositQuote("B", 0.03, mat))), "duplicate endpoint");
        expectThrow(() -> new CurveBootstrapper(val, CurveConfig.defaults()).bootstrap(List.of(
                new Quote.DepositQuote("A", 0.02, mat),
                new Quote.DepositQuote("A", 0.03, LocalDate.of(2027, 1, 5)))), "duplicate id");
    }

    static void testInvalidInputsRejected() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        LocalDate mat = LocalDate.of(2026, 7, 6);
        expectThrow(() -> new Quote.DepositQuote("X", Double.NaN, mat), "NaN rate");
        expectThrow(() -> new Quote.DepositQuote("X", Double.POSITIVE_INFINITY, mat), "infinite rate");
        expectThrow(() -> new Quote.SwapQuote("X", 0.02, List.of()), "empty schedule");
        expectThrow(() -> new Quote.SwapQuote("X", 0.02,
                List.of(LocalDate.of(2027, 1, 5), LocalDate.of(2026, 6, 1))), "non-increasing schedule");
        expectThrow(() -> new CurveBootstrapper(val, CurveConfig.defaults())
                .bootstrap(List.of(new Quote.DepositQuote("X", 0.02, val))), "end before valuation");
        // zero denominator: 1 + r*alpha == 0
        double alpha = DayCount.yearFraction(val, mat);
        expectThrow(() -> new CurveBootstrapper(val, CurveConfig.defaults())
                .bootstrap(List.of(new Quote.DepositQuote("X", -1.0 / alpha, mat))), "zero denominator");
        expectThrow(() -> DayCount.yearFraction(mat, mat), "zero-length period");
        expectThrow(() -> new Bond("B", val, List.of(mat), -1.0, 0.02), "negative notional");
        expectThrow(() -> new Bond("B", val, List.of(mat), 1.0, -0.01), "negative coupon");
    }

    static void testOutOfRangeQueryRejected() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        BootstrapResult res = new CurveBootstrapper(val, CurveConfig.defaults())
                .bootstrap(List.of(new Quote.DepositQuote("D", 0.02, LocalDate.of(2027, 1, 5))));
        Curve c = res.curve();
        expectThrow(() -> c.discountFactor(LocalDate.of(2025, 1, 5)), "before valuation");
        expectThrow(() -> c.discountFactor(LocalDate.of(2028, 1, 5)), "after last node");
        expectThrow(() -> c.discountFactor(Double.NaN), "NaN time");
    }

    static void testBondPricing() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        BootstrapResult res = new CurveBootstrapper(val, CurveConfig.defaults())
                .bootstrap(List.of(
                        new Quote.DepositQuote("D", 0.02, LocalDate.of(2026, 7, 6)),
                        new Quote.SwapQuote("S", 0.03, List.of(
                                LocalDate.of(2027, 1, 5), LocalDate.of(2028, 1, 5),
                                LocalDate.of(2029, 1, 5)))));
        Curve c = res.curve();
        Bond bond = new Bond("B", val, List.of(
                LocalDate.of(2027, 1, 5), LocalDate.of(2028, 1, 5), LocalDate.of(2029, 1, 5)),
                1_000_000, 0.025);
        BondPricer pricer = new BondPricer();

        // Settlement on a coupon date: accrued zero, price independent of notional per 100.
        LocalDate settle = LocalDate.of(2027, 1, 5);
        BondPricer.BondPrice p = pricer.price(bond, c, settle);
        assertClose(0.0, p.accrued(), 1e-12, "accrued on coupon date");
        double dSettle = c.discountFactor(settle);
        double expected = 0.0;
        LocalDate prev = settle;
        List<LocalDate> remaining = List.of(LocalDate.of(2028, 1, 5), LocalDate.of(2029, 1, 5));
        for (int i = 0; i < remaining.size(); i++) {
            double amt = 0.025 * DayCount.yearFraction(prev, remaining.get(i));
            if (i == remaining.size() - 1) {
                amt += 1.0;
            }
            expected += amt * c.discountFactor(remaining.get(i)) / dSettle;
            prev = remaining.get(i);
        }
        assertClose(expected * 100.0, p.fullPrice(), 1e-8, "full price");
        assertClose(p.fullPrice() - p.accrued(), p.cleanPrice(), 1e-12, "clean = full - accrued");

        // Mid-period settlement: accrued matches ACT/365F fraction.
        LocalDate mid = LocalDate.of(2027, 7, 6);
        BondPricer.BondPrice pm = pricer.price(bond, c, mid);
        double acc = 0.025 * DayCount.yearFraction(LocalDate.of(2027, 1, 5), mid) * 100.0;
        assertClose(acc, pm.accrued(), 1e-8, "mid-period accrued");
        // Only cash flows after settlement are counted.
        for (BondPricer.CashFlow cf : pricer.cashFlows(bond, c, mid)) {
            check(cf.date().isAfter(mid), "cash flow after settlement");
        }
    }

    static void testAccruedZeroOnCouponDate() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        Bond bond = new Bond("B", val, List.of(LocalDate.of(2027, 1, 5)), 100.0, 0.05);
        BondPricer pricer = new BondPricer();
        assertClose(0.0, pricer.accrued(bond, LocalDate.of(2027, 1, 5)), 1e-15, "accrued on coupon date");
        check(pricer.accrued(bond, LocalDate.of(2026, 7, 6)) > 0, "accrued positive mid-period");
    }

    static void testSettlementValidation() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        BootstrapResult res = new CurveBootstrapper(val, CurveConfig.defaults())
                .bootstrap(List.of(new Quote.DepositQuote("D", 0.02, LocalDate.of(2029, 1, 5))));
        Curve c = res.curve();
        Bond bond = new Bond("B", val, List.of(LocalDate.of(2027, 1, 5)), 100.0, 0.02);
        BondPricer pricer = new BondPricer();
        expectThrow(() -> pricer.price(bond, c, LocalDate.of(2025, 12, 1)), "settlement before issue");
        expectThrow(() -> pricer.price(bond, c, LocalDate.of(2027, 1, 5)), "settlement at maturity");
        expectThrow(() -> pricer.price(bond, c, LocalDate.of(2030, 1, 5)), "settlement outside curve");
    }

    static void testDv01() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        List<Quote> quotes = List.of(
                new Quote.DepositQuote("D", 0.02, LocalDate.of(2026, 7, 6)),
                new Quote.SwapQuote("S", 0.03, List.of(
                        LocalDate.of(2027, 1, 5), LocalDate.of(2028, 1, 5))));
        Bond bond = new Bond("B", val, List.of(
                LocalDate.of(2027, 1, 5), LocalDate.of(2028, 1, 5)), 100.0, 0.03);
        RiskCalculator risk = new RiskCalculator(val, CurveConfig.defaults());
        List<RiskCalculator.Dv01> dv01s = risk.dv01(quotes, bond, val);
        check(dv01s.size() == 2, "one DV01 per quote");
        for (RiskCalculator.Dv01 d : dv01s) {
            check(d.ok(), "dv01 ok for " + d.quoteId());
            check(d.dv01() >= 0, "dv01 non-negative for receiver-style sensitivity");
        }
        // Swap-quote DV01 should be the dominant risk for a 2Y bond.
        check(dv01s.get(1).dv01() > dv01s.get(0).dv01(), "swap dv01 dominates");
        check(dv01s.get(1).dv01() > 0.005 && dv01s.get(1).dv01() < 0.05, "dv01 plausible magnitude");
    }

    static void testDv01FailureRecorded() {
        LocalDate val = LocalDate.of(2026, 1, 5);
        // Bumping the deposit down by 1bp drives 1 + r*alpha to zero -> failure recorded, not zero.
        double alpha = DayCount.yearFraction(val, LocalDate.of(2026, 7, 6));
        List<Quote> quotes = List.of(
                new Quote.DepositQuote("D", -1.0 / alpha + 1e-4, LocalDate.of(2026, 7, 6)),
                new Quote.SwapQuote("S", 0.03, List.of(LocalDate.of(2027, 1, 5))));
        Bond bond = new Bond("B", val, List.of(LocalDate.of(2027, 1, 5)), 100.0, 0.03);
        RiskCalculator risk = new RiskCalculator(val, CurveConfig.defaults());
        List<RiskCalculator.Dv01> dv01s = risk.dv01(quotes, bond, val);
        check(!dv01s.get(0).ok(), "failing bump recorded");
        check(dv01s.get(0).failureReason() != null && !dv01s.get(0).failureReason().isBlank(), "reason present");
        check(Double.isNaN(dv01s.get(0).dv01()), "no zero-fill on failure");
        check(dv01s.get(1).ok(), "other quotes still computed");
    }

    private static void check(boolean cond, String name) {
        if (cond) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }

    private static void assertClose(double expected, double actual, double tol, String name) {
        check(Math.abs(expected - actual) <= tol, name + " (expected=" + expected + " actual=" + actual + ")");
    }

    private static void expectThrow(Runnable r, String name) {
        try {
            r.run();
            check(false, name + " (no exception)");
        } catch (RuntimeException e) {
            check(true, name + " -> " + e.getClass().getSimpleName());
        }
    }
}
