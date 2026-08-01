package com.example.scrollspellicons.client;

import com.example.scrollspellicons.IronSpellPerformance;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@EventBusSubscriber(modid = IronSpellPerformance.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private static final ResourceLocation SCROLL = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "scroll");

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void addDynamicAtlas(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        try {
            Path root = Files.createTempDirectory("iron-magic-duel-atlas-");
            Path atlas = root.resolve("assets/minecraft/atlases/blocks.json");
            Files.createDirectories(atlas.getParent());
            Files.writeString(atlas, buildAtlasJson());
            Path metadata = root.resolve("pack.mcmeta");
            Files.writeString(metadata, "{\"pack\":{\"description\":\"Iron Magic Duel dynamic atlas\",\"pack_format\":34}}");
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo("iron_magic_duel_dynamic_atlas", Component.literal("Iron Magic Duel"), PackSource.BUILT_IN, Optional.empty()),
                    BuiltInPackSource.fromName(path -> new PathPackResources(path, root)),
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            event.addRepositorySource(source -> source.accept(pack));
            IronSpellPerformance.LOGGER.info("Registered spell icon atlas for {} loaded mod namespaces", ModList.get().getMods().size());
        } catch (IOException exception) {
            throw new RuntimeException("Unable to create the dynamic spell icon atlas", exception);
        }
    }

    private static String buildAtlasJson() {
        List<String> sources = new ArrayList<>();
        sources.add("{\"type\":\"directory\",\"source\":\"block\",\"prefix\":\"block/\"}");
        sources.add("{\"type\":\"directory\",\"source\":\"item\",\"prefix\":\"item/\"}");
        sources.add("{\"type\":\"directory\",\"source\":\"entity/conduit\",\"prefix\":\"entity/conduit/\"}");
        sources.add("{\"type\":\"single\",\"resource\":\"entity/bell/bell_body\"}");
        sources.add("{\"type\":\"single\",\"resource\":\"entity/decorated_pot/decorated_pot_side\"}");
        sources.add("{\"type\":\"single\",\"resource\":\"entity/enchanting_table_book\"}");
        for (var mod : ModList.get().getMods()) {
            String id = mod.getModId();
            if (id.equals("minecraft")) {
                continue;
            }
            Path iconDir = mod.getOwningFile().getFile().getSecureJar().getRootPath()
                    .resolve("assets").resolve(id).resolve("textures").resolve("gui").resolve("spell_icons");
            if (!Files.isDirectory(iconDir)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(iconDir)) {
                paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".png"))
                        .sorted()
                        .forEach(path -> {
                            String relative = iconDir.relativize(path).toString().replace('\\', '/');
                            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(
                                    id, "textures/gui/spell_icons/" + relative);
                            sources.add(singleSpellIconAtlasSource(icon));
                        });
            } catch (IOException exception) {
                IronSpellPerformance.LOGGER.warn("Unable to enumerate spell icons for {}", id, exception);
            }
        }
        return "{\"sources\":[" + String.join(",", sources) + "]}";
    }

    static String singleSpellIconAtlasSource(ResourceLocation icon) {
        String path = icon.getPath();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        return "{\"type\":\"single\",\"resource\":\""
                + icon.getNamespace() + ":" + path + "\"}";
    }

    @SubscribeEvent
    public static void replaceScrollModel(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation key = ModelResourceLocation.inventory(SCROLL);
        BakedModel original = event.getModels().get(key);
        if (original == null) {
            original = event.getModels().entrySet().stream()
                    .filter(entry -> entry.getKey().id().equals(SCROLL))
                    .map(java.util.Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        IronSpellPerformance.LOGGER.info("Scroll model hook key={} found={} totalModels={}", key, original != null, event.getModels().size());
        if (original != null) {
            event.getModels().put(key, new SpellIconItemModel(original));
        }
    }
}
