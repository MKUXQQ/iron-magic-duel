package com.example.scrollspellicons.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public final class ScrollIconResolver {
    private ScrollIconResolver() {
    }

    public static Optional<ResourceLocation> iconFor(ResourceLocation spellId) {
        if (spellId == null || spellId.getPath().isEmpty()
                || ("irons_spellbooks".equals(spellId.getNamespace()) && "none".equals(spellId.getPath()))) {
            return Optional.empty();
        }
        return iconFor(spellId, spellId.getPath());
    }

    public static Optional<ResourceLocation> iconFor(ResourceLocation spellId, String spellName) {
        if (spellId == null || spellName == null || spellName.isEmpty()
                || ("irons_spellbooks".equals(spellId.getNamespace()) && "none".equals(spellId.getPath()))) {
            return Optional.empty();
        }
        // Our authored spells use stable resource IDs while their translated
        // display names are intentionally Chinese.  Never turn the translated
        // name into a filename, otherwise astral_predator (and the other duel
        // spells) silently fall back to the old icon.
        if ("iron_magic_duel".equals(spellId.getNamespace())) {
            spellName = spellId.getPath();
        }
        return Optional.of(ResourceLocation.fromNamespaceAndPath(
                spellId.getNamespace(), "textures/gui/spell_icons/" + spellName + ".png"));
    }
}
