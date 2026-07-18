package com.particlelife.species;

import com.particlelife.math.DeterministicRandom;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeciesRegistryTest {

    @Test
    void createsRequestedNumberOfSpeciesWithArchetypeDefaults() {
        SpeciesRegistry registry = new SpeciesRegistry(5);
        assertEquals(5, registry.count());
        assertEquals("Alpha", registry.get(0).name());
        assertEquals("Epsilon", registry.get(4).name());
        assertEquals(SpeciesType.ALPHA.defaultColorRgb(), registry.get(0).colorRgb());
    }

    @Test
    void indicesAreContiguousAndStable() {
        SpeciesRegistry registry = new SpeciesRegistry(6);
        for (int i = 0; i < 6; i++) {
            assertEquals(i, registry.get(i).index());
        }
    }

    @Test
    void growingPreservesExistingCustomizations() {
        SpeciesRegistry registry = new SpeciesRegistry(3);
        registry.get(1).setName("Custom");
        registry.setCount(6);
        assertEquals(6, registry.count());
        assertEquals("Custom", registry.get(1).name());
        assertEquals("Delta", registry.get(3).name());
    }

    @Test
    void shrinkingDiscardsTail() {
        SpeciesRegistry registry = new SpeciesRegistry(6);
        registry.setCount(2);
        assertEquals(2, registry.count());
        assertThrows(IndexOutOfBoundsException.class, () -> registry.get(2));
    }

    @Test
    void countMustBeWithinSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> new SpeciesRegistry(0));
        assertThrows(IllegalArgumentException.class,
                () -> new SpeciesRegistry(SpeciesType.MAX_SPECIES + 1));
    }

    @Test
    void enabledFiltersDisabledSpecies() {
        SpeciesRegistry registry = new SpeciesRegistry(4);
        registry.get(2).setEnabled(false);
        assertEquals(3, registry.enabled().size());
        assertFalse(registry.enabled().stream().anyMatch(s -> s.index() == 2));
    }

    @Test
    void randomizeColorsIsDeterministicAndDistinct() {
        SpeciesRegistry a = new SpeciesRegistry(8);
        SpeciesRegistry b = new SpeciesRegistry(8);
        a.randomizeColors(new DeterministicRandom(11L));
        b.randomizeColors(new DeterministicRandom(11L));

        Set<Integer> colors = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            assertEquals(a.get(i).colorRgb(), b.get(i).colorRgb(), "same seed, same colors");
            colors.add(a.get(i).colorRgb());
        }
        assertEquals(8, colors.size(), "golden-angle stepping should give distinct colors");
    }

    @Test
    void resetAllRestoresDefaults() {
        SpeciesRegistry registry = new SpeciesRegistry(3);
        registry.get(0).setName("X");
        registry.get(0).setColorRgb(0x123456);
        registry.get(0).setEnabled(false);
        registry.resetAllToDefaults();
        assertEquals("Alpha", registry.get(0).name());
        assertEquals(SpeciesType.ALPHA.defaultColorRgb(), registry.get(0).colorRgb());
        assertTrue(registry.get(0).isEnabled());
    }

    @Test
    void speciesValidatesMassAndRadius() {
        Species s = Species.fromType(0);
        assertThrows(IllegalArgumentException.class, () -> s.setMass(0));
        assertThrows(IllegalArgumentException.class, () -> s.setRadius(-1));
    }

    @Test
    void hsvToRgbProducesPrimaryColors() {
        assertEquals(0xFF0000, SpeciesRegistry.hsvToRgb(0, 1, 1));
        assertEquals(0x00FF00, SpeciesRegistry.hsvToRgb(120, 1, 1));
        assertEquals(0x0000FF, SpeciesRegistry.hsvToRgb(240, 1, 1));
        assertEquals(0xFFFFFF, SpeciesRegistry.hsvToRgb(0, 0, 1));
    }
}
