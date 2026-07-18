package com.particlelife.particle;

import com.particlelife.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleStoreTest {

    private static final double EPS = 1e-12;

    @Test
    void spawnAssignsUniqueIdsAndStoresAttributes() {
        ParticleStore store = new ParticleStore(10);
        int a = store.spawn(0, 1, 2, 3, 2.0, 0.5);
        int b = store.spawn(1, 4, 5, 6, 3.0, 0.7);

        assertEquals(2, store.count());
        assertTrue(store.id(a) != store.id(b));
        assertEquals(0, store.speciesIndex(a));
        assertEquals(1, store.speciesIndex(b));
        assertEquals(2.0, store.masses()[a], EPS);
        assertEquals(0.7, store.radii()[b], EPS);

        Vector3 pos = store.position(b, new Vector3());
        assertEquals(new Vector3(4, 5, 6), pos);
    }

    @Test
    void spawnInitializesPreviousPositionToSpawnPoint() {
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 7, 8, 9, 1, 1);
        Particle p = store.view(i);
        assertEquals(new Vector3(7, 8, 9), p.previousPosition(new Vector3()));
    }

    @Test
    void spawnReturnsMinusOneWhenFull() {
        ParticleStore store = new ParticleStore(2);
        store.spawn(0, 0, 0, 0, 1, 1);
        store.spawn(0, 0, 0, 0, 1, 1);
        assertEquals(-1, store.spawn(0, 0, 0, 0, 1, 1));
        assertEquals(2, store.count());
    }

    @Test
    void killSwapRemovesKeepingLiveParticlesContiguous() {
        ParticleStore store = new ParticleStore(10);
        int a = store.spawn(0, 1, 1, 1, 1, 1);
        store.spawn(1, 2, 2, 2, 1, 1);
        int c = store.spawn(2, 3, 3, 3, 1, 1);
        long idC = store.id(c);

        store.kill(a); // c is swapped into slot 0
        assertEquals(2, store.count());
        assertEquals(idC, store.id(0));
        assertEquals(2, store.speciesIndex(0));
        assertEquals(new Vector3(3, 3, 3), store.position(0, new Vector3()));
    }

    @Test
    void killLastParticleShrinksCount() {
        ParticleStore store = new ParticleStore(4);
        store.spawn(0, 1, 1, 1, 1, 1);
        int b = store.spawn(1, 2, 2, 2, 1, 1);
        store.kill(b);
        assertEquals(1, store.count());
        assertEquals(0, store.speciesIndex(0));
    }

    @Test
    void idsRemainUniqueAcrossKillAndRespawn() {
        ParticleStore store = new ParticleStore(4);
        Set<Long> seen = new HashSet<>();
        for (int round = 0; round < 20; round++) {
            int i = store.spawn(0, 0, 0, 0, 1, 1);
            assertTrue(seen.add(store.id(i)), "id reused at round " + round);
            store.kill(i);
        }
    }

    @Test
    void clearRemovesAllParticles() {
        ParticleStore store = new ParticleStore(4);
        store.spawn(0, 0, 0, 0, 1, 1);
        store.spawn(1, 0, 0, 0, 1, 1);
        store.clear();
        assertEquals(0, store.count());
        assertThrows(IndexOutOfBoundsException.class, () -> store.id(0));
    }

    @Test
    void lifeStateDefaultsToAliveAndIsMutable() {
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(0, 0, 0, 0, 1, 1);
        assertEquals(LifeState.ALIVE, store.lifeState(i));
        store.setLifeState(i, LifeState.DORMANT);
        assertEquals(LifeState.DORMANT, store.lifeState(i));
    }

    @Test
    void viewExposesFullParticleModel() {
        ParticleStore store = new ParticleStore(4);
        int i = store.spawn(3, 1, 2, 3, 4.0, 0.25);
        store.setVelocity(i, 10, 0, 0);
        store.forces()[i * 3] = 8.0;

        Particle p = store.view(i);
        assertEquals(3, p.speciesIndex());
        assertEquals(4.0, p.mass(), EPS);
        assertEquals(0.25, p.radius(), EPS);
        assertEquals(new Vector3(1, 2, 3), p.position(new Vector3()));
        assertEquals(new Vector3(10, 0, 0), p.velocity(new Vector3()));
        assertEquals(new Vector3(8, 0, 0), p.force(new Vector3()));
        // acceleration = F / m
        assertEquals(new Vector3(2, 0, 0), p.acceleration(new Vector3()));
        assertEquals(LifeState.ALIVE, p.lifeState());
        assertEquals(0.0, p.maxVelocity(), EPS);
        assertEquals(0.0, p.damping(), EPS);
    }

    @Test
    void invalidIndicesAreRejected() {
        ParticleStore store = new ParticleStore(4);
        store.spawn(0, 0, 0, 0, 1, 1);
        assertThrows(IndexOutOfBoundsException.class, () -> store.id(1));
        assertThrows(IndexOutOfBoundsException.class, () -> store.id(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> store.kill(5));
    }

    @Test
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ParticleStore(0));
    }
}
