package com.mmods.realpower.registry;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.multiblock.wind.WindStructureBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RealPower.MOD_ID);

    public static final DeferredBlock<WindStructureBlock> WIND_TOWER = BLOCKS.registerBlock("wind_tower",
            properties -> new WindStructureBlock(properties, true),
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<WindStructureBlock> WIND_BLADE = BLOCKS.registerBlock("wind_blade",
            properties -> new WindStructureBlock(properties, false),
            BlockBehaviour.Properties.of().strength(2.0F, 4.0F).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<WindTurbineControllerBlock> WIND_TURBINE_CONTROLLER_BASIC =
            windController(Tier.BASIC);
    public static final DeferredBlock<WindTurbineControllerBlock> WIND_TURBINE_CONTROLLER_INDUSTRIAL =
            windController(Tier.INDUSTRIAL);

    private static DeferredBlock<WindTurbineControllerBlock> windController(Tier tier) {
        return BLOCKS.registerBlock("wind_turbine_controller_" + tier.getSerializedName(),
                properties -> new WindTurbineControllerBlock(properties, tier),
                BlockBehaviour.Properties.of().strength(4.0F, 8.0F).sound(SoundType.METAL).noOcclusion());
    }

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
