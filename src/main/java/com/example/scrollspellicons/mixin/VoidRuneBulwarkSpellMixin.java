package com.example.scrollspellicons.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tags only the rune instances created by cataclysm_spellbooks:void_bulwark.
 * Cataclysm's Void_Rune_Entity is also used by the unrelated void_rune spell,
 * so a class-name-only damage rule would incorrectly change that spell too.
 */
@Pseudo
@Mixin(targets = "net.acetheeldritchking.cataclysm_spellbooks.spells.ender.VoidRuneBulwarkSpell", remap = false)
public abstract class VoidRuneBulwarkSpellMixin {
    private static final String VOID_BULWARK_TAG = "IronMagicVoidBulwarkRune";

    @Redirect(
            method = "spawnVoidRune",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            remap = false)
    private boolean ironMagicDuel$tagBulwarkRune(Level level, Entity entity) {
        entity.getPersistentData().putBoolean(VOID_BULWARK_TAG, true);
        entity.getPersistentData().putFloat("IronMagicVoidBulwarkDamage", 10.0F);
        return level.addFreshEntity(entity);
    }
}
