package com.example.scrollspellicons.duel;

import com.example.scrollspellicons.IronSpellPerformance;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Forge 1.20.1 item and creative-tab registrations. */
public final class SpellDuelItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, IronSpellPerformance.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IronSpellPerformance.MOD_ID);
    public static final RegistryObject<Item> PLAYER_SELECTOR = ITEMS.register("duel_player_selector", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> POINT_SELECTOR = ITEMS.register("duel_point_selector", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SHOP_EDITOR = ITEMS.register("shop_editor", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<CreativeModeTab> DUEL_TAB = TABS.register("spell_duel", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.iron_magic_duel.spell_duel"))
            .icon(() -> PLAYER_SELECTOR.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PLAYER_SELECTOR.get()); output.accept(POINT_SELECTOR.get());
                output.accept(SHOP_EDITOR.get()); output.accept(SpellDuelShop.shopStack());
            }).build());
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        event.accept(PLAYER_SELECTOR.get()); event.accept(POINT_SELECTOR.get());
        event.accept(SHOP_EDITOR.get()); event.accept(SpellDuelShop.shopStack());
    }
    private SpellDuelItems() {}
}
