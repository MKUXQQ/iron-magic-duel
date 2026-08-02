package com.example.scrollspellicons;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import com.example.scrollspellicons.config.PerformanceConfig;
import com.example.scrollspellicons.duel.SpellDuelItems;
import com.example.scrollspellicons.duel.SpellDuelNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(IronSpellPerformance.MOD_ID)
public final class IronSpellPerformance {
    public static final String MOD_ID = "iron_magic_duel";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public IronSpellPerformance() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        SpellDuelItems.ITEMS.register(modEventBus);
        SpellDuelItems.TABS.register(modEventBus);
        modEventBus.addListener(SpellDuelItems::addToCreativeTab);
        SpellDuelNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PerformanceConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PerformanceConfig.SERVER_SPEC);
    }
}
