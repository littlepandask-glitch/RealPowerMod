package com.mmods.realpower.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Gerador procedural do modelo 3D da turbina eolica, em escala real (1 unidade = 1 metro).
 * O modelo se ajusta as dimensoes que o jogador construiu: torre cônica, nacele arredondada,
 * cubo com cone e tres pas aerodinamicas (corda, espessura e torcao variando ao longo do vao).
 *
 * <p>Sistema de coordenadas local, com origem no centro do bloco controlador: +Y para cima e a
 * frente do rotor aponta para -Z. O rotor e gerado em um sistema proprio (origem no centro do
 * cubo, eixo de rotacao em Z) para poder girar sem recalcular a malha.
 */
public final class TurbineModel {
    /** Posicao do centro do cubo em relacao ao controlador (um bloco a frente). */
    public static final double HUB_Z = -1.0;

    private static final int RING_SEGMENTS = 20;
    private static final int BLADE_STATIONS = 22;
    private static final int BLADE_SECTION_POINTS = 12;

    private static final float[] WHITE = {0.93f, 0.94f, 0.95f};
    private static final float[] NACELLE = {0.88f, 0.89f, 0.91f};
    private static final float[] DARK = {0.14f, 0.15f, 0.17f};
    private static final float[] RED = {0.80f, 0.08f, 0.07f};

    /** Dimensoes da estrutura em metros: altura da torre e comprimento da pa a partir do centro do cubo. */
    public record Dims(double towerHeight, double bladeLength) {
    }

    private TurbineModel() {
    }

    public static double nacelleWidth(Dims d) {
        return clamp(0.9 + d.bladeLength() * 0.045, 1.0, 3.2);
    }

    /** Torre, nacele e detalhes fixos. */
    public static Mesh buildStatic(Dims d) {
        Mesh.Builder b = new Mesh.Builder();
        double w = nacelleWidth(d);
        double h = 1.05 * w;
        double length = 1.5 + 0.8 * w;
        double zFront = -0.5;

        // nacele: secao superelipse (cantos arredondados) com leve afunilamento para tras
        double[] stationZ = {zFront, zFront + length * 0.25, zFront + length * 0.7, zFront + length};
        double[] stationScale = {0.94, 1.0, 1.0, 0.78};
        List<Vec[]> rings = new ArrayList<>();
        for (int i = 0; i < stationZ.length; i++) {
            rings.add(roundedRect(stationZ[i], w * stationScale[i], h * stationScale[i]));
        }
        b.strip(rings, NACELLE);
        Vec center = new Vec(0, 0, zFront + length * 0.5);
        b.cap(rings.get(0), new Vec(0, 0, zFront), center, NACELLE);
        b.cap(rings.get(rings.size() - 1), new Vec(0, 0, zFront + length), center, NACELLE);

        // luzes de aviso e mastro de sensores no topo da nacele
        double top = h / 2;
        double rear = zFront + length - 0.45;
        b.box(new Vec(-0.32 * w, top + 0.12, rear), 0.16, 0.24, 0.16, RED);
        b.box(new Vec(0.32 * w, top + 0.12, rear), 0.16, 0.24, 0.16, RED);
        b.box(new Vec(0, top + 0.35, zFront + length * 0.6), 0.06, 0.7, 0.06, DARK);
        b.box(new Vec(0, top + 0.72, zFront + length * 0.6), 0.5, 0.05, 0.12, WHITE);

        // torre cônica com flange na base e porta
        double rt = clamp(0.3 + 0.1 * w, 0.35, 0.75);
        double rb = rt * 1.55;
        double yTop = -h / 2 + 0.15;
        double yBase = -d.towerHeight() - 0.5;
        List<Vec[]> tower = new ArrayList<>();
        int steps = Math.max(2, (int) Math.ceil(d.towerHeight() / 8.0));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            tower.add(circle(new Vec(0, yTop + (yBase - yTop) * t, 0), rt + (rb - rt) * t, RING_SEGMENTS));
        }
        b.strip(tower, WHITE);
        Vec axisMid = new Vec(0, (yTop + yBase) / 2, 0);
        b.cap(tower.get(0), new Vec(0, yTop, 0), axisMid, WHITE);
        List<Vec[]> flange = List.of(
                circle(new Vec(0, yBase + 0.4, 0), rb * 1.0, RING_SEGMENTS),
                circle(new Vec(0, yBase + 0.4, 0), rb * 1.14, RING_SEGMENTS),
                circle(new Vec(0, yBase, 0), rb * 1.14, RING_SEGMENTS));
        b.strip(flange, WHITE);
        b.cap(flange.get(2), new Vec(0, yBase, 0), axisMid, WHITE);
        b.box(new Vec(0, yBase + 1.1, rb + 0.02), Math.min(1.0, rb), 1.9, 0.08, DARK);

        return b.build();
    }

    /** Cubo com cone e tres pas, com o cubo na origem e o rotor girando em torno de Z. */
    public static Mesh buildRotor(Dims d) {
        Mesh.Builder b = new Mesh.Builder();
        double spinnerRadius = nacelleWidth(d) * 0.55;

        // cone do cubo: perfil (posicao axial para a frente, raio)
        double[][] profile = {
                {-0.5, 0.9}, {0.0, 1.0}, {0.45, 1.0}, {1.0, 0.88}, {1.6, 0.62}, {2.1, 0.30}, {2.4, 0.0}
        };
        List<Vec[]> spinner = new ArrayList<>();
        for (double[] p : profile) {
            double r = p[1] * spinnerRadius;
            spinner.add(circleZ(-p[0] * spinnerRadius, Math.max(r, 1e-3), RING_SEGMENTS));
        }
        b.strip(spinner, WHITE);
        b.cap(spinner.get(0), new Vec(0, 0, 0.5 * spinnerRadius), new Vec(0, 0, -spinnerRadius), WHITE);

        double root = spinnerRadius * 0.9;
        double tip = Math.max(root + 1.0, d.bladeLength());
        for (int i = 0; i < 3; i++) {
            addBlade(b, Math.toRadians(i * 120.0), root, tip);
        }
        return b.build();
    }

    private static void addBlade(Mesh.Builder b, double phi, double rootRadius, double tipRadius) {
        double span = tipRadius - rootRadius;
        double chordMax = clamp(tipRadius * 0.075, 0.9, 4.2);
        Vec radial = new Vec(Math.sin(phi), Math.cos(phi), 0);
        Vec tangent = new Vec(Math.cos(phi), -Math.sin(phi), 0);
        Vec axial = new Vec(0, 0, -1);

        List<Vec[]> stations = new ArrayList<>();
        List<Vec> centers = new ArrayList<>();
        List<Float> tipMark = new ArrayList<>();
        for (int s = 0; s < BLADE_STATIONS; s++) {
            double f = (double) s / (BLADE_STATIONS - 1);
            double chord = chordMax * (f < 0.18 ? 0.6 + 0.4 * (f / 0.18) : 1.0 - 0.8 * ((f - 0.18) / 0.82));
            double thickness = 0.24 - 0.17 * f;
            double twist = Math.toRadians(20.0 * Math.pow(1.0 - f, 1.5) + 2.0);
            double circleBlend = Math.max(0.0, 1.0 - f / 0.14);

            Vec chordDir = tangent.scale(Math.cos(twist)).add(axial.scale(Math.sin(twist)));
            Vec thickDir = tangent.scale(-Math.sin(twist)).add(axial.scale(Math.cos(twist)));
            Vec origin = radial.scale(rootRadius + span * f);

            Vec[] ring = new Vec[BLADE_SECTION_POINTS];
            for (int i = 0; i < BLADE_SECTION_POINTS; i++) {
                double[] foil = airfoilPoint(i, chord, thickness);
                double a = 2.0 * Math.PI * i / BLADE_SECTION_POINTS;
                double circleU = -0.25 * chord + Math.cos(a) * chord * 0.5;
                double circleV = Math.sin(a) * chord * 0.5;
                double u = foil[0] * (1 - circleBlend) + circleU * circleBlend;
                double v = foil[1] * (1 - circleBlend) + circleV * circleBlend;
                ring[i] = origin.add(chordDir.scale(u)).add(thickDir.scale(v));
            }
            stations.add(ring);
            centers.add(Vec.average(ring));
        }

        // pintura: branca, com faixa vermelha de sinalizacao na ponta
        int redFrom = (int) Math.floor(BLADE_STATIONS * 0.88);
        for (int s = 0; s + 1 < BLADE_STATIONS; s++) {
            b.strip(List.of(stations.get(s), stations.get(s + 1)), s >= redFrom ? RED : WHITE);
        }
        Vec[] last = stations.get(BLADE_STATIONS - 1);
        b.cap(last, centers.get(BLADE_STATIONS - 1), centers.get(BLADE_STATIONS - 2), RED);
    }

    /**
     * Ponto {@code i} da secao da pa: perfil simetrico tipo NACA com leve curvatura, percorrendo o
     * dorso do bordo de ataque ao de fuga e voltando pelo ventre. Retorna {u, v} em metros com o
     * bordo de ataque em +u.
     */
    private static double[] airfoilPoint(int i, double chord, double thicknessRatio) {
        double[] upper = {0.0, 0.04, 0.15, 0.35, 0.6, 0.85, 1.0};
        double[] lower = {0.85, 0.6, 0.35, 0.15, 0.04};
        double x;
        double sign;
        if (i < upper.length) {
            x = upper[i];
            sign = 1;
        } else {
            x = lower[i - upper.length];
            sign = -1;
        }
        double yt = 5.0 * thicknessRatio * (0.2969 * Math.sqrt(x) - 0.1260 * x - 0.3516 * x * x
                + 0.2843 * x * x * x - 0.1036 * x * x * x * x);
        double camber = 0.03 * 4.0 * x * (1.0 - x);
        return new double[] {(0.25 - x) * chord, (sign * yt + camber) * chord};
    }

    private static Vec[] circle(Vec center, double radius, int segments) {
        Vec[] ring = new Vec[segments];
        for (int i = 0; i < segments; i++) {
            double a = 2.0 * Math.PI * i / segments;
            ring[i] = center.add(new Vec(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
        return ring;
    }

    private static Vec[] circleZ(double z, double radius, int segments) {
        Vec[] ring = new Vec[segments];
        for (int i = 0; i < segments; i++) {
            double a = 2.0 * Math.PI * i / segments;
            ring[i] = new Vec(Math.cos(a) * radius, Math.sin(a) * radius, z);
        }
        return ring;
    }

    private static Vec[] roundedRect(double z, double width, double height) {
        int n = 16;
        double exponent = 2.0 / 4.0; // superelipse de expoente 4
        Vec[] ring = new Vec[n];
        for (int i = 0; i < n; i++) {
            double a = 2.0 * Math.PI * i / n;
            double c = Math.cos(a);
            double s = Math.sin(a);
            ring[i] = new Vec(Math.signum(c) * Math.pow(Math.abs(c), exponent) * width / 2,
                    Math.signum(s) * Math.pow(Math.abs(s), exponent) * height / 2, z);
        }
        return ring;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
