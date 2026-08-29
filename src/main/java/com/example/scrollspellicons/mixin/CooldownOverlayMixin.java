package com.example.scrollspellicons.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Final client-side guard: no Iron cooldown notice may reach the ActionBar overlay. */
@Mixin(Gui.class)
public abstract class CooldownOverlayMixin {
    private static final String IRON_COOLDOWN_MESSAGE = "ui.irons_spellbooks.cast_error_cooldown";

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void ironMagicDuel$hideCooldownOverlay(Component message, boolean animateColor, CallbackInfo ci) {
        if (isCooldownNotice(message)) {
            ci.cancel();
        }
    }

    private static boolean isCooldownNotice(Component message) {
        if (message.getContents() instanceof TranslatableContents translated
                && IRON_COOLDOWN_MESSAGE.equals(translated.getKey())) {
            return true;
        }
        String text = message.getString();
        return text.contains("正在冷却") || text.contains("冷却中") || text.toLowerCase(java.util.Locale.ROOT).contains("on cooldown");
    }
}
