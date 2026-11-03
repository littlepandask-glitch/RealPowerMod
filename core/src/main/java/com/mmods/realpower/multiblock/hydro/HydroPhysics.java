package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.api.tier.Tier;

/**
 * Fisica simplificada de uma usina de barragem: {@code P = rho * g * Q * H * eta}. A vazao disponivel cresce com a area
 * do lago (rio represado) ate a vazao nominal da turbina; a queda util fica entre o minimo e o maximo de projeto do tier.
 */
public final class HydroPhysics {
    public static final double WATER_DENSITY = 1000.0;
    public static final double GRAVITY = 9.81;
    /**
     * Vazao (m3/s) que cada bloco de superficie do lago fornece. Calibrada para que o tanque maximo por casa de forca
     * (64 x 64 = 4096 blocos, ver {@link TankPlan#MAX_SIDE}) sustente a vazao nominal do tier avancado (700 m3/s).
     */
    public static final double FLOW_PER_SURFACE_BLOCK = 0.175;

    /** Dados de projeto de uma turbina: vazao nominal (m3/s) e faixa de queda util (m). */
    // Queda minima: o tanque de uma casa de forca (profundidade = altura da unidade) ja a alcanca; lagos maiores dao mais queda.
    public record Spec(double ratedFlow, double minHead, double maxHead) {
    }

    private HydroPhysics() {
    }

    public static Spec spec(Tier tier) {
        return switch (tier) {
            case BASIC -> new Spec(8.0, 4.0, 20.0);
            case INDUSTRIAL -> new Spec(25.0, 6.0, 40.0);
            case HIGH_VOLTAGE -> new Spec(120.0, 9.0, 80.0);
            case ADVANCED -> new Spec(700.0, 12.0, 120.0);
            case FUTURE -> new Spec(1500.0, 40.0, 160.0);
        };
    }

    /** Vazao turbinada (m3/s): o que o lago fornece, limitado pela turbina. */
    public static double flow(Tier tier, double lakeSurfaceBlocks) {
        return Math.min(spec(tier).ratedFlow(), Math.max(0.0, lakeSurfaceBlocks) * FLOW_PER_SURFACE_BLOCK);
    }

    /** Queda util (m): 0 abaixo do minimo e limitada ao maximo de projeto. */
    public static double usableHead(Tier tier, double rawHead) {
        Spec spec = spec(tier);
        return rawHead < spec.minHead() ? 0.0 : Math.min(rawHead, spec.maxHead());
    }

    /** Potencia eletrica (W) com a queda bruta e a area do lago medidas. */
    public static double watts(Tier tier, double rawHead, double lakeSurfaceBlocks) {
        return WATER_DENSITY * GRAVITY * flow(tier, lakeSurfaceBlocks) * usableHead(tier, rawHead) * tier.efficiency();
    }
}
