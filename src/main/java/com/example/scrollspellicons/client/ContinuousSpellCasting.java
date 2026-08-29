package com.example.scrollspellicons.client;

import com.example.scrollspellicons.IronSpellPerformance;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.network.casting.CastPacket;
import io.redspace.ironsspellbooks.network.casting.QuickCastPacket;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.player.KeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;

/** Sends repeat packets while a spell key remains held; magic missile is excluded. */
@EventBusSubscriber(modid = IronSpellPerformance.MOD_ID, value = Dist.CLIENT)
public final class ContinuousSpellCasting {
    private static final ResourceLocation MAGIC_MISSILE_ID = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "magic_missile");
    private static final Set<Integer> HELD_QUICK_CAST_SLOTS = new HashSet<>();
    private static final Map<Integer, ResourceLocation> HELD_QUICK_CAST_SPELLS = new HashMap<>();
    private static boolean activeSpellKeyWasDown;
    private static ResourceLocation activeHeldSpellId;

    private ContinuousSpellCasting() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        QuickCastKeyUniqueness.normalizeLoadedMappings();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            clearHeldKeys();
            SprayClientDiagnostic.clear();
            return;
        }

        // Repeat packets are sent only after the current cast has ended.
        // Sending a packet while isCasting() is still true enters Iron's
        // replacement path and can cancel an active chant or spray.
        SpellSelectionManager selection = ClientMagicData.getSpellSelectionManager();
        boolean activeDown = KeyMappings.SPELLBOOK_CAST_ACTIVE_KEYMAP.isDown();
        if (activeDown && !activeSpellKeyWasDown) activeHeldSpellId = spellId(selection.getSelectedSpellData());
        SpellData activeSpell = selection.getSelectedSpellData();
        SprayClientDiagnostic.observe(minecraft, activeSpell, activeDown);
        if (activeDown && activeSpellKeyWasDown && shouldRearmThisTick(minecraft) && canRepeat(activeSpell)) {
            if (activeHeldSpellId != null && activeHeldSpellId.equals(spellId(activeSpell))
                    && canContinueOrRestart(activeSpell)) {
                SprayClientDiagnostic.recoveryPacket(minecraft, spellId(activeSpell));
                PacketDistributor.sendToServer(new CastPacket());
            }
        }
        activeSpellKeyWasDown = activeDown;
        if (!activeDown) activeHeldSpellId = null;

        Set<Integer> currentlyHeld = new HashSet<>();
        Set<com.mojang.blaze3d.platform.InputConstants.Key> dispatchedKeys = new HashSet<>();
        for (int slot = 0; slot < KeyMappings.QUICK_CAST_MAPPINGS.size(); slot++) {
            if (!dispatchedKeys.add(KeyMappings.QUICK_CAST_MAPPINGS.get(slot).getKey())) continue;
            if (!KeyMappings.QUICK_CAST_MAPPINGS.get(slot).isDown()) continue;
            SpellData spellData = selection.getSpellData(slot);
            currentlyHeld.add(slot);
            if (!HELD_QUICK_CAST_SLOTS.contains(slot)) HELD_QUICK_CAST_SPELLS.put(slot, spellId(spellData));
            if (HELD_QUICK_CAST_SLOTS.contains(slot) && shouldRearmThisTick(minecraft) && canRepeat(spellData)
                    && HELD_QUICK_CAST_SPELLS.get(slot) != null
                    && HELD_QUICK_CAST_SPELLS.get(slot).equals(spellId(spellData))
                    && canContinueOrRestart(spellData)) {
                SprayClientDiagnostic.recoveryPacket(minecraft, spellId(spellData));
                PacketDistributor.sendToServer(new QuickCastPacket(slot));
            }
        }
        HELD_QUICK_CAST_SLOTS.clear();
        HELD_QUICK_CAST_SLOTS.addAll(currentlyHeld);
        HELD_QUICK_CAST_SPELLS.keySet().removeIf(slot -> !currentlyHeld.contains(slot));
    }

    private static boolean isRepeatable(SpellData spellData) {
        return spellData != null && spellData != SpellData.EMPTY
                && !MAGIC_MISSILE_ID.equals(spellData.getSpell().getSpellResource());
    }

    private static ResourceLocation spellId(SpellData spellData) {
        return spellData == null || spellData == SpellData.EMPTY ? null : spellData.getSpell().getSpellResource();
    }

    /** Every selected spell can repeat while held, except the disabled missile. */
    private static boolean canRepeat(SpellData spellData) {
        return isRepeatable(spellData);
    }

    /**
     * Keeps a held spell recoverable if a cast reset the client flag. A packet
     * is never sent while the original cast is alive, so channeling remains
     * uninterruptible; as soon as it ends the held key sends the next packet.
     */
    private static boolean canContinueOrRestart(SpellData spellData) {
        return !ClientMagicData.isCasting();
    }

    /**
     * The server re-arms cone damage every tick. This client cadence only
     * limits recovery packets after a cast has ended, avoiding a packet storm
     * while preserving the held-key repeat behavior.
     */
    private static boolean shouldRearmThisTick(Minecraft minecraft) {
        return minecraft.player != null && (minecraft.player.tickCount & 1) == 0;
    }

    private static void clearHeldKeys() {
        activeSpellKeyWasDown = false;
        activeHeldSpellId = null;
        HELD_QUICK_CAST_SLOTS.clear();
        HELD_QUICK_CAST_SPELLS.clear();
    }
}
