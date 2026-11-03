package com.mmods.realpower.computer;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.cable.CableBlock;
import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.cable.StructureBase;
import com.mmods.realpower.registry.ModBlockEntities;
import com.mmods.realpower.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
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

/**
 * Computador central: rack de 3 m de largura, 1 m de profundidade e 2 m de altura (bloco base no centro, embaixo).
 * As duas celulas de ponta no chao sao as portas de dados; o resto e preenchimento. A frente aponta para FACING.
 */
public class ComputerBlock extends BaseEntityBlock implements Tiered, StructureBase {
    public static final MapCodec<ComputerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Tier.CODEC.fieldOf("tier").forGetter(ComputerBlock::tier)
    ).apply(instance, ComputerBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final int WIDTH = 3;
    public static final int HEIGHT = 2;

    private final Tier tier;

    public ComputerBlock(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public Tier tier() {
        return tier;
    }

    /** Quantos equipamentos o computador supervisiona: 8 no basico, x4 a cada tier. */
    public int maxDevices() {
        return 8 << (2 * (tier.level() - 1));
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // o conjunto 3D e desenhado pelo ComputerRenderer
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (BlockPos cell : structureCells(pos, state)) {
            if (!level.getBlockState(cell).canBeReplaced() || level.isOutsideBuildHeight(cell)) {
                return null;
            }
        }
        return state;
    }

    @Override
    public boolean hasStructure() {
        return true;
    }

    @Override
    public List<BlockPos> structureCells(BlockPos base, BlockState state) {
        Direction facing = state.getValue(FACING);
        List<BlockPos> cells = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) {
            for (int lx = -1; lx <= 1; lx++) {
                if (lx != 0 || y != 0) {
                    cells.add(CableBlock.rotate(base, facing, lx, 0, y));
                }
            }
        }
        return cells;
    }

    /** Celulas das portas de dados: as duas pontas, no chao. */
    public List<BlockPos> portCells(BlockPos base, BlockState state) {
        Direction facing = state.getValue(FACING);
        return List.of(CableBlock.rotate(base, facing, -1, 0, 0), CableBlock.rotate(base, facing, 1, 0, 0));
    }

    /** Garante as celulas do conjunto (preenchimento e portas) no lugar certo. */
    public void ensureStructure(Level level, BlockPos pos, BlockState state) {
        if (PowerStructureBlock.healing) {
            return;
        }
        List<BlockPos> ports = portCells(pos, state);
        PowerStructureBlock.healing = true;
        try {
            for (BlockPos cell : structureCells(pos, state)) {
                if (!level.isLoaded(cell)) {
                    continue;
                }
                Block wanted = ports.contains(cell) ? ModBlocks.COMPUTER_PORT.get() : ModBlocks.COMPUTER_STRUCTURE.get();
                BlockState current = level.getBlockState(cell);
                if (current.getBlock() != wanted && (current.canBeReplaced() || current.getBlock() instanceof PowerStructureBlock)) {
                    level.setBlock(cell, wanted.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        } finally {
            PowerStructureBlock.healing = false;
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            ensureStructure(level, pos, state);
            afterPlaced(level, pos, state);
        }
    }

    /** Gancho apos montar as celulas (ex.: tirar o jogador preso dentro do volume). */
    protected void afterPlaced(Level level, BlockPos pos, BlockState state) {
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            PowerStructureBlock.healing = true;
            try {
                for (BlockPos cell : structureCells(pos, state)) {
                    if (level.getBlockState(cell).getBlock() instanceof PowerStructureBlock) {
                        level.removeBlock(cell, false);
                    }
                }
            } finally {
                PowerStructureBlock.healing = false;
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
            player.openMenu(computer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ComputerBlockEntity computer && computer.redstone() ? 15 : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.COMPUTER.get(), ComputerBlockEntity::serverTick);
    }
}
