package com.example.scrollspellicons.spells;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Registers custom spells into Iron's Spell Registry without touching addon spells. */
@EventBusSubscriber(modid = "iron_magic_duel", bus = EventBusSubscriber.Bus.MOD)
public final class DuelSpellRegistry {
    private DuelSpellRegistry() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(SpellRegistry.SPELL_REGISTRY_KEY,
                CrosswindIronSlashSpell.ID,
                CrosswindIronSlashSpell::new);
        event.register(SpellRegistry.SPELL_REGISTRY_KEY,
                PhantomHalberdRingSpell.ID,
                PhantomHalberdRingSpell::new);
        event.register(SpellRegistry.SPELL_REGISTRY_KEY,
                AstralPredatorSpell.ID,
                AstralPredatorSpell::new);
        event.register(SpellRegistry.SPELL_REGISTRY_KEY,
                BlazingDragonCorridorSpell.ID,
                BlazingDragonCorridorSpell::new);
    }
}
