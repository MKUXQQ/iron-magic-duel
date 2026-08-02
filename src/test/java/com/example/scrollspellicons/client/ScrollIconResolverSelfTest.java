package com.example.scrollspellicons.client;

import net.minecraft.resources.ResourceLocation;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/** Static packaging and feature-surface verifier for the Forge 1.20.1 port. */
public final class ScrollIconResolverSelfTest {
    public static void main(String[] args) throws Exception {
        expect(ResourceLocation.parse("irons_spellbooks:textures/gui/spell_icons/fireball.png"),
                ScrollIconResolver.iconFor(ResourceLocation.parse("irons_spellbooks:fireball")).orElseThrow());
        if (ScrollIconResolver.iconFor(ResourceLocation.parse("irons_spellbooks:none")).isPresent()) throw new AssertionError("none spell must fall back");
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("gradle.properties"))) { properties.load(in); }
        expect("iron_magic_duel", properties.getProperty("mod_id")); expect("1.20.1", properties.getProperty("minecraft_version")); expect("47.4.20", properties.getProperty("forge_version"));
        String modsToml = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));
        require(modsToml, "javafml", "Forge metadata"); require(modsToml, "irons_spellbooks", "Iron's Spells dependency"); require(modsToml, "curios", "Curios dependency");
        String mainMod = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/IronSpellPerformance.java"));
        require(mainMod, "public IronSpellPerformance()", "Forge no-argument mod constructor"); require(mainMod, "FMLJavaModLoadingContext", "Forge mod event bus lookup");
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(source);
                if (content.contains("net.neoforged") || content.contains("NeoForge")) {
                    throw new AssertionError("NeoForge API remains in Forge source: " + source);
                }
            }
        }
        String network = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelNetwork.java"));
        require(network, "SimpleChannel", "Forge networking"); require(network, "broadcastSnapshots", "spectator snapshots"); require(network, "broadcastCooldowns", "cooldown synchronization"); require(network, "PlayerSelectionPayload", "player selector packets"); require(network, "spellbook", "Curios spellbook lookup");
        String manager = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelManager.java"));
        require(manager, "beginEditingGroup", "editing group creation"); require(manager, "readyForPlayers", "point group selected before new group"); require(manager, "previous.add(target, null)", "online player reassignment"); require(manager, "createPointGroup", "unlimited point group creation"); require(manager, "showPointMarkers", "private point marker particles"); require(manager, "cancelSelectedPlayer", "selected player removal"); require(manager, "clearPlayers", "group roster clearing"); require(manager, "savePoints", "persistent duel points");
        String events = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"));
        require(events, "SpellPreCastEvent", "spectator spell pre-cast guard"); require(events, "isSpectator()", "spectator cast rejection"); require(events, "setCanceled(true)", "server-authoritative cast cancellation"); require(events, "resetCastingState()", "active spectator cast reset"); require(manager, "resetCastingState()", "cast reset when joining spectator mode");
        String commands = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelCommands.java"));
        for (String command : new String[]{"tool", "shop", "start", "spectate", "hud", "clear", "fake_players"}) require(commands, command, "command surface");
        if (commands.contains("Commands.literal(\"display\").requires") || commands.contains("Commands.literal(\"point\").requires")) throw new AssertionError("removed root commands are still registered");
        String shop = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelShop.java"));
        require(shop, "iron_magic_shop.json", "persistent shop JSON"); require(shop, "PAGES = 5", "five shop pages"); require(shop, "FreeContainer", "infinite shop container"); require(shop, "EditorContainer", "shop editor");
        String hud = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/SpellDuelHud.java"));
        require(hud, "if (!mc.player.isSpectator()) return;", "spectator-only duel HUD"); require(hud, "drawManaBox", "spectator mana bar"); require(hud, "drawCooldownNumber", "spectator cooldown display"); require(hud, "entry.mana()", "spectator mana");
        require(hud, "graphics.blit(icon, x, y, 0, 0, 16, 16, 16, 16)", "direct Iron spell icon rendering");
        if (hud.contains("TextureAtlas.LOCATION_BLOCKS")) throw new AssertionError("spellbook GUI icons must not use the item/block atlas");
        if (Files.exists(Path.of("src/main/java/com/example/scrollspellicons/client/VanillaHudSuppressor.java"))) {
            throw new AssertionError("vanilla health and food HUD must not be suppressed");
        }
        String selectionNetwork = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelNetwork.java"));
        require(selectionNetwork, "getPlayerList().getPlayers()", "authoritative online player selector");
        require(selectionNetwork, "getAllLevels()", "cross-dimension player scan");
        require(selectionNetwork, "level.players()", "dimension player scan");
        require(selectionNetwork, "Map<UUID, ServerPlayer>", "UUID de-duplication");
        require(selectionNetwork, "result.sort", "stable complete player list");
        String selector = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/PlayerSelectionScreen.java"));
        require(selector, "42 + 8 + visibleRows() * ROW_HEIGHT + 45", "final player-selector row padding");
        String pointRenderer = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/PointMarkerRenderer.java"));
        require(pointRenderer, "LABEL_STORAGE", "isolated point-label render storage");
        require(pointRenderer, "LABEL_BUFFER", "isolated point-label render buffer");
        if (pointRenderer.contains("mc.renderBuffers().bufferSource()")) {
            throw new AssertionError("point labels must never flush Forge's shared world buffer");
        }
        require(selectionNetwork, "findCurios(\"spellbook\")", "Curios spellbook scan");
        require(selectionNetwork, "getActiveSpells()", "active spells read from Curios spellbook");
        if (Files.exists(Path.of("src/main/java/com/example/scrollspellicons/client/ClientModEvents.java"))
                || Files.exists(Path.of("src/main/java/com/example/scrollspellicons/client/SpellIconItemModel.java"))) {
            throw new AssertionError("scroll atlas/model replacement must not be packaged");
        }
        try (ZipFile irons = new ZipFile("libs/irons-3.16.1.jar")) {
            if (irons.getEntry("assets/irons_spellbooks/textures/gui/spell_icons/magic_arrow.png") == null) throw new AssertionError("Iron's magic-arrow icon resource missing");
            if (irons.getEntry("assets/irons_spellbooks/models/item/scroll.json") == null
                    || irons.getEntry("assets/irons_spellbooks/textures/item/scroll.png") == null) {
                throw new AssertionError("Iron's original scroll inventory model/resource missing");
            }
        }
        Path icon = Path.of("src/main/resources/icon.png"); if (!Files.exists(icon) || Files.size(icon) < 8) throw new AssertionError("mod icon missing");
    }
    private static void require(String value, String expected, String label) { if (!value.contains(expected)) throw new AssertionError("missing " + label + ": " + expected); }
    private static void expect(Object expected, Object actual) { if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual); }
}
