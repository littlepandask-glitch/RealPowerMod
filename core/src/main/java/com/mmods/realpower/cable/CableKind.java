package com.mmods.realpower.cable;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Tipo de no da rede eletrica. Cabo e rele ficam no chao (a perda cresce com o comprimento da corrida; o
 * rele reforca o sinal e divide a corrida). Poste e torre sao linhas aereas: ligam-se automaticamente a
 * outros postes/torres no alcance, com perda por metro bem menor que a do cabo enterrado.
 */
public enum CableKind implements StringRepresentable {
    CABLE("cable", 0, 1.0),
    RELAY("relay", 0, 1.0),
    POLE("power_pole", 24, 0.25),
    TOWER("transmission_tower", 96, 0.08);

    public static final Codec<CableKind> CODEC = StringRepresentable.fromEnum(CableKind::values);

    private final String id;
    private final int linkRange;
    private final double overheadFactor;

    CableKind(String id, int linkRange, double overheadFactor) {
        this.id = id;
        this.linkRange = linkRange;
        this.overheadFactor = overheadFactor;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Alcance (blocos) da linha aerea entre este no e o proximo; 0 se nao for linha aerea. */
    public int linkRange() {
        return linkRange;
    }

    /** Fracao da perda por metro de um cabo enterrado que a linha aerea sofre. */
    public double overheadFactor() {
        return overheadFactor;
    }

    public boolean overhead() {
        return linkRange > 0;
    }

    /** Cabo e rele tem bracos para cima e para baixo; poste e torre sao verticais. */
    public boolean verticalArms() {
        return !overhead();
    }
}
