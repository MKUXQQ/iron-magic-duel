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
        expect("9.19", gradleProperties.getProperty("mod_version"));
        expect("MKUXQQ", gradleProperties.getProperty("mod_authors"));
        expectNot("iron_spell_performance", gradleProperties.getProperty("mod_id"));
        expectNot("Iron Spellcasting Performance", gradleProperties.getProperty("mod_name"));
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
                || !modsToml.contains("version = \"9.19\"")
                || !modsToml.contains("authors = \"MKUXQQ\"")
                || !modsToml.contains("logoFile = \"icon.png\"")
                || modsToml.contains("modId = \"uilib\"")) {
            throw new AssertionError("Expanded NeoForge metadata still contains the removed UILib dependency");
        }
        if (modsToml.contains("iron_spell_performance")
                || modsToml.contains("Iron Spellcasting Performance")
                || modsToml.contains("scroll_spell_icons")
                || modsToml.contains("Scroll Spell Icons")
                || modsToml.contains("1.0.26")
                || modsToml.contains("version = \"1.0.0\"")
                || modsToml.contains("version = \"1.0.1\"")) {
            throw new AssertionError("Expanded NeoForge metadata still contains the old scroll-only identity");
        }

        String mixins = Files.readString(Path.of("src/main/resources/iron_magic_duel.mixins.json"));
        String learnedSnapshot = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/LearnedSpellDataWriteSnapshotMixin.java"));
        String learnedAccessor = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/SyncedSpellDataLearnedAccessorMixin.java"));
        boolean learnedSyncOk = mixins.contains("LearnedSpellDataWriteSnapshotMixin")
                && mixins.contains("SyncedSpellDataLearnedAccessorMixin")
                && learnedSnapshot.contains("writeToBuffer")
                && learnedSnapshot.contains("synchronized (data.learnedSpells)")
                && learnedSnapshot.contains("Set.copyOf(data.learnedSpells)")
                && learnedAccessor.contains("learnedSpellData");
        if (!learnedSyncOk) {
            throw new AssertionError("learned spell sync must encode a locked stable snapshot: "
                    + mixins.contains("LearnedSpellDataWriteSnapshotMixin") + ","
                    + mixins.contains("SyncedSpellDataLearnedAccessorMixin") + ","
                    + learnedSnapshot.contains("writeToBuffer") + ","
                    + learnedSnapshot.contains("synchronized (data.learnedSpells)") + ","
                    + learnedSnapshot.contains("Set.copyOf(data.learnedSpells)") + ","
                    + learnedAccessor.contains("learnedSpellData"));
        }
        String createGuard = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/CreateAllKeysThreadGuardMixin.java"));
        if (!mixins.contains("CreateAllKeysThreadGuardMixin")
                || !createGuard.contains("com.simibubi.create.AllKeys")
                || !createGuard.contains("key < 0")
                || !createGuard.contains("RenderSystem.isOnRenderThread")) {
            throw new AssertionError("Create asynchronous invalid-key guard is missing");
        }
        String glacialEdgeSpeed = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/GlacialEdgeSpeedMixin.java"));
        if (!mixins.contains("GlacialEdgeSpeedMixin")
                || !glacialEdgeSpeed.contains("discerning_the_eldritch.entity.spells.glacial_edge.GlacialEdge")
                || !glacialEdgeSpeed.contains("getSpeed()F")
                || !glacialEdgeSpeed.contains("IRON_MAGIC_SPEED_MULTIPLIER = 1.75F")) {
            throw new AssertionError("glacial_edge speed adjustment must target only its verified projectile");
        }
        String glacialHitOnce = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/GlacialEdgeHitOnceMixin.java"));
        String rayHitFreeze = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/RayOfFrostHitFreezeMixin.java"));
        String coneCadence = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/ContinuousConeCadenceMixin.java"));
        String managedField = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/ManagedFrostFieldMixin.java"));
        String managedSource = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/ManagedFreezeSourceMixin.java"));
        if (!mixins.contains("GlacialEdgeHitOnceMixin") || !glacialHitOnce.contains("allowGlacialEdgeHit")
                || !glacialHitOnce.contains("onHit(Lnet/minecraft/world/phys/HitResult;)V")
                || !glacialHitOnce.contains("onGlacialEdgeDamageApplied")
                || glacialHitOnce.contains("finishGlacialEdgeHit")
                || !glacialHitOnce.contains("createFrostField")
                || !glacialHitOnce.contains("FreezeReason.GLACIAL_EDGE")
                || !mixins.contains("SnowballFrostFieldMixin")
                || !mixins.contains("ManagedFrostFieldMixin")
                || !managedField.contains("managedFrostFieldReason")
                || !managedField.contains("FreezeReason.SNOWBALL")
                || !managedField.contains("target instanceof ServerPlayer")
                || managedField.contains("SpellDuelEvents.applyManagedFreeze")
                || !mixins.contains("RayOfFrostHitFreezeMixin")
                || !rayHitFreeze.contains("DamageSources.applyDamage")
                || !rayHitFreeze.contains("FreezeReason.RAY_OF_FROST")
                || !mixins.contains("ContinuousConeCadenceMixin")
                || !coneCadence.contains("SPRAY_MIN_ACTIVE_INTERVAL = 6L")
                || coneCadence.contains("DEFAULT_MIN_ACTIVE_INTERVAL")
                || !coneCadence.contains("magic.getAdditionalCastData() instanceof EntityCastData")
                || !coneCadence.contains("castData.getCastingEntity() == cone")
                || !coneCadence.contains("CastType.CONTINUOUS")
                || !coneCadence.contains("dealDamageActive:Z")
                || !coneCadence.contains("opcode = Opcodes.GETFIELD")
                || !coneCadence.contains("cone.setDealDamageActive()")
                || coneCadence.contains("method = \"setDealDamageActive()V\"")
                || !Files.readString(Path.of(
                        "src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"))
                    .contains("onSnowballProjectileImpact")
                || !Files.readString(Path.of(
                        "src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"))
                    .contains("event.isCanceled()")
                || !Files.readString(Path.of(
                        "src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"))
                    .contains("io.redspace.ironsspellbooks.entity.spells.snowball.Snowball snowball")
                || !Files.readString(Path.of(
                        "src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"))
                    .contains("SNOWBALL_FREEZE_HITS")
                || !Files.readString(Path.of(
                        "src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"))
                    .contains("getRayTraceResult()")
                || !Files.readString(Path.of(
                        "src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"))
                    .contains("EventPriority.LOWEST")
                || !Files.readString(Path.of(
                        "src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"))
                    .contains("debugSnowballFreeze")
                || Files.readString(Path.of(
                        "src/main/java/com/example/scrollspellicons/mixin/SnowballFrostFieldMixin.java"))
                    .contains("freezeEntityHit")
                || Files.readString(Path.of(
                        "src/main/java/com/example/scrollspellicons/mixin/SnowballFrostFieldMixin.java"))
                    .contains("applyManagedFreeze")
                || !mixins.contains("ManagedFreezeSourceMixin")
                || !managedSource.contains("setFreezeTicks(0)")
                || !managedSource.contains("isConeOfColdSpell")) {
            throw new AssertionError("managed freeze sources must be bounded and Glacial Edge collisions must be deduplicated");
        }

        // Build-time target verification: when an optional dependency is on
        // this classpath, its real target class must be loadable and the
        // matching injection descriptor must still be present in source.
        verifyMixinTargetWhenPresent(mixins, "GlacialEdgeHitOnceMixin", glacialHitOnce,
                "net.acetheeldritchking.discerning_the_eldritch.entity.spells.glacial_edge.GlacialEdge",
                "onHit(Lnet/minecraft/world/phys/HitResult;)V");
        verifyMixinTargetWhenPresent(mixins, "SnowballFrostFieldMixin", Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/SnowballFrostFieldMixin.java")),
                "io.redspace.ironsspellbooks.entity.spells.snowball.Snowball",
                "createFrostField(Lnet/minecraft/world/phys/Vec3;)V");
        verifyMixinTargetWhenPresent(mixins, "ManagedFrostFieldMixin", managedField,
                "io.redspace.ironsspellbooks.entity.spells.snowball.FrostField",
                "applyEffect(Lnet/minecraft/world/entity/LivingEntity;)V");
        verifyMixinTargetWhenPresent(mixins, "RayOfFrostHitFreezeMixin", rayHitFreeze,
                "io.redspace.ironsspellbooks.spells.ice.RayOfFrostSpell",
                "onCast(Lnet/minecraft/world/level/Level;");
        verifyMixinTargetWhenPresent(mixins, "ContinuousConeCadenceMixin", coneCadence,
                "io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile",
                "tick()V");

        String replayPayloadGuard = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/FlashbackReplayNeoForgePayloadGuardMixin.java"));
        if (!replayPayloadGuard.contains("NetworkRegistry")
                || !replayPayloadGuard.contains("handleModdedPayload")
                || !replayPayloadGuard.contains("ReplayServer")
                || !replayPayloadGuard.contains("\"neoforge\"")
                || !replayPayloadGuard.contains("ci.cancel()")) {
            throw new AssertionError("Flashback replay NeoForge payload guard is missing its targeted cancellation");
        }
        if (!mixins.contains("FlashbackReplayNeoForgePayloadGuardMixin")) {
            throw new AssertionError("Flashback replay NeoForge payload guard is not registered");
        }
        String replayConfigGuard = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/FlashbackReplayConfigPayloadGuardMixin.java"));
        if (!mixins.contains("FlashbackReplayConfigPayloadGuardMixin")
                || !replayConfigGuard.contains("apothic_attributes")
                || !replayConfigGuard.contains("sendToPlayer")
                || !replayConfigGuard.contains("ReplayServer")
                || !replayConfigGuard.contains("ci.cancel()")) {
            throw new AssertionError("Flashback replay must skip only the known Apothic config payload");
        }
        String attachmentGuard = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/FlashbackReplayAttachmentPacketGuardMixin.java"));
        if (!attachmentGuard.contains("checkPacket")
                || !attachmentGuard.contains("sync_attachments")
                || !attachmentGuard.contains("ReplayServer")
                || !attachmentGuard.contains("ServerLifecycleHooks")
                || !attachmentGuard.contains("ci.cancel()")) {
            throw new AssertionError("Flashback attachment validation guard is missing its replay-only packet check");
        }
        String fixedFov = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/client/FixedFovEvents.java"));
        if (!fixedFov.contains("ViewportEvent.ComputeFov")
                || !fixedFov.contains("minecraft.options.fov().get()")
                || !fixedFov.contains("event.setFOV")) {
            throw new AssertionError("configured FOV must be restored after every camera/FOV event");
        }

        String commands = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelCommands.java"));
        if (!commands.contains("literal(\"all\")") || !commands.contains("literal(\"group\")")
                || !commands.contains("literal(\"point\")") || commands.contains("literal(\"player\")")
                || commands.contains("literal(\"groups\")")) {
            throw new AssertionError("duel clear command surface is not the requested group-only form");
        }
        String manager = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelManager.java"));
        if (!commands.contains("literal(\"tool\")") || !manager.contains("beginEditingGroup")
                || !manager.contains("cancelEditingGroup") || !manager.contains("cancelSelectedPlayer")) {
            throw new AssertionError("tool command or editing-group player selection flow is missing");
        }
        if (!manager.contains("group.pointA() != null && group.pointB() != null")
                || !manager.contains("!editingGroups.contains(group.id())")
                || !manager.contains("nextGroupId")) {
            throw new AssertionError("player selection must reuse the lowest saved point group before allocating a new duel id");
        }
        if (!commands.contains("literal(\"stop\")") || !manager.contains("stopAll")
                || !manager.contains("showPointMarkers")) {
            throw new AssertionError("stop command or point selector markers are missing");
        }
        if (!commands.contains("literal(\"clear\")") || !commands.contains("literal(\"all\")")
                || !commands.contains("literal(\"group\")") || !commands.contains("literal(\"point\")")) {
            throw new AssertionError("point clear or group clear command is missing");
        }
        if (!commands.contains("literal(\"leave\")") || !commands.contains("literal(\"fake_players\")")
                || !commands.contains("serverValues().fakePlayers()") || commands.contains("literal(\"off\").executes(context -> leaveSpectate")) {
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
                || !hud.contains("0xFF555555")
                || hud.contains("UILIB_FRAME") || !hud.contains("0xFF33AA55")
                || !hud.contains("hudWidth(int spellCount)") || !hud.contains("SPELL_COLUMNS = 6")
                || !hud.contains("Float.isFinite(health)") || !hud.contains("renderLocalSpellHud")
                || !hud.contains("ClientMagicData.getCooldowns")
                || hud.contains("PlayerFaceRenderer") || hud.contains("AbstractClientPlayer")
                || hud.contains("PLAYER_FACE_SIZE") || hud.contains("drawManaBox")
                || hud.contains("AttributeRegistry.MAX_MANA") || !hud.contains("IRONS_SPELL_SLOT")
                || !hud.contains("textures/gui/icons.png") || !hud.contains("graphics.blit(IRONS_SPELL_SLOT")
                || !hud.contains("88, 84, 22, 22")) {
            throw new AssertionError("spell display must be a client HUD icon renderer, not a name-tag renderer");
        }
        if (hud.contains("renderFirstPersonCooldowns") || hud.contains("SpellBarOverlay")
                || hud.contains("ClientRenderCache.generateRelativeLocations")) {
            throw new AssertionError("the custom first-person spell HUD must replace, not overlay, Iron's spell bar");
        }
        String clientStateSource = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/SpellDuelClientState.java"));
        if (!hud.contains("HOTBAR_CLEARANCE") || !hud.contains("(graphics.guiWidth() - panelWidth) / 2")
                || !clientStateSource.contains("centred above the vanilla hotbar")) {
            throw new AssertionError("default duel HUD position must be centered above the hotbar");
        }
        if (!hud.contains("findCurios(\"spellbook\")") || hud.contains("player.getInventory()")
                || !hud.contains("cooldownSeconds") || !hud.contains("drawCooldownNumber")) {
            throw new AssertionError("HUD must use Curios spellbooks only and render only active cooldown seconds on icons");
        }
        String network = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelNetwork.java"));
        if (!network.contains("collectAllOnlinePlayers") || !network.contains("getAllLevels()")
                || !network.contains("level.players()") || !network.contains("Map<UUID, ServerPlayer>")) {
            throw new AssertionError("player selector must merge all server and dimension players by UUID");
        }
        String selector = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/PlayerSelectionScreen.java"));
        if (!selector.contains("42 + 8 + visibleRows() * ROW_HEIGHT + 45")) {
            throw new AssertionError("player selector must reserve top and bottom padding for its final row");
        }
        String pointRenderer = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/PointMarkerRenderer.java"));
        if (!pointRenderer.contains("LABEL_STORAGE") || !pointRenderer.contains("LABEL_BUFFER")
                || pointRenderer.contains("mc.renderBuffers().bufferSource()")) {
            throw new AssertionError("point labels must use an isolated buffer and never flush the world buffer");
        }
        if (!network.contains("findCurios(\"spellbook\")") || network.contains("player.getInventory()")
                || !network.contains("getCooldownRemaining") || !network.contains("COOLDOWN_TYPE")
                || !network.contains("broadcastCooldowns")) {
            throw new AssertionError("spectator snapshot must use Curios spellbooks and retain cooldown data");
        }
        String clientState = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/SpellDuelClientState.java"));
        if (!clientState.contains("setCooldowns") || !clientState.contains("cooldowns(UUID")
                || !clientState.contains("SyncedCooldown") || !clientState.contains("remainingTicks")) {
            throw new AssertionError("client must retain server-synchronized cooldowns for every displayed player");
        }
        String events = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"));
        if (!events.contains("SpellPreCastEvent") || !events.contains("isSpectator()")
                || !events.contains("setCanceled(true)") || !events.contains("resetCastingState()")
                || !manager.contains("resetCastingState()")) {
            throw new AssertionError("duel spectators must be unable to start or continue casting spells");
        }
        String noCast = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/NoCastZoneManager.java"));
        if (!commands.contains("literal(\"no_cast\")") || !commands.contains("literal(\"select\")")
                || !commands.contains("literal(\"remove\")") || !commands.contains("literal(\"all\")") || !noCast.contains("blocksCasting")
                || !events.contains("noCastZones") || !events.contains("此区域禁止施法")) {
            throw new AssertionError("persistent square no-cast zone tool and command are missing");
        }
        if (!events.contains("LivingDeathEvent") || !events.contains("recordDuelDeathAndScheduleRecovery(player)")
                || !events.contains("LivingDamageEvent") || !events.contains("LAST_DAMAGE_TICKS")
                || events.contains("onFatalPlayerDamage")) {
            throw new AssertionError("duel deaths must record the result while keeping vanilla death and respawn");
        }
        if (!events.contains("SpellDamageSource source") || !events.contains("ICE_FREEZE_RELEASE_TICKS")
                || !events.contains("ICE_TOMB_RELEASE_TICKS") || !events.contains("player.stopRiding()")
                || !events.contains("player.setTicksFrozen(0)")) {
                throw new AssertionError("Ice freeze and Ice Tomb must release both movement and frozen overlay state");
        }
        if (events.contains("rearmContinuousSpray") || events.contains("SPRAY_REARM_TICKS")
                || events.contains("SPRAY_DAMAGE_REARM_INTERVAL_TICKS")
                || events.contains("cone.setDealDamageActive()")) {
            throw new AssertionError("continuous sprays must not use a global server rearm");
        }
        if (!events.contains("snapshotSyncTicks") || !events.contains("% 10L == 0L")
                || !events.contains("manager.displayEnabled()")
                || events.contains("keepMountedCastingNormal")
                || events.contains("Set.copyOf(ACTIVE_TRACKED_ENTITIES)")) {
            throw new AssertionError("server tick broadcasts and active entity loops are not bounded");
        }
        for (String method : new String[] {"tickArcaneShackles", "tickSummonedHorses",
                "tickGravityFissures", "tickFangTargets", "tickFissures", "tickPetriviseCleanup"}) {
            int start = events.indexOf("void " + method);
            if (start < 0 || events.indexOf("ACTIVE_TRACKED_ENTITIES.isEmpty()", start) < 0) {
                throw new AssertionError(method + " must return immediately when no tracked entities are active");
            }
        }
        if (!events.contains("hasTrackedLance") || !events.contains("RECENT_EMPULSE_CASTERS.isEmpty()")) {
            throw new AssertionError("thunder lance tick must avoid empty-world scans");
        }
        String performanceConfig = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/config/PerformanceConfig.java"));
        String sprayDiagnostic = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SprayDiagnostic.java"));
        if (!performanceConfig.contains("debugSprayDiagnostics")
                || !sprayDiagnostic.contains("serverValues().debugSprayDiagnostics()")
                || !sprayDiagnostic.contains("cone == null")
                || !events.contains("ACTIVE_TRACKED_ENTITIES")) {
            throw new AssertionError("spray diagnostics must be opt-in and active entity ticks must avoid global scans");
        }
        String mixinConfigForFreeze = Files.readString(Path.of("src/main/resources/iron_magic_duel.mixins.json"));
        String fontCompat = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/FlashbackFontResourceMixin.java"));
        if (!mixinConfigForFreeze.contains("FlashbackFontResourceMixin")
                || !fontCompat.contains("com.moulberry.flashback.editor.ui.ReplayUI")
                || !fontCompat.contains("assets/flashback/")
                || !fontCompat.contains("getResourceManager().getResource(id).isPresent()")
                || !fontCompat.contains("stream.readAllBytes()")
                || !fontCompat.contains("require = 1")) {
            throw new AssertionError("Connector Flashback font compatibility must use the official mapped classpath resource only when the ResourceManager misses it");
        }
        String frozenTargetConeCollisionMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/FrozenTargetConeCollisionMixin.java"));
        if (mixinConfigForFreeze.contains("RayOfFrostFreezeMixin") || mixinConfigForFreeze.contains("ConeOfColdFreezeMixin")
                || !mixinConfigForFreeze.contains("FrozenTargetConeCollisionMixin")
                || !events.contains("FREEZE_DURATION_TICKS = 36")
                 || !events.contains("CONE_OF_COLD_SPELL_ID")
                 || events.contains("Utils.addFreezeTicks(player, 10)") || !events.contains("MobEffectRegistry.CHILLED")
                || !events.contains("clearFreezeState") || !events.contains("player.setTicksFrozen(duration)")
                || !events.contains("onConeOfColdPlayerDamaged")
                || !events.contains("FREEZE_STATES")
                || !events.contains("SPRAY_REFREEZE_BLOCK_UNTIL")
                || !events.contains("RAY_OF_FROST_FREEZE_TICKS = 20")
                || !events.contains("SNOWBALL_FREEZE_TICKS = 36")
                || !events.contains("FreezeReason.RAY_OF_FROST")
                || !events.contains("FreezeReason.SNOWBALL")
                || !events.contains("FreezeReason.GLACIAL_EDGE")
                || !events.contains("GLACIAL_EDGE_SPELL_ID")
                || !events.contains("managedFreezeReason")
                || !events.contains("CONE_OF_COLD_PROJECTILE_CLASS")
                || !events.contains("SNOWBALL_PROJECTILE_CLASS")
                || !events.contains("onPlayerRespawn")
                || !events.contains("onPlayerChangedDimension")
                || !events.contains("clearLifecycleFreezeState")
                || !events.contains("onFrozenPlayerAttack")
                || !events.contains("isFreezeControlActive")
                || !events.contains("player.setDeltaMovement(Vec3.ZERO)")
                || !events.contains("if (manager != null) manager.tick()")
                || !events.contains("if (manager == null) return;")
                || !events.contains("!player.isAlive()")
                || !events.contains("player.isDeadOrDying()")
                || !frozenTargetConeCollisionMixin.contains("getSubEntityCollisions")
                || !frozenTargetConeCollisionMixin.contains("@Overwrite")
                || !frozenTargetConeCollisionMixin.contains("SpellDuelEvents.isFrozenConeTarget")
                || !frozenTargetConeCollisionMixin.contains("!SpellDuelEvents.isFrozenConeTarget")
                || frozenTargetConeCollisionMixin.contains("isIceTombPassenger")) {
            throw new AssertionError("frozen and controlled targets must be excluded from repeated cone collisions");
        }
        int freezeHandler = events.indexOf("public static void onPlayerHurt");
        int freezeHandlerEnd = events.indexOf("    /**", freezeHandler + 1);
        String freezeHandlerBody = events.substring(freezeHandler, freezeHandlerEnd);
        if (freezeHandlerBody.contains("resetCastingState") || freezeHandlerBody.contains("stopUsingItem")
                || freezeHandlerBody.contains("discard()")) {
            throw new AssertionError("freeze handling must never interrupt or remove a channeled spray spell");
        }
        int coneFreezeHandler = events.indexOf("public static void onConeOfColdPlayerDamaged");
        int coneFreezeHandlerEnd = events.indexOf("    /** Spectators may inspect a duel", coneFreezeHandler);
        String coneFreezeBody = coneFreezeHandler >= 0 && coneFreezeHandlerEnd > coneFreezeHandler
                ? events.substring(coneFreezeHandler, coneFreezeHandlerEnd) : "";
        int frozenGate = events.indexOf("public static boolean isFrozenConeTarget");
        int frozenGateEnd = events.indexOf("    /** True only while this mod owns", frozenGate);
        String frozenGateBody = frozenGate >= 0 && frozenGateEnd > frozenGate
                ? events.substring(frozenGate, frozenGateEnd) : "";
        if (!coneFreezeBody.contains("applyCompleteFreeze(player, FreezeReason.CONE_OF_COLD)")
                || coneFreezeBody.contains("now - firstHit") || coneFreezeBody.contains(">= 20L")
                || frozenGateBody.contains("getTicksFrozen()")
                || events.contains("applyCompleteFreeze(player, FreezeReason.RAY_OF_FROST)")
                || events.contains("getFreezeTicks() > 0")
                || events.contains("applyCompleteFreeze(player, FreezeReason.DAMAGE_SOURCE)")
                || events.contains("CONE_OF_COLD_FIRST_HIT_TICKS")
                || events.contains("CONE_OF_COLD_LAST_HIT_TICKS")
                || events.contains("releaseStaleConeOfColdHit")) {
            throw new AssertionError("Cone of Cold must freeze on its first valid hit and only gate this mod's freeze state");
        }
        String regeneration = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/server/DuelRegenerationEvents.java"));
        if (!regeneration.contains("LivingHealEvent") || !regeneration.contains("onDeath")
                || regeneration.contains("setHealth(state.healthFloor())")
                || !regeneration.contains("REGEN_DELAY_TICKS = 100")) {
            throw new AssertionError("regen delay must not write a dead player's health back to zero after respawn");
        }
        if (!events.contains("AUTO_REGEN_PER_PULSE = 10.0F")
                || !events.contains("AUTO_REGEN_FIRST_PULSE_TICKS = 140L")
                || !events.contains("AUTO_REGEN_INTERVAL_TICKS = 40L")
                || !events.contains("AUTO_REGEN_DELAY_TICKS = 100L")
                || !events.contains("player.getHealth() + AUTO_REGEN_PER_PULSE")
                || events.contains("player.getHealth() + 0.3F")) {
            throw new AssertionError("automatic recovery must pulse five hearts at 7s and every 2s thereafter");
        }
        String customSpellTree = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/AddonModelSpell.java"))
                + Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/CrosswindIronSlashSpell.java"))
                + Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/BlazingDragonCorridorSpell.java"))
                + Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/AstralPredatorSpell.java"))
                + Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/PhantomHalberdRingSpell.java"));
        for (String forbidden : new String[]{"invulnerableTime", "setInvulnerableTime", "hurtResistantTime",
                "setHurtTime", "setLastHurt", "BYPASSES_INVULNERABILITY"}) {
            if (customSpellTree.contains(forbidden)) {
                throw new AssertionError("custom spell damage must not bypass vanilla hurt immunity: " + forbidden);
            }
        }
        if (!customSpellTree.contains("target.hurt(") || !events.contains("LivingEntity.hurt()")) {
            throw new AssertionError("custom damage must use the vanilla LivingEntity.hurt path");
        }
        if (!events.contains("LivingIncomingDamageEvent") || !events.contains("enforcePlayerHurtImmunity")
                || !events.contains("DamageTypeTags.BYPASSES_COOLDOWN")
                || !events.contains("player.invulnerableTime <= 0")
                || !events.contains("ServerPlayer player")) {
            throw new AssertionError("only server players may use the hurt immunity guard and bypass damage must remain native");
        }
        if (!network.contains("broadcastEliminationSnapshot") || !network.contains("snapshotHealth")
                || !network.contains("? 0.0F")) {
            throw new AssertionError("spectators must receive an explicit zero-health death frame");
        }
        if (!events.contains("broadcastCooldowns") || !events.contains("% 5")) {
            throw new AssertionError("server must force-sync cooldowns at a bounded interval");
        }
        String mixinConfig = Files.readString(Path.of("src/main/resources/iron_magic_duel.mixins.json"));
        if (!mixinConfig.contains("SwingcastStaffSpellSelectionGuardMixin")) {
            throw new AssertionError("replay null-player guard not registered");
        }
        String replayNullGuard = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/SwingcastStaffSpellSelectionGuardMixin.java"));
        if (!replayNullGuard.contains("@Pseudo") || !replayNullGuard.contains("event.getEntity() == null")) {
            throw new AssertionError("replay null-player guard must be optional and check the event player");
        }
        String spellBarMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/SpellBarOverlayMixin.java"));
        String cooldownMessageMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/CooldownMessageMixin.java"));
        String cooldownActionbarMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/CooldownActionbarMixin.java"));
        String cooldownOverlayMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/CooldownOverlayMixin.java"));
        String vanillaHud = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/VanillaHudSuppressor.java"));
        if (mixinConfig.contains("EntityKillMixin") || mixinConfig.contains("KillCommandMixin")
                || mixinConfig.contains("ServerPlayerDeathMixin") || !mixinConfig.contains("SpellBarOverlayMixin") || !mixinConfig.contains("ManaBarOverlayMixin")
                || !spellBarMixin.contains("ci.cancel()")
                || !spellBarMixin.contains("SpellBarOverlay")
                || !mixinConfig.contains("CooldownMessageMixin")
                || !mixinConfig.contains("CooldownActionbarMixin")
                || !mixinConfig.contains("CooldownOverlayMixin")
                || !cooldownMessageMixin.contains("CastErrorPacket.ErrorType.COOLDOWN")
                || !cooldownMessageMixin.contains("ci.cancel()")
                || !cooldownActionbarMixin.contains("ui.irons_spellbooks.cast_error_cooldown")
                || !cooldownActionbarMixin.contains("message.getContents()")
                || !cooldownActionbarMixin.contains("ci.cancel()")
                || !cooldownOverlayMixin.contains("setOverlayMessage")
                || !cooldownOverlayMixin.contains("正在冷却")
                || !cooldownOverlayMixin.contains("ci.cancel()")
                || !vanillaHud.contains("VanillaGuiLayers.PLAYER_HEALTH")
                || !vanillaHud.contains("VanillaGuiLayers.FOOD_LEVEL")
                || !vanillaHud.contains("VanillaGuiLayers.EXPERIENCE_BAR")
                || !vanillaHud.contains("VanillaGuiLayers.EXPERIENCE_LEVEL")) {
            throw new AssertionError("the original Iron spell bar and vanilla health/food/experience layers must be suppressed");
        }
        String travelOpticsCompat = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/TravelOpticsSandstormCompatMixin.java"));
        if (!mixinConfig.contains("TravelOpticsSandstormCompatMixin") || !travelOpticsCompat.contains("SANDSTORM")
                || !travelOpticsCompat.contains("DUST_BLAST") || !travelOpticsCompat.contains("@Redirect")) {
            throw new AssertionError("TravelOptics and Cataclysm sandstorm compatibility redirect is missing");
        }
        String soundOcclusion = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/IronSpellSoundOcclusionMixin.java"));
        if (!mixinConfig.contains("IronSpellSoundOcclusionMixin") || soundOcclusion.contains("getLocation().getNamespace")
                || !soundOcclusion.contains("MAX_WORLD_SOUND_DISTANCE = 32.0D")
                || soundOcclusion.contains("distanceSquared <=")
                || !soundOcclusion.contains("ClipContext.Block.COLLIDER") || !soundOcclusion.contains("ci.cancel()")) {
            throw new AssertionError("all world-positioned sounds must have namespace-independent wall occlusion and a distance cap");
        }
        if (Files.exists(Path.of("src/main/resources/data/traveloptics/tags/entity_type/element_fire.json"))) {
            throw new AssertionError("TravelOptics tag must be repaired in its own jar, not merged as a duplicate data pack resource");
        }
        if (!network.contains("HudPositionPayload") || !network.contains("sendHudPosition")) {
            throw new AssertionError("HUD position must be sent only to the command player");
        }
        if (!manager.contains("recordDuelDeathAndScheduleRecovery") || !manager.contains("finishAfterVanillaDeath")
                || !manager.contains("WINNER_RETURN_DELAY_TICKS = 5L * 20L")
                || !manager.contains("finishParticipants(group, winners, deadPlayer, challenge == null ? 0L : challenge.token)")
                || !manager.contains("pendingWinnerRestores.put") || manager.contains("getPlayerList().respawn")) {
            throw new AssertionError("only winners may wait five seconds; defeated players must stay in vanilla respawn flow");
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
        String travelFossilCompat = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/TravelOpticsMagicFossilSummonCompatMixin.java"));
        if (!mixinConfig.contains("TravelOpticsMagicFossilSummonCompatMixin")
                || !travelFossilCompat.contains("MagicFossilSummon;getSummoner()")
                || !travelFossilCompat.contains("IMagicSummon")
                || !travelFossilCompat.contains("require = 1")) {
            throw new AssertionError("TravelOptics fossil summon removal must redirect its invalid covariant API call to IMagicSummon");
        }
        String clientPerformance = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/ClientPerformanceEvents.java"));
        String serverPerformance = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/server/ServerPerformanceEvents.java"));
        if (!config.contains("onConfigLoading") || !config.contains("onConfigReloading")
                || !config.contains("clientConfigLoaded") || !config.contains("serverConfigLoaded")
                || !config.contains("ClientValues.defaults()") || !config.contains("ServerValues.defaults()")
                || !clientPerformance.contains("isClientConfigLoaded()")
                || !serverPerformance.contains("isServerConfigLoaded()")
                || clientPerformance.matches("(?s).*PerformanceConfig\\.(CLIENT|SERVER)\\.[A-Za-z0-9_]+\\.(get|getRaw)\\(.*")
                || serverPerformance.matches("(?s).*PerformanceConfig\\.(CLIENT|SERVER)\\.[A-Za-z0-9_]+\\.(get|getRaw)\\(.*")) {
            throw new AssertionError("performance tick callbacks must use config snapshots only after Loading/Reloading events");
        }
        String alshanexDuplicateGuard = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/AlshanexAddItemsCodecDuplicateMixin.java"));
        if (!mixinConfig.contains("AlshanexAddItemsCodecDuplicateMixin")
                || !alshanexDuplicateGuard.contains("GLOBAL_LOOT_MODIFIER_SERIALIZERS")
                || !alshanexDuplicateGuard.contains("alshanex_familiars")
                || !alshanexDuplicateGuard.contains("add_items")
                || !alshanexDuplicateGuard.contains("add_items_modifier")
                || !alshanexDuplicateGuard.contains("getResourceKey(value)")
                || !alshanexDuplicateGuard.contains("existingKey.isEmpty()")
                || !alshanexDuplicateGuard.contains("getHolder(existingKey.get())")
                || alshanexDuplicateGuard.contains("catch (")
                || alshanexDuplicateGuard.contains("Codec.toString")) {
            throw new AssertionError("Alshanex duplicate guard must be registry/key/value-identity scoped without exception swallowing");
        }
        String replayGuard = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/FlashbackReplayPayloadGuardMixin.java"));
        String shakeGuard = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/PerceptionShakeDisableMixin.java"));
        if (!mixinConfig.contains("FlashbackReplayPayloadGuardMixin")
                || !replayGuard.contains("com.moulberry.flashback.playback.ReplayServer")
                || !replayGuard.contains("ClientboundCustomPayloadPacket")
                || !replayGuard.contains("SYNC_ATTACHMENTS")
                || replayGuard.contains("getNamespace()")
                || replayGuard.contains("catch (")) {
            throw new AssertionError("Flashback guard must drop only sync_attachments for ReplayServer");
        }
        String ironShakeGuard = Files.readString(Path.of(
                "src/main/java/com/example/scrollspellicons/mixin/IronSpellbookCameraShakeDisableMixin.java"));
        if (!mixinConfig.contains("PerceptionShakeDisableMixin")
                || !shakeGuard.contains("ShakeManager")
                || !shakeGuard.contains("ci.cancel()")
                || !mixinConfig.contains("IronSpellbookCameraShakeDisableMixin")
                || !ironShakeGuard.contains("CameraShakeManager")
                || !ironShakeGuard.contains("ViewportEvent$ComputeCameraAngles")
                || !ironShakeGuard.contains("ClientTickEvent$Post")
                || !ironShakeGuard.contains("ci.cancel()")) {
            throw new AssertionError("spell camera shake must be disabled at both shared shake entry points");
        }
        if (!mixinConfig.contains("KnightlibCameraShakeDisableMixin")
                || !mixinConfig.contains("OctoLibCameraShakeDisableMixin")
                || !mixinConfig.contains("CataclysmScreenShakeDisableMixin")
                || !mixinConfig.contains("CataclysmCameraZoomDisableMixin")
                || !mixinConfig.contains("GtbcsScreenShakeDisableMixin")
                || !mixinConfig.contains("GtbcsFollowingScreenShakeDisableMixin")
                || !mixinConfig.contains("MowzieCameraShakeDisableMixin")
                || !mixinConfig.contains("HeroicCameraPayloadDisableMixin")
                || !fixedFov.contains("EventPriority.LOWEST")) {
            throw new AssertionError("all installed camera-shake/FOV entry points must be disabled");
        }
        String shop = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelShop.java"));
        if (!shop.contains("PREVIOUS_PAGE_SLOT = 45") || !shop.contains("NEXT_PAGE_SLOT = PAGE_SIZE - 1") || !shop.contains("previousPageButton") || !shop.contains("usedPageCount()")
                || !shop.contains("nextPageButton") || !shop.contains("class ShopMenu")
                || !shop.contains("class EditorMenu") || !shop.contains("open(serverPlayer, true, next)")) {
            throw new AssertionError("shop must reserve its final slot for a populated-page navigation button");
        }
        if (!shop.contains("Items.SNIFFER_EGG") || !shop.contains("stack.is(Items.BARREL)")) {
            throw new AssertionError("new shops must use a sniffer egg while previously-issued barrels remain usable");
        }
        String duelSpellRegistry = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/DuelSpellRegistry.java"));
        String duelItems = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelItems.java"));
        if (Files.exists(Path.of("src/main/java/com/example/scrollspellicons/spells/EmeraldWallSlashSpell.java"))
                || duelSpellRegistry.contains("EmeraldWallSlashSpell")
                || duelItems.contains("emeraldWallSlashScroll()")
                || !duelSpellRegistry.contains("PhantomHalberdRingSpell::new")
                || !duelSpellRegistry.contains("AstralPredatorSpell::new")
                || !duelItems.contains("phantomHalberdRingScroll()")
                || !duelItems.contains("astralPredatorScroll()")
                 || !shop.contains("WITHDRAWN_SHOP_SPELLS") || !shop.contains("removeWithdrawnSpells")
                 || !shop.contains("containsWithdrawnSpell")
                || !duelItems.contains("DUEL_TAB_ICON") || !duelItems.contains("DUEL_TAB_ICON.get().getDefaultInstance()")
                || !duelItems.contains("ISpellContainer.createScrollContainer")) {
            throw new AssertionError("emerald wall must be removed and both direct visual spells must be registered and listed");
        }
        if (commands.contains("literal(\"ai\")") || commands.contains("syncShopAi")
                || commands.contains("exportShopAi") || !shop.contains("WITHDRAWN_SHOP_SPELLS")
                || !shop.contains("class FreeContainer")
                || !shop.contains("void clearContent() { }") || !shop.contains("restockAll()")
                || !shop.contains("shopContainer.restockAll()")) {
            throw new AssertionError("shop must only provide items; numerical balance sync command must be unavailable");
        }
        String scrollIdKeybind = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/ScrollSpellIdKeybind.java"));
        if (!scrollIdKeybind.contains("GLFW_KEY_I") || !scrollIdKeybind.contains("IRONS_SCROLL")
                || !scrollIdKeybind.contains("getSpellResource") || !scrollIdKeybind.contains("glfwSetClipboardString")) {
            throw new AssertionError("held Iron spell scroll ID keybind is missing");
        }
        if (mixinConfig.contains("IceFreezeDurationMixin") || mixinConfig.contains("IceTombDurationMixin")
                || !events.contains("player.setTicksFrozen(duration)")
                || !events.contains("clearFreezeState(player, true)")
                || events.contains("private static void synchronizeReleasedPlayer") && events.substring(
                        events.indexOf("private static void synchronizeReleasedPlayer"),
                        Math.min(events.length(), events.indexOf("    /**", events.indexOf("private static void synchronizeReleasedPlayer")))).contains("setDeltaMovement")
                || events.contains("MAX_" + "ICE_FREEZE_TICKS")
                || events.contains("FREEZE_" + "LIMIT_TICKS")) {
            throw new AssertionError("all managed freeze paths must use explicit whitelist durations without mutating unrelated sources");
        }
        int applyFreeze = events.indexOf("private static void applyCompleteFreeze(net.minecraft.server.level.ServerPlayer player, FreezeReason reason)");
        int applyFreezeEnd = events.indexOf("    /**", applyFreeze + 1);
        int clearFreeze = events.indexOf("boolean discardIceTomb, boolean terminal)");
        int clearFreezeEnd = events.indexOf("    /**", clearFreeze + 1);
        String freezeBody = applyFreeze >= 0 && applyFreezeEnd > applyFreeze
                ? events.substring(applyFreeze, applyFreezeEnd) : "";
        String clearBody = clearFreeze >= 0 && clearFreezeEnd > clearFreeze
                ? events.substring(clearFreeze, clearFreezeEnd) : "";
        if (freezeBody.contains(".hurt(") || freezeBody.contains("setHealth") || freezeBody.contains(".die(")
                || freezeBody.contains("setInvulnerable") || freezeBody.contains("noPhysics")
                || clearBody.contains("resetCastingState") || clearBody.contains("stopUsingItem")
                || clearBody.contains("discardCastingEntity") || clearBody.contains("cone.discard")) {
            throw new AssertionError("freeze control must not damage players or terminate a caster's live spray");
        }

        String quickCastKeys = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/QuickCastKeyUniqueness.java"));
        String quickCastMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/QuickCastKeyMappingMixin.java"));
        String quickContinuousCasting = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/ContinuousSpellCasting.java"));
        String lang = Files.readString(Path.of("src/main/resources/assets/iron_magic_duel/lang/zh_cn.json"));
        if (!quickCastKeys.contains("normalizeLoadedMappings") || !quickCastKeys.contains("i < index")
                || !quickCastMixin.contains("setKey") || !quickContinuousCasting.contains("dispatchedKeys")
                || !lang.contains("key.iron_magic_duel.quick_cast_duplicate")) {
            throw new AssertionError("quick-cast key uniqueness must reject duplicate bindings and deduplicate dispatch");
        }
        if (!events.contains("SUMMONED_HORSE_HITS") || !events.contains("onSummonedHorseMount")
                || !events.contains("event.setCanceled(true)") || !events.contains("countSummonedHorseHit")
                || !events.contains("event.getNewDamage() <= 0.0F") || !events.contains("horse.die")
                || !events.contains("hits >= 5")) {
            throw new AssertionError("summoned horse must block dismount and die on the fifth effective hit");
        }
        if (events.contains("rearmContinuousSpray")) {
            throw new AssertionError("freeze release must not use a global spray rearm loop");
        }

        String pillarMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/ResoundingPillarSpellMixin.java"));
        if (!mixinConfig.contains("ResoundingPillarSpellMixin") || !pillarMixin.contains("MAX_PILLARS = 4")
                || !pillarMixin.contains("DURATION_TICKS = 20 * 20") || !pillarMixin.contains("removeExistingPillars")
                || !pillarMixin.contains("@Pseudo")) {
            throw new AssertionError("resounding pillar compatibility must be optional, capped at four, and last twenty seconds");
        }
        if (mixinConfig.contains("ContinuousSpellCastCancelMixin")) {
            throw new AssertionError("the input cancel redirect must remain disabled");
        }
        String uninterruptibleCastingMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/UninterruptibleChanneledCastMixin.java"));
        String uninterruptibleRecastMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/UninterruptibleChanneledRecastMixin.java"));
        String channeledCastGuard = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/server/ChanneledCastGuard.java"));
        if (!mixinConfig.contains("UninterruptibleChanneledCastMixin") || !mixinConfig.contains("UninterruptibleChanneledRecastMixin")
                || !uninterruptibleCastingMixin.contains("isIntentionalSprayCancellation")
                || !uninterruptibleCastingMixin.contains("cancelCast(Lnet/minecraft/server/level/ServerPlayer;Z)V")
                || !uninterruptibleRecastMixin.contains("cancelActiveSpray")
                || !uninterruptibleRecastMixin.contains("serverSideInitiateCast(Lnet/minecraft/server/level/ServerPlayer;)Z")
                || !uninterruptibleRecastMixin.contains("serverSideInitiateQuickCast(Lnet/minecraft/server/level/ServerPlayer;I)Z")
                || !uninterruptibleRecastMixin.contains("blocksReplacement")
                || !channeledCastGuard.contains("requestedSpell.getSpell().getSpellId()")
                || !channeledCastGuard.contains("getAdditionalCastData() instanceof EntityCastData")
                 || !channeledCastGuard.contains("CancelCastPacket.cancelCast(player, false)")
                 || channeledCastGuard.contains("CancelCastPacket.cancelCast(player, true)")) {
            throw new AssertionError("all chants must block only replacement spells while retaining their own cast handling");
        }
        String hollowCrystalMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/HollowCrystalCastTimeMixin.java"));
        if (!mixinConfig.contains("HollowCrystalCastTimeMixin") || !hollowCrystalMixin.contains("HollowCrystalSpell")
                || !hollowCrystalMixin.contains("cir.setReturnValue(16)")) {
            throw new AssertionError("hollow_crystal must have a 0.8-second chant time");
        }
        String continuousCasting = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/ContinuousSpellCasting.java"));
        if (!continuousCasting.contains("MAGIC_MISSILE_ID") || !continuousCasting.contains("isRepeatable")
                || !continuousCasting.contains("new QuickCastPacket(slot)") || !continuousCasting.contains("new CastPacket()")
                || !continuousCasting.contains("ClientMagicData.isCasting()")
                || !continuousCasting.contains("return !ClientMagicData.isCasting()")
                || !continuousCasting.contains("shouldRearmThisTick")
                || !continuousCasting.contains("canContinueOrRestart")
                || !continuousCasting.contains("Every selected spell can repeat")
                || continuousCasting.contains("CastType.CONTINUOUS")) {
            throw new AssertionError("held-key repeat casting must cover every selected spell while magic_missile is excluded");
        }
        String continuousConeLifetimeMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/ContinuousConeLifetimeMixin.java"));
        if (!mixinConfig.contains("ContinuousConeLifetimeMixin")
                || !continuousConeLifetimeMixin.contains("discardCastingEntity")
                || !continuousConeLifetimeMixin.contains("CastType.CONTINUOUS")
                || !continuousConeLifetimeMixin.contains("CONE_OF_COLD")
                || !continuousConeLifetimeMixin.contains("!player.level().isClientSide")
                || !continuousConeLifetimeMixin.contains("cone.isRemoved()")
                || !continuousConeLifetimeMixin.contains("!cone.isAlive()")
                || !continuousConeLifetimeMixin.contains("cone.getOwner() != player")
                || !continuousConeLifetimeMixin.contains("ci.cancel()")) {
            throw new AssertionError("only an active Cone of Cold may survive intermediate cast-data cleanup");
        }
        String horseMovement = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/SummonedHorseCastMovement.java"));
        if (!horseMovement.contains("MovementInputUpdateEvent")
                || !horseMovement.contains("EventPriority.LOWEST")
                || !horseMovement.contains("instanceof SummonedHorse")
                || !horseMovement.contains("AttributeRegistry.CASTING_MOVESPEED")
                || !horseMovement.contains("/=")) {
            throw new AssertionError("summon_horse riders must restore normal input only while casting");
        }
        if (mixinConfig.contains("ContinuousConeDiscardMixin")) {
            throw new AssertionError("invalid inherited Entity.discard mixin must not be registered; Entity.discard is not an injectable method on AbstractConeProjectile");
        }
        if (!events.contains("MAGIC_MISSILE_SPELL_ID") || !events.contains("event.getSpellId()")
                || !events.contains("onMagicMissileSpellDamage") || !events.contains("onMagicMissileJoinLevel")
                || !events.contains("onMagicMissileLivingDamage") || !events.contains("event.setNewDamage(0.0F)")) {
            throw new AssertionError("magic missile must be rejected at cast, spawn, and damage layers");
        }
        String autoSprint = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/client/AutoSprintKeybind.java"));
        if (!autoSprint.contains("GLFW.GLFW_KEY_V")
                || !autoSprint.contains("minecraft.player.input.forwardImpulse")
                || !autoSprint.contains("minecraft.player.input.leftImpulse")
                || !autoSprint.contains("minecraft.player.setSprinting(false)")) {
            throw new AssertionError("auto sprint must support all horizontal directions and stop immediately when movement stops");
        }
        // Regression checks for the two acceptance-critical state machines:
        // authored cooldowns are allowlisted, and portable inscription uses
        // the server's existing validated confirm path exactly once per input
        // change (never a per-tick material drain).
        String unconditionalCast = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/UnconditionalSpellCastMixin.java"));
        String autoWrite = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/PortableInscriptionAutoWriteMixin.java"));
        String blazing = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/BlazingDragonCorridorSpell.java"));
        String phantom = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/PhantomHalberdRingSpell.java"));
        String addonModel = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/AddonModelSpell.java"));
        String crosswind = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/CrosswindIronSlashSpell.java"));
        String astral = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/spells/AstralPredatorSpell.java"));
        String gravity = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/duel/SpellDuelEvents.java"));
        String voidBulwarkMixin = Files.readString(Path.of("src/main/java/com/example/scrollspellicons/mixin/VoidRuneBulwarkSpellMixin.java"));
        if (!events.contains("usesAuthoredCooldown")
                || !autoWrite.contains("slotsChanged") || !autoWrite.contains("clickMenuButton(player, -1)")
                || !autoWrite.contains("autoWriteInProgress") || !autoWrite.contains("isClientSide")
                || !blazing.contains("HIGH_AIR_OFFSET") || !blazing.contains("damageDescendingStrike")
                || !blazing.contains("SLASH_DAMAGE = 12") || !blazing.contains("discard()")
                || blazing.contains("setGlowingTag(true)")
                || !phantom.contains("FINAL_LIGHTNING_DAMAGE = 25.0F")
                || !phantom.contains("recordDamage") || !phantom.contains("DUEL_TICKS = 200")
                || !phantom.contains("ServerBossEvent") || !phantom.contains("spawnIronLightningStrike")
                || !phantom.contains("List.copyOf(duels)") || !phantom.contains("age % 5 == 0")
                || !phantom.contains("boolean closed") || !phantom.contains("if (closed) return")
                || !phantom.contains("setRadius(8.0F)") || !phantom.contains("setDuration(45)")
                || !phantom.contains("ZapParticleOption") || !phantom.contains("topY = point.y + 28.0D")
                || !crosswind.contains("getDamageSource(caster)")
                || !astral.contains("getDamageSource(caster)")
                || !addonModel.contains("DamageSource source = getDamageSource(caster)")
                || !gravity.contains("GRAVITY_FISSURE_RADIUS = 5.0D")
                || !gravity.contains("GRAVITY_FISSURE_LIFETIME = 80L")
                || !gravity.contains("tickGravityFissures")
                || !mixinConfig.contains("VoidRuneBulwarkSpellMixin")
                || !voidBulwarkMixin.contains("IronMagicVoidBulwarkRune")
                || !events.contains("onVoidBulwarkDamage")
                || !events.contains("event.setNewDamage(10.0F)")) {
            throw new AssertionError("cooldown allowlist, automatic validated inscription, or vertical dragon strike regression detected");
        }
        if (events.contains("DUEL_SHIELD_TAG") || events.contains("spawnDuelShield")
                || events.contains("tickDuelShields") || events.contains("blockSpellEntitiesAtDuelShields")
                || events.contains("isBlockedByWall") || mixinConfig.contains("ShieldSpellBehaviorMixin")
                || mixinConfig.contains("InfiniteShieldMixin")
                || Files.exists(Path.of("src/main/java/com/example/scrollspellicons/mixin/ShieldSpellBehaviorMixin.java"))
                || Files.exists(Path.of("src/main/java/com/example/scrollspellicons/mixin/InfiniteShieldMixin.java"))) {
            throw new AssertionError("iron_spellbooks:shield must use the native Iron's Spells implementation without custom intervention");
        }
        if (!events.contains("hasUUID(\"FollowTarget\")") || !events.contains("hasUUID(FISSURE_TARGET_TAG)")) {
            throw new AssertionError("fang and fissure tracking must validate UUID NBT before getUUID");
        }
        if (events.contains("AUTHORED_SPELL_COOLDOWNS") || events.contains("addCooldown(event.getSpellId()")
                || mixinConfig.contains("UnrestrictedCooldownMixin")
                || !unconditionalCast.contains("magic.getPlayerCooldowns().isOnCooldown(spell)")) {
            throw new AssertionError("authored spells must use the native, balance-modified per-spell cooldown only");
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

    private static void verifyMixinTargetWhenPresent(String mixinsJson, String mixinName,
                                                     String source, String targetClass,
                                                     String injectionDescriptor) {
        if (!mixinsJson.contains("\"" + mixinName + "\"")) {
            throw new AssertionError("mixin is not registered: " + mixinName);
        }
        if (!source.contains(injectionDescriptor)) {
            throw new AssertionError("injection descriptor missing for " + mixinName);
        }
        String resource = targetClass.replace('.', '/') + ".class";
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader.getResource(resource) == null) {
            System.out.println("mixin-target-check: optional target absent " + targetClass);
            return;
        }
        try {
            Class.forName(targetClass, false, loader);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("target resource exists but cannot load: " + targetClass, e);
        }
        System.out.println("mixin-target-check: loaded " + mixinName + " -> " + targetClass
                + " with " + injectionDescriptor);
    }
}
