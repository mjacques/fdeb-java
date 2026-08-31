package com.github.mjacques.fdeb;

/**
 * Configuration for the Force-Directed Edge Bundling algorithm.
 * <p>
 * Default values follow the paper by Holten & van Wijk (2009)
 * and the reference implementation by pavlin-policar/FDEB.
 *
 * @param springConstant         K — global spring stiffness; higher values keep edges closer to their original path
 * @param stepSize               S — initial displacement step per iteration; controls how far points move each step
 * @param stepSizeReduction      factor to multiply S by after each cycle (e.g. 0.5 = halve)
 * @param cycles                 number of bundling cycles; each cycle refines the polyline resolution
 * @param initialIterations      number of force-application iterations in the first cycle
 * @param iterationReduction     factor to multiply iteration count by after each cycle (paper uses 2/3)
 * @param initialSubdivisionPoints number of interior points inserted along each edge at cycle 0 (1 = midpoint only)
 * @param subdivisionPointRate   multiplier for subdivision points between cycles (2 = double each cycle)
 * @param compatibilityThreshold minimum combined compatibility score Ca*Cs*Cp*Cv for two edges to interact
 * @param epsilon                small value to prevent division by zero in distance/length calculations
 */
public record FdebConfig(
        double springConstant,
        double stepSize,
        double stepSizeReduction,
        int cycles,
        int initialIterations,
        double iterationReduction,
        int initialSubdivisionPoints,
        int subdivisionPointRate,
        double compatibilityThreshold,
        double epsilon
) {

    public static Builder builder() {
        return new Builder();
    }

    public static FdebConfig defaults() {
        return builder().build();
    }

    public static final class Builder {
        private double springConstant = 0.1;
        private double stepSize = 0.04;
        private double stepSizeReduction = 0.5;
        private int cycles = 6;
        private int initialIterations = 60;
        private double iterationReduction = 2.0 / 3.0;
        private int initialSubdivisionPoints = 1;
        private int subdivisionPointRate = 2;
        private double compatibilityThreshold = 0.6;
        private double epsilon = 1e-8;

        private Builder() {}

        public Builder springConstant(double v) { this.springConstant = v; return this; }
        public Builder stepSize(double v) { this.stepSize = v; return this; }
        public Builder stepSizeReduction(double v) { this.stepSizeReduction = v; return this; }
        public Builder cycles(int v) { this.cycles = v; return this; }
        public Builder initialIterations(int v) { this.initialIterations = v; return this; }
        public Builder iterationReduction(double v) { this.iterationReduction = v; return this; }
        public Builder initialSubdivisionPoints(int v) { this.initialSubdivisionPoints = v; return this; }
        public Builder subdivisionPointRate(int v) { this.subdivisionPointRate = v; return this; }
        public Builder compatibilityThreshold(double v) { this.compatibilityThreshold = v; return this; }
        public Builder epsilon(double v) { this.epsilon = v; return this; }

        public FdebConfig build() {
            if (springConstant <= 0) throw new IllegalArgumentException("springConstant must be > 0, got " + springConstant);
            if (stepSize <= 0) throw new IllegalArgumentException("stepSize must be > 0, got " + stepSize);
            if (stepSizeReduction <= 0 || stepSizeReduction > 1) throw new IllegalArgumentException("stepSizeReduction must be in (0, 1], got " + stepSizeReduction);
            if (cycles < 1) throw new IllegalArgumentException("cycles must be >= 1, got " + cycles);
            if (initialIterations < 1) throw new IllegalArgumentException("initialIterations must be >= 1, got " + initialIterations);
            if (iterationReduction <= 0 || iterationReduction > 1) throw new IllegalArgumentException("iterationReduction must be in (0, 1], got " + iterationReduction);
            if (initialSubdivisionPoints < 1) throw new IllegalArgumentException("initialSubdivisionPoints must be >= 1, got " + initialSubdivisionPoints);
            if (subdivisionPointRate < 1) throw new IllegalArgumentException("subdivisionPointRate must be >= 1, got " + subdivisionPointRate);
            if (compatibilityThreshold < 0 || compatibilityThreshold > 1) throw new IllegalArgumentException("compatibilityThreshold must be in [0, 1], got " + compatibilityThreshold);
            if (epsilon <= 0) throw new IllegalArgumentException("epsilon must be > 0, got " + epsilon);

            return new FdebConfig(
                    springConstant, stepSize, stepSizeReduction,
                    cycles, initialIterations, iterationReduction,
                    initialSubdivisionPoints, subdivisionPointRate,
                    compatibilityThreshold, epsilon
            );
        }
    }
}
