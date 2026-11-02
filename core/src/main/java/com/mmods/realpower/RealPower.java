package com.mmods.realpower;

import com.mmods.realpower.registry.ModBlockEntities;
import com.mmods.realpower.registry.ModBlocks;
import com.mmods.realpower.registry.ModCreativeTabs;
import com.mmods.realpower.registry.ModItems;
import com.mmods.realpower.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

@Mod(RealPower.MOD_ID)
public class RealPower {
    public static final String MOD_ID = "realpower";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealPower(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(RealPower::registerCapabilities);
        LOGGER.info("Real Power carregado");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.WIND_TURBINE_CONTROLLER.get(),
                (be, side) -> be.getEnergyOutput());
    }
}
