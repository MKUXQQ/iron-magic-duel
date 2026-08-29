package com.example.scrollspellicons.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes Iron's cooldown-only ActionBar message before the server sends it to a player. */
@Mixin(ServerPlayer.class)
public abstract class CooldownActionbarMixin {
    private static final String IRON_COOLDOWN_MESSAGE = "ui.irons_spellbooks.cast_error_cooldown";

    @Inject(method = "displayClientMessage", at = @At("HEAD"), cancellable = true)
    private void ironMagicDuel$hideCooldownActionbar(Component message, boolean actionBar, CallbackInfo ci) {
        if (actionBar
                && message.getContents() instanceof TranslatableContents translated
                && IRON_COOLDOWN_MESSAGE.equals(translated.getKey())) {
            ci.cancel();
        }
    }
}
