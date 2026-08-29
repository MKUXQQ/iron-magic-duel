package com.example.scrollspellicons.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create's asynchronous search-tree tooltip path can ask GLFW about the
 * unbound key (-1) off the render thread.  Return false for that invalid
 * query only; valid render-thread key checks remain Create's implementation.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.AllKeys", remap = false)
public abstract class CreateAllKeysThreadGuardMixin {
    @Inject(method = "isKeyDown(I)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$guardOffThreadKey(int key, CallbackInfoReturnable<Boolean> cir) {
        if (key < 0 || !RenderSystem.isOnRenderThread()) {
            cir.setReturnValue(false);
        }
    }
}
