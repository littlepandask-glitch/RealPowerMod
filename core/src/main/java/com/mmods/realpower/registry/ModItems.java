package com.mmods.realpower.registry;

import com.mmods.realpower.RealPower;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealPower.MOD_ID);

    public static final DeferredItem<BlockItem> WIND_TOWER = ITEMS.registerSimpleBlockItem(ModBlocks.WIND_TOWER);
    public static final DeferredItem<BlockItem> WIND_BLADE = ITEMS.registerSimpleBlockItem(ModBlocks.WIND_BLADE);
    public static final DeferredItem<BlockItem> WIND_TURBINE_CONTROLLER =
            ITEMS.registerSimpleBlockItem(ModBlocks.WIND_TURBINE_CONTROLLER);

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
