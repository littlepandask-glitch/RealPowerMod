package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.multiblock.ValidationResult;
import com.mmods.realpower.registry.ModBlocks;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Varredura incremental que compara o mundo com o {@link TurbineBlueprint}. Cada tick le no
 * maximo o orcamento de blocos; o resultado lista quantos blocos faltam de cada tipo.
 */
public class WindTurbineScan implements StructureScan {
    private final Level level;
    private final List<TurbineBlueprint.Part> parts;
    private final EnumMap<TurbineBlueprint.Kind, Integer> missing = new EnumMap<>(TurbineBlueprint.Kind.class);
    private int cursor;
    private boolean unloaded;
    private ValidationResult result = ValidationResult.invalid(List.of());

    public WindTurbineScan(Level level, BlockPos controller, Direction facing) {
        this.level = level;
        this.parts = TurbineBlueprint.parts(controller, facing);
        for (TurbineBlueprint.Kind kind : TurbineBlueprint.Kind.values()) {
            missing.put(kind, 0);
        }
    }

    @Override
    public boolean advance(int blockBudget) {
        for (int i = 0; i < blockBudget && cursor < parts.size() && !unloaded; i++) {
            TurbineBlueprint.Part part = parts.get(cursor++);
            if (!level.isLoaded(part.pos())) {
                unloaded = true;
                break;
            }
            if (!matches(level.getBlockState(part.pos()), part.kind())) {
                missing.merge(part.kind(), 1, Integer::sum);
            }
        }
        if (unloaded) {
            return true;
        }
        if (cursor < parts.size()) {
            return false;
        }
        finish();
        return true;
    }

    private static boolean matches(BlockState state, TurbineBlueprint.Kind kind) {
        return state.is(blockFor(kind));
    }

    public static Block blockFor(TurbineBlueprint.Kind kind) {
        return switch (kind) {
            case BASE -> ModBlocks.TURBINE_BASE.get();
            case CASING -> ModBlocks.TURBINE_CASING.get();
            case LIGHT -> ModBlocks.INDUSTRIAL_LIGHT.get();
            case FAIRING -> ModBlocks.ROTOR_FAIRING.get();
            case BLADE -> ModBlocks.TURBINE_BLADE.get();
        };
    }

    private void finish() {
        List<Component> problems = new ArrayList<>();
        for (TurbineBlueprint.Kind kind : TurbineBlueprint.Kind.values()) {
            int count = missing.get(kind);
            if (count > 0) {
                problems.add(Component.translatable("message.realpower.wind.missing", count,
                        Component.translatable("kind.realpower." + kind.name().toLowerCase())));
            }
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

    /** Total de blocos que faltam (ou estao errados) no projeto. */
    public int missingTotal() {
        return missing.values().stream().mapToInt(Integer::intValue).sum();
    }
}
