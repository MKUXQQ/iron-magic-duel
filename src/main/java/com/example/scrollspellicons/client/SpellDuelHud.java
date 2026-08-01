package com.example.scrollspellicons.client;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import com.daqem.uilib.client.util.GuiGraphicsUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
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
    // Keep UILib's nine-slice renderer, but use the vanilla widget atlas' button
    // frame. UILib gui.png (0,0) is the scrollbar texture, which caused repeated
    // vertical bars to appear around every spell and health panel.
    private static final ResourceLocation VANILLA_WIDGETS_FRAME =
            ResourceLocation.withDefaultNamespace("textures/gui/widgets.png");

    private SpellDuelHud() {}

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.player.isSpectator()) {
            renderSpectatorSnapshot(event.getGuiGraphics(), mc);
            return;
        }
        renderLocalSpellHud(event.getGuiGraphics(), mc);
        if (!SpellDuelClientState.displayEnabled()) return;
        for (Player player : mc.level.players()) if (player.isAlive()) renderPlayerSpellBar(event.getGuiGraphics(), mc, player);
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
        graphics.fill(x - 3, y - 3, x + panelWidth + 3, y + panelHeight + 3, 0xB0000000);
        drawSpellBox(graphics, mc, spells, contentX, y, hudWidth, spellHeight);
        drawHealthBox(graphics, mc, contentX, healthY, hudWidth, player.getHealth(), player.getMaxHealth());
        graphics.drawString(mc.font, name, x + (panelWidth - mc.font.width(name)) / 2,
                healthY + 22, 0xFFFFFF, true);
    }

    private static void renderLocalSpellHud(GuiGraphics graphics, Minecraft mc) {
        List<SpellVisual> spells = spells(mc.player);
        int hudWidth = hudWidth(spells.size());
        int spellHeight = spellRows(spells.size()) * 18 + 6;
        int panelWidth = PLAYER_FACE_SIZE + 8 + hudWidth;
        int panelHeight = 16 + spellHeight + 4 + 20 + 3 + 20 + 4;
        int x = hudX(graphics, panelWidth);
        int y = hudY(graphics, panelHeight);
        int contentX = x + PLAYER_FACE_SIZE + 6;
        int spellY = y + 16;
        int healthY = spellY + spellHeight + 4;
        int manaY = healthY + 23;
        drawLocalPanelFrame(graphics, x, y, panelWidth, panelHeight);
        renderLocalPlayerFace(graphics, mc, x + 4, y + 4);
        graphics.drawString(mc.font, mc.player.getName().getString(), contentX, y + 3, 0xFFFFFF, true);
        drawSpellBox(graphics, mc, spells, contentX, spellY, hudWidth, spellHeight);
        drawHealthBox(graphics, mc, contentX, healthY, hudWidth, mc.player.getHealth(), mc.player.getMaxHealth());
        drawManaBox(graphics, mc, contentX, manaY, hudWidth, ClientMagicData.getPlayerMana(),
                (float) mc.player.getAttributeValue(AttributeRegistry.MAX_MANA));
    }

    private static int hudX(GuiGraphics graphics, int panelWidth) {
        return Math.max(0, Math.min(SpellDuelClientState.hudX(), graphics.guiWidth() - panelWidth));
    }

    private static int hudY(GuiGraphics graphics, int panelHeight) {
        return Math.max(0, Math.min(SpellDuelClientState.hudY(), graphics.guiHeight() - panelHeight));
    }

    private static void drawLocalPanelFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF080808);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xC0181818);
    }

    private static void renderLocalPlayerFace(GuiGraphics graphics, Minecraft mc, int x, int y) {
        drawFrame(graphics, x, y, PLAYER_FACE_SIZE, PLAYER_FACE_SIZE);
        if (mc.player instanceof AbstractClientPlayer clientPlayer) {
            PlayerFaceRenderer.draw(graphics, clientPlayer.getSkin(), x + 4, y + 4, 16);
        }
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
            PlayerFaceRenderer.draw(graphics, clientPlayer.getSkin(), x, y, 24);
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
        // Draw the health frame with solid rectangles. The textured nine-slice
        // frame can be batched after fills by some render types and cover the bar.
        graphics.fill(x, y, x + width, y + 20, 0, 0xFF080808);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 18, 0, 0xFF666666);
        float ratio = maxHealth > 0 && Float.isFinite(health) && Float.isFinite(maxHealth)
                ? Math.max(0, Math.min(1, health / maxHealth)) : 0;
        int innerX = x + 4;
        int innerY = y + 5;
        int barWidth = width - 8;
        graphics.fill(innerX, innerY, innerX + barWidth, innerY + 10, 0, 0xFF202020);
        int filledWidth = Math.round((barWidth - 2) * ratio);
        if (filledWidth > 0) {
            graphics.fill(innerX + 1, innerY + 1, innerX + 1 + filledWidth, innerY + 9, 0, GREEN);
        }
    }

    private static void drawManaBox(GuiGraphics graphics, Minecraft mc, int x, int y, int width,
                                    float mana, float maxMana) {
        float ratio = maxMana > 0 && Float.isFinite(mana) && Float.isFinite(maxMana)
                ? Math.max(0, Math.min(1, mana / maxMana)) : 0;
        // Unified mana bar: black outer plate, gray outline, black track, blue fill.
        graphics.fill(x, y, x + width, y + 20, 0xFF000000);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 18, 0xFF707070);
        graphics.fill(x + 5, y + 5, x + width - 5, y + 15, 0xFF171717);
        int filledWidth = Math.round((width - 10) * ratio);
        if (filledWidth > 0) {
            graphics.fill(x + 5, y + 5, x + 5 + filledWidth, y + 15, 0xFF2A91E5);
        }
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        GuiGraphicsUtils.blitNineSliced(graphics, VANILLA_WIDGETS_FRAME, x, y, width, height,
                2, 2, 2, 2, 200, 20, 0, 66);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xFF111111);
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
        if (icon == null) return;
        ResourceLocation spriteId = ResourceLocation.fromNamespaceAndPath(icon.getNamespace(),
                icon.getPath().substring("textures/".length(), icon.getPath().length() - ".png".length()));
        TextureAtlasSprite sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(spriteId);
        graphics.blit(x, y, 0, 16, 16, sprite);
        drawCooldownNumber(graphics, mc, x, y, visual.cooldownTicks());
    }

    private static void drawCooldownNumber(GuiGraphics graphics, Minecraft mc, int x, int y, int cooldownTicks) {
        int seconds = cooldownSeconds(cooldownTicks);
        if (seconds <= 0) return;
        String value = Integer.toString(seconds);
        int textX = x + 15 - mc.font.width(value);
        graphics.fill(textX - 1, y + 8, x + 16, y + 16, 0xB0000000);
        graphics.drawString(mc.font, value, textX, y + 8, 0xFFFFFF, true);
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
