package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.multiblock.ValidationResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Varredura incremental da barragem de uma usina. Mede o nivel do lago a partir da tomada d'agua, confere que a parede de
 * modulos {@link DamBlock} fecha o vale (cada linha, da base da usina ate o nivel da agua, vai de margem a margem e termina
 * em terreno solido) e conta a superficie do lago (que define a vazao).
 */
public class DamScan implements StructureScan {
    public static final int MAX_ROWS = 150;
    public static final int MAX_HALF_WIDTH = 200;
    public static final int AREA_CAP = 120_000;

    private enum Phase { WATER, WALL, AREA, DONE }

    private final Level level;
    private final BlockPos intake;
    private final Direction upstream;
    private final int baseY;
    private final Predicate<BlockPos> unitCell;
    private final Direction lateral;
    private final BlockPos wallCenter;

    private Phase phase = Phase.WATER;
    private int waterTop;
    private int row;
    private int side;
    private int offset;
    private int breachRows;
    private int firstBreach = Integer.MIN_VALUE;
    private boolean noWater;
    private boolean unloaded;
    private int area;
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private final Set<Long> visited = new HashSet<>();
    private ValidationResult result = ValidationResult.invalid(List.of());

    /**
     * @param intake   bloco logo a montante da tomada d'agua, onde o lago deve estar
     * @param upstream sentido do lago (de dentro da usina para a agua)
     * @param baseY    primeira linha da parede (chao da usina)
     * @param unitCell diz se uma posicao pertence a propria usina (conta como parede)
     */
    public DamScan(Level level, BlockPos intake, Direction upstream, int baseY, Predicate<BlockPos> unitCell) {
        this.level = level;
        this.intake = intake;
        this.upstream = upstream;
        this.baseY = baseY;
        this.unitCell = unitCell;
        this.lateral = upstream.getClockWise();
        this.wallCenter = intake.relative(upstream.getOpposite());
        this.waterTop = intake.getY();
        this.row = baseY;
    }

    private boolean isWater(BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    private boolean isWall(BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof DamBlock || unitCell.test(pos);
    }

    private static boolean isSolidTerrain(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && state.blocksMotion();
    }

    @Override
    public boolean advance(int blockBudget) {
        int budget = blockBudget;
        while (budget > 0 && phase != Phase.DONE && !unloaded) {
            switch (phase) {
                case WATER -> budget -= stepWater();
                case WALL -> budget -= stepWall();
                case AREA -> budget -= stepArea();
                default -> {
                }
            }
        }
        if (unloaded) {
            return true;
        }
        if (phase == Phase.DONE) {
            finish();
            return true;
        }
        return false;
    }

    private boolean load(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            unloaded = true;
            return false;
        }
        return true;
    }

    private int stepWater() {
        if (!load(intake)) {
            return 1;
        }
        if (!isWater(intake)) {
            noWater = true;
            phase = Phase.DONE;
            return 1;
        }
        BlockPos above = new BlockPos(intake.getX(), waterTop + 1, intake.getZ());
        if (waterTop - intake.getY() < MAX_ROWS && load(above) && isWater(above)) {
            waterTop++;
            return 1;
        }
        phase = Phase.WALL;
        return 1;
    }

    /** Um bloco da linha corrente; a linha e percorrida a partir do centro, primeiro para um lado e depois para o outro. */
    private int stepWall() {
        if (row > waterTop) {
            phase = Phase.AREA;
            queue.add(new BlockPos(intake.getX(), waterTop, intake.getZ()));
            visited.add(queue.peek().asLong());
            area = 1;
            return 1;
        }
        Direction dir = side == 0 ? lateral : lateral.getOpposite();
        int k = side == 0 ? offset : offset + 1;
        BlockPos pos = new BlockPos(wallCenter.getX(), row, wallCenter.getZ()).relative(dir, k);
        if (!load(pos)) {
            return 1;
        }
        boolean endOfRun = false;
        if (k <= MAX_HALF_WIDTH && isWall(pos)) {
            offset++;
        } else {
            endOfRun = true;
            if (k == 0 || k > MAX_HALF_WIDTH || !isSolidTerrain(level.getBlockState(pos))) {
                breach();
            }
        }
        if (endOfRun) {
            offset = 0;
            if (side == 0) {
                side = 1;
            } else {
                side = 0;
                row++;
            }
        }
        return 1;
    }

    private void breach() {
        breachRows++;
        if (firstBreach == Integer.MIN_VALUE) {
            firstBreach = row;
        }
    }

    private int stepArea() {
        if (queue.isEmpty() || area >= AREA_CAP) {
            phase = Phase.DONE;
            return 1;
        }
        BlockPos current = queue.poll();
        int reads = 1;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos next = current.relative(dir);
            reads++;
            if (!visited.contains(next.asLong()) && load(next) && isWater(next)) {
                visited.add(next.asLong());
                queue.add(next);
                area++;
            }
        }
        return reads;
    }

    private void finish() {
        List<Component> problems = new ArrayList<>();
        if (noWater) {
            problems.add(Component.translatable("message.realpower.dam.no_water"));
        } else if (breachRows > 0) {
            problems.add(Component.translatable("message.realpower.dam.breach", breachRows, firstBreach - baseY));
        }
        result = problems.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(problems);
    }

    @Override
    public boolean isUnloaded() {
        return unloaded;
    }

    @Override
    public ValidationResult result() {
        return result;
    }

    /** Y da superficie do lago (topo do bloco de agua mais alto da coluna). */
    public double lakeSurfaceY() {
        return waterTop + 1.0;
    }

    /** Blocos de superficie do lago ligados a tomada (com teto). */
    public int lakeArea() {
        return area;
    }
}
