package rates246;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Prices fixed-rate bonds off a discount curve, per 100 notional. */
public final class BondPricer {

    /** Per-100 price breakdown. */
    public record BondPrice(double fullPrice, double accrued, double cleanPrice) {}

    /** A single cash flow: date, amount (fraction of notional), discount factor ratio. */
    public record CashFlow(LocalDate date, double amount, double discountRatio) {}

    public BondPrice price(Bond bond, Curve curve, LocalDate settlement) {
        validateSettlement(bond, curve, settlement);
        double dSettle = curve.discountFactor(settlement);
        double full = 0.0;
        for (CashFlow cf : cashFlows(bond, curve, settlement, dSettle)) {
            full += cf.amount() * cf.discountRatio();
        }
        double accrued = accrued(bond, settlement);
        return new BondPrice(full * 100.0, accrued * 100.0, (full - accrued) * 100.0);
    }

    /** Cash flows with payment date strictly after settlement, discounted by D_pay / D_settle. */
    public List<CashFlow> cashFlows(Bond bond, Curve curve, LocalDate settlement) {
        validateSettlement(bond, curve, settlement);
        return cashFlows(bond, curve, settlement, curve.discountFactor(settlement));
    }

    private List<CashFlow> cashFlows(Bond bond, Curve curve, LocalDate settlement, double dSettle) {
        List<CashFlow> flows = new ArrayList<>();
        LocalDate prev = bond.issueDate();
        int n = bond.couponDates().size();
        for (int i = 0; i < n; i++) {
            LocalDate pay = bond.couponDates().get(i);
            double alpha = DayCount.yearFraction(prev, pay);
            double amount = bond.annualCoupon() * alpha;
            if (i == n - 1) {
                amount += 1.0;
            }
            if (pay.isAfter(settlement)) {
                double ratio = curve.discountFactor(pay) / dSettle;
                flows.add(new CashFlow(pay, amount, ratio));
            }
            prev = pay;
        }
        return flows;
    }

    /** Accrued interest as a fraction of notional; zero on coupon dates. */
    public double accrued(Bond bond, LocalDate settlement) {
        LocalDate periodStart = bond.issueDate();
        for (LocalDate pay : bond.couponDates()) {
            if (settlement.equals(periodStart) || settlement.equals(pay)) {
                return 0.0;
            }
            if (settlement.isBefore(pay)) {
                double alpha = DayCount.yearFraction(periodStart, settlement);
                return bond.annualCoupon() * alpha;
            }
            periodStart = pay;
        }
        throw new IllegalArgumentException("settlement " + settlement + " not before maturity");
    }

    private void validateSettlement(Bond bond, Curve curve, LocalDate settlement) {
        if (settlement == null) {
            throw new IllegalArgumentException("settlement must not be null");
        }
        if (settlement.isBefore(bond.issueDate())) {
            throw new IllegalArgumentException("settlement " + settlement + " before issue date " + bond.issueDate());
        }
        if (!settlement.isBefore(bond.maturity())) {
            throw new IllegalArgumentException("settlement " + settlement + " must be before maturity " + bond.maturity());
        }
        if (settlement.isBefore(curve.valuationDate()) || settlement.isAfter(curve.endDate())) {
            throw new IllegalArgumentException("settlement " + settlement + " outside curve range ["
                    + curve.valuationDate() + ", " + curve.endDate() + "]");
        }
    }
}
