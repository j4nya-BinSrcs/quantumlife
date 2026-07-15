package com.particlelife.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicRandomTest {

    @Test
    void sameSeedProducesIdenticalSequence() {
        DeterministicRandom a = new DeterministicRandom(42L);
        DeterministicRandom b = new DeterministicRandom(42L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
    }

    @Test
    void differentSeedsProduceDifferentSequences() {
        DeterministicRandom a = new DeterministicRandom(1L);
        DeterministicRandom b = new DeterministicRandom(2L);
        assertNotEquals(a.nextLong(), b.nextLong());
    }

    @Test
    void nextDoubleStaysInUnitInterval() {
        DeterministicRandom rng = new DeterministicRandom(7L);
        for (int i = 0; i < 10_000; i++) {
            double d = rng.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0, "out of range: " + d);
        }
    }

    @Test
    void nextDoubleRangeRespectsBounds() {
        DeterministicRandom rng = new DeterministicRandom(7L);
        for (int i = 0; i < 10_000; i++) {
            double d = rng.nextDouble(-2.5, 3.5);
            assertTrue(d >= -2.5 && d < 3.5, "out of range: " + d);
        }
    }

    @Test
    void nextIntRespectsBoundAndRejectsInvalid() {
        DeterministicRandom rng = new DeterministicRandom(7L);
        for (int i = 0; i < 10_000; i++) {
            int n = rng.nextInt(13);
            assertTrue(n >= 0 && n < 13);
        }
        assertThrows(IllegalArgumentException.class, () -> rng.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> rng.nextInt(-5));
    }

    @Test
    void nextDoubleIsRoughlyUniform() {
        DeterministicRandom rng = new DeterministicRandom(123L);
        int[] buckets = new int[10];
        int samples = 100_000;
        for (int i = 0; i < samples; i++) {
            buckets[(int) (rng.nextDouble() * 10)]++;
        }
        for (int count : buckets) {
            // Each bucket should hold ~10% of samples; allow generous tolerance.
            assertTrue(Math.abs(count - samples / 10.0) < samples * 0.01,
                    "bucket count " + count + " deviates too far from uniform");
        }
    }

    @Test
    void nextInSphereStaysInsideRadius() {
        DeterministicRandom rng = new DeterministicRandom(99L);
        Vector3 v = new Vector3();
        for (int i = 0; i < 10_000; i++) {
            rng.nextInSphere(5.0, v);
            assertTrue(v.length() <= 5.0 + 1e-9);
        }
    }

    @Test
    void splitProducesIndependentStream() {
        DeterministicRandom parent = new DeterministicRandom(42L);
        DeterministicRandom child = parent.split();
        assertNotEquals(parent.nextLong(), child.nextLong());
        // A split from the same parent state is reproducible.
        DeterministicRandom parent2 = new DeterministicRandom(42L);
        DeterministicRandom child2 = parent2.split();
        child2.nextLong(); // advance to match child, which was consumed once above
        assertEquals(child.nextLong(), child2.nextLong());
    }
}
