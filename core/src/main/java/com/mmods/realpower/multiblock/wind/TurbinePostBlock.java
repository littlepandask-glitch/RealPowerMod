package com.mmods.realpower.multiblock.wind;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Fuste do poste da porta de energia da turbina (parte da estrutura; nao conduz energia). */
public class TurbinePostBlock extends Block {
    public static final MapCodec<TurbinePostBlock> CODEC = simpleCodec(TurbinePostBlock::new);
    private static final VoxelShape SHAPE = Block.box(5.5, 0, 5.5, 10.5, 16, 10.5);

    public TurbinePostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
