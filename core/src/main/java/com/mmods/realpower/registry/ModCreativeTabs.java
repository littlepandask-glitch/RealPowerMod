package com.mmods.realpower.registry;

import com.mmods.realpower.RealPower;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealPower.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + RealPower.MOD_ID))
                    .icon(() -> ModItems.WIND_TURBINE_CONTROLLER_BASIC.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ModItems.STEEL_INGOT.get());
                        output.accept(ModItems.ENGINEER_GOGGLES.get());
                        output.accept(ModItems.WRENCH.get());
                        output.accept(ModItems.TERRAFORMER.get());
                        output.accept(ModItems.WIND_TURBINE_CONTROLLER_BASIC.get());
                        output.accept(ModItems.WIND_TURBINE_CONTROLLER_INDUSTRIAL.get());
                        ModItems.NODES.values().forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.TURBINE_BASE.get());
                        output.accept(ModItems.TURBINE_CASING.get());
                        output.accept(ModItems.ROTOR_FAIRING.get());
                        output.accept(ModItems.TURBINE_BLADE.get());
                        output.accept(ModItems.INDUSTRIAL_LIGHT.get());
                        output.accept(ModItems.TURBINE_POWER_PORT.get());
                        ModItems.CAPACITOR_BANKS.values().forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.COMPUTER_BASIC.get());
                        output.accept(ModItems.COMPUTER_INDUSTRIAL.get());
                        output.accept(ModItems.DAM_CONCRETE.get());
                        output.accept(ModItems.DAM_CREST.get());
                        ModItems.HYDRO_UNITS.values().forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.SCREEN.get());
                        output.accept(ModItems.DATA_CABLE.get());
                        output.accept(ModItems.DATA_PORT.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
