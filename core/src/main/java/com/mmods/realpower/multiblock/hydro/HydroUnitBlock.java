package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.cable.CableBlock;
import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.cable.StructureBase;
import com.mmods.realpower.generator.AbstractGeneratorBlockEntity;
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
import net.minecraft.world.phys.Vec3;

/**
 * Casa de forca da barragem (unidade geradora): multibloco de tamanho fixo por tier, base no centro do chao. {@code FACING}
 * aponta para jusante (o lado do canal de fuga, onde ficam as duas portas de energia); a tomada d'agua fica no lado oposto,
 * encostada na parede da barragem, voltada para o lago.
 */
public class HydroUnitBlock extends BaseEntityBlock implements Tiered, StructureBase {
    public static final MapCodec<HydroUnitBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Tier.CODEC.fieldOf("tier").forGetter(HydroUnitBlock::tier)
    ).apply(instance, HydroUnitBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Tier tier;

    public HydroUnitBlock(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public Tier tier() {
        return tier;
    }

    /** Dimensoes (largura, altura, profundidade) em blocos; largura e profundidade sao impares (a base fica no centro). */
    public static int[] size(Tier tier) {
        return switch (tier) {
            case BASIC, INDUSTRIAL -> new int[]{7, 9, 9};
            case HIGH_VOLTAGE -> new int[]{11, 14, 13};
            case ADVANCED, FUTURE -> new int[]{15, 20, 19};
        };
    }

    /** Altura (acima do chao da unidade) do eixo da turbina, de onde se mede a queda. */
    public static int turbineHeight(Tier tier) {
        return size(tier)[1] * 3 / 10;
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
        return RenderShape.INVISIBLE; // o conjunto 3D e desenhado pelo HydroUnitRenderer
    }

    /** O jogador olha para a barragem (montante); a unidade fica de frente para ele, com o canal de fuga voltado a ele. */
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
    public BlockState footprintState(Direction look) {
        return defaultBlockState().setValue(FACING, look.getOpposite());
    }

    @Override
    public boolean hasStructure() {
        return true;
    }

    @Override
    public List<BlockPos> structureCells(BlockPos base, BlockState state) {
        int[] size = size(tier);
        int halfW = (size[0] - 1) / 2;
        int halfD = (size[2] - 1) / 2;
        Direction facing = state.getValue(FACING);
        List<BlockPos> cells = new ArrayList<>(size[0] * size[1] * size[2]);
        for (int y = 0; y < size[1]; y++) {
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

    @Override
    public boolean containsCell(BlockPos base, BlockState state, BlockPos cell) {
        int[] size = size(tier);
        int[] local = CableBlock.unrotate(base, state.getValue(FACING), cell);
        return local[2] >= 0 && local[2] < size[1] && Math.abs(local[0]) <= (size[0] - 1) / 2
                && Math.abs(local[1]) <= (size[2] - 1) / 2 && (local[0] != 0 || local[1] != 0 || local[2] != 0);
    }

    /** Duas portas de energia, no chao, nos cantos do lado de jusante (canal de fuga). */
    public List<BlockPos> portCells(BlockPos base, BlockState state) {
        int[] size = size(tier);
        Direction facing = state.getValue(FACING);
        int halfW = (size[0] - 1) / 2;
        int halfD = (size[2] - 1) / 2;
        return List.of(CableBlock.rotate(base, facing, -halfW, -halfD, 0), CableBlock.rotate(base, facing, halfW, -halfD, 0));
    }

    /** Sentido do lago (montante): oposto ao canal de fuga. */
    public static Direction upstream(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    /** Bloco logo a montante da tomada d'agua (onde o lago deve estar), junto ao chao da unidade. */
    public BlockPos intakePos(BlockPos base, BlockState state) {
        int halfD = (size(tier)[2] - 1) / 2;
        return CableBlock.rotate(base, state.getValue(FACING), 0, halfD + 1, 2);
    }

    /** Garante as celulas do conjunto: solidas (piso, parede, salao), ocas (patio e ar acima do telhado) e as portas. */
    public void ensureStructure(Level level, BlockPos pos, BlockState state) {
        if (PowerStructureBlock.healing) {
            return;
        }
        int[] size = size(tier);
        int halfW = (size[0] - 1) / 2;
        int halfD = (size[2] - 1) / 2;
        Direction facing = state.getValue(FACING);
        PowerStructureBlock.healing = true;
        try {
            for (int y = 0; y < size[1]; y++) {
                for (int lx = -halfW; lx <= halfW; lx++) {
                    for (int lz = -halfD; lz <= halfD; lz++) {
                        if (lx == 0 && lz == 0 && y == 0) {
                            continue;
                        }
                        BlockPos cell = CableBlock.rotate(pos, facing, lx, lz, y);
                        if (!level.isLoaded(cell)) {
                            continue;
                        }
                        boolean port = y == 0 && Math.abs(lx) == halfW && lz == -halfD;
                        Block wanted = port ? ModBlocks.HYDRO_PORT.get()
                                : HydroLayout.solid(size[0], size[1], size[2], lx, lz, y) ? ModBlocks.HYDRO_STRUCTURE.get()
                                : ModBlocks.STRUCTURE_HOLLOW.get();
                        BlockState current = level.getBlockState(cell);
                        if (current.is(net.minecraft.world.level.block.Blocks.WATER)
                                && HydroLayout.conduit(size[0], size[1], size[2], lx, lz, y)) {
                            continue; // conduto aberto: agua passando
                        }
                        if (current.getBlock() != wanted && (current.canBeReplaced() || current.getBlock() instanceof PowerStructureBlock)) {
                            level.setBlock(cell, wanted.defaultBlockState(),
                                    port ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
            }
        } finally {
            PowerStructureBlock.healing = false;
        }
    }

    /** Abre (agua passando da tomada ao canal de fuga) ou fecha (de volta a parede e ao patio) o conduto da unidade. */
    public void setConduit(Level level, BlockPos base, BlockState state, boolean open) {
        int[] size = size(tier);
        int halfD = (size[2] - 1) / 2;
        Direction facing = state.getValue(FACING);
        PowerStructureBlock.healing = true;
        try {
            int halfW = (size[0] - 1) / 2;
            for (int lx = -halfW; lx <= halfW; lx++) {
                for (int lz = -halfD; lz <= halfD; lz++) {
                    for (int y = 1; y < size[1]; y++) {
                        if (!HydroLayout.conduit(size[0], size[1], size[2], lx, lz, y)) {
                            continue;
                        }
                        BlockPos cell = CableBlock.rotate(base, facing, lx, lz, y);
                        if (!level.isLoaded(cell)) {
                            continue;
                        }
                        BlockState current = level.getBlockState(cell);
                        if (open) {
                            if (current.getBlock() instanceof PowerStructureBlock || current.canBeReplaced()
                                    || current.is(net.minecraft.world.level.block.Blocks.WATER) && !current.getFluidState().isSource()) {
                                level.setBlock(cell, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                            }
                        } else if (current.is(net.minecraft.world.level.block.Blocks.WATER)) {
                            Block closed = HydroLayout.solid(size[0], size[1], size[2], lx, lz, y) ? ModBlocks.HYDRO_STRUCTURE.get()
                                    : ModBlocks.STRUCTURE_HOLLOW.get();
                            level.setBlock(cell, closed.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
            }
        } finally {
            PowerStructureBlock.healing = false;
        }
    }

    private boolean isConduitWater(Level level, BlockPos base, BlockState state, BlockPos cell) {
        if (!level.getBlockState(cell).is(net.minecraft.world.level.block.Blocks.WATER)) {
            return false;
        }
        int[] size = size(tier);
        int[] l = CableBlock.unrotate(base, state.getValue(FACING), cell);
        return HydroLayout.conduit(size[0], size[1], size[2], l[0], l[1], l[2]);
    }

    /** Onde o jogador e posto se ficar preso ao posicionar: no patio de jusante, sobre o piso. */
    private Vec3 patio(BlockPos base, BlockState state) {
        int halfD = (size(tier)[2] - 1) / 2;
        BlockPos spot = CableBlock.rotate(base, state.getValue(FACING), 0, -halfD + 1, 1);
        return Vec3.atBottomCenterOf(spot);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            ensureStructure(level, pos, state);
            int[] size = size(tier);
            PowerStructureBlock.relocateStuckPlayers(level, new net.minecraft.world.phys.AABB(pos).inflate(size[0], size[1], size[2]), patio(pos, state));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            PowerStructureBlock.healing = true;
            try {
                for (BlockPos cell : structureCells(pos, state)) {
                    if (isConduitWater(level, pos, state, cell) || level.getBlockState(cell).getBlock() instanceof PowerStructureBlock) {
                        level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            } finally {
                PowerStructureBlock.healing = false;
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                  net.minecraft.world.entity.player.Player player,
                                                                  net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HydroUnitBlockEntity unit) {
            player.openMenu(unit, pos);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HydroUnitBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.HYDRO_UNIT.get(),
                AbstractGeneratorBlockEntity::serverTick);
    }
}
