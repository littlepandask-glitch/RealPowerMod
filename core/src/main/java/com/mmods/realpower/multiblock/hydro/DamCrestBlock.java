package com.mmods.realpower.multiblock.hydro;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cubo de crista: piso de passarela que pode ter um guarda-corpo numa das bordas ({@code RAIL}). O construtor de modulos poe
 * o guarda-corpo so nas duas bordas externas da crista, deixando o meio livre para andar.
 */
public class DamCrestBlock extends DamBlock {
    public static final MapCodec<DamCrestBlock> CODEC = simpleCodec(DamCrestBlock::new);
    public static final EnumProperty<Rail> RAIL = EnumProperty.create("rail", Rail.class);

    /** Borda com guarda-corpo (ou nenhuma). */
    public enum Rail implements StringRepresentable {
        NONE("none", null), NORTH("north", Direction.NORTH), EAST("east", Direction.EAST),
        SOUTH("south", Direction.SOUTH), WEST("west", Direction.WEST);

        private final String id;
        private final Direction side;

        Rail(String id, Direction side) {
            this.id = id;
            this.side = side;
        }

        public static Rail of(Direction side) {
            return switch (side) {
                case NORTH -> NORTH;
                case EAST -> EAST;
                case SOUTH -> SOUTH;
                case WEST -> WEST;
                default -> NONE;
            };
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    private static final VoxelShape BODY = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape[] SHAPES = new VoxelShape[Rail.values().length];

    static {
        SHAPES[Rail.NONE.ordinal()] = BODY;
        SHAPES[Rail.NORTH.ordinal()] = Shapes.or(BODY, Block.box(0, 16, 0, 16, 24, 2));
        SHAPES[Rail.SOUTH.ordinal()] = Shapes.or(BODY, Block.box(0, 16, 14, 16, 24, 16));
        SHAPES[Rail.WEST.ordinal()] = Shapes.or(BODY, Block.box(0, 16, 0, 2, 24, 16));
        SHAPES[Rail.EAST.ordinal()] = Shapes.or(BODY, Block.box(14, 16, 0, 16, 24, 16));
    }

    public DamCrestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RAIL, Rail.NONE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RAIL);
    }

    /** Colocado a mao: guarda-corpo na borda voltada para onde o jogador olha (o lado do lago). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(RAIL, Rail.of(context.getHorizontalDirection()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(RAIL).ordinal()];
    }
}
