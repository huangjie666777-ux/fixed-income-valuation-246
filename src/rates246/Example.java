package rates246;

import java.time.LocalDate;
import java.util.List;

/** End-to-end valuation and risk example. */
public final class Example {
    public static void main(String[] args) {
        LocalDate valuation = LocalDate.of(2026, 10, 9);

        List<Quote> quotes = List.of(
                new Quote.DepositQuote("DEP-3M", 0.0210, LocalDate.of(2027, 1, 11)),
                new Quote.DepositQuote("DEP-6M", 0.0225, LocalDate.of(2027, 4, 9)),
                new Quote.SwapQuote("SWP-1Y", 0.0240, List.of(LocalDate.of(2027, 10, 11))),
                new Quote.SwapQuote("SWP-2Y", 0.0255, List.of(
                        LocalDate.of(2027, 10, 11), LocalDate.of(2028, 10, 9))),
                new Quote.SwapQuote("SWP-3Y", 0.0268, List.of(
                        LocalDate.of(2027, 10, 11), LocalDate.of(2028, 10, 9),
                        LocalDate.of(2029, 10, 9))),
                new Quote.SwapQuote("SWP-5Y", 0.0290, List.of(
                        LocalDate.of(2027, 10, 11), LocalDate.of(2028, 10, 9),
                        LocalDate.of(2029, 10, 9), LocalDate.of(2030, 10, 9),
                        LocalDate.of(2031, 10, 9))));

        CurveConfig config = CurveConfig.defaults();
        BootstrapResult result = new CurveBootstrapper(valuation, config).bootstrap(quotes);
        Curve curve = result.curve();

        System.out.println("=== Bootstrapped curve (valuation " + valuation + ") ===");
        for (BootstrapResult.Repricing r : result.repricings()) {
            System.out.printf("%-8s market=%.6f  repriced=%.6f  residual=%.2e%n",
                    r.quoteId(), r.marketRate(), r.repricedRate(), r.residual());
        }
        for (Quote q : quotes) {
            System.out.printf("D(%s) = %.8f%n", q.endDate(), curve.discountFactor(q.endDate()));
        }

        Bond bond = new Bond("BOND-5Y", LocalDate.of(2026, 10, 9), List.of(
                LocalDate.of(2027, 10, 9), LocalDate.of(2028, 10, 9),
                LocalDate.of(2029, 10, 9), LocalDate.of(2030, 10, 9),
                LocalDate.of(2031, 10, 9)),
                1_000_000.0, 0.0280);
        LocalDate settlement = LocalDate.of(2027, 1, 15);

        BondPricer pricer = new BondPricer();
        BondPricer.BondPrice price = pricer.price(bond, curve, settlement);
        System.out.println();
        System.out.println("=== Bond " + bond.id() + " @ settlement " + settlement + " (per 100) ===");
        System.out.printf("full   = %.6f%n", price.fullPrice());
        System.out.printf("accrued= %.6f%n", price.accrued());
        System.out.printf("clean  = %.6f%n", price.cleanPrice());
        System.out.println("cash flows after settlement:");
        for (BondPricer.CashFlow cf : pricer.cashFlows(bond, curve, settlement)) {
            System.out.printf("  %s  amount=%.6f  D_pay/D_settle=%.8f%n",
                    cf.date(), cf.amount() * 100.0, cf.discountRatio());
        }

        System.out.println();
        System.out.println("=== DV01 by quote (clean price, 1bp bumps) ===");
        RiskCalculator risk = new RiskCalculator(valuation, config);
        for (RiskCalculator.Dv01 d : risk.dv01(quotes, bond, settlement)) {
            if (d.ok()) {
                System.out.printf("%-8s DV01=%.6f%n", d.quoteId(), d.dv01());
            } else {
                System.out.printf("%-8s FAILED: %s%n", d.quoteId(), d.failureReason());
            }
        }
    }
}
