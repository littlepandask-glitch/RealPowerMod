package com.mmods.realpower.registry;

import com.mmods.realpower.RealPower;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealPower.MOD_ID);

    public static final DeferredItem<BlockItem> TURBINE_BASE = ITEMS.registerSimpleBlockItem(ModBlocks.TURBINE_BASE);
    public static final DeferredItem<BlockItem> TURBINE_CASING = ITEMS.registerSimpleBlockItem(ModBlocks.TURBINE_CASING);
    /** Itens dos nos da rede eletrica, pelo mesmo id dos blocos. */
    public static final Map<String, DeferredItem<BlockItem>> NODES = new LinkedHashMap<>();

    static {
        ModBlocks.NODES.forEach((id, block) -> NODES.put(id, ITEMS.registerSimpleBlockItem(block)));
    }

    public static final DeferredItem<BlockItem> INDUSTRIAL_LIGHT = ITEMS.registerSimpleBlockItem(ModBlocks.INDUSTRIAL_LIGHT);
    public static final DeferredItem<BlockItem> TURBINE_POST = ITEMS.registerSimpleBlockItem(ModBlocks.TURBINE_POST);
    public static final DeferredItem<BlockItem> TURBINE_POWER_PORT = ITEMS.registerSimpleBlockItem(ModBlocks.TURBINE_POWER_PORT);
    public static final DeferredItem<BlockItem> ROTOR_FAIRING = ITEMS.registerSimpleBlockItem(ModBlocks.ROTOR_FAIRING);
    public static final DeferredItem<BlockItem> TURBINE_BLADE = ITEMS.registerSimpleBlockItem(ModBlocks.TURBINE_BLADE);
    public static final DeferredItem<BlockItem> WIND_TURBINE_CONTROLLER_BASIC =
            ITEMS.registerSimpleBlockItem(ModBlocks.WIND_TURBINE_CONTROLLER_BASIC);
    public static final DeferredItem<BlockItem> WIND_TURBINE_CONTROLLER_INDUSTRIAL =
            ITEMS.registerSimpleBlockItem(ModBlocks.WIND_TURBINE_CONTROLLER_INDUSTRIAL);

    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
