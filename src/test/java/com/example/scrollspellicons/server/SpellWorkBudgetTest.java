package com.example.scrollspellicons.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpellWorkBudgetTest {
    @Test
    void consumesOnlyTheCurrentTickBudget() {
        SpellWorkBudget budget = new SpellWorkBudget();
        budget.beginTick(4, 100);

        assertTrue(budget.tryConsume(60));
        assertFalse(budget.tryConsume(41));
        assertEquals(40, budget.remainingNanos());

        budget.beginTick(5, 100);
        assertTrue(budget.tryConsume(100));
    }
}
