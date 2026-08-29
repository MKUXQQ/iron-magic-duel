package com.example.scrollspellicons.mixin;

import com.example.scrollspellicons.client.QuickCastKeyUniqueness;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject duplicate bindings only for Iron's quick-cast slots. */
@Mixin(value = KeyMapping.class, remap = true)
public abstract class QuickCastKeyMappingMixin {
    @Inject(method = "setKey", at = @At("HEAD"), cancellable = true)
    private void ironMagicDuel$rejectDuplicateQuickCast(InputConstants.Key requested, CallbackInfo ci) {
        if (!QuickCastKeyUniqueness.allowChange((KeyMapping) (Object) this, requested)) ci.cancel();
    }
}
