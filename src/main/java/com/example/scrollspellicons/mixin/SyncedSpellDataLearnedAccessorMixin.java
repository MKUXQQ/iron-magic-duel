package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.api.magic.LearnedSpellData;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes Iron's learned-spell data only so main-thread writes share the encoder lock. */
@Mixin(value = SyncedSpellData.class, remap = false)
public interface SyncedSpellDataLearnedAccessorMixin {
    @Accessor("learnedSpellData")
    LearnedSpellData ironMagic$getLearnedSpellData();
}
