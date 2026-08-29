package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.api.magic.LearnedSpellData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Set;

/** Serializes a stable learned-spell snapshot while Iron's sync packet is encoded. */
@Mixin(value = LearnedSpellData.class, remap = false)
public abstract class LearnedSpellDataWriteSnapshotMixin {
    /** @reason The upstream set is mutable while sync_player_data is encoded. */
    @Overwrite
    public void writeToBuffer(FriendlyByteBuf buffer) {
        LearnedSpellData data = (LearnedSpellData) (Object) this;
        Set<ResourceLocation> snapshot;
        synchronized (data.learnedSpells) {
            snapshot = Set.copyOf(data.learnedSpells);
        }
        buffer.writeInt(snapshot.size());
        for (ResourceLocation spellId : snapshot) {
            buffer.writeResourceLocation(spellId);
        }
    }
}
