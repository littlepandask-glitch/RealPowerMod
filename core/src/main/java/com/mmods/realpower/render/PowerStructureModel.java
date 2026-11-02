package com.mmods.realpower.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Malhas 3D em escala real (1 unidade = 1 m) de postes e torres de transmissao. Origem no centro da base, y para cima,
 * x lateral (travessas) e z ao longo da linha. Sem dependencia do Minecraft.
 */
public final class PowerStructureModel {
    private static final float[] STEEL = {0.36f, 0.38f, 0.42f};
    private static final float[] DARK = {0.22f, 0.23f, 0.26f};
    private static final float[] WOOD = {0.42f, 0.30f, 0.18f};
    private static final float[] CERAMIC = {0.82f, 0.84f, 0.86f};
    private static final float[] CONCRETE = {0.52f, 0.52f, 0.54f};

    private PowerStructureModel() {
    }

    private static Vec v(double x, double y, double z) {
        return new Vec(x, y, z);
    }

    // ---------- poste ----------

    public static Mesh pole(double h, boolean wood, float[] accent) {
        Mesh.Builder b = new Mesh.Builder();
        float[] body = wood ? WOOD : STEEL;
        b.beam(v(0, 0, 0), v(0, h * 0.5, 0), 0.38, body);
        b.beam(v(0, h * 0.5, 0), v(0, h, 0), 0.28, body);
        b.box(v(0, 0.2, 0), 0.9, 0.4, 0.9, CONCRETE);
        double arm = h - 1.0;
        b.beam(v(-1.4, arm, 0), v(1.4, arm, 0), 0.16, body);
        b.beam(v(0, arm - 1.3, 0), v(-0.95, arm, 0), 0.1, body);
        b.beam(v(0, arm - 1.3, 0), v(0.95, arm, 0), 0.1, body);
        for (double x : new double[]{-1.15, 1.15}) {
            insulatorUp(b, x, arm, 0.6, accent);
        }
        insulatorUp(b, 0, h, 0.6, accent);
        return b.build();
    }

    /** Pontos de fixacao dos fios (x, y, z), na ordem esquerda, topo, direita. */
    public static double[][] poleAnchors(double h) {
        return new double[][]{{-1.15, h - 0.35, 0}, {0, h + 0.65, 0}, {1.15, h - 0.35, 0}};
    }

    private static void insulatorUp(Mesh.Builder b, double x, double y, double length, float[] accent) {
        b.beam(v(x, y, 0), v(x, y + length, 0), 0.16, CERAMIC);
        for (double d = 0.12; d < length; d += 0.17) {
            b.box(v(x, y + d, 0), 0.3, 0.05, 0.3, CERAMIC);
        }
        b.box(v(x, y + length + 0.05, 0), 0.2, 0.1, 0.2, accent);
    }

    // ---------- torre ----------

    private static double halfWidth(double y, double taperTop) {
        return 4.0 - 3.0 * Math.min(1.0, y / taperTop);
    }

    public static Mesh tower(double h, float[] accent) {
        Mesh.Builder b = new Mesh.Builder();
        double taperTop = h * 0.6;
        List<Double> levels = new ArrayList<>();
        for (double y = 0; y < taperTop - 1e-6; y += 3.0) {
            levels.add(y);
        }
        levels.add(taperTop);
        for (double y = taperTop + 2.0; y < h - 1e-6; y += 2.0) {
            levels.add(y);
        }
        levels.add(h);
        int[][] corners = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
        for (int i = 0; i < levels.size(); i++) {
            double y = levels.get(i);
            double w = halfWidth(y, taperTop);
            for (int c = 0; c < 4; c++) {
                Vec p = v(corners[c][0] * w, y, corners[c][1] * w);
                Vec q = v(corners[(c + 1) % 4][0] * w, y, corners[(c + 1) % 4][1] * w);
                if (i > 0) {
                    b.beam(p, q, 0.16, STEEL);
                }
                if (i + 1 < levels.size()) {
                    double y1 = levels.get(i + 1);
                    double w1 = halfWidth(y1, taperTop);
                    Vec pu = v(corners[c][0] * w1, y1, corners[c][1] * w1);
                    Vec qu = v((corners[(c + 1) % 4][0]) * w1, y1, corners[(c + 1) % 4][1] * w1);
                    b.beam(p, pu, 0.4, DARK);
                    b.beam(p, qu, 0.12, STEEL);
                    b.beam(q, pu, 0.12, STEEL);
                }
            }
        }
        for (int c = 0; c < 4; c++) {
            b.box(v(corners[c][0] * 4.0, 0.3, corners[c][1] * 4.0), 1.3, 0.6, 1.3, CONCRETE);
            b.box(v(corners[c][0] * 4.0, 0.7, corners[c][1] * 4.0), 0.3, 0.2, 0.3, accent);
        }
        b.box(v(0, h + 0.1, 0), 2.3, 0.2, 2.3, DARK);
        b.beam(v(0, h, 0), v(0, h + 2.6, 0), 0.16, STEEL);
        b.box(v(0, h + 2.7, 0), 0.3, 0.2, 0.3, accent);
        double[][] arms = towerArms(h);
        for (double[] arm : arms) {
            double y = arm[0];
            double half = arm[1];
            for (int side = -1; side <= 1; side += 2) {
                boolean wired = side == arm[2];
                for (double z : new double[]{-0.85, 0.85}) {
                    b.beam(v(side * 1.0, y, z), v(side * half, y, z), 0.16, STEEL);
                    b.beam(v(side * 1.0, y + 1.1, z), v(side * half, y + 0.35, z), 0.12, STEEL);
                    int seg = 5;
                    for (int s = 0; s < seg; s++) {
                        double x0 = 1.0 + (half - 1.0) * s / seg;
                        double x1 = 1.0 + (half - 1.0) * (s + 1) / seg;
                        double t0 = 1.1 - 0.75 * s / seg;
                        double t1 = 1.1 - 0.75 * (s + 1) / seg;
                        if (s % 2 == 0) {
                            b.beam(v(side * x0, y, z), v(side * x1, y + t1, z), 0.1, STEEL);
                        } else {
                            b.beam(v(side * x0, y + t0, z), v(side * x1, y, z), 0.1, STEEL);
                        }
                    }
                }
                b.beam(v(side * half, y, -0.85), v(side * half, y, 0.85), 0.14, STEEL);
                if (!wired) {
                    continue;
                }
                // cadeia de isoladores pendurada na ponta
                double drop = 2.2;
                b.beam(v(side * half, y, 0), v(side * half, y - drop, 0), 0.16, CERAMIC);
                for (double d = 0.25; d < drop; d += 0.28) {
                    b.box(v(side * half, y - d, 0), 0.5, 0.07, 0.5, CERAMIC);
                }
                b.box(v(side * half, y - drop - 0.1, 0), 0.28, 0.2, 0.28, accent);
            }
        }
        return b.build();
    }

    /** Travessas da torre: {altura, meia-largura, lado do fio}; cada uma mais larga que a de cima. */
    private static double[][] towerArms(double h) {
        return new double[][]{{h - 3, 4.5, -1}, {h - 8, 6.0, 1}, {h - 13, 7.5, -1}};
    }

    /** Pontos de fixacao dos 3 fios da torre: um por travessa (lados alternados), de cima para baixo. */
    public static double[][] towerAnchors(double h) {
        double[][] arms = towerArms(h);
        double[][] out = new double[arms.length][];
        for (int i = 0; i < arms.length; i++) {
            out[i] = new double[]{arms[i][2] * arms[i][1], arms[i][0] - 2.5, 0};
        }
        return out;
    }
}
