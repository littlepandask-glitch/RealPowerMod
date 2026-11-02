package com.mmods.realpower.storage;

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
import net.minecraft.world.item.context.BlockPlaceContext;
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
 * Banco de capacitores: galpao que cresce com o tier (bloco base no centro da face inferior). As quatro celulas de
 * porta, no chao e no centro de cada face lateral, sao onde os cabos de energia se ligam; o resto e preenchimento.
 */
public class CapacitorBankBlock extends BaseEntityBlock implements Tiered, StructureBase {
    public static final MapCodec<CapacitorBankBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Tier.CODEC.fieldOf("tier").forGetter(CapacitorBankBlock::tier)
    ).apply(instance, CapacitorBankBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Tier tier;

    public CapacitorBankBlock(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public Tier tier() {
        return tier;
    }

    /** Dimensoes (largura, profundidade, altura) em blocos; largura e profundidade sao impares (a base fica no centro). */
    public static int[] size(Tier tier) {
        return switch (tier) {
            case BASIC -> new int[]{3, 3, 2};
            case INDUSTRIAL -> new int[]{5, 3, 3};
            case HIGH_VOLTAGE -> new int[]{5, 5, 3};
            case ADVANCED -> new int[]{7, 5, 4};
            case FUTURE -> new int[]{9, 5, 4};
        };
    }

    /** Energia guardada por metro cubico do galpao, por tier (FE). */
    public static long density(Tier tier) {
        return switch (tier) {
            case BASIC -> 2_000_000L;
            case INDUSTRIAL -> 5_000_000L;
            case HIGH_VOLTAGE -> 15_000_000L;
            case ADVANCED -> 50_000_000L;
            case FUTURE -> 200_000_000L;
        };
    }

    public long capacity() {
        int[] size = size(tier);
        return (long) size[0] * size[1] * size[2] * density(tier);
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
        int[] size = size(tier);
        int halfW = (size[0] - 1) / 2;
        int halfD = (size[1] - 1) / 2;
        Direction facing = state.getValue(FACING);
        List<BlockPos> cells = new ArrayList<>();
        for (int y = 0; y < size[2]; y++) {
            for (int lx = -halfW; lx <= halfW; lx++) {
                for (int lz = -halfD; lz <= halfD; lz++) {
                    if (lx != 0 || lz != 0 || y != 0) {
                        cells.add(CableBlock.rotate(base, facing, lx, lz, y));
                    }
                }
            }
        }
        return cells;
    }

    /** Celulas de porta de energia: no chao, no centro de cada face lateral. */
    public List<BlockPos> portCells(BlockPos base, BlockState state) {
        int[] size = size(tier);
        int halfW = (size[0] - 1) / 2;
        int halfD = (size[1] - 1) / 2;
        Direction facing = state.getValue(FACING);
        return List.of(CableBlock.rotate(base, facing, -halfW, 0, 0), CableBlock.rotate(base, facing, halfW, 0, 0),
                CableBlock.rotate(base, facing, 0, -halfD, 0), CableBlock.rotate(base, facing, 0, halfD, 0));
    }

    /** Garante as celulas do conjunto (preenchimento e portas) no lugar certo. */
    void ensureStructure(Level level, BlockPos pos, BlockState state) {
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
                Block wanted = ports.contains(cell) ? ModBlocks.CAPACITOR_PORT.get() : ModBlocks.CAPACITOR_STRUCTURE.get();
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
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            for (BlockPos cell : structureCells(pos, state)) {
                if (level.getBlockState(cell).getBlock() instanceof PowerStructureBlock) {
                    level.removeBlock(cell, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CapacitorBankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.CAPACITOR_BANK.get(), CapacitorBankBlockEntity::serverTick);
    }
}
