package com.example.scrollspellicons.duel;

import com.example.scrollspellicons.IronSpellPerformance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public final class SpellDuelItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IronSpellPerformance.MOD_ID);
    public static final DeferredItem<Item> PLAYER_SELECTOR = ITEMS.register("duel_player_selector", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> POINT_SELECTOR = ITEMS.register("duel_point_selector", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SHOP_EDITOR = ITEMS.register("shop_editor", () -> new Item(new Item.Properties().stacksTo(1)));

    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) return;
        addOnce(event, PLAYER_SELECTOR.get().getDefaultInstance());
        addOnce(event, POINT_SELECTOR.get().getDefaultInstance());
        addOnce(event, SHOP_EDITOR.get().getDefaultInstance());
        addOnce(event, SpellDuelShop.shopStack());
    }

    private static void addOnce(BuildCreativeModeTabContentsEvent event, ItemStack stack) {
        boolean exists = event.getParentEntries().stream().anyMatch(existing -> ItemStack.isSameItem(existing, stack))
                || event.getSearchEntries().stream().anyMatch(existing -> ItemStack.isSameItem(existing, stack));
        if (!exists) event.accept(stack);
    }

    private SpellDuelItems() {}
}
