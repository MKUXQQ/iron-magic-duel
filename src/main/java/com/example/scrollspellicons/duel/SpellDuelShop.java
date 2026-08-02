package com.example.scrollspellicons.duel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** 五页、每页54格、免费且自动补充的商店。 */
@EventBusSubscriber(modid = "iron_magic_duel")
public final class SpellDuelShop {
    private static final int PAGES = 5;
    private static final int PAGE_SIZE = 54;
    private static final Map<net.minecraft.server.MinecraftServer, ShopData> DATA = new WeakHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, Map<UUID, Integer>> PAGES_BY_PLAYER = new WeakHashMap<>();

    private SpellDuelShop() {}

    public static ItemStack shopStack() {
        ItemStack stack = new ItemStack(Items.BARREL);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("IronMagicShop", true);
        stack.setTag(tag);
        stack.setHoverName(Component.literal("无限商店"));
        return stack;
    }

    public static boolean isShop(ItemStack stack) {
        return stack.is(Items.BARREL)
                && stack.hasTag() && stack.getTag().getBoolean("IronMagicShop");
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) return;
        ItemStack held = event.getItemStack();
        boolean editor = held.is(SpellDuelItems.SHOP_EDITOR.get());
        if (!isShop(held) && !editor) return;
        int page = page(player);
        if (player.isCrouching()) {
            page = (page + 1) % PAGES;
            player.displayClientMessage(Component.literal("已切换到第 " + (page + 1) + "/" + PAGES + " 页"), true);
        }
        setPage(player, page);
        open(player, editor, page);
        event.setCanceled(true);
    }

    private static void open(ServerPlayer player, boolean editor, int page) {
        ShopData data = DATA.computeIfAbsent(player.getServer(), ShopData::new);
        data.reload();
        SimpleContainer backing = editor ? new EditorContainer(data, page) : new FreeContainer(data, page);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, ignored) ->
                        editor ? new EditorMenu(id, inv, (EditorContainer) backing)
                                : ChestMenu.sixRows(id, inv, backing),
                Component.literal(editor ? "商店编辑器 第" + (page + 1) + "页" : "无限商店 第" + (page + 1) + "页")));
    }

    private static int page(ServerPlayer player) {
        return PAGES_BY_PLAYER.computeIfAbsent(player.getServer(), ignored -> new java.util.HashMap<>())
                .getOrDefault(player.getUUID(), 0);
    }

    private static void setPage(ServerPlayer player, int page) {
        PAGES_BY_PLAYER.computeIfAbsent(player.getServer(), ignored -> new java.util.HashMap<>())
                .put(player.getUUID(), page);
    }

    private static final class ShopData {
        final ItemStack[][] items = new ItemStack[PAGES][PAGE_SIZE];
        final net.minecraft.server.MinecraftServer server;

        ShopData(net.minecraft.server.MinecraftServer server) {
            this.server = server;
            reload();
        }

        void reload() {
            for (int p = 0; p < PAGES; p++) for (int i = 0; i < PAGE_SIZE; i++) items[p][i] = ItemStack.EMPTY;
            load();
            java.nio.file.Path json = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/iron_magic_shop.json");
            if (!java.nio.file.Files.exists(json)) save();
        }

        void save() {
            try {
                java.nio.file.Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("data/iron_magic_shop.json");
                com.google.gson.JsonObject root = new com.google.gson.JsonObject();
                com.google.gson.JsonArray pages = new com.google.gson.JsonArray();
                for (int p = 0; p < PAGES; p++) {
                    com.google.gson.JsonObject page = new com.google.gson.JsonObject();
                    for (int i = 0; i < PAGE_SIZE; i++) {
                        ItemStack stack = items[p][i];
                        if (!stack.isEmpty()) page.addProperty(Integer.toString(i), stack.save(new CompoundTag()).toString());
                    }
                    pages.add(page);
                }
                root.add("pages", pages);
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                java.nio.file.Files.writeString(temporary, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root),
                        java.nio.charset.StandardCharsets.UTF_8);
                try { java.nio.file.Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    java.nio.file.Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) { }
        }

        void load() {
            java.nio.file.Path json = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/iron_magic_shop.json");
            try {
                if (java.nio.file.Files.exists(json)) {
                    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                            java.nio.file.Files.readString(json, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    com.google.gson.JsonArray pages = root.getAsJsonArray("pages");
                    for (int p = 0; p < PAGES && p < pages.size(); p++) {
                        com.google.gson.JsonObject page = pages.get(p).getAsJsonObject();
                        for (int i = 0; i < PAGE_SIZE; i++) {
                            String key = Integer.toString(i);
                            if (!page.has(key)) continue;
                            try {
                                items[p][i] = ItemStack.of(net.minecraft.nbt.TagParser.parseTag(page.get(key).getAsString()));
                            } catch (Exception ignoredSlot) {
                                // A damaged or unavailable item must not erase the other slots.
                                items[p][i] = ItemStack.EMPTY;
                            }
                        }
                    }
                    return;
                }
                // Migrate the previous NBT file once, so existing shop contents are not lost.
                java.nio.file.Path legacy = json.resolveSibling("iron_magic_shop.nbt");
                if (java.nio.file.Files.exists(legacy)) {
                    CompoundTag root;
                    try (var input = java.nio.file.Files.newInputStream(legacy)) {
                        root = net.minecraft.nbt.NbtIo.readCompressed(input);
                    }
                    for (int p = 0; p < PAGES; p++) for (int i = 0; i < PAGE_SIZE; i++) {
                        String key = "p" + p + "_" + i;
                        if (root.contains(key)) items[p][i] = ItemStack.of(root.getCompound(key));
                    }
                    save();
                }
            } catch (Exception ignored) { }
        }
    }

    private static final class FreeContainer extends SimpleContainer {
        FreeContainer(ShopData data, int page) {
            super(PAGE_SIZE);
            for (int i = 0; i < PAGE_SIZE; i++) super.setItem(i, data.items[page][i].copy());
            data.save();
        }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
        @Override public ItemStack removeItem(int slot, int amount) {
            return getItem(slot).copyWithCount(Math.min(amount, getItem(slot).getCount()));
        }
        @Override public ItemStack removeItemNoUpdate(int slot) { return getItem(slot).copy(); }
    }

    private static final class EditorContainer extends SimpleContainer {
        private final ShopData data;
        private final int page;
        private boolean initializing = true;

        EditorContainer(ShopData data, int page) {
            super(PAGE_SIZE);
            this.data = data;
            this.page = page;
            for (int i = 0; i < PAGE_SIZE; i++) super.setItem(i, data.items[page][i].copy());
            initializing = false;
        }

        @Override public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            if (!initializing && slot >= 0 && slot < PAGE_SIZE) {
                data.items[page][slot] = stack.copy();
                data.save();
            }
        }

        @Override public void setChanged() {
            super.setChanged();
            if (initializing) return;
            for (int i = 0; i < PAGE_SIZE; i++) data.items[page][i] = getItem(i).copy();
            data.save();
        }

        // AbstractContainerMenu uses removeItemNoUpdate while closing. Do not treat
        // that lifecycle cleanup as an edit, otherwise reopening loses the shop.
        @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }

        /** ChestMenu calls clearContent when it closes; editor contents are persistent data, not drops. */
        @Override public void clearContent() { }

        void saveSnapshot() {
            for (int i = 0; i < PAGE_SIZE; i++) data.items[page][i] = getItem(i).copy();
            data.save();
        }
    }

    /** Editor menu that never invokes vanilla's close-time container clearing. */
    private static final class EditorMenu extends ChestMenu {
        private final EditorContainer editorContainer;

        EditorMenu(int id, net.minecraft.world.entity.player.Inventory inventory, EditorContainer container) {
            super(net.minecraft.world.inventory.MenuType.GENERIC_9x6, id, inventory, container, 6);
            this.editorContainer = container;
        }

        @Override public void removed(net.minecraft.world.entity.player.Player player) {
            editorContainer.saveSnapshot();
        }
    }
}
