package com.mmods.realpower.multiblock.wind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bloco de andaime da turbina (torre ou pa). Quando a estrutura e reconhecida o controlador
 * marca {@link #FORMED}: o bloco deixa de ser desenhado (o modelo 3D da turbina o substitui)
 * e, no caso das pas, deixa de colidir, ja que a pa real e fina.
 */
public class WindStructureBlock extends Block {
    public static final MapCodec<WindStructureBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.BOOL.fieldOf("solid_when_formed").forGetter(block -> block.solidWhenFormed)
    ).apply(instance, WindStructureBlock::new));

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private final boolean solidWhenFormed;

    public WindStructureBlock(Properties properties, boolean solidWhenFormed) {
        super(properties);
        this.solidWhenFormed = solidWhenFormed;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(FORMED) && !solidWhenFormed) {
            return Shapes.empty();
        }
        return Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) || super.propagatesSkylightDown(state, level, pos);
    }
}
