package com.mmods.realpower.data;

import com.mmods.realpower.cable.PowerStructureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Equipamento que se deixa supervisionar (e talvez controlar) pelo computador central atraves de uma porta de dados. */
public interface DataDevice {
    /** Leitura instantanea do equipamento. */
    record Telemetry(boolean online, double powerWatts, long stored, long capacity) {
    }

    Component deviceLabel();

    Telemetry telemetry();

    /** Se aceita {@link #setEnabled} e {@link #setLimit}. */
    default boolean controllable() {
        return false;
    }

    default boolean enabled() {
        return true;
    }

    default void setEnabled(boolean enabled) {
    }

    /** Limite de saida em porcentagem (0-100). */
    default int limit() {
        return 100;
    }

    default void setLimit(int percent) {
    }

    /** Equipamento ligado ao bloco em {@code pos} (o proprio bloco ou a base do multibloco a que pertence), ou null. */
    static DataDevice at(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof DataDevice device) {
            return device;
        }
        if (level.getBlockState(pos).getBlock() instanceof PowerStructureBlock) {
            BlockPos base = PowerStructureBlock.findBase(level, pos);
            if (base != null && level.getBlockEntity(base) instanceof DataDevice device) {
                return device;
            }
        }
        return null;
    }
}
