package com.example.scrollspellicons.client;

import com.example.scrollspellicons.duel.SpellDuelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/** Small client-only screens for the server-authoritative challenge flow. */
public final class DuelClientScreens {
    private static boolean closingFromServer;

    private DuelClientScreens() {}

    public static void openInteraction(long token, UUID target, String targetName) {
        Minecraft.getInstance().setScreen(new InteractionScreen(token, target, targetName));
    }

    public static void openInvite(long token, String challengerName, long expiresAt) {
        Minecraft.getInstance().setScreen(new InviteScreen(token, challengerName));
    }

    public static void openPoints(long token, List<String> points) {
        Minecraft.getInstance().setScreen(new PointScreen(token, points));
    }

    public static void openLearned(String targetName, boolean hasSpellbook, List<String> spellIds) {
        Minecraft.getInstance().setScreen(new LearnedScreen(targetName, hasSpellbook, spellIds));
    }

    public static void closeIfOpen() {
        if (Minecraft.getInstance().screen instanceof InteractionScreen
                || Minecraft.getInstance().screen instanceof InviteScreen
                || Minecraft.getInstance().screen instanceof PointScreen
                || Minecraft.getInstance().screen instanceof LearnedScreen) {
            closingFromServer = true;
            Minecraft.getInstance().setScreen(null);
            closingFromServer = false;
        }
    }

    private static void send(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) Minecraft.getInstance().getConnection().send(payload);
    }

    private static final class InteractionScreen extends Screen {
        private final long token;
        private final UUID target;
        private final String targetName;

        private InteractionScreen(long token, UUID target, String targetName) {
            super(Component.literal("玩家交互"));
            this.token = token;
            this.target = target;
            this.targetName = targetName;
        }

        @Override protected void init() {
            int left = width / 2 - 80;
            addRenderableWidget(Button.builder(Component.literal("单挑"), button -> {
                send(new SpellDuelNetwork.InteractionActionPayload(token, target, (byte) 0));
                Minecraft.getInstance().setScreen(null);
            }).bounds(left, height / 2 - 20, 160, 20).build());
            addRenderableWidget(Button.builder(Component.literal("查看法术"), button -> {
                send(new SpellDuelNetwork.InteractionActionPayload(token, target, (byte) 1));
                Minecraft.getInstance().setScreen(null);
            }).bounds(left, height / 2 + 6, 160, 20).build());
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, "玩家：" + targetName, width / 2, height / 2 - 52, 0xFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override public boolean isPauseScreen() { return false; }
    }

    private static final class InviteScreen extends Screen {
        private final long token;
        private final String challengerName;
        private boolean answered;

        private InviteScreen(long token, String challengerName) {
            super(Component.literal("单挑邀请"));
            this.token = token;
            this.challengerName = challengerName;
        }

        @Override protected void init() {
            int left = width / 2 - 80;
            addRenderableWidget(Button.builder(Component.literal("接受"), button -> answer(true))
                    .bounds(left, height / 2 - 10, 76, 20).build());
            addRenderableWidget(Button.builder(Component.literal("拒绝"), button -> answer(false))
                    .bounds(left + 84, height / 2 - 10, 76, 20).build());
        }

        private void answer(boolean accepted) {
            answered = true;
            send(new SpellDuelNetwork.ChallengeReplyPayload(token, accepted));
            Minecraft.getInstance().setScreen(null);
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, challengerName + " 邀请你进行单挑", width / 2, height / 2 - 40, 0xFFFFFF);
            graphics.drawCenteredString(font, "邀请有效期15秒", width / 2, height / 2 - 25, 0xAAAAAA);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override public void onClose() {
            if (!answered && !closingFromServer) {
                answered = true;
                send(new SpellDuelNetwork.ChallengeReplyPayload(token, false));
            }
            super.onClose();
        }

        @Override public boolean isPauseScreen() { return false; }
    }

    private static final class PointScreen extends Screen {
        private final long token;
        private final List<String> points;
        private boolean chosen;

        private PointScreen(long token, List<String> points) {
            super(Component.literal("选择单挑点位"));
            this.token = token;
            this.points = points;
        }

        @Override protected void init() {
            int left = width / 2 - 100;
            for (int i = 0; i < points.size() && i < 12; i++) {
                String group = points.get(i);
                addRenderableWidget(Button.builder(Component.literal(group), button -> {
                    chosen = true;
                    send(new SpellDuelNetwork.ChallengePointChoicePayload(token, group));
                    Minecraft.getInstance().setScreen(null);
                }).bounds(left, height / 2 - 80 + i * 22, 200, 20).build());
            }
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, "选择已配置的 duel_n A/B 点位", width / 2, height / 2 - 100, 0xFFFFFF);
            if (points.isEmpty()) graphics.drawCenteredString(font, "暂无空闲点位", width / 2, height / 2 - 70, 0xFF5555);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override public void onClose() {
            if (!chosen && !closingFromServer) send(new SpellDuelNetwork.ChallengeCancelPayload(token));
            super.onClose();
        }

        @Override public boolean isPauseScreen() { return false; }
    }

    private static final class LearnedScreen extends Screen {
        private static final int PAGE_SIZE = 18;
        private final String targetName;
        private final boolean hasSpellbook;
        private final List<String> spellIds;
        private int page;

        private LearnedScreen(String targetName, boolean hasSpellbook, List<String> spellIds) {
            super(Component.literal("已学法术"));
            this.targetName = targetName;
            this.hasSpellbook = hasSpellbook;
            this.spellIds = spellIds;
        }

        @Override protected void init() {
            addRenderableWidget(Button.builder(Component.literal("上一页"), button -> page = Math.max(0, page - 1))
                    .bounds(width / 2 - 130, height - 35, 80, 20).build());
            addRenderableWidget(Button.builder(Component.literal("下一页"), button -> page = Math.min(pageCount() - 1, page + 1))
                    .bounds(width / 2 + 50, height - 35, 80, 20).build());
            addRenderableWidget(Button.builder(Component.literal("关闭"), button -> Minecraft.getInstance().setScreen(null))
                    .bounds(width / 2 - 40, height - 35, 80, 20).build());
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, hasSpellbook
                    ? targetName + " 当前装备法术书中的法术"
                    : "该玩家未装备法术书", width / 2, 20, 0xFFFFFF);
            graphics.drawCenteredString(font, "第 " + (page + 1) + " / " + pageCount() + " 页", width / 2, 32, 0xAAAAAA);
            if (hasSpellbook && spellIds.isEmpty()) {
                graphics.drawCenteredString(font, "法术书内没有可用法术", width / 2, 55, 0xAAAAAA);
            }
            int y = 42;
            int start = page * PAGE_SIZE;
            int end = Math.min(spellIds.size(), start + PAGE_SIZE);
            for (int index = start; index < end; index++) {
                String id = spellIds.get(index);
                String key = "spell." + id.replace(':', '.');
                String name = Component.translatable(key).getString();
                if (name.equals(key)) name = id;
                graphics.drawString(font, name, 20, y, 0xFFFFFF, false);
                y += 14;
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private int pageCount() {
            return Math.max(1, (spellIds.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        }

        @Override public boolean isPauseScreen() { return false; }
    }
}
