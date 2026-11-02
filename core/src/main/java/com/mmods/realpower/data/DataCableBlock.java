package com.mmods.realpower.data;

import com.mmods.realpower.cable.CableBlock;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Cabo de dados: fino, sem tier nem perda; liga-se a qualquer outro no de dados vizinho. */
public class DataCableBlock extends Block implements DataNode {
    public static final MapCodec<DataCableBlock> CODEC = simpleCodec(DataCableBlock::new);

    private static final VoxelShape CORE = Block.box(5.5, 5.5, 5.5, 10.5, 10.5, 10.5);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(6.5, 6.5, 0, 9.5, 9.5, 6.5),
            Direction.SOUTH, Block.box(6.5, 6.5, 9.5, 9.5, 9.5, 16),
            Direction.WEST, Block.box(0, 6.5, 6.5, 6.5, 9.5, 9.5),
            Direction.EAST, Block.box(9.5, 6.5, 6.5, 16, 9.5, 9.5),
            Direction.DOWN, Block.box(6.5, 0, 6.5, 9.5, 6.5, 9.5),
            Direction.UP, Block.box(6.5, 9.5, 6.5, 9.5, 16, 9.5)));

    private final VoxelShape[] shapes = new VoxelShape[64];

    public DataCableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
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
        CableBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
    }

    /** Vizinho no lado {@code side} e um no de dados que aceita ligar-se de volta. */
    static boolean linksTo(BlockGetter level, BlockPos pos, Direction side) {
        BlockState neighbor = level.getBlockState(pos.relative(side));
        return neighbor.getBlock() instanceof DataNode node && node.connects(neighbor, side.getOpposite());
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction side : Direction.values()) {
            state = state.setValue(CableBlock.PROPERTY_BY_DIRECTION.get(side), linksTo(level, pos, side));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction),
                neighborState.getBlock() instanceof DataNode node && node.connects(neighborState, direction.getOpposite()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (state.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(side))) {
                mask |= 1 << side.ordinal();
            }
        }
        VoxelShape shape = shapes[mask];
        if (shape == null) {
            shape = CORE;
            for (Direction side : Direction.values()) {
                if ((mask & (1 << side.ordinal())) != 0) {
                    shape = Shapes.or(shape, ARMS.get(side));
                }
            }
            shapes[mask] = shape;
        }
        return shape;
    }
}
