package com.example.scrollspellicons.duel;

import com.example.scrollspellicons.IronSpellPerformance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import com.example.scrollspellicons.spells.CrosswindIronSlashSpell;
import com.example.scrollspellicons.spells.PhantomHalberdRingSpell;
import com.example.scrollspellicons.spells.AstralPredatorSpell;
import com.example.scrollspellicons.spells.BlazingDragonCorridorSpell;

public final class SpellDuelItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IronSpellPerformance.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IronSpellPerformance.MOD_ID);
    public static final DeferredItem<Item> PLAYER_SELECTOR = ITEMS.register("duel_player_selector", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> POINT_SELECTOR = ITEMS.register("duel_point_selector", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> NO_CAST_SELECTOR = ITEMS.register("no_cast_selector", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SHOP_EDITOR = ITEMS.register("shop_editor", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PLAYER_INTERACTOR = ITEMS.register("player_interactor", () -> new Item(new Item.Properties().stacksTo(1)));
    /** Pixel-art jade wall used as the visual identity of the duel tab. */
    public static final DeferredItem<Item> DUEL_TAB_ICON = ITEMS.register("duel_tab_icon", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DUEL_TAB = TABS.register("spell_duel", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iron_magic_duel.spell_duel"))
            .icon(() -> DUEL_TAB_ICON.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PLAYER_SELECTOR.get());
                output.accept(POINT_SELECTOR.get());
                output.accept(NO_CAST_SELECTOR.get());
                output.accept(SHOP_EDITOR.get());
                output.accept(PLAYER_INTERACTOR.get());
                output.accept(crosswindIronSlashScroll());
                output.accept(phantomHalberdRingScroll());
                output.accept(astralPredatorScroll());
                output.accept(blazingDragonCorridorScroll());
                output.accept(SpellDuelShop.shopStack());
            })
            .build());

    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) return;
        addOnce(event, PLAYER_SELECTOR.get().getDefaultInstance());
        addOnce(event, POINT_SELECTOR.get().getDefaultInstance());
        addOnce(event, NO_CAST_SELECTOR.get().getDefaultInstance());
        addOnce(event, SHOP_EDITOR.get().getDefaultInstance());
        addOnce(event, crosswindIronSlashScroll());
        addOnce(event, phantomHalberdRingScroll());
        addOnce(event, astralPredatorScroll());
        addOnce(event, blazingDragonCorridorScroll());
        addOnce(event, SpellDuelShop.shopStack());
    }

    private static ItemStack crosswindIronSlashScroll() {
        var spell = SpellRegistry.getSpell(CrosswindIronSlashSpell.ID);
        if (spell == SpellRegistry.none()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.set(stack, ISpellContainer.createScrollContainer(spell, 1, stack));
        return stack;
    }

    private static ItemStack phantomHalberdRingScroll() {
        var spell = SpellRegistry.getSpell(PhantomHalberdRingSpell.ID);
        if (spell == SpellRegistry.none()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.set(stack, ISpellContainer.createScrollContainer(spell, 1, stack));
        return stack;
    }

    private static ItemStack astralPredatorScroll() {
        var spell = SpellRegistry.getSpell(AstralPredatorSpell.ID);
        if (spell == SpellRegistry.none()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.set(stack, ISpellContainer.createScrollContainer(spell, 1, stack));
        return stack;
    }

    private static ItemStack blazingDragonCorridorScroll() {
        var spell = SpellRegistry.getSpell(BlazingDragonCorridorSpell.ID);
        if (spell == SpellRegistry.none()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.set(stack, ISpellContainer.createScrollContainer(spell, 1, stack));
        return stack;
    }

    private static void addOnce(BuildCreativeModeTabContentsEvent event, ItemStack stack) {
        boolean exists = event.getParentEntries().stream().anyMatch(existing -> ItemStack.isSameItem(existing, stack))
                || event.getSearchEntries().stream().anyMatch(existing -> ItemStack.isSameItem(existing, stack));
        if (!exists) event.accept(stack);
    }

    private SpellDuelItems() {}
}
