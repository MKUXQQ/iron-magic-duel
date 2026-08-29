package com.example.scrollspellicons.client;

import io.redspace.ironsspellbooks.player.KeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Keeps Iron's quick-cast slots one-key-per-slot, with slot 01 winning ties. */
public final class QuickCastKeyUniqueness {
    private QuickCastKeyUniqueness() {}

    public static boolean isQuickCast(KeyMapping mapping) {
        try {
            return KeyMappings.QUICK_CAST_MAPPINGS.contains(mapping);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Called by the KeyMapping mixin before a key is saved. */
    public static boolean allowChange(KeyMapping mapping, InputConstants.Key requested) {
        if (!isQuickCast(mapping) || requested == null || requested.equals(InputConstants.UNKNOWN)) return true;
        List<? extends KeyMapping> mappings = KeyMappings.QUICK_CAST_MAPPINGS;
        int index = mappings.indexOf(mapping);
        for (int i = 0; i < index; i++) {
            KeyMapping earlier = mappings.get(i);
            if (requested.equals(earlier.getKey())) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.translatable(
                            "key.iron_magic_duel.quick_cast_duplicate", i + 1), true);
                }
                return false;
            }
        }
        return true;
    }

    /** Repairs old options files deterministically: the first slot keeps its key. */
    public static void normalizeLoadedMappings() {
        try {
            Set<InputConstants.Key> used = new HashSet<>();
            for (KeyMapping mapping : KeyMappings.QUICK_CAST_MAPPINGS) {
                InputConstants.Key key = mapping.getKey();
                if (key == null || key.equals(InputConstants.UNKNOWN)) continue;
                if (!used.add(key)) mapping.setKey(InputConstants.UNKNOWN);
            }
        } catch (RuntimeException ignored) {
            // The list is optional on clients without Iron's spellbook key set.
        }
    }
}
