package rsp.app.posts.services;

import java.util.Random;

public final class OuSpikeSampleSource implements IntSampleSource {

    private final double mu;
    private final double theta;
    private final double sigma;
    private final double spikeRate;
    private final double spikePeakMin;
    private final double spikePeakMax;
    private final double decayFactor;
    private final Random random;

    private double baseline;
    private double spikeRemaining;

    public OuSpikeSampleSource(final double mu,
                               final double theta,
                               final double sigma,
                               final double spikeRate,
                               final double spikePeakMin,
                               final double spikePeakMax,
                               final double decayFactor,
                               final Random random) {
        if (theta <= 0 || theta > 1) {
            throw new IllegalArgumentException("theta must be in (0, 1]");
        }
        if (sigma < 0) {
            throw new IllegalArgumentException("sigma must be non-negative");
        }
        if (spikeRate < 0 || spikeRate > 1) {
            throw new IllegalArgumentException("spikeRate must be in [0, 1]");
        }
        if (spikePeakMin < 0 || spikePeakMax < spikePeakMin) {
            throw new IllegalArgumentException("invalid spike peak range");
        }
        if (decayFactor < 0 || decayFactor >= 1) {
            throw new IllegalArgumentException("decayFactor must be in [0, 1)");
        }
        this.mu = mu;
        this.theta = theta;
        this.sigma = sigma;
        this.spikeRate = spikeRate;
        this.spikePeakMin = spikePeakMin;
        this.spikePeakMax = spikePeakMax;
        this.decayFactor = decayFactor;
        this.random = random == null ? new Random() : random;
        this.baseline = mu;
        this.spikeRemaining = 0.0;
    }

    public static OuSpikeSampleSource commentsRateDefaults(final Random random) {
        return new OuSpikeSampleSource(
                120.0,
                0.08,
                6.0,
                1.0 / 18.0,
                120.0,
                200.0,
                0.72,
                random);
    }

    @Override
    public int next() {
        baseline = baseline + theta * (mu - baseline) + sigma * random.nextGaussian();
        if (random.nextDouble() < spikeRate) {
            spikeRemaining += spikePeakMin + random.nextDouble() * (spikePeakMax - spikePeakMin);
        }
        double value = baseline + spikeRemaining;
        spikeRemaining *= decayFactor;
        return Math.max(0, (int) Math.round(value));
    }
}
