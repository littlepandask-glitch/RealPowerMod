package com.mmods.realpower.energy;

/** Escala entre potencia fisica e a energia do jogo: 1 FE/tick equivale a 1 kW. */
public final class PowerScale {
    public static final double WATTS_PER_FE_TICK = 1000.0;

    private PowerScale() {
    }

    public static double toFePerTick(double watts) {
        return watts / WATTS_PER_FE_TICK;
    }
}
