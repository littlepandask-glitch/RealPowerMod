package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.api.tier.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Modelo fisico simplificado de turbina eolica. 1 bloco = 1 metro. */
public final class WindTurbinePhysics {
    public static final double AIR_DENSITY = 1.225;      // kg/m3
    public static final double BETZ_LIMIT = 0.593;       // maximo teorico de extracao de energia do vento
    public static final double CUT_IN_SPEED = 3.0;       // m/s: abaixo disso nao gira
    public static final double RATED_SPEED = 12.0;       // m/s: potencia nominal; acima disso e limitada
    public static final double CUT_OUT_SPEED = 25.0;     // m/s: acima disso freia por seguranca

    private WindTurbinePhysics() {
    }

    /** Coeficiente de potencia (Cp): fracao do limite de Betz que o tier consegue extrair. */
    public static double powerCoefficient(Tier tier) {
        return BETZ_LIMIT * tier.efficiency();
    }

    /** Velocidade do vento (m/s) no cubo do rotor, a {@code hubHeight} metros do solo. */
    public static double windSpeed(Level level, BlockPos pos, double hubHeight) {
        if (!level.dimensionType().hasSkyLight()) {
            return 0.0;
        }
        double base = level.isThundering() ? 14.0 : level.isRaining() ? 10.0 : 6.5;
        double t = level.getGameTime();
        double phase = pos.asLong() % 10007L;
        double noise = 0.5 * Math.sin(t / 2400.0 + phase)
                + 0.3 * Math.sin(t / 613.0 + phase * 1.7)
                + 0.2 * Math.sin(t / 137.0 + phase * 2.3);
        double speedAt10m = base * (1.0 + 0.35 * noise);
        // Lei de potencia do perfil de vento: velocidade cresce com a altura
        return speedAt10m * Math.pow(Math.max(hubHeight, 1.0) / 10.0, 0.143);
    }

    /** Potencia eletrica (W) para um rotor de raio {@code radius} (m), vento {@code speed} (m/s) e coeficiente {@code cp}. */
    public static double powerWatts(double radius, double speed, double cp) {
        if (speed < CUT_IN_SPEED || speed > CUT_OUT_SPEED) {
            return 0.0;
        }
        double effective = Math.min(speed, RATED_SPEED);
        double sweptArea = Math.PI * radius * radius;
        return 0.5 * AIR_DENSITY * sweptArea * effective * effective * effective * cp;
    }
}
