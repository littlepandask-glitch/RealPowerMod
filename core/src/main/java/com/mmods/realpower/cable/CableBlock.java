package com.mmods.realpower.cable;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Cabo de energia. Cada face fica conectada quando o vizinho expoe energia (outro cabo, gerador,
 * consumidor). A vazao e a perda dependem do {@link Tier}; a logica da rede esta em {@link CableNetworks}.
 */
public class CableBlock extends BaseEntityBlock implements Tiered, StructureBase {
    public static final MapCodec<CableBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Tier.CODEC.fieldOf("tier").forGetter(CableBlock::tier),
            CableKind.CODEC.fieldOf("kind").forGetter(CableBlock::kind)
    ).apply(instance, CableBlock::new));

    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = new EnumMap<>(Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP,
            Direction.DOWN, BlockStateProperties.DOWN));

    private static VoxelShape coreOf(CableKind kind) {
        return switch (kind) {
            case CABLE -> Block.box(6, 6, 6, 10, 10, 10);
            case RELAY -> Block.box(4, 4, 4, 12, 12, 12);
            case POLE -> Block.box(5.5, 0, 5.5, 10.5, 16, 10.5);
            case TOWER -> Block.box(2, 0, 2, 14, 3, 14);
            case TRANSFORMER -> Shapes.block();
        };
    }

    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6),
            Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16),
            Direction.WEST, Block.box(0, 6, 6, 6, 10, 10),
            Direction.EAST, Block.box(10, 6, 6, 16, 10, 10),
            Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10),
            Direction.UP, Block.box(6, 10, 6, 10, 16, 10)));
    /** Sentido da linha de um poste/torre: as travessas ficam de lado e os fios seguem nesse eixo. */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final VoxelShape[] shapes = new VoxelShape[64];

    private final Tier tier;
    private final CableKind kind;

    public CableBlock(Properties properties, Tier tier, CableKind kind) {
        super(properties);
        this.tier = tier;
        this.kind = kind;
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state.setValue(FACING, Direction.NORTH));
    }

    @Override
    public Tier tier() {
        return tier;
    }

    public CableKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PROPERTY_BY_DIRECTION.values().forEach(builder::add);
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    /** Conectado quando o vizinho tem capability de energia voltada para este cabo. */
    private static boolean connectsTo(Level level, BlockPos pos, Direction direction) {
        Block neighbor = level.getBlockState(pos.relative(direction)).getBlock();
        if (neighbor == ModBlocks.TRANSFORMER_PORT.get() || neighbor == ModBlocks.TURBINE_POWER_PORT.get()) {
            return true;
        }
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(direction), direction.getOpposite()) != null;
    }

    private BlockState withConnections(Level level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connectsTo(level, pos, direction));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = withConnections(context.getLevel(), context.getClickedPos(),
                defaultBlockState().setValue(FACING, context.getHorizontalDirection()));
        if (kind.overhead() && !hasRoom(context.getLevel(), context.getClickedPos(), state)) {
            return null; // sem espaco livre para o conjunto inteiro
        }
        return state;
    }

    @Override
    public BlockState footprintState(Direction look) {
        return defaultBlockState().setValue(FACING, look);
    }

    private boolean hasRoom(Level level, BlockPos pos, BlockState state) {
        for (BlockPos cell : structureCells(pos, state)) {
            if (!level.getBlockState(cell).canBeReplaced() || level.isOutsideBuildHeight(cell)) {
                return false;
            }
        }
        return true;
    }

    /** Celulas de porta de energia (nivel do chao, no centro de cada uma das 4 faces laterais) do transformador. */
    public java.util.List<BlockPos> portCells(BlockPos base, BlockState state) {
        if (kind != CableKind.TRANSFORMER) {
            return java.util.List.of();
        }
        int halfW = (kind.size(tier)[0] - 1) / 2;
        int halfD = (kind.size(tier)[1] - 1) / 2;
        Direction facing = state.getValue(FACING);
        return java.util.List.of(rotate(base, facing, -halfW, 0, 0), rotate(base, facing, halfW, 0, 0),
                rotate(base, facing, 0, -halfD, 0), rotate(base, facing, 0, halfD, 0));
    }

    /** Posicao mundial de uma celula local (lx lateral, lz de profundidade, y de altura) de um conjunto virado para {@code facing}. */
    public static BlockPos rotate(BlockPos base, Direction facing, int lx, int lz, int y) {
        return switch (facing) {
            case EAST -> base.offset(-lz, y, lx);
            case SOUTH -> base.offset(-lx, y, -lz);
            case WEST -> base.offset(lz, y, -lx);
            default -> base.offset(lx, y, lz);
        };
    }

    /** Inverso de {@link #rotate}: coordenadas locais {lx, lz, y} de uma posicao mundial. */
    public static int[] unrotate(BlockPos base, Direction facing, BlockPos cell) {
        int dx = cell.getX() - base.getX();
        int dz = cell.getZ() - base.getZ();
        int y = cell.getY() - base.getY();
        return switch (facing) {
            case EAST -> new int[]{dz, -dx, y};
            case SOUTH -> new int[]{-dx, -dz, y};
            case WEST -> new int[]{-dz, dx, y};
            default -> new int[]{dx, dz, y};
        };
    }

    /** Base do transformador a que a celula de porta {@code port} pertence, ou null. */
    public static BlockPos baseOfPort(BlockGetter level, BlockPos port) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos candidate = port.offset(dx, 0, dz);
                BlockState candidateState = level.getBlockState(candidate);
                if (candidateState.getBlock() instanceof CableBlock cable && cable.portCells(candidate, candidateState).contains(port)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @Override
    public boolean hasStructure() {
        return kind.overhead();
    }

    /** Celulas (sem o bloco base) que o conjunto multibloco ocupa; a largura segue o eixo local x, girado pelo FACING. */
    @Override
    public java.util.List<BlockPos> structureCells(BlockPos base, BlockState state) {
        int[] size = kind.size(tier);
        Direction facing = state.getValue(FACING);
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (int y = 0; y < size[2]; y++) {
            for (int lx = -(size[0] - 1) / 2; lx <= (size[0] - 1) / 2; lx++) {
                for (int lz = -(size[1] - 1) / 2; lz <= (size[1] - 1) / 2; lz++) {
                    if (lx == 0 && lz == 0 && y == 0) {
                        continue;
                    }
                    cells.add(rotate(base, facing, lx, lz, y));
                }
            }
        }
        return cells;
    }

    private Block fillerBlock() {
        return switch (kind) {
            case POLE -> ModBlocks.POLE_STRUCTURE.get();
            case TOWER -> ModBlocks.TOWER_STRUCTURE.get();
            default -> ModBlocks.TRANSFORMER_STRUCTURE.get();
        };
    }

    /** Garante as celulas do conjunto (preenchimento e portas) no lugar certo; repara conjuntos de versoes antigas. */
    private void ensureStructure(Level level, BlockPos pos, BlockState state) {
        if (PowerStructureBlock.healing) {
            return;
        }
        java.util.List<BlockPos> ports = portCells(pos, state);
        PowerStructureBlock.healing = true;
        try {
            for (BlockPos cell : structureCells(pos, state)) {
                if (!level.isLoaded(cell)) {
                    continue;
                }
                Block wanted = ports.contains(cell) ? ModBlocks.TRANSFORMER_PORT.get() : fillerBlock();
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
        // trocar o estado aqui dentro faria o setBlock externo nao avisar os vizinhos; adia para o proximo tick
        if (!level.isClientSide) {
            if (kind.overhead()) {
                ensureStructure(level, pos, state);
            }
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (kind.overhead() && !state.is(newState.getBlock()) && !level.isClientSide) {
            for (BlockPos cell : structureCells(pos, state)) {
                if (level.getBlockState(cell).getBlock() instanceof PowerStructureBlock) {
                    level.removeBlock(cell, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refresh(state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        refresh(state, level, pos);
    }

    /** Recalcula as conexoes (servidor); se mudarem, atualiza o bloco e refaz as redes. */
    private void refresh(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        if (kind.overhead()) {
            ensureStructure(level, pos, state);
        }
        BlockState updated = withConnections(level, pos, state);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
            CableNetworks.markDirty(level);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) {
                mask |= 1 << direction.ordinal();
            }
        }
        VoxelShape shape = shapes[mask];
        if (shape == null) {
            shape = coreOf(kind);
            for (Direction direction : Direction.values()) {
                boolean vertical = direction.getAxis() == Direction.Axis.Y;
                if ((mask & (1 << direction.ordinal())) != 0 && (kind.verticalArms() || !vertical)) {
                    shape = Shapes.or(shape, ARMS.get(direction));
                }
            }
            shapes[mask] = shape;
        }
        return shape;
    }
}
