package com.mmods.realpower.cable;

import com.mmods.realpower.api.tier.Tier;
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
    TOWER("transmission_tower", 14, 160, 0.08, 36),
    /** Transformador elevador: ponte entre geradores/cabos e a linha aerea; liga-se por fio a postes e torres. */
    TRANSFORMER("transformer", 8, 48, 0.1, 1);

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

    /**
     * Dimensoes (largura, profundidade, altura) em blocos do conjunto multibloco. Poste e torre sao colunas; o
     * transformador cresce com o tier. Largura e profundidade sao impares (o bloco base fica no centro).
     */
    public int[] size(Tier tier) {
        if (this != TRANSFORMER) {
            return new int[]{1, 1, height};
        }
        return switch (tier) {
            case BASIC, INDUSTRIAL -> new int[]{3, 3, 3};
            case HIGH_VOLTAGE -> new int[]{5, 3, 4};
            case ADVANCED -> new int[]{7, 5, 5};
            case FUTURE -> new int[]{9, 5, 6};
        };
    }

    /** Altura (m) onde os fios se prendem, acima da base. */
    public double anchorHeight(Tier tier) {
        return this == TRANSFORMER ? size(tier)[2] + 1.3 : height;
    }

    /** Poste e torre so se ligam ao longo do eixo das travessas (nunca de lado). */
    public boolean linksAlongAxisOnly() {
        return this == POLE || this == TOWER;
    }

    public boolean overhead() {
        return linkRange > 0;
    }

    /** Cabo e rele tem bracos para cima e para baixo; poste e torre sao verticais. */
    public boolean verticalArms() {
        return !overhead();
    }
}
