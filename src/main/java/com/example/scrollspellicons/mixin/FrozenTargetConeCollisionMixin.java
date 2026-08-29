package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.entity.spells.ConePart;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.example.scrollspellicons.duel.SpellDuelEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Prevents a live cone from repeatedly colliding with a frozen/controlled target. */
@Mixin(value = AbstractConeProjectile.class, remap = false)
public abstract class FrozenTargetConeCollisionMixin {
    @Shadow @Final
    protected ConePart[] subEntities;

    /* Keep Iron's normal line-of-sight and cadence, but reject every target
       carrying a freeze/control state before it can be hit again. */
    @Overwrite(remap = false)
    protected Set<Entity> getSubEntityCollisions() {
        AbstractConeProjectile cone = (AbstractConeProjectile) (Object) this;
        List<Entity> candidates = new ArrayList<>();
        for (ConePart part : subEntities) {
            candidates.addAll(cone.level().getEntities(cone, part.getBoundingBox()));
        }
        return candidates.stream()
                .filter(target -> target != cone.getOwner()
                        && target instanceof LivingEntity living
                        && !living.isDeadOrDying()
                        && !SpellDuelEvents.isFrozenConeTarget(living)
                        && Utils.hasLineOfSight(cone.level(), cone, target, true))
                .collect(Collectors.toSet());
    }
}
