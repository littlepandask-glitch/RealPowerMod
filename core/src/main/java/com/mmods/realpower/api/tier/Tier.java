package com.mmods.realpower.api.tier;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** Tiers de evolucao do mod. Cada tier define eficiencia e taxa maxima de transferencia. */
public enum Tier implements StringRepresentable {
    BASIC("basic", 1, 0.50, 512L),
    INDUSTRIAL("industrial", 2, 0.68, 4_096L),
    HIGH_VOLTAGE("high_voltage", 3, 0.80, 65_536L),
    ADVANCED("advanced", 4, 0.90, 1_048_576L),
    FUTURE("future", 5, 0.97, 16_777_216L);

    public static final Codec<Tier> CODEC = StringRepresentable.fromEnum(Tier::values);

    private final String id;
    private final int level;
    private final double efficiency;
    private final long maxTransferPerTick;

    Tier(String id, int level, double efficiency, long maxTransferPerTick) {
        this.id = id;
        this.level = level;
        this.efficiency = efficiency;
        this.maxTransferPerTick = maxTransferPerTick;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** 1 (basico) a 5 (futuro). */
    public int level() {
        return level;
    }

    /** Fracao do rendimento teorico maximo que equipamentos deste tier alcancam (0 a 1). */
    public double efficiency() {
        return efficiency;
    }

    /** Taxa maxima de transferencia (FE/tick) de cabos e conexoes deste tier. */
    public long maxTransferPerTick() {
        return maxTransferPerTick;
    }

    public boolean atLeast(Tier other) {
        return level >= other.level;
    }

    public Component displayName() {
        return Component.translatable("tier.realpower." + id);
    }
}
