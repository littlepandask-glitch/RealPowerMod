package com.mmods.realpower.registry;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RealPower.MOD_ID);

    public static final DeferredBlock<Block> WIND_TOWER = BLOCKS.registerBlock("wind_tower", Block::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL));

    public static final DeferredBlock<Block> WIND_BLADE = BLOCKS.registerBlock("wind_blade", Block::new,
            BlockBehaviour.Properties.of().strength(2.0F, 4.0F).sound(SoundType.METAL));

    public static final DeferredBlock<WindTurbineControllerBlock> WIND_TURBINE_CONTROLLER = BLOCKS.registerBlock(
            "wind_turbine_controller", WindTurbineControllerBlock::new,
            BlockBehaviour.Properties.of().strength(4.0F, 8.0F).sound(SoundType.METAL));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
