package com.example.scrollspellicons.client;

import com.example.scrollspellicons.config.PerformanceConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = "iron_magic_duel", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientPerformanceEvents {
    private static volatile ParticleBudget particleBudget = new ParticleBudget(1.0, 4096);
    private static final Map<Integer, PositionSmoother.Position> SMOOTHED_PROJECTILES = new HashMap<>();

    private ClientPerformanceEvents() {
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (!PerformanceConfig.CLIENT.enableClientOptimizations.get()) {
            return;
        }
        ClientPerformanceState.beginFrame();
        long currentFrame = ClientPerformanceState.frameId();
        particleBudget = new ParticleBudget(
                PerformanceConfig.CLIENT.particleDistanceMultiplier.get(),
                PerformanceConfig.CLIENT.maxParticlesPerFrame.get());
        particleBudget.beginFrame(currentFrame);
        applyRenderSmoothing();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (PerformanceConfig.CLIENT.enableClientOptimizations.get()) {
            particleBudget.beginFrame(ClientPerformanceState.frameId());
            updateSpellProjectileTargets();
        }
    }

    private static void updateSpellProjectileTargets() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            SMOOTHED_PROJECTILES.clear();
            return;
        }
        Vec3 viewer = minecraft.player == null ? minecraft.level.getSharedSpawnPos().getCenter() : minecraft.player.position();
        List<Entity> projectiles = new ArrayList<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (isIronSpellProjectile(entity)) {
                projectiles.add(entity);
            }
        }
        projectiles.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(viewer.x, viewer.y, viewer.z)));
        int maxVisible = PerformanceConfig.CLIENT.maxVisibleSpellProjectiles.get();
        int renderDistance = PerformanceConfig.CLIENT.projectileRenderDistance.get();
        double maxDistanceSqr = renderDistance == 0 ? Double.POSITIVE_INFINITY : (double) renderDistance * renderDistance;
        int visible = 0;
        java.util.HashSet<Integer> active = new java.util.HashSet<>();
        for (Entity entity : projectiles) {
            Vec3 current = entity.position();
            int id = entity.getId();
            active.add(id);
            boolean shouldRender = entity.distanceToSqr(viewer.x, viewer.y, viewer.z) <= maxDistanceSqr
                    && (maxVisible == 0 || visible++ < maxVisible);
            entity.setInvisible(!shouldRender);
            PositionSmoother.Position previous = SMOOTHED_PROJECTILES.putIfAbsent(
                    id, new PositionSmoother.Position(current.x, current.y, current.z));
            if (previous == null) {
                continue;
            }
            double factor = PositionSmoother.factorForSpeed(entity.getDeltaMovement().lengthSqr());
            PositionSmoother.Position smoothed = PositionSmoother.lerp(
                    previous, new PositionSmoother.Position(current.x, current.y, current.z), factor);
            SMOOTHED_PROJECTILES.put(id, smoothed);
        }
        SMOOTHED_PROJECTILES.keySet().removeIf(id -> !active.contains(id));
    }

    private static void applyRenderSmoothing() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!isIronSpellProjectile(entity)) {
                continue;
            }
            PositionSmoother.Position smoothed = SMOOTHED_PROJECTILES.get(entity.getId());
            if (smoothed != null) {
                entity.xOld = smoothed.x();
                entity.yOld = smoothed.y();
                entity.zOld = smoothed.z();
            }
        }
    }

    private static boolean isIronSpellProjectile(Entity entity) {
        String name = entity.getClass().getName();
        return name.startsWith("io.redspace.ironsspellbooks.entity.spells.")
                && name.endsWith("Projectile");
    }

    public static boolean admitSpellParticle(double squaredDistance, int weight) {
        if (!PerformanceConfig.CLIENT.enableClientOptimizations.get()) {
            return true;
        }
        return particleBudget.tryAccept(squaredDistance, weight);
    }
}
