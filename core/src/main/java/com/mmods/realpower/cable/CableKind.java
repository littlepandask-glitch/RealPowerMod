package com.mmods.realpower.cable;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Tipo de no da rede eletrica. Cabo e rele ficam no chao (a perda cresce com o comprimento da corrida; o
 * rele reforca o sinal e divide a corrida). Poste e torre sao linhas aereas: ligam-se automaticamente a
 * vizinhos mais proximos no alcance (sem atravessar outros), com perda por metro bem menor que a do cabo enterrado.
 */
public enum CableKind implements StringRepresentable {
    CABLE("cable", 0, 0, 1.0, 1),
    RELAY("relay", 0, 0, 1.0, 1),
    POLE("power_pole", 3, 48, 0.25, 12),
    TOWER("transmission_tower", 14, 160, 0.08, 36);

    public static final Codec<CableKind> CODEC = StringRepresentable.fromEnum(CableKind::values);

    private final String id;
    private final int minLink;
    private final int linkRange;
    private final double overheadFactor;
    private final int height;

    CableKind(String id, int minLink, int linkRange, double overheadFactor, int height) {
        this.height = height;
        this.minLink = minLink;
        this.id = id;
        this.linkRange = linkRange;
        this.overheadFactor = overheadFactor;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Distancia minima (blocos) entre duas estruturas para se ligarem: vizinhas coladas nao se conectam. */
    public int minLink() {
        return minLink;
    }

    /** Alcance (blocos) da linha aerea entre este no e o proximo; 0 se nao for linha aerea. */
    public int linkRange() {
        return linkRange;
    }

    /** Fracao da perda por metro de um cabo enterrado que a linha aerea sofre. */
    public double overheadFactor() {
        return overheadFactor;
    }

    /** Altura da estrutura em blocos (1 bloco = 1 m); poste e torre ocupam uma coluna acima do bloco base. */
    public int height() {
        return height;
    }

    public boolean overhead() {
        return linkRange > 0;
    }

    /** Cabo e rele tem bracos para cima e para baixo; poste e torre sao verticais. */
    public boolean verticalArms() {
        return !overhead();
    }
}
