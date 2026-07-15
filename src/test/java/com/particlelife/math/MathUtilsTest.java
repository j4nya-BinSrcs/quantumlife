package com.particlelife.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathUtilsTest {

    private static final double EPS = 1e-12;

    @Test
    void clampDouble() {
        assertEquals(2.0, MathUtils.clamp(5.0, 0.0, 2.0), EPS);
        assertEquals(0.0, MathUtils.clamp(-5.0, 0.0, 2.0), EPS);
        assertEquals(1.0, MathUtils.clamp(1.0, 0.0, 2.0), EPS);
    }

    @Test
    void clampInt() {
        assertEquals(2, MathUtils.clamp(5, 0, 2));
        assertEquals(0, MathUtils.clamp(-5, 0, 2));
    }

    @Test
    void lerpInterpolatesLinearly() {
        assertEquals(5.0, MathUtils.lerp(0.0, 10.0, 0.5), EPS);
        assertEquals(0.0, MathUtils.lerp(0.0, 10.0, 0.0), EPS);
        assertEquals(10.0, MathUtils.lerp(0.0, 10.0, 1.0), EPS);
    }

    @Test
    void wrapHandlesPositiveAndNegativeValues() {
        assertEquals(3.0, MathUtils.wrap(13.0, 10.0), EPS);
        assertEquals(7.0, MathUtils.wrap(-3.0, 10.0), EPS);
        assertEquals(0.0, MathUtils.wrap(10.0, 10.0), EPS);
        assertEquals(5.0, MathUtils.wrap(5.0, 10.0), EPS);
    }

    @Test
    void minimumImagePicksShortestPathAcrossSeam() {
        // Two points 9 apart in a size-10 world are actually 1 apart through the seam.
        assertEquals(-1.0, MathUtils.minimumImage(9.0, 10.0), EPS);
        assertEquals(1.0, MathUtils.minimumImage(-9.0, 10.0), EPS);
        // Short deltas are unchanged.
        assertEquals(3.0, MathUtils.minimumImage(3.0, 10.0), EPS);
        assertEquals(-4.0, MathUtils.minimumImage(-4.0, 10.0), EPS);
    }

    @Test
    void minimumImageResultAlwaysWithinHalfSize() {
        for (double d = -25.0; d <= 25.0; d += 0.37) {
            double m = MathUtils.minimumImage(MathUtils.wrap(d, 10.0) - 5.0, 10.0);
            assertTrue(Math.abs(m) <= 5.0 + EPS, "delta " + d + " -> " + m);
        }
    }
}
