package com.example.scrollspellicons.client;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ScrollIconResolverSelfTest {
    public static void main(String[] args) throws IOException {
        expect(ResourceLocation.parse("irons_spellbooks:textures/gui/spell_icons/fireball.png"),
                ScrollIconResolver.iconFor(ResourceLocation.parse("irons_spellbooks:fireball")).orElseThrow());
        expect(ResourceLocation.parse("addon_magic:textures/gui/spell_icons/rituals/meteor.png"),
                ScrollIconResolver.iconFor(ResourceLocation.parse("addon_magic:rituals/meteor")).orElseThrow());
        expect(ResourceLocation.parse("addon_magic:textures/gui/spell_icons/meteor_barrage.png"),
                ScrollIconResolver.iconFor(ResourceLocation.parse("addon_magic:rituals/meteor"), "meteor_barrage").orElseThrow());
        String atlasSource = ClientModEvents.singleSpellIconAtlasSource(
                ResourceLocation.parse("irons_spellbooks:textures/gui/spell_icons/fire_arrow.png"));
        if (!atlasSource.contains("\"type\":\"single\"")
                || !atlasSource.contains("\"resource\":\"irons_spellbooks:gui/spell_icons/fire_arrow\"")) {
            throw new AssertionError("spell icon atlas source must be namespace-aware: " + atlasSource);
        }
        if (ScrollIconResolver.iconFor(ResourceLocation.parse("irons_spellbooks:none")).isPresent()) {
            throw new AssertionError("none spell must fall back");
        }

        Properties gradleProperties = new Properties();
        try (InputStream inputStream = Files.newInputStream(Path.of("gradle.properties"))) {
            gradleProperties.load(inputStream);
        }
        expect("iron_magic_duel", gradleProperties.getProperty("mod_id"));
        expect("Iron Magic Duel", gradleProperties.getProperty("mod_name"));
        expect("1.0.44", gradleProperties.getProperty("mod_version"));
        expect("MKUXQQ", gradleProperties.getProperty("mod_authors"));
        expectNot("iron_spell_performance", gradleProperties.getProperty("mod_id"));
        expectNot("Iron Spellcasting Performance", gradleProperties.getProperty("mod_name"));
        expectNot("Codex", gradleProperties.getProperty("mod_authors"));
        expectNot("scroll_spell_icons", gradleProperties.getProperty("mod_id"));
        expectNot("Scroll Spell Icons", gradleProperties.getProperty("mod_name"));
        expectNot("1.0.26", gradleProperties.getProperty("mod_version"));

        String modsToml;
        try (InputStream inputStream = ScrollIconResolverSelfTest.class
                .getClassLoader()
                .getResourceAsStream("META-INF/neoforge.mods.toml")) {
            if (inputStream == null) {
                throw new AssertionError("Missing META-INF/neoforge.mods.toml on resolverTest classpath");
            }
            modsToml = new String(inputStream.readAllBytes());
        }
        if (!modsToml.contains("modId = \"iron_magic_duel\"")
                || !modsToml.contains("displayName = \"Iron Magic Duel\"")
                || !modsToml.contains("version = \"1.0.44\"")
                || !modsToml.contains("authors = \"MKUXQQ\"")
                || !modsToml.contains("logoFile = \"icon.png\"")
                || !modsToml.contains("modId = \"uilib\"")) {
            throw new AssertionError("Expanded NeoForge metadata did not contain the renamed mod identity");
        }
        if (modsToml.contains("iron_spell_performance")
                || modsToml.contains("Iron Spellcasting Performance")
                || modsToml.contains("Codex")
                || modsToml.contains("scroll_spell_icons")
                || modsToml.contains("Scroll Spell Icons")
                || modsToml.contains("1.0.26")
                || modsToml.contains("version = \"1.0.0\"")
                || modsToml.contains("version = \"1.0.1\"")) {
            throw new AssertionError("Expanded NeoForge metadata still contains the old scroll-only identity");
        }

        String commands = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelCommands.java"));
        if (!commands.contains("literal(\"all\")") || !commands.contains("literal(\"group\")")
                || !commands.contains("literal(\"point\")") || commands.contains("literal(\"player\")")
                || commands.contains("literal(\"groups\")")) {
            throw new AssertionError("duel clear command surface is not the requested group-only form");
        }
        if (!commands.contains("literal(\"tool\")")
                || !Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelManager.java")).contains("state.players.clear()")) {
            throw new AssertionError("tool command or per-group player selection reset is missing");
        }
        if (!commands.contains("literal(\"clear\")") || !commands.contains("literal(\"all\")")
                || !commands.contains("literal(\"group\")") || !commands.contains("literal(\"point\")")) {
            throw new AssertionError("point clear or group clear command is missing");
        }
        if (!commands.contains("literal(\"leave\")") || !commands.contains("literal(\"fake_players\")")
                || !commands.contains("fakePlayers.get()") || commands.contains("literal(\"off\").executes(context -> leaveSpectate")) {
            throw new AssertionError("spectator leave or fake-player command is missing");
        }
        if (!commands.contains("literal(\"hud\")") || commands.contains("literal(\"position\")")
                || !commands.contains("IntegerArgumentType") || !commands.contains("setHudPosition")) {
            throw new AssertionError("HUD numeric coordinate command is missing");
        }
        if (commands.contains("SPECTATOR_BLOCK") || commands.contains("spectator_block")) {
            throw new AssertionError("spectator block must be removed from the command surface");
        }
        String hud = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/SpellDuelHud.java"));
        if (hud.contains("RenderNameTagEvent") || !hud.contains("renderSpellIcon")
                || !hud.contains("GREEN") || !hud.contains("entry.cooldowns()")
                || !hud.contains("SPELL_BOX_WIDTH") || !hud.contains("drawHealthBox")
                || !hud.contains("drawLocalPanelFrame")
                || !hud.contains("0xFF42BCEB")
                || hud.contains("UILIB_FRAME") || !hud.contains("0xFF33AA55")
                || !hud.contains("hudWidth(int spellCount)") || !hud.contains("SPELL_COLUMNS = 6")
                || !hud.contains("Float.isFinite(health)") || !hud.contains("renderLocalSpellHud")
                || !hud.contains("ClientMagicData.getCooldowns") || !hud.contains("renderLocalPlayerFace")
                || !hud.contains("drawManaBox") || !hud.contains("ClientMagicData.getPlayerMana")
                || !hud.contains("AttributeRegistry.MAX_MANA") || !hud.contains("entry.mana()")
                || !hud.contains("entry.maxMana()")) {
            throw new AssertionError("spell display must be a client HUD icon renderer, not a name-tag renderer");
        }
        if (hud.contains("renderFirstPersonCooldowns") || hud.contains("SpellBarOverlay")
                || hud.contains("ClientRenderCache.generateRelativeLocations")) {
            throw new AssertionError("the custom first-person spell HUD must replace, not overlay, Iron's spell bar");
        }
        if (!hud.contains("findCurios(\"spellbook\")") || hud.contains("player.getInventory()")
                || !hud.contains("cooldownSeconds") || !hud.contains("drawCooldownNumber")) {
            throw new AssertionError("HUD must use Curios spellbooks only and render only active cooldown seconds on icons");
        }
        String network = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelNetwork.java"));
        if (!network.contains("findCurios(\"spellbook\")") || network.contains("player.getInventory()")
                || !network.contains("getCooldownRemaining") || !network.contains("COOLDOWN_TYPE")
                || !network.contains("broadcastCooldowns")) {
            throw new AssertionError("spectator snapshot must use Curios spellbooks and retain cooldown data");
        }
        if (!network.contains("entry.mana") || !network.contains("entry.maxMana")
                || !network.contains("AttributeRegistry.MAX_MANA")) {
            throw new AssertionError("spectator snapshots must carry current and maximum mana");
        }
        String clientState = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/SpellDuelClientState.java"));
        if (!clientState.contains("setCooldowns") || !clientState.contains("cooldowns(UUID")
                || !clientState.contains("SyncedCooldown") || !clientState.contains("remainingTicks")) {
            throw new AssertionError("client must retain server-synchronized cooldowns for every displayed player");
        }
        String events = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"));
        if (!events.contains("broadcastCooldowns") || !events.contains("% 5")) {
            throw new AssertionError("server must force-sync cooldowns at a bounded interval");
        }
        String mixinConfig = Files.readString(Path.of("src/main/resources/iron_magic_duel.mixins.json"));
        String spellBarMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/SpellBarOverlayMixin.java"));
        String vanillaHud = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/VanillaHudSuppressor.java"));
        if (!mixinConfig.contains("SpellBarOverlayMixin") || !mixinConfig.contains("ManaBarOverlayMixin")
                || !spellBarMixin.contains("ci.cancel()")
                || !spellBarMixin.contains("SpellBarOverlay")
                || !vanillaHud.contains("VanillaGuiLayers.PLAYER_HEALTH")
                || !vanillaHud.contains("VanillaGuiLayers.FOOD_LEVEL")
                || !vanillaHud.contains("VanillaGuiLayers.EXPERIENCE_BAR")
                || !vanillaHud.contains("VanillaGuiLayers.EXPERIENCE_LEVEL")) {
            throw new AssertionError("the original Iron spell bar and vanilla health/food/experience layers must be suppressed");
        }
        if (!network.contains("HudPositionPayload") || !network.contains("sendHudPosition")) {
            throw new AssertionError("HUD position must be sent only to the command player");
        }
        if (!clientState.contains("hudX") || !clientState.contains("hudY") || !clientState.contains("setHudPosition")) {
            throw new AssertionError("client must retain the requested HUD coordinates");
        }
        String fakePlayers = Files.readString(Path.of("src/main/resources/data/iron_magic_duel/function/spawn_duel_players.mcfunction"));
        for (String command : new String[]{"player Alex spawn", "player XingYear_ spawn", "player Steve spawn", "player fomg23333 spawn"}) {
            if (!fakePlayers.contains(command)) throw new AssertionError("missing fake-player function command: " + command);
        }
        Path icon = Path.of("src/main/resources/icon.png");
        if (!Files.exists(icon) || Files.size(icon) == 0L) {
            throw new AssertionError("mod icon must be present as src/main/resources/icon.png");
        }
        byte[] iconBytes = Files.readAllBytes(icon);
        if (iconBytes.length < 8
                || iconBytes[0] != (byte) 0x89
                || iconBytes[1] != 0x50
                || iconBytes[2] != 0x4E
                || iconBytes[3] != 0x47
                || iconBytes[4] != 0x0D
                || iconBytes[5] != 0x0A
                || iconBytes[6] != 0x1A
                || iconBytes[7] != 0x0A) {
            throw new AssertionError("mod icon must be a real PNG file");
        }
        String config = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/config/PerformanceConfig.java"));
        if (!config.contains("fakePlayers") || !config.contains("Alex") || !config.contains("fomg23333")) {
            throw new AssertionError("fake-player names must be editable in server config");
        }
    }

    private static void expect(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }

    private static void expectNot(Object unexpected, Object actual) {
        if (unexpected.equals(actual)) {
            throw new AssertionError("did not expect " + unexpected);
        }
    }
}
