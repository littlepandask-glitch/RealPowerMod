package com.mmods.realpower.multiblock.wind;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Revestimento da turbina. Formado, fica invisivel (o corpo 3D o substitui) e sua colisao segue a
 * parede lisa do modelo: {@link #MASK} diz quais das 9 sub-colunas 3x3 do bloco (bit = ix * 3 + iz,
 * eixos x e z do mundo) sao solidas. Opaco para a luz: o interior da torre e escuro sem lampadas.
 */
public class TurbineCasingBlock extends WindStructureBlock {
    public static final MapCodec<TurbineCasingBlock> CASING_CODEC = simpleCodec(TurbineCasingBlock::new);
    public static final IntegerProperty MASK = IntegerProperty.create("mask", 0, TurbineBlueprint.FULL_MASK);

    private static final VoxelShape[] SHAPES = new VoxelShape[TurbineBlueprint.FULL_MASK + 1];

    public TurbineCasingBlock(Properties properties) {
        super(properties, true);
        registerDefaultState(defaultBlockState().setValue(MASK, TurbineBlueprint.FULL_MASK));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CASING_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MASK);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FORMED) ? shapeFor(state.getValue(MASK)) : Shapes.block();
    }

    /** noOcclusion evita que as faces vizinhas (a laje) sumam, mas a luz continua bloqueada. */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    private static VoxelShape shapeFor(int mask) {
        VoxelShape shape = SHAPES[mask];
        if (shape == null) {
            shape = Shapes.empty();
            for (int ix = 0; ix < 3; ix++) {
                for (int iz = 0; iz < 3; iz++) {
                    if ((mask & (1 << (ix * 3 + iz))) != 0) {
                        shape = Shapes.or(shape, Block.box(ix * 16.0 / 3.0, 0, iz * 16.0 / 3.0,
                                (ix + 1) * 16.0 / 3.0, 16, (iz + 1) * 16.0 / 3.0));
                    }
                }
            }
            SHAPES[mask] = shape;
        }
        return shape;
    }
}
