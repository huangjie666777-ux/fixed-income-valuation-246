package rates246;

import java.util.List;

/** Bootstrapped curve plus per-instrument repriced values and residuals. */
public record BootstrapResult(Curve curve, List<Repricing> repricings) {
    public BootstrapResult {
        repricings = List.copyOf(repricings);
    }

    /** Repriced quote value (implied rate) and residual versus the market quote. */
    public record Repricing(String quoteId, double marketRate, double repricedRate, double residual) {}
}
