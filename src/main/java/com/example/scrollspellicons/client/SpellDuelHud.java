package com.example.scrollspellicons.client;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;
import top.theillusivec4.curios.api.CuriosApi;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(value = Dist.CLIENT, modid = "iron_magic_duel")
public final class SpellDuelHud {
    private static final int MIN_HUD_WIDTH = 120;
    private static final int MAX_HUD_WIDTH = 120;
    private static final int SPELL_BOX_WIDTH = MIN_HUD_WIDTH;
    private static final int CARD_WIDTH = MAX_HUD_WIDTH + 34;
    private static final int SPELL_COLUMNS = 6;
    private static final int PLAYER_FACE_SIZE = 24;
    private static final int HUD_MARGIN = 8;
    // GuiGraphics colors are ARGB. Without FF this bar is fully transparent.
    private static final int GREEN = 0xFF33AA55;
    private static final int BLUE = 0xFF2A8FE8;
    private SpellDuelHud() {}

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // F3 is Minecraft's diagnostic view.  Do not place any duel overlays on
        // top of it: the extra spell icons made the debug screen misleading.
        if (mc.options.renderDebug) return;
        // Duel information is spectator-only.  Normal players retain the
        // vanilla health/food HUD and never receive projected player panels or
        // spell icons above other players.
        if (!mc.player.isSpectator()) return;
        renderSpectatorSnapshot(event.getGuiGraphics(), mc);
    }

    private static void renderPlayerSpellBar(GuiGraphics graphics, Minecraft mc, Player player) {
        float[] screen = project(mc, player);
        if (screen == null) return;
        List<SpellVisual> spells = spells(player);
        int hudWidth = hudWidth(spells.size());
        int rows = spellRows(spells.size());
        int spellHeight = rows * 18 + 6;
        String name = player.getName().getString();
        int panelWidth = Math.max(hudWidth, mc.font.width(name)) + 10;
        int panelHeight = spellHeight + 2 + 20 + 14;
        int x = Math.round(screen[0]) - panelWidth / 2;
        int y = Math.round(screen[1]) - panelHeight - 4;
        int contentX = x + (panelWidth - hudWidth) / 2;
        int healthY = y + spellHeight + 2;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xFF000000);
        drawSpellBox(graphics, mc, spells, contentX, y, hudWidth, spellHeight);
        drawHealthBox(graphics, mc, contentX, healthY, hudWidth, player.getHealth(), player.getMaxHealth());
        graphics.drawString(mc.font, name, x + (panelWidth - mc.font.width(name)) / 2,
                healthY + 22, 0xFFFFFF, true);
    }

    private static void renderLocalSpellHud(GuiGraphics graphics, Minecraft mc) {
        List<SpellVisual> spells = spells(mc.player);
        int hudWidth = hudWidth(spells.size());
        int spellHeight = spellRows(spells.size()) * 18 + 6;
        int panelWidth = PLAYER_FACE_SIZE + 12 + hudWidth;
        int panelHeight = 22 + spellHeight + 5 + 20 + 4 + 20 + 7;
        int x = hudX(graphics, panelWidth);
        int y = hudY(graphics, panelHeight);
        int contentX = x + PLAYER_FACE_SIZE + 8;
        int spellY = y + 22;
        int healthY = spellY + spellHeight + 5;
        int manaY = healthY + 24;
        drawLocalPanelFrame(graphics, x, y, panelWidth, panelHeight);
        renderLocalPlayerFace(graphics, mc, x + 5, y + 5);
        graphics.drawString(mc.font, mc.player.getName().getString(), contentX, y + 7, 0xFFFFFFFF, true);
        drawSpellBox(graphics, mc, spells, contentX, spellY, hudWidth, spellHeight);
        drawHealthBox(graphics, mc, contentX, healthY, hudWidth, mc.player.getHealth(), mc.player.getMaxHealth());
        drawManaBox(graphics, mc, contentX, manaY, hudWidth, ClientMagicData.getPlayerMana(),
                (float) mc.player.getAttributeValue(AttributeRegistry.MAX_MANA.get()));
    }

    private static int hudX(GuiGraphics graphics, int panelWidth) {
        int rightMargin = SpellDuelClientState.hudX() >= 9000 ? HUD_MARGIN : 0;
        return Math.max(0, Math.min(SpellDuelClientState.hudX(), graphics.guiWidth() - panelWidth - rightMargin));
    }

    private static int hudY(GuiGraphics graphics, int panelHeight) {
        return Math.max(0, Math.min(SpellDuelClientState.hudY(), graphics.guiHeight() - panelHeight));
    }

    private static void drawLocalPanelFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF555555);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xFF0B0B0B);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 21, 0xFF121212);
    }

    private static void renderLocalPlayerFace(GuiGraphics graphics, Minecraft mc, int x, int y) {
        drawFrame(graphics, x, y, PLAYER_FACE_SIZE, PLAYER_FACE_SIZE);
        AbstractClientPlayer clientPlayer = mc.player;
        PlayerFaceRenderer.draw(graphics, clientPlayer.getSkinTextureLocation(), x + 4, y + 4, 16);
    }

    private static void renderSpectatorSnapshot(GuiGraphics graphics, Minecraft mc) {
        int leftY = 12;
        int rightY = 12;
        int rightX = Math.max(8, graphics.guiWidth() - CARD_WIDTH - 8);
        for (var entry : SpellDuelClientState.snapshot()) {
            int x = entry.team() == 0 ? 8 : rightX;
            int y = entry.team() == 0 ? leftY : rightY;
            int height = renderSpectatorEntry(graphics, mc, entry, x, y);
            if (entry.team() == 0) leftY += height + 8; else rightY += height + 8;
        }
    }

    private static int renderSpectatorEntry(GuiGraphics graphics, Minecraft mc,
                                            com.example.scrollspellicons.duel.SpellDuelNetwork.SnapshotEntry entry,
                                            int x, int y) {
        int spellCount = entry.spells().isBlank() ? 0 : entry.spells().split(",").length;
        int hudWidth = hudWidth(spellCount);
        int rows = spellRows(spellCount);
        int spellHeight = rows * 18 + 6;
        int healthY = y + 24 + spellHeight + 2;
        int manaY = healthY + 23;
        int height = manaY - y + 21 + (entry.casting().isBlank() ? 0 : 12);
        // Opaque panel: spectator information should remain readable over the world.
        graphics.fill(x - 6, y - 5, x + hudWidth + 34, y + height + 4, 0xFF000000);

        Player found = mc.level.players().stream()
                .filter(player -> player.getName().getString().equals(entry.name()))
                .findFirst().orElse(null);
        if (found instanceof AbstractClientPlayer clientPlayer) {
            PlayerFaceRenderer.draw(graphics, clientPlayer.getSkinTextureLocation(), x, y, 24);
        }
        int textX = x + 30;
        graphics.drawString(mc.font, entry.name(), textX, y + 2,
                entry.team() == 0 ? 0x55AAFF : 0xFF7777, true);
        drawSpellBox(graphics, mc, parseSpells(entry.spells(), parseCooldowns(entry.cooldowns())), textX, y + 19, hudWidth, spellHeight);
        drawHealthBox(graphics, mc, textX, healthY, hudWidth, entry.health(), entry.maxHealth());
        drawManaBox(graphics, mc, textX, manaY, hudWidth, entry.mana(), entry.maxMana());
        healthY = manaY;
        if (!entry.casting().isBlank()) {
            graphics.drawString(mc.font, "施法：" + shortId(entry.casting()), textX, healthY + 23, 0x55FFFF, true);
        }
        return height;
    }

    private static void drawSpellBox(GuiGraphics graphics, Minecraft mc, List<SpellVisual> spells,
                                     int x, int y, int width, int height) {
        drawFrame(graphics, x, y, width, height);
        for (int i = 0; i < spells.size(); i++) {
            SpellVisual visual = spells.get(i);
            renderSpellIcon(graphics, mc, visual, x + 4 + (i % SPELL_COLUMNS) * 18,
                    y + 3 + (i / SPELL_COLUMNS) * 18);
        }
    }

    private static void drawHealthBox(GuiGraphics graphics, Minecraft mc, int x, int y, int width,
                                      float health, float maxHealth) {
        float ratio = maxHealth > 0 && Float.isFinite(health) && Float.isFinite(maxHealth)
                ? Math.max(0, Math.min(1, health / maxHealth)) : 0;
        drawStatusBar(graphics, x, y, width, ratio, GREEN);
    }

    private static void drawManaBox(GuiGraphics graphics, Minecraft mc, int x, int y, int width,
                                    float mana, float maxMana) {
        float ratio = maxMana > 0 && Float.isFinite(mana) && Float.isFinite(maxMana)
                ? Math.max(0, Math.min(1, mana / maxMana)) : 0;
        drawStatusBar(graphics, x, y, width, ratio, BLUE);
    }

    private static void drawStatusBar(GuiGraphics graphics, int x, int y, int width, float ratio, int fillColor) {
        graphics.fill(x, y, x + width, y + 20, 0xFF000000);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 18, 0xFF555555);
        graphics.fill(x + 5, y + 5, x + width - 5, y + 15, 0xFF161616);
        int filledWidth = Math.round((width - 10) * ratio);
        if (filledWidth > 0) graphics.fill(x + 5, y + 5, x + 5 + filledWidth, y + 15, fillColor);
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF07090D);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF555555);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xFF151515);
    }

    private static int spellRows(int count) {
        return Math.max(1, (count + SPELL_COLUMNS - 1) / SPELL_COLUMNS);
    }

    private static int hudWidth(int spellCount) {
        int visibleColumns = Math.max(1, Math.min(SPELL_COLUMNS, spellCount));
        return Math.max(MIN_HUD_WIDTH, Math.min(MAX_HUD_WIDTH, 8 + visibleColumns * 18));
    }

    private static List<SpellVisual> parseSpells(String encoded, Map<String, Integer> cooldowns) {
        List<SpellVisual> result = new ArrayList<>();
        if (encoded.isBlank()) return result;
        for (String value : encoded.split(",")) {
            String[] parts = value.split("\\|", 2);
            try {
                ResourceLocation spellId = ResourceLocation.parse(parts[0]);
                result.add(new SpellVisual(spellId, 1, cooldowns.getOrDefault(spellId.toString(), 0)));
            }
            catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    private static Map<String, Integer> parseCooldowns(String encoded) {
        Map<String, Integer> result = new HashMap<>();
        if (encoded.isBlank()) return result;
        for (String entry : encoded.trim().split("\\s+")) {
            int separator = entry.lastIndexOf(':');
            if (separator <= 0 || separator == entry.length() - 1) continue;
            try { result.put(entry.substring(0, separator), Integer.parseInt(entry.substring(separator + 1))); }
            catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private static String shortId(String id) {
        int index = id.indexOf(':');
        return index >= 0 ? id.substring(index + 1) : id;
    }

    private static void renderSpellIcon(GuiGraphics graphics, Minecraft mc, SpellVisual visual, int x, int y) {
        ResourceLocation icon = ScrollIconResolver.iconFor(visual.id()).orElse(null);
        if (icon == null) {
            graphics.fill(x, y, x + 16, y + 16, 0xFF111111);
            graphics.fill(x + 2, y + 2, x + 14, y + 14, 0xFF555555);
            drawCooldownNumber(graphics, mc, x, y, visual.cooldownTicks());
            return;
        }
        // Iron's spellbook GUI uses these 16x16 images directly.  They are not
        // item/block-atlas sprites, so looking them up in LOCATION_BLOCKS turns
        // them into missing-texture squares.  Bind and draw the original image.
        graphics.blit(icon, x, y, 0, 0, 16, 16, 16, 16);
        drawCooldownNumber(graphics, mc, x, y, visual.cooldownTicks());
    }

    private static void drawCooldownNumber(GuiGraphics graphics, Minecraft mc, int x, int y, int cooldownTicks) {
        int seconds = cooldownSeconds(cooldownTicks);
        if (seconds <= 0) return;
        String value = Integer.toString(seconds);
        graphics.fill(x, y, x + 16, y + 16, 0xD9000000);
        graphics.fill(x, y, x + 16, y + 1, 0xFFFFC94D);
        graphics.fill(x, y + 15, x + 16, y + 16, 0xFFFFC94D);
        graphics.fill(x, y, x + 1, y + 16, 0xFFFFC94D);
        graphics.fill(x + 15, y, x + 16, y + 16, 0xFFFFC94D);
        graphics.drawCenteredString(mc.font, value, x + 8, y + 4, 0xFFFFFFFF);
    }

    private static int cooldownSeconds(int cooldownTicks) {
        return cooldownTicks <= 0 ? 0 : (cooldownTicks + 19) / 20;
    }

    private static float[] project(Minecraft mc, Player player) {
        var camera = mc.gameRenderer.getMainCamera();
        var relative = player.position().add(0, player.getBbHeight() + 0.25, 0).subtract(camera.getPosition());
        Quaternionf inverseCamera = new Quaternionf(camera.rotation()).conjugate();
        Vector4f clip = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1).rotate(inverseCamera);
        mc.gameRenderer.getProjectionMatrix(mc.options.fov().get().floatValue()).transform(clip);
        if (clip.w <= 0.05f) return null;
        float x = (clip.x / clip.w * 0.5f + 0.5f) * mc.getWindow().getGuiScaledWidth();
        float y = (1.0f - (clip.y / clip.w * 0.5f + 0.5f)) * mc.getWindow().getGuiScaledHeight();
        if (x < -320 || x > mc.getWindow().getGuiScaledWidth() + 320 || y < -120 || y > mc.getWindow().getGuiScaledHeight() + 120) return null;
        return new float[]{x, y};
    }

    private static List<SpellVisual> spells(Player player) {
        List<SpellVisual> result = new ArrayList<>();
        boolean localPlayer = player == Minecraft.getInstance().player;
        Map<String, CooldownInstance> cooldowns = localPlayer
                ? ClientMagicData.getCooldowns().getSpellCooldowns()
                : MagicData.getPlayerMagicData(player).getPlayerCooldowns().getSpellCooldowns();
        Map<String, Integer> synchronizedCooldowns = SpellDuelClientState.cooldowns(player.getUUID());
        boolean hasSynchronizedCooldowns = SpellDuelClientState.hasCooldowns(player.getUUID());
        List<ItemStack> stacks = getSpellbookStacks(player);
        for (ItemStack stack : stacks) {
            if (!ISpellContainer.isSpellContainer(stack)) continue;
            for (SpellSlot slot : ISpellContainer.get(stack).getActiveSpells()) {
                if (slot.getSpell() != null) {
                    ResourceLocation spellId = slot.getSpell().getSpellResource();
                    CooldownInstance cooldown = cooldowns.get(spellId.toString());
                    int cooldownTicks = !localPlayer && hasSynchronizedCooldowns
                            ? synchronizedCooldowns.getOrDefault(spellId.toString(), 0)
                            : cooldown == null ? 0 : cooldown.getCooldownRemaining();
                    result.add(new SpellVisual(spellId, slot.getLevel(), cooldownTicks));
                }
            }
        }
        return result;
    }

    private static List<ItemStack> getSpellbookStacks(Player player) {
        List<ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.findCurios("spellbook").forEach(slot -> result.add(slot.stack())));
        return result;
    }

    private record SpellVisual(ResourceLocation id, int level, int cooldownTicks) {}
}
