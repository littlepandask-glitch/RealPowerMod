package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.cable.PowerStructureBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Modulo de crista: passarela de 1 m com guarda-corpo nas duas bordas, no mesmo nivel do topo da casa de forca (a passarela
 * fica na face de cima). {@code AXIS} e o sentido em que a passarela corre; ao colocar, acompanha o muro vizinho.
 */
public class DamCrestBlock extends DamBlock {
    public static final MapCodec<DamCrestBlock> CODEC = simpleCodec(DamCrestBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private static final VoxelShape ALONG_X = Shapes.or(Block.box(0, 0, 0, 16, 16, 16),
            Block.box(0, 16, 0, 16, 24, 2), Block.box(0, 16, 14, 16, 24, 16));
    private static final VoxelShape ALONG_Z = Shapes.or(Block.box(0, 0, 0, 16, 16, 16),
            Block.box(0, 16, 0, 2, 24, 16), Block.box(14, 16, 0, 16, 24, 16));

    public DamCrestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    /** O muro corre para o lado em que ha vizinhos de muro; sem vizinhos, perpendicular a quem olha. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        int alongX = wall(level, pos.east()) + wall(level, pos.west());
        int alongZ = wall(level, pos.north()) + wall(level, pos.south());
        Direction.Axis axis = alongX != alongZ ? (alongX > alongZ ? Direction.Axis.X : Direction.Axis.Z)
                : context.getHorizontalDirection().getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return defaultBlockState().setValue(AXIS, axis);
    }

    private static int wall(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DamBlock || state.getBlock() instanceof PowerStructureBlock ? 1 : 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }
}
