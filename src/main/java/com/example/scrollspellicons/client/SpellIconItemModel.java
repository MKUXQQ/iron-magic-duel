package com.example.scrollspellicons.client;

import com.example.scrollspellicons.IronSpellPerformance;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

final class SpellIconItemModel implements BakedModel {
    private static final AtomicInteger RESOLVE_LOGS = new AtomicInteger();
    private final BakedModel original;
    private final ItemOverrides overrides;
    private final ConcurrentHashMap<ResourceLocation, BakedModel> iconModels = new ConcurrentHashMap<>();

    SpellIconItemModel(BakedModel original) {
        this.original = original;
        BlockModel missing = null;
        this.overrides = new ItemOverrides(new ModelBaker() {
            @Override public Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> getModelTextureGetter() { return null; }
            @Override public @Nullable UnbakedModel getTopLevelModel(ModelResourceLocation location) { return null; }
            @Override public BakedModel bake(ResourceLocation location, ModelState state, Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> sprites) { return null; }
            @Override public @Nullable BakedModel bakeUncached(UnbakedModel model, ModelState state, Function<net.minecraft.client.resources.model.Material, TextureAtlasSprite> sprites) { return null; }
            @Override public UnbakedModel getModel(ResourceLocation resourceLocation) { return null; }
            @Override public @Nullable BakedModel bake(ResourceLocation resourceLocation, ModelState modelState) { return null; }
        }, missing, Collections.<ItemOverride>emptyList()) {
            @Override
            public BakedModel resolve(@NotNull BakedModel ignored, @NotNull ItemStack stack, @Nullable net.minecraft.client.multiplayer.ClientLevel level, @Nullable LivingEntity entity, int seed) {
                Optional<ResourceLocation> icon = spellIcon(stack);
                if (icon.isEmpty()) {
                    return SpellIconItemModel.this.original;
                }
                return iconModels.computeIfAbsent(icon.get(), SpellIconItemModel.this::modelForIcon);
            }
        };
    }

    private static Optional<ResourceLocation> spellIcon(ItemStack stack) {
        boolean hasContainer = ISpellContainer.isSpellContainer(stack);
        if (RESOLVE_LOGS.getAndIncrement() < 20) {
            IronSpellPerformance.LOGGER.info("Scroll render stack item={} hasSpellContainer={} components={}",
                    stack.getItem(), hasContainer, stack.getComponents());
        }
        if (!hasContainer) {
            return Optional.empty();
        }
        var spell = ISpellContainer.get(stack).getSpellAtIndex(0).getSpell();
        ResourceLocation spellId = spell.getSpellResource();
        var icon = ScrollIconResolver.iconFor(spellId, spell.getSpellName());
        if (RESOLVE_LOGS.get() <= 20) {
            IronSpellPerformance.LOGGER.info("Scroll render spell={} icon={}", spellId, icon.orElse(null));
        }
        return icon;
    }

    private BakedModel modelForIcon(ResourceLocation icon) {
        Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        ResourceLocation spriteLocation = ResourceLocation.fromNamespaceAndPath(icon.getNamespace(),
                icon.getPath().substring("textures/".length(), icon.getPath().length() - ".png".length()));
        TextureAtlasSprite sprite = atlas.apply(spriteLocation);
        TextureAtlasSprite missing = atlas.apply(MissingTextureAtlasSprite.getLocation());
        IronSpellPerformance.LOGGER.info("Scroll icon sprite lookup icon={} sprite={} missing={}", icon, sprite, sprite == missing);
        if (sprite == missing) {
            return original;
        }
        return new SpellIconBakedModel(original, sprite);
    }

    @Override public ItemOverrides getOverrides() { return overrides; }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) { return original.getQuads(state, side, rand); }
    @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return original.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return original.getTransforms(); }
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level, net.minecraft.core.BlockPos pos, BlockState state, ModelData data) { return original.getModelData(level, pos, state, data); }
}
