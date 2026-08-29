package com.example.scrollspellicons.duel;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.registries.ItemRegistry;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** 五页、每页54格、免费且自动补充的商店。 */
@EventBusSubscriber(modid = "iron_magic_duel")
public final class SpellDuelShop {
    private static final int PAGES = 5;
    private static final int PAGE_SIZE = 54;
    private static final int PREVIOUS_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = PAGE_SIZE - 1;
    /** Hand-editable shop/AI balance document in the world serverconfig folder. */
    private static final String AI_FILE_NAME = "iron_magic_shop_ai.toml";
    private static final String BALANCE_FILE_NAME = "portable_inscription_table_spell_balance.json";
    private static final String AI_FORMAT = "iron_magic_duel_shop_ai_v1";
    /** Duel spells remain registered and obtainable by commands, but are not shop products. */
    private static final Set<String> WITHDRAWN_SHOP_SPELLS = Set.of(
            "iron_magic_duel:astral_predator",
            "iron_magic_duel:phantom_halberd_ring",
            "iron_magic_duel:crosswind_iron_slash");
    private static final Map<net.minecraft.server.MinecraftServer, ShopData> DATA = new WeakHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, Map<UUID, Integer>> PAGES_BY_PLAYER = new WeakHashMap<>();

    private SpellDuelShop() {}

    public static ItemStack shopStack() {
        ItemStack stack = new ItemStack(Items.SNIFFER_EGG);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("IronMagicShop", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("无限商店"));
        return stack;
    }

    public static boolean isShop(ItemStack stack) {
        return (stack.is(Items.SNIFFER_EGG) || stack.is(Items.BARREL))
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains("IronMagicShop");
    }

    /**
     * Creates an editable text snapshot on first use. Subsequent uses read the
     * replacement text, restore its spell-item NBT, and update the same balance
     * JSON consumed by the separate chant-time/balance mod.
     */
    public static AiSyncResult syncAi(net.minecraft.server.MinecraftServer server) {
        ShopData data = DATA.computeIfAbsent(server, ShopData::new);
        data.reload();
        Path file = aiFile(server);
        if (!Files.exists(file)) {
            return exportAi(server, data, file);
        }
        try {
            com.google.gson.JsonObject root = readAiToml(file);
            if (!AI_FORMAT.equals(root.has("format") ? root.get("format").getAsString() : "")) {
                return new AiSyncResult(false, 0, 0, file, "文本格式不是 Iron Magic Duel 商店平衡文件，未作修改");
            }
            int itemCount = importSpellItems(server, data, root);
            data.migrateReservedNavigationSlots();
            data.save();
            BalanceApplyResult balanceResult = applyBalanceValues(root);
            return new AiSyncResult(true, itemCount, balanceResult.count(), file,
                    "已读取文本：商店法术物品 " + itemCount + " 个，平衡数值 " + balanceResult.count() + " 条"
                            + (balanceResult.liveApplied() ? "，修改已立即生效" : "，已写入配置，重启服务端后生效"));
        } catch (Exception exception) {
            return new AiSyncResult(false, 0, 0, file, "读取文本失败：" + exception.getClass().getSimpleName());
        }
    }

    /** Explicitly overwrites the snapshot with the current shop NBT and current balance values. */
    public static AiSyncResult exportAi(net.minecraft.server.MinecraftServer server) {
        ShopData data = DATA.computeIfAbsent(server, ShopData::new);
        data.reload();
        return exportAi(server, data, aiFile(server));
    }

    private static AiSyncResult exportAi(net.minecraft.server.MinecraftServer server, ShopData data, Path file) {
        try {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("format", AI_FORMAT);
            root.addProperty("generatedAt", Instant.now().toString());
            root.addProperty("balanceConfig", BALANCE_FILE_NAME);
            root.addProperty("instructions", "编辑 spells 内的数值和 shopSpellItems 内的 nbt；替换本文件后执行 /spell_duel shop ai 应用。");
            com.google.gson.JsonObject configuredBalances = readBalanceConfig();
            com.google.gson.JsonObject spells = new com.google.gson.JsonObject();
            com.google.gson.JsonArray items = new com.google.gson.JsonArray();
            Map<String, Set<Integer>> levelsBySpell = new LinkedHashMap<>();
            int itemCount = 0;
            for (int page = 0; page < PAGES; page++) {
                for (int slot = 0; slot < PAGE_SIZE; slot++) {
                    if (!isItemSlot(slot)) continue;
                    ItemStack stack = data.items[page][slot];
                    List<SpellSlot> spellSlots = spellSlots(stack);
                    if (spellSlots.isEmpty()) continue;
                    com.google.gson.JsonObject entry = new com.google.gson.JsonObject();
                    entry.addProperty("page", page + 1);
                    entry.addProperty("slot", slot);
                    entry.addProperty("nbt", stack.save(server.registryAccess()).toString());
                    com.google.gson.JsonArray entrySpells = new com.google.gson.JsonArray();
                    for (SpellSlot spellSlot : spellSlots) {
                        AbstractSpell spell = spellSlot.getSpell();
                        if (spell == null || spell == SpellRegistry.none()) continue;
                        String id = spell.getSpellResource().toString();
                        int level = spellSlot.getLevel();
                        com.google.gson.JsonObject listedSpell = new com.google.gson.JsonObject();
                        listedSpell.addProperty("id", id);
                        listedSpell.addProperty("level", level);
                        entrySpells.add(listedSpell);
                        levelsBySpell.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(level);
                    }
                    if (!entrySpells.isEmpty()) {
                        entry.add("spells", entrySpells);
                        items.add(entry);
                        itemCount++;
                    }
                }
            }
            for (Map.Entry<String, Set<Integer>> entry : levelsBySpell.entrySet()) {
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(entry.getKey());
                if (id == null) continue;
                AbstractSpell spell = SpellRegistry.getSpell(id);
                if (spell == SpellRegistry.none()) continue;
                int level = entry.getValue().stream().mapToInt(Integer::intValue).max().orElse(1);
                com.google.gson.JsonObject balance = configuredBalances.has(entry.getKey())
                        ? configuredBalances.getAsJsonObject(entry.getKey()).deepCopy()
                        : defaultBalance(spell, level);
                balance.addProperty("displayName", spell.getSpellName());
                balance.addProperty("castType", spell.getCastType().name());
                com.google.gson.JsonArray levels = new com.google.gson.JsonArray();
                entry.getValue().stream().sorted().forEach(levels::add);
                balance.add("shopLevels", levels);
                spells.add(entry.getKey(), balance);
            }
            root.add("shopSpellItems", items);
            root.add("spells", spells);
            writeAtomically(file, toAiToml(root));
            return new AiSyncResult(true, itemCount, spells.size(), file,
                    "已导出商店法术 NBT 与平衡数值；编辑或替换该 TXT 后再次执行 /spell_duel shop ai");
        } catch (Exception exception) {
            return new AiSyncResult(false, 0, 0, file, "导出文本失败：" + exception.getClass().getSimpleName());
        }
    }

    private static int importSpellItems(net.minecraft.server.MinecraftServer server, ShopData data, com.google.gson.JsonObject root) {
        if (!root.has("shopSpellItems") || !root.get("shopSpellItems").isJsonArray()) return 0;
        int imported = 0;
        for (com.google.gson.JsonElement element : root.getAsJsonArray("shopSpellItems")) {
            try {
                com.google.gson.JsonObject entry = element.getAsJsonObject();
                int page = entry.get("page").getAsInt() - 1;
                int slot = entry.get("slot").getAsInt();
                if (page < 0 || page >= PAGES || !isItemSlot(slot) || !entry.has("nbt")) continue;
                ItemStack parsed = ItemStack.parse(server.registryAccess(), net.minecraft.nbt.TagParser.parseTag(entry.get("nbt").getAsString())).orElse(ItemStack.EMPTY);
                if (parsed.isEmpty() || spellSlots(parsed).isEmpty() || containsWithdrawnSpell(parsed)) continue;
                data.items[page][slot] = parsed;
                imported++;
            } catch (Exception ignored) {
                // One hand-edited broken NBT entry must not discard other shop slots.
            }
        }
        return imported;
    }

    private static List<SpellSlot> spellSlots(ItemStack stack) {
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) return List.of();
        List<SpellSlot> result = new ArrayList<>();
        for (SpellSlot slot : ISpellContainer.get(stack).getActiveSpells()) {
            if (slot.getSpell() != null && slot.getSpell() != SpellRegistry.none()) result.add(slot);
        }
        return result;
    }

    private static boolean containsWithdrawnSpell(ItemStack stack) {
        for (SpellSlot spellSlot : spellSlots(stack)) {
            if (WITHDRAWN_SHOP_SPELLS.contains(spellSlot.getSpell().getSpellResource().toString())) return true;
        }
        return false;
    }

    private static com.google.gson.JsonObject defaultBalance(AbstractSpell spell, int level) {
        com.google.gson.JsonObject balance = new com.google.gson.JsonObject();
        balance.addProperty("castTimeTicks", spell.getCastTime(Math.max(1, level)));
        balance.addProperty("cooldownSeconds", spell.getSpellCooldown() / 20.0D);
        balance.addProperty("manaCostMultiplier", 1.0D);
        balance.addProperty("powerMultiplier", 1.0D);
        balance.addProperty("survivalAllowed", true);
        balance.addProperty("projectileSpeed", 1.0D);
        return balance;
    }

    private static com.google.gson.JsonObject readBalanceConfig() {
        Path file = balanceFile();
        try {
            if (Files.exists(file)) {
                return com.google.gson.JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) {
        }
        return new com.google.gson.JsonObject();
    }

    private static BalanceApplyResult applyBalanceValues(com.google.gson.JsonObject root) {
        if (!root.has("spells") || !root.get("spells").isJsonObject()) return new BalanceApplyResult(0, false);
        com.google.gson.JsonObject config = readBalanceConfig();
        int updated = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("spells").entrySet()) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(entry.getKey());
            if (id == null || !entry.getValue().isJsonObject() || SpellRegistry.getSpell(id) == SpellRegistry.none()) continue;
            com.google.gson.JsonObject values = entry.getValue().getAsJsonObject();
            com.google.gson.JsonObject normalized = new com.google.gson.JsonObject();
            normalized.addProperty("castTimeTicks", nonNegativeInt(values, "castTimeTicks", 0));
            normalized.addProperty("cooldownSeconds", nonNegativeDouble(values, "cooldownSeconds", 0.0D));
            normalized.addProperty("manaCostMultiplier", nonNegativeDouble(values, "manaCostMultiplier", 1.0D));
            normalized.addProperty("powerMultiplier", nonNegativeDouble(values, "powerMultiplier", 1.0D));
            normalized.addProperty("survivalAllowed", booleanValue(values, "survivalAllowed", true));
            normalized.addProperty("projectileSpeed", nonNegativeDouble(values, "projectileSpeed", 1.0D));
            config.add(entry.getKey(), normalized);
            updated++;
        }
        boolean liveApplied = false;
        if (updated > 0) {
            try {
                writeAtomically(balanceFile(), new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(config));
                liveApplied = tryApplyPortableInscriptionBalance();
            } catch (Exception ignored) {
            }
        }
        return new BalanceApplyResult(updated, liveApplied);
    }

    private static int nonNegativeInt(com.google.gson.JsonObject object, String key, int fallback) {
        try { return Math.max(0, object.has(key) ? object.get(key).getAsInt() : fallback); }
        catch (Exception ignored) { return fallback; }
    }

    private static double nonNegativeDouble(com.google.gson.JsonObject object, String key, double fallback) {
        try {
            double value = object.has(key) ? object.get(key).getAsDouble() : fallback;
            return Double.isFinite(value) ? Math.max(0.0D, value) : fallback;
        } catch (Exception ignored) { return fallback; }
    }

    private static boolean booleanValue(com.google.gson.JsonObject object, String key, boolean fallback) {
        try { return object.has(key) ? object.get(key).getAsBoolean() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    /** Works when the chant-time mod is installed; otherwise its normal next-startup load applies the JSON. */
    private static boolean tryApplyPortableInscriptionBalance() {
        try {
            Class<?> store = Class.forName("com.example.portableinscriptiontable.balance.SpellBalanceStore");
            Method loadAndApply = store.getMethod("loadAndApply");
            loadAndApply.invoke(null);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static Path aiFile(net.minecraft.server.MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("serverconfig")
                .resolve(AI_FILE_NAME);
    }

    /** Writes a real TOML document so it can be safely edited outside Minecraft. */
    private static String toAiToml(com.google.gson.JsonObject root) {
        StringBuilder output = new StringBuilder("# Iron Magic Duel shop spell NBT and balance configuration\n");
        for (String key : List.of("format", "generatedAt", "balanceConfig", "instructions")) {
            if (root.has(key)) output.append(key).append(" = ").append(tomlString(root.get(key).getAsString())).append('\n');
        }
        for (com.google.gson.JsonElement element : root.getAsJsonArray("shopSpellItems")) {
            com.google.gson.JsonObject item = element.getAsJsonObject();
            output.append("\n[[shopSpellItems]]\n");
            output.append("page = ").append(item.get("page").getAsInt()).append('\n');
            output.append("slot = ").append(item.get("slot").getAsInt()).append('\n');
            output.append("nbt = ").append(tomlString(item.get("nbt").getAsString())).append('\n');
            output.append("spells = [");
            boolean first = true;
            for (com.google.gson.JsonElement spell : item.getAsJsonArray("spells")) {
                if (!first) output.append(", ");
                com.google.gson.JsonObject value = spell.getAsJsonObject();
                output.append("{ id = ").append(tomlString(value.get("id").getAsString()))
                        .append(", level = ").append(value.get("level").getAsInt()).append(" }");
                first = false;
            }
            output.append("]\n");
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("spells").entrySet()) {
            com.google.gson.JsonObject spell = entry.getValue().getAsJsonObject();
            output.append("\n[spells.").append(tomlString(entry.getKey())).append("]\n");
            for (Map.Entry<String, com.google.gson.JsonElement> value : spell.entrySet()) {
                output.append(value.getKey()).append(" = ");
                if (value.getValue().isJsonArray()) {
                    output.append('[');
                    boolean first = true;
                    for (com.google.gson.JsonElement level : value.getValue().getAsJsonArray()) {
                        if (!first) output.append(", ");
                        output.append(level.getAsInt());
                        first = false;
                    }
                    output.append(']');
                } else if (value.getValue().getAsJsonPrimitive().isString()) {
                    output.append(tomlString(value.getValue().getAsString()));
                } else {
                    output.append(value.getValue());
                }
                output.append('\n');
            }
        }
        return output.toString();
    }

    private static com.google.gson.JsonObject readAiToml(Path file) throws java.io.IOException {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        com.google.gson.JsonArray items = new com.google.gson.JsonArray();
        com.google.gson.JsonObject spells = new com.google.gson.JsonObject();
        root.add("shopSpellItems", items);
        root.add("spells", spells);
        com.google.gson.JsonObject currentItem = null;
        com.google.gson.JsonObject currentSpell = null;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.equals("[[shopSpellItems]]")) {
                currentItem = new com.google.gson.JsonObject();
                items.add(currentItem);
                currentSpell = null;
                continue;
            }
            if (line.startsWith("[spells.") && line.endsWith("]")) {
                String id = tomlUnquote(line.substring("[spells.".length(), line.length() - 1));
                currentSpell = new com.google.gson.JsonObject();
                spells.add(id, currentSpell);
                currentItem = null;
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 1) continue;
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (currentItem != null) {
                if ("page".equals(key) || "slot".equals(key)) currentItem.addProperty(key, Integer.parseInt(value));
                else if ("nbt".equals(key)) currentItem.addProperty(key, tomlUnquote(value));
                else if ("spells".equals(key)) currentItem.add("spells", parseInlineSpells(value));
            } else if (currentSpell != null) {
                if ("shopLevels".equals(key)) currentSpell.add(key, parseIntArray(value));
                else if ("castTimeTicks".equals(key)) currentSpell.addProperty(key, Integer.parseInt(value));
                else if ("survivalAllowed".equals(key)) currentSpell.addProperty(key, Boolean.parseBoolean(value));
                else if ("displayName".equals(key) || "castType".equals(key)) currentSpell.addProperty(key, tomlUnquote(value));
                else currentSpell.addProperty(key, Double.parseDouble(value));
            } else if ("format".equals(key) || "generatedAt".equals(key) || "balanceConfig".equals(key) || "instructions".equals(key)) {
                root.addProperty(key, tomlUnquote(value));
            }
        }
        return root;
    }

    private static com.google.gson.JsonArray parseInlineSpells(String value) {
        com.google.gson.JsonArray result = new com.google.gson.JsonArray();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\s*id\\s*=\\s*\"([^\"]*)\"\\s*,\\s*level\\s*=\\s*(\\d+)\\s*\\}").matcher(value);
        while (matcher.find()) {
            com.google.gson.JsonObject spell = new com.google.gson.JsonObject();
            spell.addProperty("id", matcher.group(1));
            spell.addProperty("level", Integer.parseInt(matcher.group(2)));
            result.add(spell);
        }
        return result;
    }

    private static com.google.gson.JsonArray parseIntArray(String value) {
        com.google.gson.JsonArray result = new com.google.gson.JsonArray();
        String inner = value.trim().replaceAll("^\\[|\\]$", "");
        if (!inner.isBlank()) for (String part : inner.split(",")) result.add(Integer.parseInt(part.trim()));
        return result;
    }

    private static String tomlString(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    private static String tomlUnquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) trimmed = trimmed.substring(1, trimmed.length() - 1);
        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (escaping) {
                result.append(character == 'n' ? '\n' : character);
                escaping = false;
            } else if (character == '\\') {
                escaping = true;
            } else result.append(character);
        }
        if (escaping) result.append('\\');
        return result.toString();
    }

    private static Path balanceFile() {
        return FMLPaths.CONFIGDIR.get().resolve(BALANCE_FILE_NAME);
    }

    private static void writeAtomically(Path file, String contents) throws java.io.IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    public record AiSyncResult(boolean success, int itemCount, int spellCount, Path file, String message) {}

    private record BalanceApplyResult(int count, boolean liveApplied) {}

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) return;
        ItemStack held = event.getItemStack();
        boolean editor = held.is(SpellDuelItems.SHOP_EDITOR.get());
        if (!isShop(held) && !editor) return;
        ShopData data = DATA.computeIfAbsent(player.getServer(), ShopData::new);
        data.reload();
        int page = editor ? Math.floorMod(page(player), PAGES) : Math.floorMod(page(player), data.usedPageCount());
        if (player.isCrouching()) {
            int pageCount = editor ? PAGES : data.usedPageCount();
            page = (page + 1) % pageCount;
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
                        editor ? new EditorMenu(id, inv, (EditorContainer) backing, page)
                                : new ShopMenu(id, inv, (FreeContainer) backing, page),
                Component.literal(editor ? "商店编辑器 第 " + (page + 1) + "/" + PAGES + " 页"
                        : "无限商店 第 " + (page + 1) + "/" + data.usedPageCount() + " 页")));
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
            migrateReservedNavigationSlots();
            removeWithdrawnSpells();
            java.nio.file.Path json = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/iron_magic_shop.json");
            if (!java.nio.file.Files.exists(json)) save();
        }

        int usedPageCount() {
            for (int page = PAGES - 1; page >= 0; page--) {
                for (int slot = 0; slot < PAGE_SIZE; slot++) {
                    if (!isItemSlot(slot)) continue;
                    if (!items[page][slot].isEmpty()) return page + 1;
                }
            }
            return 1;
        }

        /** Existing shops could use either navigation slot; migrate it before reserving those slots for navigation. */
        private void migrateReservedNavigationSlots() {
            boolean changed = false;
            for (int page = 0; page < PAGES; page++) {
                for (int reservedSlot : new int[]{PREVIOUS_PAGE_SLOT, NEXT_PAGE_SLOT}) {
                    ItemStack reserved = items[page][reservedSlot];
                    if (reserved.isEmpty()) continue;
                    int destination = firstFreeItemSlot();
                    if (destination < 0) continue;
                    items[destination / PAGE_SIZE][destination % PAGE_SIZE] = reserved;
                    items[page][reservedSlot] = ItemStack.EMPTY;
                    changed = true;
                }
            }
            if (changed) save();
        }

        private int firstFreeItemSlot() {
            for (int page = 0; page < PAGES; page++) {
                for (int slot = 0; slot < PAGE_SIZE; slot++) {
                    if (isItemSlot(slot) && items[page][slot].isEmpty()) return page * PAGE_SIZE + slot;
                }
            }
            return -1;
        }

        private void removeWithdrawnSpells() {
            boolean changed = false;
            for (int page = 0; page < PAGES; page++) {
                for (int slot = 0; slot < PAGE_SIZE; slot++) {
                    if (!isItemSlot(slot) || !containsWithdrawnSpell(items[page][slot])) continue;
                    items[page][slot] = ItemStack.EMPTY;
                    changed = true;
                }
            }
            if (changed) save();
        }

        private boolean containsWithdrawnSpell(ItemStack stack) {
            for (SpellSlot spellSlot : spellSlots(stack)) {
                if (WITHDRAWN_SHOP_SPELLS.contains(spellSlot.getSpell().getSpellResource().toString())) return true;
            }
            return false;
        }

        private ItemStack spellScroll(AbstractSpell spell) {
            ItemStack stack = new ItemStack(ItemRegistry.SCROLL.get());
            ISpellContainer.set(stack, ISpellContainer.createScrollContainer(spell, 1, stack));
            return stack;
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
                        if (!stack.isEmpty() && !containsWithdrawnSpell(stack)) {
                            page.addProperty(Integer.toString(i), stack.save(server.registryAccess()).toString());
                        }
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
                                items[p][i] = ItemStack.parse(server.registryAccess(),
                                        net.minecraft.nbt.TagParser.parseTag(page.get(key).getAsString())).orElse(ItemStack.EMPTY);
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
                        root = net.minecraft.nbt.NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    }
                    for (int p = 0; p < PAGES; p++) for (int i = 0; i < PAGE_SIZE; i++) {
                        String key = "p" + p + "_" + i;
                        if (root.contains(key)) items[p][i] = ItemStack.parse(server.registryAccess(), root.getCompound(key)).orElse(ItemStack.EMPTY);
                    }
                    save();
                }
            } catch (Exception ignored) { }
        }
    }

    private static final class FreeContainer extends SimpleContainer {
        private final int pageCount;
        private final ItemStack[] templates = new ItemStack[PAGE_SIZE];
        private boolean initializing = true;

        FreeContainer(ShopData data, int page) {
            super(PAGE_SIZE);
            this.pageCount = data.usedPageCount();
            for (int i = 0; i < PAGE_SIZE; i++) if (isItemSlot(i)) {
                templates[i] = data.items[page][i].copy();
                super.setItem(i, templates[i].copy());
            }
            super.setItem(PREVIOUS_PAGE_SLOT, previousPageButton(page, pageCount));
            super.setItem(NEXT_PAGE_SLOT, nextPageButton(page, pageCount));
            initializing = false;
            data.save();
        }
        int pageCount() { return pageCount; }

        /**
         * The free shop is a read-only template.  Vanilla shift-click and close
         * cleanup can call setItem/clearContent after removeItem; ignoring those
         * writes prevents a player action or a page switch from emptying the
         * displayed template.  Each removal returns a copy, so the player still
         * receives the item while the source slot is instantly replenished.
         */
        @Override public void setItem(int slot, ItemStack stack) {
            if (initializing) super.setItem(slot, stack);
        }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
        @Override public ItemStack removeItem(int slot, int amount) {
            if (!isItemSlot(slot) || amount <= 0) return ItemStack.EMPTY;
            return getItem(slot).copyWithCount(Math.min(amount, getItem(slot).getCount()));
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            return !isItemSlot(slot) ? ItemStack.EMPTY : getItem(slot).copy();
        }
        @Override public void clearContent() { }

        /** Restores every product slot after vanilla has handled any click type. */
        void restockAll() {
            for (int slot = 0; slot < PAGE_SIZE; slot++) {
                if (isItemSlot(slot)) super.setItem(slot, templates[slot].copy());
            }
            setChanged();
        }
    }

    private static boolean isItemSlot(int slot) {
        return slot >= 0 && slot < PAGE_SIZE && slot != PREVIOUS_PAGE_SLOT && slot != NEXT_PAGE_SLOT;
    }

    private static ItemStack previousPageButton(int page, int pageCount) {
        ItemStack button = new ItemStack(Items.ARROW);
        button.set(DataComponents.CUSTOM_NAME, Component.literal("切换上一页（第 " + (page + 1) + "/" + pageCount + " 页）"));
        return button;
    }

    private static ItemStack nextPageButton(int page, int pageCount) {
        ItemStack button = new ItemStack(Items.ARROW);
        button.set(DataComponents.CUSTOM_NAME, Component.literal("切换下一页（第 " + (page + 1) + "/" + pageCount + " 页）"));
        return button;
    }

    private static final class EditorContainer extends SimpleContainer {
        private final ShopData data;
        private final int page;
        private boolean initializing = true;

        EditorContainer(ShopData data, int page) {
            super(PAGE_SIZE);
            this.data = data;
            this.page = page;
            for (int i = 0; i < PAGE_SIZE; i++) if (isItemSlot(i)) super.setItem(i, data.items[page][i].copy());
            super.setItem(PREVIOUS_PAGE_SLOT, previousPageButton(page, PAGES));
            super.setItem(NEXT_PAGE_SLOT, nextPageButton(page, PAGES));
            initializing = false;
        }

        @Override public void setItem(int slot, ItemStack stack) {
            if (!isItemSlot(slot) && !initializing) return;
            super.setItem(slot, stack);
            if (!initializing && isItemSlot(slot)) {
                data.items[page][slot] = stack.copy();
                data.save();
            }
        }

        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return isItemSlot(slot); }

        @Override public void setChanged() {
            super.setChanged();
            if (initializing) return;
            for (int i = 0; i < PAGE_SIZE; i++) if (isItemSlot(i)) data.items[page][i] = getItem(i).copy();
            data.save();
        }

        // AbstractContainerMenu uses removeItemNoUpdate while closing. Do not treat
        // that lifecycle cleanup as an edit, otherwise reopening loses the shop.
        @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }

        /** ChestMenu calls clearContent when it closes; editor contents are persistent data, not drops. */
        @Override public void clearContent() { }

        void saveSnapshot() {
            for (int i = 0; i < PAGE_SIZE; i++) if (isItemSlot(i)) data.items[page][i] = getItem(i).copy();
            data.save();
        }
    }

    /** Editor menu that never invokes vanilla's close-time container clearing. */
    private static final class EditorMenu extends ChestMenu {
        private final EditorContainer editorContainer;
        private final int page;

        EditorMenu(int id, net.minecraft.world.entity.player.Inventory inventory, EditorContainer container, int page) {
            super(net.minecraft.world.inventory.MenuType.GENERIC_9x6, id, inventory, container, 6);
            this.editorContainer = container;
            this.page = page;
        }

        @Override public void clicked(int slotId, int button, ClickType clickType, net.minecraft.world.entity.player.Player player) {
            if (slotId == PREVIOUS_PAGE_SLOT) {
                if (player instanceof ServerPlayer serverPlayer) {
                    editorContainer.saveSnapshot();
                    int previous = Math.floorMod(page - 1, PAGES);
                    setPage(serverPlayer, previous);
                    open(serverPlayer, true, previous);
                }
                return;
            }
            if (slotId == NEXT_PAGE_SLOT) {
                if (player instanceof ServerPlayer serverPlayer) {
                    editorContainer.saveSnapshot();
                    int next = (page + 1) % PAGES;
                    setPage(serverPlayer, next);
                    open(serverPlayer, true, next);
                }
                return;
            }
            super.clicked(slotId, button, clickType, player);
        }

        @Override public void removed(net.minecraft.world.entity.player.Player player) {
            editorContainer.saveSnapshot();
        }
    }

    /** The final shop slot is an action, so its click opens the next populated page instead of giving an item. */
    private static final class ShopMenu extends ChestMenu {
        private final int page;
        private final int pageCount;
        private final FreeContainer shopContainer;

        ShopMenu(int id, net.minecraft.world.entity.player.Inventory inventory, FreeContainer container, int page) {
            super(net.minecraft.world.inventory.MenuType.GENERIC_9x6, id, inventory, container, 6);
            this.page = page;
            this.pageCount = container.pageCount();
            this.shopContainer = container;
        }

        @Override public void clicked(int slotId, int button, ClickType clickType, net.minecraft.world.entity.player.Player player) {
            if (slotId == PREVIOUS_PAGE_SLOT) {
                if (player instanceof ServerPlayer serverPlayer) {
                    int previous = Math.floorMod(page - 1, pageCount);
                    setPage(serverPlayer, previous);
                    open(serverPlayer, false, previous);
                }
                return;
            }
            if (slotId == NEXT_PAGE_SLOT) {
                if (player instanceof ServerPlayer serverPlayer) {
                    int next = (page + 1) % pageCount;
                    setPage(serverPlayer, next);
                    open(serverPlayer, false, next);
                }
                return;
            }
            super.clicked(slotId, button, clickType, player);
            // Crouch-click, shift-click, drag pickup and double-click use
            // different vanilla paths. Always restore the template and sync it.
            shopContainer.restockAll();
            broadcastChanges();
        }
    }
}
