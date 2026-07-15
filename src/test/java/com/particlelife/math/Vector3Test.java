package com.particlelife.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Vector3Test {

    private static final double EPS = 1e-12;

    @Test
    void defaultConstructorIsZero() {
        Vector3 v = new Vector3();
        assertEquals(0.0, v.x, EPS);
        assertEquals(0.0, v.y, EPS);
        assertEquals(0.0, v.z, EPS);
    }

    @Test
    void addAccumulatesComponents() {
        Vector3 v = new Vector3(1, 2, 3).add(new Vector3(10, 20, 30));
        assertEquals(new Vector3(11, 22, 33), v);
    }

    @Test
    void addScaledIsFusedMultiplyAdd() {
        Vector3 v = new Vector3(1, 1, 1).addScaled(new Vector3(2, 4, 6), 0.5);
        assertEquals(new Vector3(2, 3, 4), v);
    }

    @Test
    void mutatingOperationsReturnSameInstanceForChaining() {
        Vector3 v = new Vector3();
        assertSame(v, v.set(1, 2, 3).add(1, 1, 1).scale(2.0).normalize());
    }

    @Test
    void lengthAndLengthSquared() {
        Vector3 v = new Vector3(3, 4, 12);
        assertEquals(169.0, v.lengthSquared(), EPS);
        assertEquals(13.0, v.length(), EPS);
    }

    @Test
    void normalizeProducesUnitLength() {
        Vector3 v = new Vector3(5, -3, 2).normalize();
        assertEquals(1.0, v.length(), EPS);
    }

    @Test
    void normalizeZeroVectorStaysZero() {
        Vector3 v = new Vector3().normalize();
        assertEquals(0.0, v.length(), EPS);
    }

    @Test
    void clampLengthShortensLongVectors() {
        Vector3 v = new Vector3(10, 0, 0).clampLength(2.0);
        assertEquals(2.0, v.length(), EPS);
        assertEquals(2.0, v.x, EPS);
    }

    @Test
    void clampLengthLeavesShortVectorsUntouched() {
        Vector3 v = new Vector3(1, 0, 0).clampLength(2.0);
        assertEquals(1.0, v.length(), EPS);
    }

    @Test
    void distanceToMatchesEuclideanDistance() {
        Vector3 a = new Vector3(1, 2, 3);
        Vector3 b = new Vector3(4, 6, 3);
        assertEquals(5.0, a.distanceTo(b), EPS);
        assertEquals(25.0, a.distanceSquaredTo(b), EPS);
    }

    @Test
    void dotProduct() {
        assertEquals(32.0, new Vector3(1, 2, 3).dot(new Vector3(4, 5, 6)), EPS);
    }

    @Test
    void copyIsIndependent() {
        Vector3 a = new Vector3(1, 2, 3);
        Vector3 b = a.copy();
        assertNotSame(a, b);
        b.set(9, 9, 9);
        assertEquals(new Vector3(1, 2, 3), a);
    }

    @Test
    void isFiniteDetectsNanAndInfinity() {
        assertTrue(new Vector3(1, 2, 3).isFinite());
        assertFalse(new Vector3(Double.NaN, 0, 0).isFinite());
        assertFalse(new Vector3(0, Double.POSITIVE_INFINITY, 0).isFinite());
    }
}
