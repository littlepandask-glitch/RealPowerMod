package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.registry.ModBlockEntities;
import com.mmods.realpower.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = RealPower.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.WIND_TURBINE_CONTROLLER.get(),
                context -> new WindTurbineRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.CABLE.get(), context -> new PowerLineRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.COMPUTER.get(), context -> new ComputerRenderer());
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WIND_TURBINE.get(), TurbineScreen::new);
    }
}
