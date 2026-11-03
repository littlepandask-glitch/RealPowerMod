package com.mmods.realpower.registry;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.computer.ComputerMenu;
import com.mmods.realpower.menu.HydroMenu;
import com.mmods.realpower.menu.TurbineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, RealPower.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<TurbineMenu>> WIND_TURBINE =
            MENUS.register("wind_turbine", () -> IMenuTypeExtension.create(TurbineMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<HydroMenu>> HYDRO_UNIT =
            MENUS.register("hydro_unit", () -> IMenuTypeExtension.create(HydroMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ComputerMenu>> COMPUTER =
            MENUS.register("computer", () -> IMenuTypeExtension.create(ComputerMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
