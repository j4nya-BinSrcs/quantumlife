package com.particlelife.forces;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceFunctionTest {

    private static final double EPS = 1e-9;
    private static final double BETA = 0.3;

    private final ForceFunction linear = new PiecewiseLinearForce(BETA);
    private final ForceFunction smooth = new SmoothForce(BETA);

    @Test
    void contactRepulsionIsMinusOneRegardlessOfAttraction() {
        assertEquals(-1.0, linear.evaluate(0.0, 1.0), EPS);
        assertEquals(-1.0, linear.evaluate(0.0, -1.0), EPS);
        assertEquals(-1.0, smooth.evaluate(0.0, 1.0), EPS);
    }

    @Test
    void repulsionZoneIsIndependentOfMatrixEntry() {
        for (double x = 0.0; x < BETA; x += 0.05) {
            assertEquals(linear.evaluate(x, 1.0), linear.evaluate(x, -1.0), EPS);
            assertTrue(linear.evaluate(x, 1.0) <= 0.0, "must repel below beta");
        }
    }

    @Test
    void forceIsZeroAtBetaBoundary() {
        assertEquals(0.0, linear.evaluate(BETA, 0.7), EPS);
        assertEquals(0.0, smooth.evaluate(BETA, 0.7), EPS);
    }

    @Test
    void peakEqualsAttractionAtMidpoint() {
        double midpoint = (1.0 + BETA) / 2.0;
        assertEquals(0.8, linear.evaluate(midpoint, 0.8), EPS);
        assertEquals(-0.6, linear.evaluate(midpoint, -0.6), EPS);
        assertEquals(0.8, smooth.evaluate(midpoint, 0.8), EPS);
    }

    @Test
    void forceFadesToZeroAtInteractionRadius() {
        assertEquals(0.0, linear.evaluate(1.0, 1.0), EPS);
        assertEquals(0.0, linear.evaluate(1.5, 1.0), EPS);
        assertEquals(0.0, smooth.evaluate(1.0, 1.0), EPS);
        // Approach to the edge is continuous.
        assertEquals(0.0, linear.evaluate(0.999999, 1.0), 1e-4);
        assertEquals(0.0, smooth.evaluate(0.999999, 1.0), 1e-4);
    }

    @Test
    void signFollowsAttractionInOuterZone() {
        double x = 0.6;
        assertTrue(linear.evaluate(x, 0.5) > 0.0);
        assertTrue(linear.evaluate(x, -0.5) < 0.0);
        assertEquals(0.0, linear.evaluate(x, 0.0), EPS);
    }

    @Test
    void kernelIsBounded() {
        for (ForceFunction f : new ForceFunction[] {linear, smooth}) {
            for (double x = 0.0; x <= 1.2; x += 0.01) {
                for (double a = -1.0; a <= 1.0; a += 0.25) {
                    double v = f.evaluate(x, a);
                    assertTrue(Math.abs(v) <= 1.0 + EPS,
                            "%s unbounded at x=%.2f a=%.2f: %f".formatted(f, x, a, v));
                }
            }
        }
    }

    @Test
    void linearKernelIsContinuousEverywhere() {
        double a = 0.7;
        double prev = linear.evaluate(0.0, a);
        for (double x = 0.0005; x <= 1.1; x += 0.0005) {
            double v = linear.evaluate(x, a);
            assertTrue(Math.abs(v - prev) < 0.01, "jump at x=" + x);
            prev = v;
        }
    }

    @Test
    void betaMustBeInOpenUnitInterval() {
        assertThrows(IllegalArgumentException.class, () -> new PiecewiseLinearForce(0.0));
        assertThrows(IllegalArgumentException.class, () -> new PiecewiseLinearForce(1.0));
        assertThrows(IllegalArgumentException.class, () -> new SmoothForce(-0.1));
    }

    @Test
    void factoryCreatesMatchingStrategy() {
        assertTrue(ForceFunctionType.PIECEWISE_LINEAR.create(0.25) instanceof PiecewiseLinearForce);
        assertTrue(ForceFunctionType.SMOOTH.create(0.25) instanceof SmoothForce);
        assertEquals(0.25, ForceFunctionType.PIECEWISE_LINEAR.create(0.25).beta(), EPS);
    }
}
