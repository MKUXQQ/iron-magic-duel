package com.example.scrollspellicons.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParticleBudgetTest {
    @Test
    void acceptsParticlesUntilTheFrameBudgetIsExhausted() {
        ParticleBudget budget = new ParticleBudget(1.0, 3);
        budget.beginFrame(10);

        assertTrue(budget.tryAccept(4.0, 2));
        assertTrue(budget.tryAccept(4.0, 1));
        assertFalse(budget.tryAccept(4.0, 1));
    }

    @Test
    void rejectsParticlesOutsideConfiguredDistance() {
        ParticleBudget budget = new ParticleBudget(0.5, 0);
        budget.beginFrame(1);

        assertTrue(budget.tryAccept(24.0, 1));
        assertFalse(budget.tryAccept(25.0, 1));
    }

    @Test
    void resetsBudgetWhenFrameChanges() {
        ParticleBudget budget = new ParticleBudget(1.0, 1);
        budget.beginFrame(1);
        assertTrue(budget.tryAccept(1.0, 1));
        assertFalse(budget.tryAccept(1.0, 1));

        budget.beginFrame(2);
        assertTrue(budget.tryAccept(1.0, 1));
    }
}
