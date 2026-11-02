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

    /**
     * Dimensoes do corpo e da nacele em metros, relativas ao centro do bloco do controlador
     * (x = lateral, y = altura, z = para tras; o rotor aponta para -z).
     */
    public record StructureSpec(double towerOuterBottom, double towerOuterTop, int towerHeight, double wall,
                                double doorHalfWidth, double doorHeight, int nacelleBack, int nacelleFront,
                                int nacelleHalfWidth, int nacelleFloor, int nacelleRoof, int hatchRadiusSq) {
    }

    private static final float[] TOWER_PAINT = {0.90f, 0.91f, 0.92f};
    private static final float[] INNER_PAINT = {0.66f, 0.68f, 0.70f};
    private static final int TOWER_SEGMENTS = 48;

    /** Trecho da malha: externo (luz do ceu) ou interno (luz do mundo no ponto indicado, no referencial do modelo). */
    public record Section(Mesh mesh, boolean interior, double lx, double ly, double lz) {
    }

    private static final float[] LIGHT_RED = {1.0f, 0.08f, 0.06f};
    private static final double LIGHT_BAND = 10.0;

    /** Corpo oco (parede lisa, porta atras) e nacele em casca com escotilha no piso. */
    public static List<Section> buildStructure(StructureSpec s) {
        Mesh.Builder outer = new Mesh.Builder();
        List<Double> bands = new ArrayList<>(List.of(0.5, s.doorHeight() + 0.5));
        for (double y = 0.5 + LIGHT_BAND; y < s.towerHeight() + 0.5; y += LIGHT_BAND) {
            bands.add(y);
        }
        bands.add(s.towerHeight() + 0.5);
        List<Section> sections = new ArrayList<>();
        List<Mesh.Builder> innerBands = new ArrayList<>();
        for (int band = 0; band + 1 < bands.size(); band++) {
            double y0 = bands.get(band);
            double y1 = bands.get(band + 1);
            double r0 = outerRadius(s, y0);
            double r1 = outerRadius(s, y1);
            Mesh.Builder inner = new Mesh.Builder();
            innerBands.add(inner);
            for (int i = 0; i < TOWER_SEGMENTS; i++) {
                double a0 = 2.0 * Math.PI * i / TOWER_SEGMENTS;
                double a1 = 2.0 * Math.PI * (i + 1) / TOWER_SEGMENTS;
                double mx = Math.sin((a0 + a1) / 2.0);
                double mz = Math.cos((a0 + a1) / 2.0);
                if (band == 0 && doorSegment(i, r0, s)) {
                    // vao da porta: fecha a secao da parede (verga e batentes) para nao parecer oca
                    double i1d = r1 - s.wall();
                    double i0d = r0 - s.wall();
                    Vec solidAbove = new Vec(0, y1 + 5, 0);
                    outer.quad(ring(a0, r1, y1), ring(a1, r1, y1), ring(a1, i1d, y1), ring(a0, i1d, y1), solidAbove, TOWER_PAINT);
                    for (int side = -1; side <= 1; side += 2) {
                        int n = Math.floorMod(i + side, TOWER_SEGMENTS);
                        if (!doorSegment(n, r0, s)) {
                            double a = side < 0 ? a0 : a1;
                            double aSolid = 2.0 * Math.PI * (n + 0.5) / TOWER_SEGMENTS;
                            outer.quad(ring(a, r0, y0), ring(a, i0d, y0), ring(a, i1d, y1), ring(a, r1, y1),
                                    ring(aSolid, r0 - s.wall() / 2.0, (y0 + y1) / 2.0), TOWER_PAINT);
                        }
                    }
                    continue;
                }
                double ym = (y0 + y1) / 2.0;
                outer.quad(ring(a0, r0, y0), ring(a1, r0, y0), ring(a1, r1, y1), ring(a0, r1, y1), new Vec(0, ym, 0), TOWER_PAINT);
                double i0 = r0 - s.wall();
                double i1 = r1 - s.wall();
                inner.quad(ring(a0, i0, y0), ring(a1, i0, y0), ring(a1, i1, y1), ring(a0, i1, y1),
                        new Vec(mx * 50, ym, mz * 50), INNER_PAINT);
            }
            sections.add(new Section(null, true, 0, (y0 + y1) / 2.0, 0));
        }

        // nacele: casca de 1 bloco de espessura (x lateral, z para tras, u = -z)
        Mesh.Builder nac = new Mesh.Builder();
        double xo = s.nacelleHalfWidth() + 0.5;
        double xi = s.nacelleHalfWidth() - 0.5;
        double zBack = s.nacelleBack() + 0.5;
        double zFront = -(s.nacelleFront() + 0.5);
        double zBackIn = s.nacelleBack() - 0.5;
        double zFrontIn = -(s.nacelleFront() - 0.5);
        double yFloor = s.nacelleFloor() - 0.5;
        double yFloorTop = s.nacelleFloor() + 0.5;
        double yRoof = s.nacelleRoof() + 0.5;
        double yRoofIn = s.nacelleRoof() - 0.5;
        double yMid = (yFloor + yRoof) / 2.0;
        Vec center = new Vec(0, yMid, (zBack + zFront) / 2.0);

        // paredes externas e teto
        outer.quad(new Vec(-xo, yFloor, zFront), new Vec(xo, yFloor, zFront), new Vec(xo, yRoof, zFront), new Vec(-xo, yRoof, zFront), center, TOWER_PAINT);
        outer.quad(new Vec(-xo, yFloor, zBack), new Vec(xo, yFloor, zBack), new Vec(xo, yRoof, zBack), new Vec(-xo, yRoof, zBack), center, TOWER_PAINT);
        outer.quad(new Vec(-xo, yFloor, zFront), new Vec(-xo, yFloor, zBack), new Vec(-xo, yRoof, zBack), new Vec(-xo, yRoof, zFront), center, TOWER_PAINT);
        outer.quad(new Vec(xo, yFloor, zFront), new Vec(xo, yFloor, zBack), new Vec(xo, yRoof, zBack), new Vec(xo, yRoof, zFront), center, TOWER_PAINT);
        outer.quad(new Vec(-xo, yRoof, zFront), new Vec(xo, yRoof, zFront), new Vec(xo, yRoof, zBack), new Vec(-xo, yRoof, zBack), center, TOWER_PAINT);
        // paredes internas e forro (normais para dentro)
        double zIn = (zBackIn + zFrontIn) / 2.0;
        double yIn = (yFloorTop + yRoofIn) / 2.0;
        nac.quad(new Vec(-xi, yFloorTop, zFrontIn), new Vec(xi, yFloorTop, zFrontIn), new Vec(xi, yRoofIn, zFrontIn), new Vec(-xi, yRoofIn, zFrontIn), new Vec(0, yIn, zIn - 1000), INNER_PAINT);
        nac.quad(new Vec(-xi, yFloorTop, zBackIn), new Vec(xi, yFloorTop, zBackIn), new Vec(xi, yRoofIn, zBackIn), new Vec(-xi, yRoofIn, zBackIn), new Vec(0, yIn, zIn + 1000), INNER_PAINT);
        nac.quad(new Vec(-xi, yFloorTop, zFrontIn), new Vec(-xi, yFloorTop, zBackIn), new Vec(-xi, yRoofIn, zBackIn), new Vec(-xi, yRoofIn, zFrontIn), new Vec(-1000, yIn, zIn), INNER_PAINT);
        nac.quad(new Vec(xi, yFloorTop, zFrontIn), new Vec(xi, yFloorTop, zBackIn), new Vec(xi, yRoofIn, zBackIn), new Vec(xi, yRoofIn, zFrontIn), new Vec(1000, yIn, zIn), INNER_PAINT);
        nac.quad(new Vec(-xi, yRoofIn, zFrontIn), new Vec(xi, yRoofIn, zFrontIn), new Vec(xi, yRoofIn, zBackIn), new Vec(-xi, yRoofIn, zBackIn), new Vec(0, yIn - 1000, zIn), INNER_PAINT);

        // piso em celulas de 1 bloco, com a escotilha vazada (u = -z, v = x)
        int hs = s.hatchRadiusSq();
        for (int u = -s.nacelleBack(); u <= s.nacelleFront(); u++) {
            for (int v = -s.nacelleHalfWidth(); v <= s.nacelleHalfWidth(); v++) {
                if (hatch(u, v, hs)) {
                    continue;
                }
                double x0 = v - 0.5;
                double x1 = v + 0.5;
                double z0 = -u - 0.5;
                double z1 = -u + 0.5;
                Vec mid = new Vec(v, (yFloor + yFloorTop) / 2.0, -u);
                outer.quad(new Vec(x0, yFloor, z0), new Vec(x1, yFloor, z0), new Vec(x1, yFloor, z1), new Vec(x0, yFloor, z1), new Vec(v, yFloorTop + 5, -u), TOWER_PAINT);
                nac.quad(new Vec(x0, yFloorTop, z0), new Vec(x1, yFloorTop, z0), new Vec(x1, yFloorTop, z1), new Vec(x0, yFloorTop, z1), new Vec(v, yFloor - 5, -u), INNER_PAINT);
                // borda da escotilha
                if (hatch(u, v + 1, hs)) {
                    nac.quad(new Vec(x1, yFloor, z0), new Vec(x1, yFloor, z1), new Vec(x1, yFloorTop, z1), new Vec(x1, yFloorTop, z0), mid, INNER_PAINT);
                }
                if (hatch(u, v - 1, hs)) {
                    nac.quad(new Vec(x0, yFloor, z0), new Vec(x0, yFloor, z1), new Vec(x0, yFloorTop, z1), new Vec(x0, yFloorTop, z0), mid, INNER_PAINT);
                }
                if (hatch(u + 1, v, hs)) {
                    nac.quad(new Vec(x0, yFloor, z0), new Vec(x1, yFloor, z0), new Vec(x1, yFloorTop, z0), new Vec(x0, yFloorTop, z0), mid, INNER_PAINT);
                }
                if (hatch(u - 1, v, hs)) {
                    nac.quad(new Vec(x0, yFloor, z1), new Vec(x1, yFloor, z1), new Vec(x1, yFloorTop, z1), new Vec(x0, yFloorTop, z1), mid, INNER_PAINT);
                }
            }
        }

        List<Section> result = new ArrayList<>();
        result.add(new Section(outer.build(), false, 0, 0, 0));
        for (int i = 0; i < innerBands.size(); i++) {
            Section placeholder = sections.get(i);
            result.add(new Section(innerBands.get(i).build(), true, 0, placeholder.ly(), 0));
        }
        result.add(new Section(nac.build(), true, 0, yIn, zIn));
        return result;
    }

    /** Luzes de posicao (obstaculo aereo) no teto da nacele, na traseira. */
    public static Mesh buildStructureLights(StructureSpec s) {
        Mesh.Builder b = new Mesh.Builder();
        double y = s.nacelleRoof() + 0.5 + 0.3;
        double z = s.nacelleBack() - 1.0;
        b.box(new Vec(-2.5, y, z), 0.5, 0.6, 0.5, LIGHT_RED);
        b.box(new Vec(2.5, y, z), 0.5, 0.6, 0.5, LIGHT_RED);
        return b.build();
    }

    /** Luzes de posicao na ponta de cada pa (mesmo referencial do rotor). */
    public static Mesh buildRotorLights(RotorSpec spec) {
        Mesh.Builder b = new Mesh.Builder();
        for (int i = 0; i < 3; i++) {
            double phi = Math.toRadians(i * 120.0);
            double r = spec.tipRadius() - 0.8;
            b.box(new Vec(Math.sin(phi) * r, Math.cos(phi) * r, 0.0), 0.7, 0.7, 0.7, LIGHT_RED);
        }
        return b.build();
    }

    /** O segmento angular {@code i} da faixa da porta cai dentro do vao (atras, +z)? */
    private static boolean doorSegment(int i, double radius, StructureSpec s) {
        double mid = 2.0 * Math.PI * (i + 0.5) / TOWER_SEGMENTS;
        return Math.cos(mid) > 0 && Math.abs(Math.sin(mid) * radius) < s.doorHalfWidth();
    }

    private static boolean hatch(int u, int v, int radiusSq) {
        return u * u + v * v <= radiusSq;
    }

    private static double outerRadius(StructureSpec s, double y) {
        return s.towerOuterBottom() + (s.towerOuterTop() - s.towerOuterBottom()) * (y - 1.0) / (s.towerHeight() - 1.0);
    }

    /** Ponto do anel de angulo {@code a} (0 = +z, atras) e raio {@code r}. */
    private static Vec ring(double a, double r, double y) {
        return new Vec(Math.sin(a) * r, y, Math.cos(a) * r);
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
