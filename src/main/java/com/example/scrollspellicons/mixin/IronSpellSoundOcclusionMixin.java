package com.example.scrollspellicons.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every sound with a physical world source uses room-style isolation. Every
 * listener in the same open space hears the sound, but a single collidable
 * block between source and listener silences it. This intentionally covers
 * every mod and sound type, not only Iron's Spells or spell addons.
 */
@Mixin(SoundEngine.class)
public abstract class IronSpellSoundOcclusionMixin {
    /** Maximum distance at which any spatial world sound can be heard. */
    private static final double MAX_WORLD_SOUND_DISTANCE = 32.0D;

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void ironMagicDuel$applyRoomIsolationToAllWorldSounds(SoundInstance sound, CallbackInfo ci) {
        // UI/menu/music sounds have no world source to test against a wall.
        // Every spatial sound, from every namespace, continues below.
        if (sound.isRelative()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        Vec3 listener = minecraft.player.getEyePosition();
        Vec3 source = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        double distanceSquared = listener.distanceToSqr(source);
        if (distanceSquared > MAX_WORLD_SOUND_DISTANCE * MAX_WORLD_SOUND_DISTANCE
                || hasBlockingWall(minecraft, listener, source)) ci.cancel();
    }

    /**
     * A direct source-to-listener ray means people inside one sealed room hear
     * every spatial sound normally, while listeners outside have the boundary
     * block intercepted and therefore receive no sound. Any collidable block
     * —wood, stone, glass, fence, door, etc.—blocks the ray.
     */
    private static boolean hasBlockingWall(Minecraft minecraft, Vec3 listener, Vec3 source) {
        HitResult hit = minecraft.level.clip(new ClipContext(source, listener,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.BLOCK;
    }
}
