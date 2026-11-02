package com.mmods.realpower.render;

/**
 * Estado de renderizacao de uma turbina no cliente: malhas em cache (so reconstruidas quando as
 * dimensoes mudam) e angulo do rotor, que avanca de acordo com a velocidade do vento.
 */
public final class TurbineRenderState {
    /** Razao entre a velocidade da ponta da pa e a do vento (tipico de turbinas de 3 pas). */
    private static final double TIP_SPEED_RATIO = 7.0;
    private static final double MAX_ANGULAR_SPEED = 2.4; // rad/s, limite por seguranca visual

    private TurbineModel.Dims dims;
    private Mesh staticMesh;
    private Mesh rotorMesh;
    private double angle;
    private double lastTime = Double.NaN;

    public Mesh staticMesh(TurbineModel.Dims wanted) {
        refresh(wanted);
        return staticMesh;
    }

    public Mesh rotorMesh(TurbineModel.Dims wanted) {
        refresh(wanted);
        return rotorMesh;
    }

    private void refresh(TurbineModel.Dims wanted) {
        if (!wanted.equals(dims)) {
            dims = wanted;
            staticMesh = TurbineModel.buildStatic(wanted);
            rotorMesh = TurbineModel.buildRotor(wanted);
        }
    }

    /**
     * Avanca o rotor ate {@code timeTicks} (tempo de jogo com parte fracionaria) e devolve o angulo em radianos.
     *
     * @param tipRadius raio da ponta da pa em metros
     */
    public double advance(double timeTicks, double windSpeed, double tipRadius) {
        if (!Double.isNaN(lastTime)) {
            double dtSeconds = Math.max(0.0, Math.min(timeTicks - lastTime, 20.0)) / 20.0;
            double omega = windSpeed < 3.0 || windSpeed > 25.0 ? 0.0
                    : Math.min(MAX_ANGULAR_SPEED, TIP_SPEED_RATIO * Math.min(windSpeed, 12.0) / Math.max(tipRadius, 1.0));
            angle = (angle + omega * dtSeconds) % (2.0 * Math.PI);
        }
        lastTime = timeTicks;
        return angle;
    }
}
