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
        return Optional.of(ResourceLocation.fromNamespaceAndPath(
                spellId.getNamespace(), "textures/gui/spell_icons/" + spellName + ".png"));
    }
}
