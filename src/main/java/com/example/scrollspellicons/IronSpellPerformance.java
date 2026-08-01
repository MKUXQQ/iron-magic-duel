package com.example.scrollspellicons;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import com.example.scrollspellicons.config.PerformanceConfig;
import com.example.scrollspellicons.duel.SpellDuelItems;
import com.example.scrollspellicons.duel.SpellDuelNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(IronSpellPerformance.MOD_ID)
public final class IronSpellPerformance {
    public static final String MOD_ID = "iron_magic_duel";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public IronSpellPerformance(IEventBus modEventBus, ModContainer modContainer) {
        SpellDuelItems.ITEMS.register(modEventBus);
        modEventBus.addListener(SpellDuelItems::addToCreativeTab);
        modEventBus.addListener(SpellDuelNetwork::register);
        modContainer.registerConfig(ModConfig.Type.CLIENT, PerformanceConfig.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, PerformanceConfig.SERVER_SPEC);
    }
}
