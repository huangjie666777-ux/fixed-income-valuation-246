package rates246;

/** Solver budget and repricing tolerance for bootstrapping. */
public record CurveConfig(int maxEvaluations, double solverAccuracy, double repricingTolerance) {
    public CurveConfig {
        if (maxEvaluations <= 0) {
            throw new IllegalArgumentException("maxEvaluations must be positive");
        }
        if (!Double.isFinite(solverAccuracy) || solverAccuracy <= 0) {
            throw new IllegalArgumentException("solverAccuracy must be positive and finite");
        }
        if (!Double.isFinite(repricingTolerance) || repricingTolerance <= 0) {
            throw new IllegalArgumentException("repricingTolerance must be positive and finite");
        }
    }

    public static CurveConfig defaults() {
        return new CurveConfig(1000, 1e-14, 1e-10);
    }
}
