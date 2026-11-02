package com.mmods.realpower.registry;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.cable.CableBlockEntity;
import com.mmods.realpower.multiblock.wind.TurbinePowerPortBlockEntity;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RealPower.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindTurbineControllerBlockEntity>>
            WIND_TURBINE_CONTROLLER = BLOCK_ENTITIES.register("wind_turbine_controller",
                    () -> BlockEntityType.Builder.of(WindTurbineControllerBlockEntity::new,
                            ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get(),
                            ModBlocks.WIND_TURBINE_CONTROLLER_INDUSTRIAL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> CABLE =
            BLOCK_ENTITIES.register("cable", () -> BlockEntityType.Builder.of(CableBlockEntity::new,
                    ModBlocks.NODES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurbinePowerPortBlockEntity>> TURBINE_POWER_PORT =
            BLOCK_ENTITIES.register("turbine_power_port", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new TurbinePowerPortBlockEntity(ModBlockEntities.TURBINE_POWER_PORT.get(), pos, state),
                    ModBlocks.TURBINE_POWER_PORT.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
