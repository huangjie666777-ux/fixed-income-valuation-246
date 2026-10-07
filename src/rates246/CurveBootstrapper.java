package rates246;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.exception.NoBracketingException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;

/**
 * Bootstraps a discount curve from deposit and fixed-for-float swap quotes,
 * one instrument at a time in ascending end-date order.
 */
public final class CurveBootstrapper {
    private final LocalDate valuationDate;
    private final CurveConfig config;

    public CurveBootstrapper(LocalDate valuationDate, CurveConfig config) {
        if (valuationDate == null) {
            throw new IllegalArgumentException("valuation date must not be null");
        }
        this.valuationDate = valuationDate;
        this.config = config == null ? CurveConfig.defaults() : config;
    }

    public BootstrapResult bootstrap(List<Quote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            throw new IllegalArgumentException("quotes must be non-empty");
        }
        Set<String> ids = new HashSet<>();
        List<Quote> sorted = new ArrayList<>(quotes);
        for (Quote q : sorted) {
            if (q == null) {
                throw new IllegalArgumentException("null quote");
            }
            if (!ids.add(q.id())) {
                throw new IllegalArgumentException("duplicate quote id: " + q.id());
            }
            if (!q.endDate().isAfter(valuationDate)) {
                throw new IllegalArgumentException("quote " + q.id() + ": end date must be after valuation date");
            }
        }
        sorted.sort(Comparator.comparing(Quote::endDate));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).endDate().equals(sorted.get(i - 1).endDate())) {
                throw new IllegalArgumentException("duplicate curve endpoint: " + sorted.get(i).endDate());
            }
        }

        List<LocalDate> nodeDates = new ArrayList<>();
        List<Double> logDfs = new ArrayList<>();
        for (Quote q : sorted) {
            double t = DayCount.yearFraction(valuationDate, q.endDate());
            double logD = switch (q) {
                case Quote.DepositQuote dep -> solveDeposit(dep);
                case Quote.SwapQuote swap -> solveSwap(swap, nodeDates, logDfs, t);
            };
            if (!Double.isFinite(logD)) {
                throw new IllegalStateException("quote " + q.id() + ": non-finite discount factor");
            }
            nodeDates.add(q.endDate());
            logDfs.add(logD);
        }

        List<Double> dfs = new ArrayList<>();
        for (double l : logDfs) {
            double d = Math.exp(l);
            if (!Double.isFinite(d) || d <= 0) {
                throw new IllegalStateException("discount factor must be positive and finite");
            }
            dfs.add(d);
        }
        Curve curve = new Curve(valuationDate, nodeDates, dfs);

        List<BootstrapResult.Repricing> repricings = new ArrayList<>();
        for (Quote q : sorted) {
            double implied = switch (q) {
                case Quote.DepositQuote dep -> {
                    double alpha = DayCount.yearFraction(valuationDate, dep.maturity());
                    yield (1.0 / curve.discountFactor(dep.maturity()) - 1.0) / alpha;
                }
                case Quote.SwapQuote swap -> {
                    double annuity = 0.0;
                    LocalDate prev = valuationDate;
                    for (LocalDate pay : swap.paymentDates()) {
                        annuity += DayCount.yearFraction(prev, pay) * curve.discountFactor(pay);
                        prev = pay;
                    }
                    yield (1.0 - curve.discountFactor(swap.endDate())) / annuity;
                }
            };
            double residual = implied - q.rate();
            if (!Double.isFinite(implied) || Math.abs(residual) > config.repricingTolerance()) {
                throw new IllegalStateException("quote " + q.id() + " failed repricing: residual " + residual
                        + " exceeds tolerance " + config.repricingTolerance());
            }
            repricings.add(new BootstrapResult.Repricing(q.id(), q.rate(), implied, residual));
        }
        return new BootstrapResult(curve, repricings);
    }

    private double solveDeposit(Quote.DepositQuote dep) {
        double alpha = DayCount.yearFraction(valuationDate, dep.maturity());
        double denominator = 1.0 + dep.rate() * alpha;
        if (denominator == 0.0 || !Double.isFinite(denominator)) {
            throw new IllegalArgumentException("quote " + dep.id() + ": zero or non-finite denominator 1 + r*alpha");
        }
        double d = 1.0 / denominator;
        if (!Double.isFinite(d) || d <= 0) {
            throw new IllegalArgumentException("quote " + dep.id() + ": discount factor must be positive and finite");
        }
        return Math.log(d);
    }

    private double solveSwap(Quote.SwapQuote swap, List<LocalDate> nodeDates, List<Double> logDfs, double endTime) {
        List<Double> payTimes = new ArrayList<>();
        List<Double> alphas = new ArrayList<>();
        LocalDate prev = valuationDate;
        for (LocalDate pay : swap.paymentDates()) {
            payTimes.add(DayCount.days(valuationDate, pay) / 365.0);
            alphas.add(DayCount.yearFraction(prev, pay));
            prev = pay;
        }
        double fixedRate = swap.rate();

        UnivariateFunction objective = x -> {
            double annuity = 0.0;
            for (int i = 0; i < payTimes.size(); i++) {
                annuity += alphas.get(i) * Math.exp(interpLogDf(nodeDates, logDfs, payTimes.get(i), endTime, x));
            }
            double dT = Math.exp(x);
            return fixedRate * annuity - (1.0 - dT);
        };

        BrentSolver solver = new BrentSolver(config.solverAccuracy());
        double lo = -50.0;
        double hi = 50.0;
        double x;
        try {
            x = solver.solve(config.maxEvaluations(), objective, lo, hi);
        } catch (NoBracketingException | TooManyEvaluationsException e) {
            throw new IllegalStateException("quote " + swap.id() + ": swap root solve failed: " + e.getMessage());
        }
        double residual = objective.value(x);
        if (!Double.isFinite(x) || Math.abs(residual) > 1e-8) {
            throw new IllegalStateException("quote " + swap.id() + ": swap root solve did not converge, residual " + residual);
        }
        return x;
    }

    /**
     * Log D at time t given known nodes plus a candidate pending node at
     * (candidateT, candidateLogD). Payments inside the new segment interpolate
     * against the pending node.
     */
    private double interpLogDf(List<LocalDate> nodeDates, List<Double> logDfs,
                               double t, double candidateT, double candidateLogD) {
        if (t <= 0.0) {
            return 0.0;
        }
        double prevT = 0.0;
        double prevL = 0.0;
        for (int i = 0; i < nodeDates.size(); i++) {
            double nodeT = DayCount.days(valuationDate, nodeDates.get(i)) / 365.0;
            if (t <= nodeT) {
                double w = (t - prevT) / (nodeT - prevT);
                return prevL + w * (logDfs.get(i) - prevL);
            }
            prevT = nodeT;
            prevL = logDfs.get(i);
        }
        double w = (t - prevT) / (candidateT - prevT);
        return prevL + w * (candidateLogD - prevL);
    }
}
