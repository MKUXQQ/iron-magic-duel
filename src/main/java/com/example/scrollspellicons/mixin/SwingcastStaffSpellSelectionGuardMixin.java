package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager.SpellSelectionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents an invalid replay-only spell-selection event from reaching the
 * third-party handler, which assumes a non-null player. */
@Pseudo
@Mixin(targets = "jp.aquafactory.apprenticecodex.item.swingstaff.SwingcastStaffSpellSelectionEvents", remap = false)
public abstract class SwingcastStaffSpellSelectionGuardMixin {
    @Inject(method = "onSpellSelection", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$skipNullPlayer(SpellSelectionEvent event, CallbackInfo ci) {
        if (event == null || event.getEntity() == null) {
            ci.cancel();
        }
    }
}
