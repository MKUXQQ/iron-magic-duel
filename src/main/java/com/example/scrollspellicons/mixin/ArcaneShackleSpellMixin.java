package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.entity.spells.ender_chain.EnderChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Sets only Arcane Shackle's generated EnderChain health to one point. */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.spells.ender.ArcaneShackleSpell", remap = false)
public abstract class ArcaneShackleSpellMixin {
    @Redirect(method = "onCast", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/entity/spells/ender_chain/EnderChain;setHealth(F)V"), remap = false)
    private void ironMagicDuel$oneHeartChain(EnderChain chain, float ignored) {
        chain.setHealth(1.0F);
        chain.getPersistentData().putBoolean("IronMagicArcaneShackle", true);
    }
}
