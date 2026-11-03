package com.mmods.realpower.computer;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.cable.CableBlock;
import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sala de comando: um computador central em que o jogador entra. Predio de 9 x 5 x 7 m (base no centro do piso, porta na frente,
 * mesa de controle ao fundo); usar qualquer parte da mesa abre o menu do computador. As duas portas de dados ficam no piso, no
 * meio de cada parede lateral.
 */
public class ControlRoomBlock extends ComputerBlock {
    public static final MapCodec<ControlRoomBlock> CODEC = simpleCodec(ControlRoomBlock::new);
    public static final int WIDTH_CELLS = 9;
    public static final int HEIGHT_CELLS = 5;
    public static final int DEPTH_CELLS = 7;
    private static final int HALF_W = 4;
    private static final int HALF_D = 3;

    public ControlRoomBlock(Properties properties) {
        super(properties, Tier.HIGH_VOLTAGE);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    private enum Kind { SOLID, HOLLOW, PORT }

    private static Kind kind(int lx, int lz, int y) {
        if (y == 0 && Math.abs(lx) == HALF_W && lz == 0) {
            return Kind.PORT;
        }
        boolean door = lz == -HALF_D && lx == 0 && (y == 1 || y == 2);
        if (door) {
            return Kind.HOLLOW;
        }
        boolean shell = y == 0 || y == HEIGHT_CELLS - 1 || Math.abs(lx) == HALF_W || Math.abs(lz) == HALF_D;
        boolean desk = y == 1 && lz == 2 && Math.abs(lx) <= 3;
        return shell || desk ? Kind.SOLID : Kind.HOLLOW;
    }

    @Override
    public List<BlockPos> structureCells(BlockPos base, BlockState state) {
        Direction facing = state.getValue(FACING);
        List<BlockPos> cells = new ArrayList<>(WIDTH_CELLS * HEIGHT_CELLS * DEPTH_CELLS);
        for (int y = 0; y < HEIGHT_CELLS; y++) {
            for (int lx = -HALF_W; lx <= HALF_W; lx++) {
                for (int lz = -HALF_D; lz <= HALF_D; lz++) {
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
        int[] local = CableBlock.unrotate(base, state.getValue(FACING), cell);
        return local[2] >= 0 && local[2] < HEIGHT_CELLS && Math.abs(local[0]) <= HALF_W && Math.abs(local[1]) <= HALF_D
                && (local[0] != 0 || local[1] != 0 || local[2] != 0);
    }

    @Override
    public List<BlockPos> portCells(BlockPos base, BlockState state) {
        Direction facing = state.getValue(FACING);
        return List.of(CableBlock.rotate(base, facing, -HALF_W, 0, 0), CableBlock.rotate(base, facing, HALF_W, 0, 0));
    }

    @Override
    public void ensureStructure(Level level, BlockPos pos, BlockState state) {
        if (PowerStructureBlock.healing) {
            return;
        }
        Direction facing = state.getValue(FACING);
        PowerStructureBlock.healing = true;
        try {
            for (int y = 0; y < HEIGHT_CELLS; y++) {
                for (int lx = -HALF_W; lx <= HALF_W; lx++) {
                    for (int lz = -HALF_D; lz <= HALF_D; lz++) {
                        if (lx == 0 && lz == 0 && y == 0) {
                            continue;
                        }
                        BlockPos cell = CableBlock.rotate(pos, facing, lx, lz, y);
                        if (!level.isLoaded(cell)) {
                            continue;
                        }
                        Kind kind = kind(lx, lz, y);
                        Block wanted = switch (kind) {
                            case PORT -> ModBlocks.COMPUTER_PORT.get();
                            case SOLID -> ModBlocks.COMPUTER_STRUCTURE.get();
                            case HOLLOW -> ModBlocks.STRUCTURE_HOLLOW.get();
                        };
                        BlockState current = level.getBlockState(cell);
                        if (current.getBlock() != wanted && (current.canBeReplaced() || current.getBlock() instanceof PowerStructureBlock)) {
                            level.setBlock(cell, wanted.defaultBlockState(),
                                    kind == Kind.PORT ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
            }
        } finally {
            PowerStructureBlock.healing = false;
        }
    }

    /** Quem ficou preso no piso ao posicionar aparece dentro da sala. */
    @Override
    protected void afterPlaced(Level level, BlockPos pos, BlockState state) {
        PowerStructureBlock.relocateStuckPlayers(level, new AABB(pos).inflate(WIDTH_CELLS, HEIGHT_CELLS, DEPTH_CELLS),
                Vec3.atBottomCenterOf(pos.above()));
    }
}
