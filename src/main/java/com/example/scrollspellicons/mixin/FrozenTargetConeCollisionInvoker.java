package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.entity.spells.ConePart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/** Accesses Iron's original protected cone handlers without replacing spell-specific effects. */
@Mixin(value = AbstractConeProjectile.class, remap = false)
public interface FrozenTargetConeCollisionInvoker {
    @Invoker("getSubEntityCollisions")
    Set<Entity> ironMagicDuel$getSubEntityCollisions();

    @Invoker("onHitEntity")
    void ironMagicDuel$invokeOnHitEntity(EntityHitResult hit);

    @Accessor("subEntities")
    ConePart[] ironMagicDuel$getSubEntities();

    @Accessor("dealDamageActive")
    boolean ironMagicDuel$isDealDamageActive();
}
