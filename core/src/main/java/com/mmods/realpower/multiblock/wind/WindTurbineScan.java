package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.multiblock.ValidationResult;
import com.mmods.realpower.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Varredura parametrica de uma turbina eolica:
 * <ul>
 *   <li>torre: coluna de {@code wind_tower} abaixo do controlador, apoiada em solo solido;</li>
 *   <li>cubo: um {@code wind_blade} a frente do controlador;</li>
 *   <li>pas: bracos de {@code wind_blade} saindo do cubo (cima, baixo, esquerda, direita).</li>
 * </ul>
 * O raio do rotor e a altura da torre vem do que o jogador construiu, sem planta fixa.
 */
public class WindTurbineScan implements StructureScan {
    public static final int MIN_TOWER_HEIGHT = 10;
    public static final int MIN_BLADE_LENGTH = 5;
    public static final int MIN_BLADES = 3;
    public static final int MAX_TOWER_HEIGHT = 320;
    public static final int MAX_BLADE_LENGTH = 128;

    private enum Phase { TOWER, HUB, ARMS, DONE }

    private final Level level;
    private final BlockPos origin;
    private final Direction[] armDirections;

    private Phase phase = Phase.TOWER;
    private BlockPos cursor;
    private BlockPos hubPos;
    private int armIndex;
    private boolean unloaded;

    private int towerHeight;
    private boolean grounded;
    private boolean hasHub;
    private final int[] armLengths = new int[4];

    private ValidationResult result = ValidationResult.invalid(List.of());
    private double bladeRadius;

    public WindTurbineScan(Level level, BlockPos origin, Direction facing) {
        this.level = level;
        this.origin = origin;
        this.armDirections = new Direction[] {
                Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()
        };
        this.hubPos = origin.relative(facing);
    }

    @Override
    public boolean advance(int blockBudget) {
        for (int i = 0; i < blockBudget && phase != Phase.DONE; i++) {
            step();
        }
        return phase == Phase.DONE;
    }

    private void step() {
        switch (phase) {
            case TOWER -> stepTower();
            case HUB -> stepHub();
            case ARMS -> stepArms();
            case DONE -> { }
        }
    }

    private void stepTower() {
        if (cursor == null) {
            cursor = origin.below();
        }
        if (!level.isLoaded(cursor)) {
            abortUnloaded();
            return;
        }
        BlockState state = level.getBlockState(cursor);
        if (state.is(ModBlocks.WIND_TOWER.get()) && towerHeight < MAX_TOWER_HEIGHT) {
            towerHeight++;
            cursor = cursor.below();
        } else {
            grounded = state.isFaceSturdy(level, cursor, Direction.UP);
            phase = Phase.HUB;
        }
    }

    private void stepHub() {
        if (!level.isLoaded(hubPos)) {
            abortUnloaded();
            return;
        }
        hasHub = level.getBlockState(hubPos).is(ModBlocks.WIND_BLADE.get());
        if (!hasHub) {
            finish();
            return;
        }
        phase = Phase.ARMS;
        armIndex = 0;
        cursor = hubPos.relative(armDirections[0]);
    }

    private void stepArms() {
        if (!level.isLoaded(cursor)) {
            abortUnloaded();
            return;
        }
        if (level.getBlockState(cursor).is(ModBlocks.WIND_BLADE.get()) && armLengths[armIndex] < MAX_BLADE_LENGTH) {
            armLengths[armIndex]++;
            cursor = cursor.relative(armDirections[armIndex]);
            return;
        }
        armIndex++;
        if (armIndex == armLengths.length) {
            finish();
        } else {
            cursor = hubPos.relative(armDirections[armIndex]);
        }
    }

    private void abortUnloaded() {
        unloaded = true;
        phase = Phase.DONE;
    }

    private void finish() {
        List<Component> problems = new ArrayList<>();
        if (towerHeight < MIN_TOWER_HEIGHT) {
            problems.add(Component.translatable("message.realpower.wind.tower_too_short", towerHeight, MIN_TOWER_HEIGHT));
        }
        if (!grounded) {
            problems.add(Component.translatable("message.realpower.wind.tower_not_grounded"));
        }
        if (!hasHub) {
            problems.add(Component.translatable("message.realpower.wind.no_hub"));
        } else {
            int usable = 0;
            int sum = 0;
            int longest = 0;
            for (int length : armLengths) {
                if (length >= MIN_BLADE_LENGTH) {
                    usable++;
                    sum += length;
                }
                longest = Math.max(longest, length);
            }
            if (usable < MIN_BLADES) {
                problems.add(Component.translatable("message.realpower.wind.blades_missing",
                        usable, MIN_BLADES, MIN_BLADE_LENGTH));
            } else {
                bladeRadius = (double) sum / usable;
            }
            if (longest > towerHeight) {
                problems.add(Component.translatable("message.realpower.wind.blade_hits_ground"));
            }
        }
        result = problems.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(problems);
        phase = Phase.DONE;
    }

    @Override
    public boolean isUnloaded() {
        return unloaded;
    }

    @Override
    public ValidationResult result() {
        return result;
    }

    /** Comprimentos dos 4 bracos de pa: cima, baixo, direita e esquerda (relativos a frente do controlador). */
    public int[] armLengths() {
        return armLengths.clone();
    }

    /** Altura da torre em metros (blocos de torre abaixo do controlador). */
    public int towerHeight() {
        return towerHeight;
    }

    /** Raio medio do rotor em metros; 0 se a estrutura for invalida. */
    public double bladeRadius() {
        return result.valid() ? bladeRadius : 0.0;
    }
}
