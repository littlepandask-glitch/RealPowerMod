package com.mmods.realpower.render;

import java.util.List;

/**
 * Malhas da casa de forca da barragem (concreto realista, fora do estilo industrial). Referencial local: x lateral, y para cima
 * a partir do chao da unidade, +z para montante (o lago) e -z para jusante (canal de fuga); origem no centro da base.
 * A parte fixa e o rotor do gerador (que gira em torno do eixo vertical em {@code x=0, z=}{@link #axisZ}) sao malhas separadas.
 */
public final class HydroModel {
    private static final float[] CONCRETE = {0.60f, 0.60f, 0.58f};
    private static final float[] CONCRETE_LIGHT = {0.69f, 0.69f, 0.66f};
    private static final float[] CONCRETE_DARK = {0.43f, 0.43f, 0.42f};
    private static final float[] FOUNDATION = {0.40f, 0.40f, 0.39f};
    private static final float[] ROOF = {0.27f, 0.30f, 0.33f};
    private static final float[] GLASS = {0.13f, 0.18f, 0.25f};
    private static final float[] STEEL = {0.33f, 0.36f, 0.40f};
    private static final float[] STEEL_DARK = {0.22f, 0.24f, 0.27f};
    private static final float[] RACK = {0.07f, 0.08f, 0.10f};
    private static final float[] WATER = {0.12f, 0.24f, 0.34f};
    private static final float[] YELLOW = {0.82f, 0.66f, 0.12f};
    private static final float[] GENERATOR = {0.20f, 0.37f, 0.47f};

    private static final double WALL = 0.6;
    private static final double APRON = 1.5;
    private static final int SIDES = 10;

    private HydroModel() {
    }

    private static Vec v(double x, double y, double z) {
        return new Vec(x, y, z);
    }

    /** Espessura da parede de montante (a face da barragem, onde fica a tomada d'agua). */
    private static double wallThickness(int d) {
        return Math.max(2.0, d * 0.25);
    }

    private static double hallHeight(int h) {
        return Math.floor(h * 0.72);
    }

    /** Posicao z do eixo da turbina e do gerador, no meio do salao. */
    public static double axisZ(int d) {
        return (APRON - wallThickness(d)) / 2.0;
    }

    private static double spiralRadius(int w, int d) {
        double hallLength = d - wallThickness(d) - APRON;
        return Math.max(1.0, Math.min(w, hallLength) * 0.30);
    }

    /** Cilindro horizontal ao longo de z. */
    private static void pipe(Mesh.Builder b, double cx, double cy, double z0, double z1, double r, float[] rgb) {
        Vec[] front = new Vec[SIDES];
        Vec[] back = new Vec[SIDES];
        for (int i = 0; i < SIDES; i++) {
            double a = Math.PI * 2 * i / SIDES;
            front[i] = v(cx + r * Math.cos(a), cy + r * Math.sin(a), z0);
            back[i] = v(cx + r * Math.cos(a), cy + r * Math.sin(a), z1);
        }
        Vec mid = v(cx, cy, (z0 + z1) / 2);
        b.strip(List.of(front, back), rgb);
        b.cap(front, v(cx, cy, z0), mid, rgb);
        b.cap(back, v(cx, cy, z1), mid, rgb);
    }

    /** Casa de forca completa (parte fixa): fundacao, parede da barragem com a tomada, salao, tubo forcado e portas. */
    public static Mesh powerhouse(int w, int h, int d, int turbineY, IndustrialStyle.Palette palette) {
        Mesh.Builder b = new Mesh.Builder();
        double hw = w / 2.0;
        double hd = d / 2.0;
        double t = wallThickness(d);
        double hall = hallHeight(h);
        double frontZ = -hd + APRON;
        double backZ = hd - t;
        double length = backZ - frontZ;
        double zc = axisZ(d);
        float[] band = {palette.accent()[0] * 0.8f, palette.accent()[1] * 0.8f, palette.accent()[2] * 0.8f};

        b.box(v(0, 0.4, 0), w, 0.8, d, FOUNDATION);

        // parede da barragem: corpo da unidade ate a crista
        b.box(v(0, h / 2.0, hd - t / 2), w, h, t, CONCRETE);
        for (double y = 3.0; y < h - 0.5; y += 3.0) {
            b.box(v(0, y, hd + 0.02), w, 0.07, 0.04, CONCRETE_DARK);
        }
        for (double x = -hw + 4.0; x < hw - 1.0; x += 4.0) {
            b.box(v(x, h / 2.0, hd + 0.02), 0.07, h, 0.04, CONCRETE_DARK);
            b.box(v(x, h / 2.0, backZ - 0.02), 0.07, h, 0.04, CONCRETE_DARK);
        }
        b.box(v(0, h - 0.12, hd - t / 2), w + 0.2, 0.24, t + 0.2, CONCRETE_LIGHT);
        for (int side = 0; side < 2; side++) {
            double z = side == 0 ? hd - 0.15 : backZ + 0.15;
            for (double x = -hw + 0.3; x <= hw - 0.3 + 1e-6; x += 2.0) {
                b.beam(v(x, h, z), v(x, h + 1.0, z), 0.08, STEEL);
            }
            b.beam(v(-hw + 0.3, h + 1.0, z), v(hw - 0.3, h + 1.0, z), 0.08, STEEL);
        }

        // tomada d'agua: grade de limpeza na face de montante
        double iw = w * 0.55;
        double iy0 = 0.9;
        double iy1 = Math.min(h - 1.5, turbineY + 2.2);
        b.box(v(0, (iy0 + iy1) / 2, hd + 0.04), iw, iy1 - iy0, 0.08, RACK);
        for (double x = -iw / 2 + 0.2; x <= iw / 2 - 0.2 + 1e-6; x += 0.4) {
            b.beam(v(x, iy0 + 0.1, hd + 0.1), v(x, iy1 - 0.1, hd + 0.1), 0.06, STEEL);
        }
        for (int k = 0; k < 3; k++) {
            double y = iy0 + (iy1 - iy0) * (k + 0.5) / 3;
            b.beam(v(-iw / 2, y, hd + 0.13), v(iw / 2, y, hd + 0.13), 0.12, STEEL_DARK);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            b.beam(v(sx * iw / 2, iy0 - 0.1, hd + 0.12), v(sx * iw / 2, iy1 + 0.1, hd + 0.12), 0.2, STEEL_DARK);
        }
        b.beam(v(-iw / 2, iy1 + 0.1, hd + 0.12), v(iw / 2, iy1 + 0.1, hd + 0.12), 0.2, STEEL_DARK);

        // salao do gerador: paredes laterais, fachada com abertura, telhado
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * (hw - WALL / 2), hall / 2, (frontZ + backZ) / 2), WALL, hall, length, CONCRETE_LIGHT);
            b.box(v(sx * (hw + 0.03), hall * 0.62, (frontZ + backZ) / 2), 0.06, hall * 0.28, length - 1.0, GLASS);
            for (double z = frontZ + 1.0; z <= backZ - 1.0 + 1e-6; z += 2.0) {
                b.box(v(sx * (hw + 0.05), hall * 0.62, z), 0.1, hall * 0.28 + 0.1, 0.18, CONCRETE_LIGHT);
            }
            b.box(v(sx * (hw + 0.03), hall * 0.28, (frontZ + backZ) / 2), 0.06, 0.3, length, band);
        }
        double ow = w * 0.62;
        double pillar = (w - ow) / 2;
        double sill = turbineY + 0.2;
        double lintel = hall - 0.9;
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * (ow / 2 + pillar / 2), hall / 2, frontZ + WALL / 2), pillar, hall, WALL, CONCRETE_LIGHT);
        }
        b.box(v(0, sill / 2, frontZ + WALL / 2), ow, sill, WALL, CONCRETE_LIGHT);
        b.box(v(0, (lintel + hall) / 2, frontZ + WALL / 2), ow, hall - lintel, WALL, CONCRETE_LIGHT);
        b.box(v(0, hall * 0.28, frontZ - 0.03), w, 0.3, 0.06, band);
        b.box(v(0, hall + 0.2, (frontZ + backZ) / 2 - 0.15), w + 0.6, 0.4, length + 0.3, ROOF);
        b.box(v(0, hall + 0.45, (frontZ + backZ) / 2 - 0.15), w + 0.8, 0.1, length + 0.5, CONCRETE_DARK);

        // canal de fuga na frente do salao e as duas portas de energia nos cantos
        double tail = Math.max(1.0, hw - 1.6);
        b.box(v(0, 0.83, -hd + APRON / 2), tail * 2, 0.06, APRON, WATER);
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * (tail + 0.15), 0.9, -hd + APRON / 2), 0.3, 0.2, APRON, CONCRETE_DARK);
            double px = sx * (hw - 0.5);
            double pz = -(hd - 0.5);
            b.box(v(px, 0.45, pz), 0.9, 0.9, 0.9, STEEL);
            b.box(v(px, 0.9, pz), 0.96, 0.08, 0.96, STEEL_DARK);
            b.glow(true);
            IndustrialStyle.cylinder(b, px, pz, 0.98, 1.0, 0.26, palette.accent());
            b.box(v(px + sx * 0.47, 0.45, pz), 0.06, 0.5, 0.5, palette.accent());
            b.box(v(px, 0.45, pz - 0.47), 0.5, 0.5, 0.06, palette.accent());
            b.glow(false);
        }

        // tubo forcado da parede ate a caixa espiral, caixa espiral, tubo de succao e pedestal do gerador
        double rs = spiralRadius(w, d);
        double rp = rs * 0.5;
        pipe(b, 0, turbineY, zc, backZ, rp, STEEL);
        for (double z = zc + rs; z < backZ - 0.2; z += 1.2) {
            pipe(b, 0, turbineY, z, z + 0.14, rp + 0.04, STEEL_DARK);
        }
        pipe(b, 0, turbineY, backZ - 0.5, backZ - 0.2, rp + 0.12, STEEL_DARK);
        double spiralBottom = turbineY - rs * 0.45;
        IndustrialStyle.cylinder(b, 0, zc, spiralBottom, turbineY + rs * 0.45, rs, STEEL);
        IndustrialStyle.cylinder(b, 0, zc, spiralBottom - 0.02, spiralBottom + 0.15, rs + 0.06, STEEL_DARK);
        IndustrialStyle.cylinder(b, 0, zc, 0.8, spiralBottom, rs * 0.6, CONCRETE_DARK);
        double rotorBottom = rotorBottom(turbineY, rs);
        IndustrialStyle.cylinder(b, 0, zc, turbineY + rs * 0.45, rotorBottom - 0.02, rs * 0.9 + 0.12, STEEL_DARK);

        // ponte rolante: trilhos nas laterais, viga ponte e carrinho
        double crane = hall - 0.7;
        for (int sx = -1; sx <= 1; sx += 2) {
            b.beam(v(sx * (hw - 0.9), crane, frontZ + WALL + 0.1), v(sx * (hw - 0.9), crane, backZ - 0.1), 0.25, STEEL_DARK);
        }
        b.beam(v(-(hw - 0.9), crane + 0.1, zc), v(hw - 0.9, crane + 0.1, zc), 0.3, YELLOW);
        b.box(v(0, crane - 0.25, zc), 0.9, 0.4, 0.9, YELLOW);
        b.beam(v(0, crane - 0.45, zc), v(0, crane - 0.9, zc), 0.05, STEEL_DARK);
        return b.build();
    }

    private static double rotorBottom(int turbineY, double spiralRadius) {
        return turbineY + spiralRadius * 0.45 + 0.4;
    }

    /** Rotor do gerador (gira em torno do eixo vertical na origem x=0, z=0 do referencial local da malha). */
    public static Mesh rotor(int w, int h, int d, int turbineY) {
        Mesh.Builder b = new Mesh.Builder();
        double rs = spiralRadius(w, d);
        double rg = rs * 0.85;
        double y0 = rotorBottom(turbineY, rs);
        double y1 = Math.max(y0 + 1.0, hallHeight(h) - 1.7);
        IndustrialStyle.cylinder(b, 0, 0, turbineY + rs * 0.45, y0, 0.18, STEEL);
        IndustrialStyle.cylinder(b, 0, 0, y0, y1, rg, GENERATOR);
        IndustrialStyle.cylinder(b, 0, 0, y1, y1 + 0.12, rg * 0.9, STEEL_DARK);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k;
            double x = (rg + 0.02) * Math.cos(a);
            double z = (rg + 0.02) * Math.sin(a);
            b.beam(v(x, y0 + 0.1, z), v(x, y1 - 0.1, z), 0.22, YELLOW);
        }
        for (double y : new double[]{y0 + 0.25, y1 - 0.37}) {
            Vec[] low = new Vec[SIDES];
            Vec[] high = new Vec[SIDES];
            for (int i = 0; i < SIDES; i++) {
                double a = Math.PI * 2 * i / SIDES;
                low[i] = v((rg + 0.05) * Math.cos(a), y, (rg + 0.05) * Math.sin(a));
                high[i] = v((rg + 0.05) * Math.cos(a), y + 0.12, (rg + 0.05) * Math.sin(a));
            }
            b.strip(List.of(low, high), STEEL_DARK);
        }
        return b.build();
    }
}
