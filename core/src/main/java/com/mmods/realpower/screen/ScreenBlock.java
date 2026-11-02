package com.mmods.realpower.screen;

import com.mmods.realpower.data.DataNode;
import com.mmods.realpower.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Modulo de tela: placa fina (3 cm... 3/16 m) presa na parede que, junto com os vizinhos coplanares, forma um painel
 * que mostra os dados do computador central. Liga-se a cabos de dados por qualquer lado, menos a frente.
 */
public class ScreenBlock extends BaseEntityBlock implements DataNode {
    public static final MapCodec<ScreenBlock> CODEC = simpleCodec(ScreenBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Espessura da placa, em pixels de modelo (1/16 m). */
    public static final int THICKNESS = 3;

    public ScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean connects(BlockState state, Direction side) {
        return side != state.getValue(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(0, 0, 0, 16, 16, THICKNESS);
            case NORTH -> Block.box(0, 0, 16 - THICKNESS, 16, 16, 16);
            case EAST -> Block.box(0, 0, 0, THICKNESS, 16, 16);
            default -> Block.box(16 - THICKNESS, 0, 0, 16, 16, 16);
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScreenBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SCREEN.get(), ScreenBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ScreenGroup group = ScreenGroup.of(level, pos);
            if (level.getBlockEntity(group.master()) instanceof ScreenBlockEntity master) {
                Vec3 rel = hit.getLocation().subtract(group.master().getX(), group.master().getY(), group.master().getZ());
                Direction right = ScreenGroup.right(group.facing());
                // distancia a borda esquerda do painel: a borda esquerda do mestre e seu canto minimo se a direita aponta para +
                double along = rel.x * right.getStepX() + rel.z * right.getStepZ() + (right.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 1.0 : 0.0);
                double px = along * ScreenLayout.PX_PER_BLOCK;
                double py = (1.0 - rel.y) * ScreenLayout.PX_PER_BLOCK;
                master.click(group, px, py);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
