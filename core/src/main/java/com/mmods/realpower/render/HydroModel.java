package com.mmods.realpower.render;

import com.mmods.realpower.multiblock.hydro.HydroLayout;
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
    private static final float[] RIVER = {0.16f, 0.36f, 0.50f};
    private static final float[] FOAM = {0.82f, 0.90f, 0.94f};
    /** Comprimento (m) do rio de saida que a casa de forca despeja a jusante. */
    public static final double OUTFLOW_LENGTH = 9.0;
    private static final float[] YELLOW = {0.82f, 0.66f, 0.12f};
    private static final float[] GENERATOR = {0.20f, 0.37f, 0.47f};

    private static final double WALL = 1.0;
    private static final double APRON = HydroLayout.APRON;
    private static final int SIDES = 10;

    private HydroModel() {
    }

    private static Vec v(double x, double y, double z) {
        return new Vec(x, y, z);
    }

    private static double wallThickness(int d) {
        return HydroLayout.wallCells(d);
    }

    private static double hallHeight(int h) {
        return HydroLayout.hallHeight(h);
    }

    /** Altura da grade da tomada: de {@code y0} a {@code y1}. */
    private static double intakeBottom() {
        return 1.0;
    }

    private static double intakeTop(int h, int turbineY) {
        return Math.min(h - 1.5, turbineY + 2.2);
    }

    /** Curso da comporta da tomada: ela sobe a sua propria altura. */
    public static double gateTravel(int h, int turbineY) {
        return intakeTop(h, turbineY) - intakeBottom();
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

        b.box(v(0, 0.5, 0), w, 1.0, d, FOUNDATION);

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
            double z = side == 0 ? hd - 0.1 : backZ + 0.1;
            for (double x = -hw + 0.3; x <= hw - 0.3 + 1e-6; x += 2.0) {
                b.beam(v(x, h, z), v(x, h + 0.75, z), 0.08, STEEL);
            }
            b.beam(v(-hw + 0.3, h + 0.75, z), v(hw - 0.3, h + 0.75, z), 0.08, STEEL);
        }

        // tomada d'agua: grade de limpeza na face de montante
        double iw = w * 0.55;
        double iy0 = intakeBottom();
        double iy1 = intakeTop(h, turbineY);
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

        // guias da comporta e cabrestante no topo
        double travel = iy1 - iy0;
        for (int sx = -1; sx <= 1; sx += 2) {
            b.beam(v(sx * (iw / 2 + 0.12), iy0, hd + 0.2), v(sx * (iw / 2 + 0.12), iy1 + travel + 0.4, hd + 0.2), 0.22, STEEL_DARK);
        }
        b.box(v(0, iy1 + travel + 0.45, hd + 0.25), iw * 0.7, 0.5, 0.5, YELLOW);
        b.beam(v(-iw / 2, iy1 + travel + 0.2, hd + 0.2), v(iw / 2, iy1 + travel + 0.2, hd + 0.2), 0.16, STEEL_DARK);

        // salao do gerador: paredes laterais, fachada com abertura, telhado
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * (hw - WALL / 2), hall / 2, (frontZ + backZ) / 2), WALL, hall, length, CONCRETE_LIGHT);
            b.box(v(sx * (hw + 0.03), hall * 0.62, (frontZ + backZ) / 2), 0.06, hall * 0.28, length - 1.0, GLASS);
            for (double z = frontZ + 1.0; z <= backZ - 1.0 + 1e-6; z += 2.0) {
                b.box(v(sx * (hw + 0.05), hall * 0.62, z), 0.1, hall * 0.28 + 0.1, 0.18, CONCRETE_LIGHT);
            }
            b.box(v(sx * (hw + 0.03), hall * 0.28, (frontZ + backZ) / 2), 0.06, 0.3, length, band);
        }
        double ow = HydroLayout.openingCells(w);
        double pillar = (w - ow) / 2;
        double sill = turbineY;
        double lintel = hall - 1.0;
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * (ow / 2 + pillar / 2), hall / 2, frontZ + WALL / 2), pillar, hall, WALL, CONCRETE_LIGHT);
        }
        b.box(v(0, sill / 2, frontZ + WALL / 2), ow, sill, WALL, CONCRETE_LIGHT);
        b.box(v(0, (lintel + hall) / 2, frontZ + WALL / 2), ow, hall - lintel, WALL, CONCRETE_LIGHT);
        b.box(v(0, hall * 0.28, frontZ - 0.03), w, 0.3, 0.06, band);
        b.box(v(0, hall + 0.5, (frontZ + backZ) / 2 - 0.15), w + 0.4, 1.0, length + 0.3, ROOF);
        b.box(v(0, hall + 1.04, (frontZ + backZ) / 2 - 0.15), w + 0.6, 0.08, length + 0.5, CONCRETE_DARK);

        // canal de fuga na frente do salao e as duas portas de energia nos cantos
        double tail = Math.max(1.0, hw - 1.6);
        b.box(v(0, 1.03, -hd + APRON / 2), tail * 2, 0.06, APRON, WATER);
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * (tail + 0.15), 1.1, -hd + APRON / 2), 0.3, 0.2, APRON, CONCRETE_DARK);
            double px = sx * (hw - 0.5);
            double pz = -(hd - 0.5);
            b.box(v(px, 0.5, pz), 1.02, 1.02, 1.02, STEEL);
            b.box(v(px, 1.01, pz), 1.04, 0.06, 1.04, STEEL_DARK);
            b.glow(true);
            IndustrialStyle.cylinder(b, px, pz, 1.04, 1.1, 0.26, palette.accent());
            b.box(v(px + sx * 0.52, 0.5, pz), 0.06, 0.6, 0.6, palette.accent());
            b.box(v(px, 0.5, pz - 0.52), 0.6, 0.6, 0.06, palette.accent());
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
        IndustrialStyle.cylinder(b, 0, zc, 1.0, spiralBottom, rs * 0.6, CONCRETE_DARK);
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

    /** Comporta da tomada (painel de aco com nervuras) na posicao fechada; o renderer a sobe pelo curso {@link #gateTravel}. */
    public static Mesh gate(int w, int h, int d, int turbineY) {
        Mesh.Builder b = new Mesh.Builder();
        double hd = d / 2.0;
        double iw = w * 0.55;
        double y0 = intakeBottom();
        double y1 = intakeTop(h, turbineY);
        double z = hd + 0.2;
        b.box(v(0, (y0 + y1) / 2, z), iw + 0.1, y1 - y0, 0.2, STEEL);
        for (int k = 0; k < 4; k++) {
            double y = y0 + (y1 - y0) * (k + 0.5) / 4;
            b.beam(v(-iw / 2, y, z + 0.16), v(iw / 2, y, z + 0.16), 0.14, STEEL_DARK);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * (iw / 2 + 0.02), (y0 + y1) / 2, z), 0.2, y1 - y0, 0.3, STEEL_DARK);
        }
        b.box(v(0, y1 + 0.1, z), 0.4, 0.3, 0.3, YELLOW);
        return b.build();
    }

    /**
     * Agua saindo da turbina: lamina que cai da borda do canal de fuga, rio que se abre sobre o chao a jusante e faixas de espuma
     * que correm no sentido do fluxo ({@code phase} 0..1 avanca com o tempo). Referencial da unidade; so se desenha com a usina gerando.
     */
    public static Mesh outflow(int w, int d, double phase, double strength) {
        Mesh.Builder b = new Mesh.Builder();
        double hw = w / 2.0;
        double hd = d / 2.0;
        double tail = Math.max(1.0, hw - 1.6);
        double len = OUTFLOW_LENGTH * (0.6 + 0.4 * strength);
        double drop = 1.03;
        // lamina vertical da borda do canal ate o chao
        b.box(v(0, drop / 2, -hd - 0.04), tail * 2, drop, 0.08, RIVER);
        for (int i = 0; i < 4; i++) {
            double y = drop - ((i + phase * 3) % 4) / 4.0 * drop;
            b.box(v(0, y, -hd - 0.09), tail * 2 - 0.2, 0.14, 0.04, FOAM);
        }
        // rio sobre o chao: alarga com a distancia
        double spread = 4.0;
        for (int i = 0; i < 6; i++) {
            double s0 = i / 6.0;
            double s1 = (i + 1) / 6.0;
            double wm = tail * 2 + spread * (s0 + s1) / 2;
            b.box(v(0, 0.04, -hd - 0.1 - len * (s0 + s1) / 2), wm, 0.04, len / 6 + 0.02, RIVER);
        }
        // faixas de espuma que correm e somem
        int stripes = 7;
        for (int i = 0; i < stripes; i++) {
            double s = ((i + phase) % stripes) / stripes;
            double thick = 0.08 + 0.45 * Math.sin(Math.PI * s);
            double width = (tail * 2 + spread * s) * 0.85 * (0.4 + 0.6 * Math.sin(Math.PI * s));
            b.box(v(0, 0.075, -hd - 0.1 - len * s), width, 0.03, thick, FOAM);
        }
        // faixas no proprio canal de fuga
        for (int i = 0; i < 3; i++) {
            double s = ((i + phase) % 3) / 3.0;
            b.box(v(0, 1.07, -hd + APRON - APRON * s), tail * 2 - 0.4, 0.03, 0.12 + 0.3 * Math.sin(Math.PI * s), FOAM);
        }
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
