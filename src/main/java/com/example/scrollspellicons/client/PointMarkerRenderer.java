package com.example.scrollspellicons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.example.scrollspellicons.duel.SpellDuelItems;

/** Client-only floating labels above point selector markers. */
@EventBusSubscriber(value = Dist.CLIENT, modid = "iron_magic_duel")
public final class PointMarkerRenderer {
    private PointMarkerRenderer() {}
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null || mc.level == null) return;
        if (!mc.player.getMainHandItem().is(SpellDuelItems.POINT_SELECTOR.get())
                && !mc.player.getOffhandItem().is(SpellDuelItems.POINT_SELECTOR.get())) {
            SpellDuelClientState.setPointMarkers(java.util.List.of());
            return;
        }
        var camera = mc.gameRenderer.getMainCamera(); var pos = camera.getPosition();
        PoseStack pose = event.getPoseStack(); MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        for (var marker : SpellDuelClientState.pointMarkers()) {
            pose.pushPose(); pose.translate(marker.x() - pos.x, marker.y() - pos.y, marker.z() - pos.z);
            pose.mulPose(camera.rotation()); pose.scale(-0.025F, -0.025F, 0.025F);
            int width = mc.font.width(marker.label());
            mc.font.drawInBatch(marker.label(), -width / 2.0F, 0, 0xFFFFFFFF, false, pose.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0x66000000, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffer.endBatch();
    }
}
