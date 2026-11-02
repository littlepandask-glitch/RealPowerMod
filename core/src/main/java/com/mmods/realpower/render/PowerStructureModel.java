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

    // ---------- computador central ----------

    /** Rack de 3 x 1 x 2 m com a frente em -z e uma porta de dados visivel em cada ponta, no chao. */
    public static Mesh computer(float[] accent) {
        Mesh.Builder b = new Mesh.Builder();
        b.box(v(0, 0.1, 0), 3.0, 0.2, 1.0, CONCRETE);
        b.box(v(0, 1.1, 0), 2.9, 1.8, 0.9, DARK);
        b.box(v(0, 2.02, 0), 2.94, 0.04, 0.94, STEEL);
        for (double y = 0.5; y < 1.9; y += 0.36) {
            for (int col = -1; col <= 1; col++) {
                b.box(v(col * 0.96, y, -0.46), 0.9, 0.3, 0.06, STEEL);
                b.box(v(col * 0.96 + 0.3, y, -0.5), 0.2, 0.04, 0.02, accent);
                b.box(v(col * 0.96 - 0.3, y, -0.5), 0.3, 0.06, 0.02, DARK);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            b.box(v(side * 1.2, 0.55, 0), 0.6, 0.7, 0.7, STEEL);
            b.box(v(side * 1.52, 0.55, 0), 0.12, 0.5, 0.5, CERAMIC);
            b.box(v(side * 1.58, 0.55, 0), 0.06, 0.26, 0.26, accent);
        }
        return b.build();
    }

    // ---------- banco de capacitores ----------

    /**
     * Galpao de baterias de w x d x h m (centro da base na origem, frente em -z): soleira de concreto, gabinete com
     * nervuras, barras de LED na frente que acendem {@code lit} de {@code segments} segmentos e uma porta de energia
     * visivel no chao no centro de cada face lateral.
     */
    public static Mesh capacitorBank(int w, int d, int h, int lit, int segments, float[] accent) {
        Mesh.Builder b = new Mesh.Builder();
        double bodyH = h - 0.3;
        double halfW = w / 2.0;
        double halfD = d / 2.0;
        b.box(v(0, 0.15, 0), w, 0.3, d, CONCRETE);
        b.box(v(0, 0.3 + bodyH / 2, 0), w - 0.2, bodyH, d - 0.2, DARK);
        b.box(v(0, h + 0.04, 0), w - 0.1, 0.08, d - 0.1, STEEL);
        // nervuras verticais nas quatro faces
        for (double x = -halfW + 0.7; x < halfW - 0.5; x += 0.8) {
            b.box(v(x, 0.3 + bodyH / 2, -halfD + 0.06), 0.12, bodyH, 0.08, STEEL);
            b.box(v(x, 0.3 + bodyH / 2, halfD - 0.06), 0.12, bodyH, 0.08, STEEL);
        }
        for (double z = -halfD + 0.7; z < halfD - 0.5; z += 0.8) {
            b.box(v(-halfW + 0.06, 0.3 + bodyH / 2, z), 0.08, bodyH, 0.12, STEEL);
            b.box(v(halfW - 0.06, 0.3 + bodyH / 2, z), 0.08, bodyH, 0.12, STEEL);
        }
        // respiros no teto
        for (double x = -halfW + 0.9; x < halfW - 0.8; x += 1.0) {
            b.box(v(x, h + 0.14, 0), 0.5, 0.12, d - 1.2, DARK);
        }
        // barras de LED na frente (-z): fundo escuro e segmentos acesos de baixo para cima
        double barBottom = 1.2;
        double barTop = h - 0.3;
        double segmentH = 0.2;
        int total = Math.max(1, (int) Math.floor((barTop - barBottom) / segmentH));
        float[] off = {0.14f, 0.16f, 0.2f};
        for (double x = -halfW + 1.0; x < halfW - 0.8; x += 1.0) {
            b.box(v(x, (barBottom + barTop) / 2, -halfD + 0.02), 0.34, barTop - barBottom + 0.1, 0.05, STEEL);
            for (int i = 0; i < total; i++) {
                boolean on = i * segments < lit * total;
                b.box(v(x, barBottom + segmentH * (i + 0.5), -halfD - 0.01), 0.22, segmentH - 0.05, 0.03, on ? accent : off);
            }
        }
        // portas de energia nas quatro faces laterais, no nivel do chao
        for (int side = -1; side <= 1; side += 2) {
            b.box(v(side * (halfW - 0.4), 0.5, 0), 0.8, 0.8, 0.8, STEEL);
            b.box(v(side * (halfW - 0.06), 0.5, 0), 0.12, 0.64, 0.64, CERAMIC);
            b.box(v(side * halfW, 0.5, 0), 0.06, 0.34, 0.34, accent);
            b.box(v(0, 0.5, side * (halfD - 0.4)), 0.8, 0.8, 0.8, STEEL);
            b.box(v(0, 0.5, side * (halfD - 0.06)), 0.64, 0.64, 0.12, CERAMIC);
            b.box(v(0, 0.5, side * halfD), 0.34, 0.34, 0.06, accent);
        }
        return b.build();
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

    // ---------- transformador ----------

    /** Posicao x das buchas (3 condutores) de um transformador de largura {@code w}. */
    private static double bushingSpacing(int w) {
        return w * 0.3;
    }

    /** Transformador elevador w x d x h metros, centrado na origem; o eixo x e o da largura. */
    public static Mesh transformer(int w, int d, int h, float[] accent) {
        Mesh.Builder b = new Mesh.Builder();
        b.box(v(0, 0.15, 0), w, 0.3, d, CONCRETE);
        double tankW = w - 1.6;
        double tankD = d - 0.6;
        double tankTop = h - 0.9;
        b.box(v(0, 0.3 + (tankTop - 0.3) / 2, 0), tankW, tankTop - 0.3, tankD, DARK);
        b.box(v(0, 0.3 + (tankTop - 0.3) * 0.5, 0), tankW + 0.06, 0.2, tankD + 0.06, accent);
        b.box(v(0, tankTop + 0.05, 0), tankW + 0.2, 0.1, tankD + 0.2, STEEL);
        // radiadores de aletas nas duas extremidades
        double radiatorHeight = tankTop - 1.5;
        for (int side = -1; side <= 1; side += 2) {
            double x = side * (tankW / 2 + 0.45);
            b.box(v(x, 1.4 + radiatorHeight / 2, 0), 0.9, radiatorHeight, tankD - 0.2, DARK);
            for (double z = -(tankD / 2 - 0.3); z <= tankD / 2 - 0.3 + 1e-6; z += 0.3) {
                b.box(v(x + side * 0.1, 1.4 + radiatorHeight / 2, z), 1.0, radiatorHeight - 0.1, 0.1, STEEL);
            }
            // porta de energia visivel: caixa de conexao no nivel do chao, com o conector luminoso para fora
            b.box(v(side * (w / 2.0 - 0.4), 0.7, 0), 0.8, 0.8, 0.8, STEEL);
            b.box(v(side * (w / 2.0 - 0.06), 0.7, 0), 0.12, 0.64, 0.64, CERAMIC);
            b.box(v(side * (w / 2.0 - 0.0), 0.7, 0), 0.06, 0.34, 0.34, accent);
        }
        // portas nas faces transversais (frente e fundo)
        for (int side = -1; side <= 1; side += 2) {
            b.box(v(0, 0.7, side * (d / 2.0 - 0.4)), 0.8, 0.8, 0.8, STEEL);
            b.box(v(0, 0.7, side * (d / 2.0 - 0.06)), 0.64, 0.64, 0.12, CERAMIC);
            b.box(v(0, 0.7, side * d / 2.0), 0.34, 0.34, 0.06, accent);
        }
        // conservador de oleo sobre o tanque
        b.beam(v(-tankW * 0.3, tankTop + 0.45, -tankD * 0.25), v(tankW * 0.3, tankTop + 0.45, -tankD * 0.25), 0.55, STEEL);
        // buchas de porcelana, uma por condutor
        double s = bushingSpacing(w);
        for (int i = -1; i <= 1; i++) {
            double x = i * s;
            b.box(v(x, tankTop + 0.2, 0), 0.5, 0.2, 0.5, STEEL);
            double top = h + 1.2;
            b.beam(v(x, tankTop + 0.3, 0), v(x, top, 0), 0.28, CERAMIC);
            for (double y = tankTop + 0.45; y < top; y += 0.2) {
                b.box(v(x, y, 0), 0.5, 0.05, 0.5, CERAMIC);
            }
            b.box(v(x, top + 0.07, 0), 0.2, 0.14, 0.2, accent);
        }
        return b.build();
    }

    /** Pontos de fixacao dos 3 fios do transformador, no topo das buchas. */
    public static double[][] transformerAnchors(int w, int h) {
        double s = bushingSpacing(w);
        double y = h + 1.3;
        return new double[][]{{-s, y, 0}, {0, y, 0}, {s, y, 0}};
    }
}
