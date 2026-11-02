package com.mmods.realpower.data;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import com.mmods.realpower.cable.CableBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Porta de dados: placa com conector que se fixa numa face de um equipamento (gerador, transformador, computador)
 * e o expoe a rede de dados. FACING aponta para fora; o equipamento fica atras. Cabos de dados ligam-se nos outros lados.
 */
public class DataPortBlock extends Block implements DataNode {
    public static final MapCodec<DataPortBlock> CODEC = simpleCodec(DataPortBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public DataPortBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (BooleanProperty property : CableBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        CableBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        for (Direction side : Direction.values()) {
            state = state.setValue(CableBlock.PROPERTY_BY_DIRECTION.get(side),
                    side != context.getClickedFace().getOpposite() && DataCableBlock.linksTo(context.getLevel(), context.getClickedPos(), side));
        }
        return state;
    }

    /** As propriedades de lado so marcam onde ha cabo ligado: o modelo desenha um trecho de conector ate ele. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean linked = direction != state.getValue(FACING).getOpposite()
                && neighborState.getBlock() instanceof DataNode node && node.connects(neighborState, direction.getOpposite());
        return state.setValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction), linked);
    }

    /** O no nao se liga ao equipamento que esta atras dele. */
    @Override
    public boolean connects(BlockState state, Direction side) {
        return side != state.getValue(FACING).getOpposite();
    }

    /** Posicao do equipamento a que a porta esta fixada. */
    public static BlockPos attachedPos(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    public static DataDevice deviceOf(Level level, BlockPos pos, BlockState state) {
        return DataDevice.at(level, attachedPos(state, pos));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape plate = switch (state.getValue(FACING)) {
            case NORTH -> Block.box(2, 2, 12, 14, 14, 16);
            case SOUTH -> Block.box(2, 2, 0, 14, 14, 4);
            case WEST -> Block.box(12, 2, 2, 16, 14, 14);
            case EAST -> Block.box(0, 2, 2, 4, 14, 14);
            case DOWN -> Block.box(2, 12, 2, 14, 16, 14);
            case UP -> Block.box(2, 0, 2, 14, 4, 14);
        };
        return Shapes.or(plate, switch (state.getValue(FACING)) {
            case NORTH -> Block.box(5, 5, 8, 11, 11, 12);
            case SOUTH -> Block.box(5, 5, 4, 11, 11, 8);
            case WEST -> Block.box(8, 5, 5, 12, 11, 11);
            case EAST -> Block.box(4, 5, 5, 8, 11, 11);
            case DOWN -> Block.box(5, 8, 5, 11, 12, 11);
            case UP -> Block.box(5, 4, 5, 11, 8, 11);
        });
    }
}
