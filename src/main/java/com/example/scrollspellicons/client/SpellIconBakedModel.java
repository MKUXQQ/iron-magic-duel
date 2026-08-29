package com.example.scrollspellicons.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class SpellIconBakedModel implements BakedModel {
    private static final int UV_ELEMENT_OFFSET = 4;
    private static final int VERTEX_STRIDE = 8;
    private final BakedModel original;
    private final TextureAtlasSprite icon;

    SpellIconBakedModel(BakedModel original, TextureAtlasSprite icon) {
        this.original = original;
        this.icon = icon;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        List<BakedQuad> source = original.getQuads(state, side, rand);
        List<BakedQuad> result = new ArrayList<>(source.size());
        for (BakedQuad quad : source) {
            result.add(remap(quad));
        }
        return result;
    }

    private BakedQuad remap(BakedQuad quad) {
        TextureAtlasSprite sourceSprite = quad.getSprite();
        if (sourceSprite == null || sourceSprite == icon) {
            return quad;
        }
        int[] vertices = quad.getVertices().clone();
        float sourceWidth = sourceSprite.getU1() - sourceSprite.getU0();
        float sourceHeight = sourceSprite.getV1() - sourceSprite.getV0();
        for (int vertex = 0; vertex < 4; vertex++) {
            int uv = vertex * VERTEX_STRIDE + UV_ELEMENT_OFFSET;
            float u = Float.intBitsToFloat(vertices[uv]);
            float v = Float.intBitsToFloat(vertices[uv + 1]);
            float u01 = (u - sourceSprite.getU0()) / sourceWidth;
            float v01 = (v - sourceSprite.getV0()) / sourceHeight;
            vertices[uv] = Float.floatToRawIntBits(icon.getU(u01));
            vertices[uv + 1] = Float.floatToRawIntBits(icon.getV(v01));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), icon,
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, ModelData data, @Nullable net.minecraft.client.renderer.RenderType renderType) { return getQuads(state, side, rand); }
    @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return original.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return icon; }
    @Override public ItemTransforms getTransforms() { return original.getTransforms(); }
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level, net.minecraft.core.BlockPos pos, BlockState state, ModelData data) { return original.getModelData(level, pos, state, data); }
}
