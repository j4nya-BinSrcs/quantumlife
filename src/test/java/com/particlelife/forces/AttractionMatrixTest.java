package com.particlelife.forces;

import com.particlelife.math.DeterministicRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttractionMatrixTest {

    private static final double EPS = 1e-12;

    @Test
    void newMatrixIsZero() {
        AttractionMatrix m = new AttractionMatrix(4);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(0.0, m.get(i, j), EPS);
            }
        }
    }

    @Test
    void setAndGetRoundTrip() {
        AttractionMatrix m = new AttractionMatrix(3);
        m.set(0, 2, 0.75);
        m.set(2, 0, -0.5);
        assertEquals(0.75, m.get(0, 2), EPS);
        assertEquals(-0.5, m.get(2, 0), EPS);
        assertEquals(0.0, m.get(1, 1), EPS);
    }

    @Test
    void asymmetryIsAllowedByDefault() {
        AttractionMatrix m = new AttractionMatrix(2);
        m.set(0, 1, 1.0);
        m.set(1, 0, -1.0);
        assertNotEquals(m.get(0, 1), m.get(1, 0));
    }

    @Test
    void valuesAreClampedToUnitRange() {
        AttractionMatrix m = new AttractionMatrix(2);
        m.set(0, 1, 5.0);
        m.set(1, 0, -5.0);
        assertEquals(1.0, m.get(0, 1), EPS);
        assertEquals(-1.0, m.get(1, 0), EPS);
    }

    @Test
    void symmetricModeMirrorsEdits() {
        AttractionMatrix m = new AttractionMatrix(3);
        m.setSymmetric(true);
        m.set(0, 2, 0.6);
        assertEquals(0.6, m.get(2, 0), EPS);
    }

    @Test
    void enablingSymmetricModeAveragesExistingPairs() {
        AttractionMatrix m = new AttractionMatrix(2);
        m.set(0, 1, 1.0);
        m.set(1, 0, 0.0);
        m.setSymmetric(true);
        assertEquals(0.5, m.get(0, 1), EPS);
        assertEquals(0.5, m.get(1, 0), EPS);
    }

    @Test
    void randomizeIsDeterministicAndInRange() {
        AttractionMatrix a = new AttractionMatrix(6);
        AttractionMatrix b = new AttractionMatrix(6);
        a.randomize(new DeterministicRandom(5L));
        b.randomize(new DeterministicRandom(5L));
        boolean anyNonZero = false;
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                assertEquals(a.get(i, j), b.get(i, j), EPS);
                assertTrue(Math.abs(a.get(i, j)) <= 1.0);
                anyNonZero |= Math.abs(a.get(i, j)) > EPS;
            }
        }
        assertTrue(anyNonZero);
    }

    @Test
    void randomizeInSymmetricModeStaysSymmetric() {
        AttractionMatrix m = new AttractionMatrix(5);
        m.setSymmetric(true);
        m.randomize(new DeterministicRandom(9L));
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                assertEquals(m.get(i, j), m.get(j, i), EPS);
            }
        }
    }

    @Test
    void resizePreservesOverlappingBlock() {
        AttractionMatrix m = new AttractionMatrix(3);
        m.set(0, 1, 0.3);
        m.set(2, 2, -0.7);
        m.resize(5);
        assertEquals(5, m.size());
        assertEquals(0.3, m.get(0, 1), EPS);
        assertEquals(-0.7, m.get(2, 2), EPS);
        assertEquals(0.0, m.get(4, 4), EPS);

        m.resize(2);
        assertEquals(2, m.size());
        assertEquals(0.3, m.get(0, 1), EPS);
    }

    @Test
    void toArrayAndSetFromRoundTrip() {
        AttractionMatrix m = new AttractionMatrix(3);
        m.randomize(new DeterministicRandom(1L));
        double[][] arr = m.toArray();

        AttractionMatrix restored = new AttractionMatrix(1);
        restored.setFrom(arr);
        assertEquals(3, restored.size());
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(m.get(i, j), restored.get(i, j), EPS);
            }
        }
    }

    @Test
    void setFromRejectsNonSquareInput() {
        AttractionMatrix m = new AttractionMatrix(2);
        assertThrows(IllegalArgumentException.class,
                () -> m.setFrom(new double[][] {{1, 2}, {3}}));
    }

    @Test
    void copyIsDeepAndIndependent() {
        AttractionMatrix m = new AttractionMatrix(3);
        m.randomize(new DeterministicRandom(2L));
        AttractionMatrix c = m.copy();
        c.set(0, 0, 0.999);
        assertNotEquals(m.get(0, 0), c.get(0, 0));
        assertEquals(m.size(), c.size());
    }

    @Test
    void resetZeroesEverything() {
        AttractionMatrix m = new AttractionMatrix(3);
        m.randomize(new DeterministicRandom(3L));
        m.reset();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(0.0, m.get(i, j), EPS);
            }
        }
    }
}
