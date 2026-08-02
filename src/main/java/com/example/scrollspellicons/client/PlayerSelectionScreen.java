package com.example.scrollspellicons.client;

import com.example.scrollspellicons.duel.SpellDuelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Centered online-player picker.  Esc deletes its current editing group. */
public final class PlayerSelectionScreen extends Screen {
    private static PlayerSelectionScreen current;
    private final List<SpellDuelNetwork.PlayerChoice> players;
    private int scroll;
    private static final int SINGLE_COLUMN_WIDTH = 260;
    private static final int DOUBLE_COLUMN_WIDTH = 430;
    private static final int ROW_HEIGHT = 32;

    private PlayerSelectionScreen(List<SpellDuelNetwork.PlayerChoice> players) {
        super(Component.literal("选择对战玩家"));
        LinkedHashMap<java.util.UUID, SpellDuelNetwork.PlayerChoice> unique = new LinkedHashMap<>();
        for (SpellDuelNetwork.PlayerChoice player : players) unique.put(player.id(), player);
        this.players = new ArrayList<>(unique.values());
    }

    public static void open(List<SpellDuelNetwork.PlayerChoice> players) {
        Minecraft.getInstance().setScreen(current = new PlayerSelectionScreen(players));
    }

    public static void closeIfOpen() {
        if (current != null) {
            current = null;
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    protected void init() {
        super.init();
        current = this;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF111111);
        graphics.fill(left + 2, top + 2, left + panelWidth - 2, top + 34, 0xFF303030);
        graphics.drawString(font, "选择在线玩家进行对战（检测到 " + players.size() + " 人）", left + 12, top + 12, 0xFFFFFF, false);
        graphics.drawString(font, "左键：A队  右键：B队  已选玩家左键删除  Esc：取消本次编辑", left + 12, top + 24, 0xBBBBBB, false);

        int listTop = top + 42;
        int listBottom = top + panelHeight - 45;
        graphics.fill(left + 8, listTop, left + panelWidth - 8, listBottom, 0xFF202020);
        int columns = columns();
        int rowWidth = (panelWidth - 28) / columns;
        for (int index = scroll; index < players.size(); index++) {
            int visible = index - scroll;
            int column = visible % columns;
            int row = visible / columns;
            int x = left + 12 + column * rowWidth;
            int y = listTop + 4 + row * ROW_HEIGHT;
            if (y + ROW_HEIGHT > listBottom) break;
            SpellDuelNetwork.PlayerChoice choice = players.get(index);
            boolean locked = !choice.selectedGroup().isEmpty() && choice.ownTeam() < 0;
            boolean hover = mouseX >= x && mouseX < x + rowWidth - 4 && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            graphics.fill(x, y, x + rowWidth - 6, y + ROW_HEIGHT - 3, hover ? 0xFF454545 : 0xFF2B2B2B);
            drawFace(graphics, choice.id(), x + 6, y + 6);
            graphics.drawString(font, choice.name(), x + 32, y + 5, locked ? 0xFFFF5555 : 0xFFFFFF, false);
            String state = choice.ownTeam() == 0 ? "已选 A 队" : choice.ownTeam() == 1 ? "已选 B 队"
                    : locked ? "已被 " + choice.selectedGroup() + " 选中" : "未选择";
            graphics.drawString(font, state, x + 32, y + 17, locked ? 0xFFFF7777 : 0xAAAAAA, false);
        }
        if (players.size() > scroll + visibleRows() * columns) {
            graphics.drawString(font, "滚轮查看更多玩家", left + panelWidth - 118, top + panelHeight - 58, 0xAAAAAA, false);
        }
        int buttonLeft = left + 12;
        int buttonTop = top + panelHeight - 35;
        boolean buttonHover = mouseX >= buttonLeft && mouseX < left + panelWidth - 12 && mouseY >= buttonTop && mouseY < buttonTop + 24;
        graphics.fill(buttonLeft, buttonTop, left + panelWidth - 12, buttonTop + 24, buttonHover ? 0xFF3FAF63 : 0xFF277A43);
        graphics.drawCenteredString(font, "创建对战", width / 2, buttonTop + 8, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int columns() { return players.size() <= 6 ? 1 : 2; }
    private int visibleRows() {
        int needed = Math.max(1, (players.size() + columns() - 1) / columns());
        int screenCapacity = Math.max(1, (height - 110) / ROW_HEIGHT);
        return Math.min(needed, screenCapacity);
    }
    private int panelWidth() { return columns() == 1 ? SINGLE_COLUMN_WIDTH : DOUBLE_COLUMN_WIDTH; }
    // The list starts four pixels below listTop. Keep matching padding at the
    // bottom, otherwise the final row is clipped while scrolling still treats
    // it as visible (for example, player 9 in a two-column list).
    private int panelHeight() { return 42 + 8 + visibleRows() * ROW_HEIGHT + 45; }

    private void drawFace(GuiGraphics graphics, java.util.UUID id, int x, int y) {
        PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null
                : Minecraft.getInstance().getConnection().getPlayerInfo(id);
        ResourceLocation skin = info == null ? new ResourceLocation("minecraft", "textures/entity/player/wide/alex.png") : info.getSkinLocation();
        graphics.blit(skin, x, y, 20, 20, 8, 8, 8, 8, 64, 64);
        graphics.blit(skin, x, y, 20, 20, 40, 8, 8, 8, 64, 64);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int buttonTop = top + panelHeight - 35;
        if (mouseX >= left + 12 && mouseX < left + panelWidth - 12 && mouseY >= buttonTop && mouseY < buttonTop + 24) {
            SpellDuelNetwork.sendToServer(new SpellDuelNetwork.CreateSelectionPayload());
            return true;
        }
        int listTop = top + 42;
        int listBottom = top + panelHeight - 45;
        int columns = columns();
        int rowWidth = (panelWidth - 28) / columns;
        if (button != 0 && button != 1) return super.mouseClicked(mouseX, mouseY, button);
        if (mouseY >= listTop && mouseY < listBottom) {
            int column = (int) ((mouseX - (left + 12)) / rowWidth);
            int row = (int) ((mouseY - listTop - 4) / ROW_HEIGHT);
            if (column >= 0 && column < columns && row >= 0) {
                int index = scroll + row * columns + column;
                if (index >= 0 && index < players.size()) {
                    // While a custom screen is open the server-side crouching flag can be
                    // one tick behind. Read the actual Shift key so sneak-left is reliable.
                    boolean crouching = Minecraft.getInstance().options.keyShift.isDown();
                    // Clicking a player in this editing group removes them. A
                    // player selected by a different non-active group is instead
                    // reassigned by the server in one click.
                    boolean selectedInThisGroup = players.get(index).ownTeam() >= 0;
                    SpellDuelNetwork.sendToServer(new SpellDuelNetwork.SelectPlayerPayload(
                            players.get(index).id(), (byte) (button == 0 && (crouching || selectedInThisGroup) ? 2 : button == 0 ? 0 : 1)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int columns = columns();
        int max = Math.max(0, players.size() - visibleRows() * columns);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * columns));
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (Minecraft.getInstance().getConnection() != null) {
            SpellDuelNetwork.sendToServer(new SpellDuelNetwork.CancelSelectionPayload());
        }
        current = null;
        super.onClose();
    }
}
