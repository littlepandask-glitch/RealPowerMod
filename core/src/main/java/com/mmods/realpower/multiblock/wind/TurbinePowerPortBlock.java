package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Topo do poste da turbina: a travessa com isoladores onde cabos e linhas se conectam. */
public class TurbinePowerPortBlock extends BaseEntityBlock {
    public static final MapCodec<TurbinePowerPortBlock> CODEC = simpleCodec(TurbinePowerPortBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5.5, 0, 5.5, 10.5, 13, 10.5), Block.box(0, 11, 5, 16, 14, 11));

    public TurbinePowerPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbinePowerPortBlockEntity(ModBlockEntities.TURBINE_POWER_PORT.get(), pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
