package com.example.scrollspellicons.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollIconResolverTest {
    @Test
    void mapsVanillaSpellToItsGuiIcon() {
        assertEquals(ResourceLocation.parse("irons_spellbooks:textures/gui/spell_icons/fireball.png"),
                ScrollIconResolver.iconFor(ResourceLocation.parse("irons_spellbooks:fireball")).orElseThrow());
    }

    @Test
    void preservesAddonNamespaceAndPath() {
        assertEquals(ResourceLocation.parse("addon_magic:textures/gui/spell_icons/rituals/meteor.png"),
                ScrollIconResolver.iconFor(ResourceLocation.parse("addon_magic:rituals/meteor")).orElseThrow());
    }

    @Test
    void rejectsTheEmptySpell() {
        assertTrue(ScrollIconResolver.iconFor(ResourceLocation.parse("irons_spellbooks:none")).isEmpty());
    }
}
