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
     * Banco de capacitores de w x d x h m (centro da base na origem, frente em -z): estrutura de chapas com cantos
     * luminosos, cilindros de capacitores visiveis, painel de controle com raio e barras de LED na frente que acendem
     * conforme {@code lit} de {@code segments}, e uma porta de energia visivel no chao no centro de cada face lateral.
     */
    public static Mesh capacitorBank(int w, int d, int h, int lit, int segments, IndustrialStyle.Palette p) {
        Mesh.Builder b = new Mesh.Builder();
        float[] plate = p.plate();
        float[] shade = p.shade();
        double hw = w / 2.0;
        double hd = d / 2.0;
        double railY = h - 0.1;
        b.box(v(0, 0.15, 0), w, 0.3, d, plate);
        b.box(v(0, 0.33, 0), w - 0.3, 0.06, d - 0.3, shade);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                double x = sx * (hw - 0.15);
                double z = sz * (hd - 0.15);
                b.beam(v(x, 0.3, z), v(x, railY, z), 0.22, plate);
                IndustrialStyle.node(b, v(x, railY + 0.12, z), 0.3, p);
                IndustrialStyle.node(b, v(x, 0.5, z), 0.3, p);
            }
        }
        for (int sz = -1; sz <= 1; sz += 2) {
            b.beam(v(-(hw - 0.15), railY, sz * (hd - 0.15)), v(hw - 0.15, railY, sz * (hd - 0.15)), 0.16, shade);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            double x = sx * (hw - 0.15);
            b.beam(v(x, railY, -(hd - 0.15)), v(x, railY, hd - 0.15), 0.16, shade);
            b.beam(v(x, 0.5, -(hd - 0.15)), v(x, railY, hd - 0.15), 0.08, IndustrialStyle.STEEL);
            b.beam(v(x, 0.5, hd - 0.15), v(x, railY, -(hd - 0.15)), 0.08, IndustrialStyle.STEEL);
        }
        // cilindros dos capacitores
        int cols = Math.max(1, (int) Math.floor((w - 0.6) / 0.95));
        int rows = Math.max(1, (int) Math.floor((d - 0.6) / 0.95));
        double pitchX = (w - 0.8) / cols;
        double pitchZ = (d - 0.8) / rows;
        double radius = Math.min(0.4, Math.min(pitchX, pitchZ) * 0.42);
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                double x = -(w - 0.8) / 2 + pitchX * (i + 0.5);
                double z = -(d - 0.8) / 2 + pitchZ * (j + 0.5);
                IndustrialStyle.cylinder(b, x, z, 0.4, h - 0.3, radius, IndustrialStyle.TANK);
                IndustrialStyle.cylinder(b, x, z, 0.62, 0.72, radius + 0.02, IndustrialStyle.DARK);
                IndustrialStyle.cylinder(b, x, z, h - 0.55, h - 0.45, radius + 0.02, IndustrialStyle.DARK);
                IndustrialStyle.cylinder(b, x, z, h - 0.3, h - 0.2, radius * 0.7, IndustrialStyle.DARK);
            }
        }
        // painel de controle na frente, acima da porta
        double zf = -hd + 0.05;
        b.box(v(0, 1.38, zf), 1.0, 0.84, 0.16, plate);
        b.box(v(0, 1.38, zf - 0.07), 0.88, 0.72, 0.06, IndustrialStyle.SCREEN);
        IndustrialStyle.bolt(b, 0, 1.52, -hd - 0.06, -1, 0.4, p.accent());
        int bars = 5;
        int litBars = lit <= 0 ? 0 : Math.min(bars, (lit * bars + segments - 1) / segments);
        IndustrialStyle.ledPad(b, 0, 1.14, -hd - 0.02, -1, 0.74, 0.24, bars, litBars, p);
        // topo: postes curtos com nos luminosos ligados por uma barra
        for (int i = -1; i <= 1; i++) {
            double x = i * (hw - 0.7);
            b.beam(v(x, railY, 0), v(x, h + 0.2, 0), 0.14, plate);
            IndustrialStyle.node(b, v(x, h + 0.32, 0), 0.24, p);
        }
        b.beam(v(-(hw - 0.7), h + 0.32, 0), v(hw - 0.7, h + 0.32, 0), 0.07, shade);
        // portas de energia (conector luminoso para fora)
        for (int side = -1; side <= 1; side += 2) {
            port(b, side * (hw - 0.4), 0.55, 0, true, side, p);
            port(b, 0, 0.55, side * (hd - 0.4), false, side, p);
        }
        return b.build();
    }

    /** Caixa de conexao de uma porta de energia na extremidade {@code side} do eixo x ({@code alongX}) ou z. */
    private static void port(Mesh.Builder b, double x, double y, double z, boolean alongX, int side, IndustrialStyle.Palette p) {
        double dx = alongX ? side : 0;
        double dz = alongX ? 0 : side;
        b.box(v(x, y, z), 0.8, 0.5, 0.8, p.shade());
        b.box(v(x + dx * 0.34, y, z + dz * 0.34), alongX ? 0.12 : 0.64, 0.4, alongX ? 0.64 : 0.12, IndustrialStyle.STEEL);
        b.glow(true);
        b.box(v(x + dx * 0.4, y, z + dz * 0.4), alongX ? 0.06 : 0.34, 0.34, alongX ? 0.34 : 0.06, p.accent());
        b.glow(false);
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

    /** Transformador elevador w x d x h metros, centrado na origem; o eixo x e o da largura; paineis nas duas faces z. */
    public static Mesh transformer(int w, int d, int h, IndustrialStyle.Palette p) {
        Mesh.Builder b = new Mesh.Builder();
        float[] plate = p.plate();
        float[] shade = p.shade();
        double tankTop = h - 0.9;
        double bodyW = w - 0.8;
        double bodyD = d - 0.8;
        double coreBottom = 0.45;
        double coreH = tankTop - coreBottom;
        double coreY = coreBottom + coreH / 2;
        b.box(v(0, 0.15, 0), w, 0.3, d, plate);
        b.box(v(0, 0.33, 0), w - 0.3, 0.06, d - 0.3, shade);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                IndustrialStyle.node(b, v(sx * (w / 2.0 - 0.28), 0.46, sz * (d / 2.0 - 0.28)), 0.36, p);
                IndustrialStyle.node(b, v(sx * (bodyW / 2 + 0.15), tankTop + 0.28, sz * (bodyD / 2 + 0.05)), 0.3, p);
            }
        }
        b.box(v(0, coreY, 0), bodyW, coreH, bodyD, IndustrialStyle.DARK);
        for (int side = -1; side <= 1; side += 2) {
            b.box(v(side * (bodyW / 2 + 0.15), coreY, 0), 0.3, coreH, bodyD + 0.1, plate);
        }
        b.box(v(0, tankTop + 0.07, 0), bodyW + 0.5, 0.14, bodyD + 0.3, plate);
        // frente e fundo: aletas dos radiadores nas laterais e painel com raio no centro
        double pillar = Math.max(0.5, Math.min(1.25, bodyW * 0.3));
        double panelBottom = 1.45;
        double panelTop = tankTop - 0.12;
        double panelH = Math.max(0.4, panelTop - panelBottom);
        double padWidth = Math.max(0.7, Math.min(1.2, 2 * pillar - 0.4));
        for (int face = -1; face <= 1; face += 2) {
            double zFin = face * (bodyD / 2 + 0.07);
            for (double x = -bodyW / 2 + 0.2; x <= bodyW / 2 - 0.2 + 1e-6; x += 0.32) {
                if (Math.abs(x) >= pillar + 0.15) {
                    b.box(v(x, coreY, zFin), 0.17, coreH - 0.2, 0.14, IndustrialStyle.FIN);
                }
            }
            for (int side = -1; side <= 1; side += 2) {
                b.box(v(side * pillar, coreY, face * (bodyD / 2 + 0.09)), 0.3, coreH, 0.18, plate);
            }
            double zPanel = face * (bodyD / 2 + 0.05);
            b.box(v(0, panelBottom + panelH / 2, zPanel), 2 * pillar - 0.3, panelH, 0.06, IndustrialStyle.SCREEN);
            IndustrialStyle.bolt(b, 0, panelBottom + panelH / 2, face * (bodyD / 2 + 0.09), face, Math.min(1.2, panelH - 0.15), p.accent());
            IndustrialStyle.ledPad(b, 0, 1.12, face * (bodyD / 2 + 0.1), face, padWidth, 0.38, 3, 3, p);
        }
        // buchas ribadas, uma por condutor, com no luminoso no topo, ligadas por uma barra
        double s = bushingSpacing(w);
        double top = h + 1.2;
        for (int i = -1; i <= 1; i++) {
            double x = i * s;
            b.box(v(x, tankTop + 0.3, 0), 0.55, 0.3, 0.55, plate);
            b.beam(v(x, tankTop + 0.4, 0), v(x, top - 0.1, 0), 0.26, IndustrialStyle.DARK);
            for (double y = tankTop + 0.55; y < top - 0.15; y += 0.2) {
                b.box(v(x, y, 0), 0.52, 0.1, 0.52, IndustrialStyle.FIN);
            }
            IndustrialStyle.node(b, v(x, top + 0.07, 0), 0.32, p);
        }
        b.beam(v(-s, top + 0.07, 0), v(s, top + 0.07, 0), 0.07, IndustrialStyle.DARK);
        // portas de energia nas quatro faces, no chao
        for (int side = -1; side <= 1; side += 2) {
            port(b, side * (w / 2.0 - 0.4), 0.6, 0, true, side, p);
            port(b, 0, 0.6, side * (d / 2.0 - 0.4), false, side, p);
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
