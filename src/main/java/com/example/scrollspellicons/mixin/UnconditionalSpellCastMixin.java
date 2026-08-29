package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.example.scrollspellicons.duel.SpellDuelEvents;

/**
 * Duel rules remove spell-book/scroll/learning and addon-specific pre-cast
 * restrictions, while deliberately preserving Iron's Spells' native
 * per-spell cooldown.  Mana remains unlimited for duel casting.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class UnconditionalSpellCastMixin {
    private static final String DUEL_NAMESPACE = "iron_magic_duel";
    private static final ResourceLocation MAGIC_MISSILE =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "magic_missile");

    /**
     * Iron checks MagicData.isCasting before it checks the requested spell's
     * native cooldown. Instant authored spells can leave that marker behind
     * for one packet after cast completion; clear only that authored marker,
     * never an active non-authored chant.
     */
    @Inject(method = "attemptInitiateCast", at = @At("HEAD"), remap = false)
    private void ironMagicDuel$clearFinishedAuthoredMarker(net.minecraft.world.item.ItemStack stack,
                                                            int spellLevel, net.minecraft.world.level.Level level,
                                                            Player player, CastSource source, boolean trigger,
                                                            String slot, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        MagicData magic = MagicData.getPlayerMagicData(serverPlayer);
        String active = magic.getCastingSpellId();
        AbstractSpell requested = (AbstractSpell) (Object) this;
        if (magic.isCasting() && SpellDuelEvents.usesAuthoredCooldown(active)
                && SpellDuelEvents.usesAuthoredCooldown(requested.getSpellResource().toString())) {
            boolean activeChant = magic.getCastDuration() > 0
                    && magic.getCastDurationRemaining() > 0;
            if (!activeChant) {
                magic.resetCastingState();
                serverPlayer.stopUsingItem();
            }
        }
    }

    @Inject(method = "canBeCastedBy", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$allowEverySource(int spellLevel, CastSource source, MagicData magic,
                                                  Player player, CallbackInfoReturnable<CastResult> cir) {
        // Magic Missile remains disabled by SpellDuelEvents; do not turn its
        // pre-cast guard into a successful cast here.
        AbstractSpell spell = (AbstractSpell) (Object) this;
        if (!MAGIC_MISSILE.equals(spell.getSpellResource())) {
            // Duel spells use SpellDuelEvents' authoritative server tick gate.
            // Iron's client/native CooldownInstance can remain stale after a
            // long cast or a reconnect, which used to make the spell appear
            // ready while attemptInitiateCast still rejected it.
            if (magic != null && !player.isCreative()
                    && magic.getPlayerCooldowns().isOnCooldown(spell)) {
                cir.setReturnValue(new CastResult(CastResult.Type.FAILURE));
                return;
            }
            cir.setReturnValue(new CastResult(CastResult.Type.SUCCESS));
        }
    }

    @Inject(method = "checkPreCastConditions", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$ignoreSpellSpecificConditions(net.minecraft.world.level.Level level, int spellLevel,
                                                              LivingEntity caster, MagicData magic,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (!MAGIC_MISSILE.equals(((AbstractSpell) (Object) this).getSpellResource())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * A number of addon spells override checkPreCastConditions (some of them
     * even mark the override final).  Injecting the base method therefore does
     * not reach those spells.  Redirect the two calls made by the common
     * server cast entry point so every spell uses the same unrestricted duel
     * rule, regardless of which addon supplied its implementation.
     */
    @Redirect(method = "attemptInitiateCast", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;canBeCastedBy(ILio/redspace/ironsspellbooks/api/spells/CastSource;Lio/redspace/ironsspellbooks/api/magic/MagicData;Lnet/minecraft/world/entity/player/Player;)Lio/redspace/ironsspellbooks/api/spells/CastResult;"), remap = false)
    private CastResult ironMagicDuel$forceCastPermission(AbstractSpell spell, int spellLevel, CastSource source,
                                                          MagicData magic, Player player) {
        if (MAGIC_MISSILE.equals(spell.getSpellResource())) {
            return new CastResult(CastResult.Type.FAILURE);
        }
        if (magic != null && !player.isCreative()
                && magic.getPlayerCooldowns().isOnCooldown(spell)) {
            return new CastResult(CastResult.Type.FAILURE);
        }
        return new CastResult(CastResult.Type.SUCCESS);
    }

    private static boolean isDuelSpell(AbstractSpell spell) {
        ResourceLocation id = spell.getSpellResource();
        return id != null && DUEL_NAMESPACE.equals(id.getNamespace());
    }

    @Redirect(method = "attemptInitiateCast", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;checkPreCastConditions(Lnet/minecraft/world/level/Level;ILnet/minecraft/world/entity/LivingEntity;Lio/redspace/ironsspellbooks/api/magic/MagicData;)Z"), remap = false)
    private boolean ironMagicDuel$ignoreAllSpellConditions(AbstractSpell spell, net.minecraft.world.level.Level level,
                                                             int spellLevel, LivingEntity caster, MagicData magic) {
        if (MAGIC_MISSILE.equals(spell.getSpellResource())) return false;
        // Some addon spells perform essential target-area/entity setup in
        // checkPreCastConditions (Heroic Scorch is one example).  Calling the
        // real override first preserves that setup; the duel rule still
        // ignores only the boolean veto and lets the cast path continue.
        spell.checkPreCastConditions(level, spellLevel, caster, magic);
        return true;
    }

    /** Keep the native mana bar full; duel casting never consumes mana. */
    @Redirect(method = "castSpell", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;setMana(F)V"), remap = false)
    private void ironMagicDuel$keepMana(MagicData magic, float ignored) {
        // Deliberately do nothing.  The existing value remains available for
        // every subsequent cast and the normal sync packet still runs.
    }
}
