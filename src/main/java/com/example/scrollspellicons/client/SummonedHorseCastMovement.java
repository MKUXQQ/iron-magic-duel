package com.example.scrollspellicons.client;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.entity.mobs.SummonedHorse;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Restores the rider's normal horse input while a summon_horse rider casts.
 *
 * Iron's client handler scales forward/strafe input by
 * {@code 0.2 + casting_movespeed - 1}.  Running last and applying the exact
 * reciprocal removes only that cast multiplier; the horse's own attributes,
 * sprinting and jumping remain untouched.  SummonedHorse is used instead of
 * AbstractHorse so ordinary and other-mod mounts retain the normal cast
 * penalty.
 */
@EventBusSubscriber(modid = "iron_magic_duel", value = Dist.CLIENT)
public final class SummonedHorseCastMovement {
    private SummonedHorseCastMovement() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restoreHorseInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (!ClientMagicData.isCasting() || !player.isPassenger()
                || !(player.getVehicle() instanceof SummonedHorse)) {
            return;
        }
        double castingSpeed = player.getAttributeValue(AttributeRegistry.CASTING_MOVESPEED);
        float castMultiplier = (float) (0.2D + castingSpeed - 1.0D);
        if (!Float.isFinite(castMultiplier) || castMultiplier <= 0.001F
                || Math.abs(castMultiplier - 1.0F) < 0.0001F) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse /= castMultiplier;
        input.leftImpulse /= castMultiplier;
    }
}
