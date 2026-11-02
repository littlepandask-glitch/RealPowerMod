package com.mmods.realpower.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Gerador procedural do rotor 3D da turbina eolica, em escala real (1 unidade = 1 metro):
 * carenagem elipsoidal e tres pas aerodinamicas (corda, espessura e torcao variando ao longo do
 * vao), com faixa vermelha de sinalizacao na ponta.
 *
 * <p>Sistema de coordenadas do rotor: origem no centro do cubo, eixo de rotacao em Z e a frente
 * apontando para -Z; as pas ficam no plano XY (+Y para cima).
 */
public final class TurbineModel {
    private static final int RING_SEGMENTS = 24;
    private static final int BLADE_STATIONS = 26;
    private static final int BLADE_SECTION_POINTS = 12;

    private static final float[] WHITE = {0.93f, 0.94f, 0.95f};
    private static final float[] RED = {0.80f, 0.08f, 0.07f};

    /**
     * Dimensoes do rotor em metros.
     *
     * @param spinnerRadius     raio maximo da carenagem (no plano do rotor)
     * @param spinnerHalfLength semi-eixo axial do elipsoide centrado no plano do rotor
     * @param spinnerBack       quanto a carenagem se estende atras do plano do rotor (cortada pela nacele)
     * @param spinnerFront      quanto a carenagem se estende a frente do plano do rotor
     * @param rootRadius    distancia do eixo onde a pa comeca
     * @param tipRadius     distancia do eixo ate a ponta da pa
     */
    public record RotorSpec(double spinnerRadius, double spinnerHalfLength, double spinnerBack, double spinnerFront,
                            double rootRadius, double tipRadius) {
    }

    private TurbineModel() {
    }

    public static Mesh buildRotor(RotorSpec spec) {
        Mesh.Builder b = new Mesh.Builder();

        // carenagem: elipsoide centrado no plano do rotor, cortado atras pela nacele
        int rings = 14;
        List<Vec[]> spinner = new ArrayList<>();
        for (int i = 0; i <= rings; i++) {
            double a = -spec.spinnerBack() + (spec.spinnerBack() + spec.spinnerFront()) * i / rings;
            double t = a / spec.spinnerHalfLength();
            double r = spec.spinnerRadius() * Math.sqrt(Math.max(0.0, 1.0 - t * t));
            spinner.add(circleZ(-a, Math.max(r, 1e-3)));
        }
        b.strip(spinner, WHITE);
        b.cap(spinner.get(0), new Vec(0, 0, spec.spinnerBack()), new Vec(0, 0, -spec.spinnerFront() / 2.0), WHITE);

        for (int i = 0; i < 3; i++) {
            addBlade(b, Math.toRadians(i * 120.0), spec.rootRadius(), spec.tipRadius());
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
        b.cap(stations.get(BLADE_STATIONS - 1), centers.get(BLADE_STATIONS - 1), centers.get(BLADE_STATIONS - 2), RED);
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

    private static Vec[] circleZ(double z, double radius) {
        Vec[] ring = new Vec[RING_SEGMENTS];
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a = 2.0 * Math.PI * i / RING_SEGMENTS;
            ring[i] = new Vec(Math.cos(a) * radius, Math.sin(a) * radius, z);
        }
        return ring;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
