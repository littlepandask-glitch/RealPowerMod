package com.mmods.realpower.render;

import java.util.List;

/**
 * Estilo visual dos blocos funcionais novos (transformador, banco de capacitores...): chapas de aco cinza com cantos
 * luminosos, paineis escuros com raio e barras de LED. A cor da chapa e do brilho muda por tier.
 */
public final class IndustrialStyle {
    static final float[] DARK = {0.08f, 0.09f, 0.11f};
    static final float[] FIN = {0.17f, 0.18f, 0.22f};
    static final float[] STEEL = {0.34f, 0.36f, 0.40f};
    static final float[] TANK = {0.58f, 0.60f, 0.64f};
    static final float[] SCREEN = {0.04f, 0.05f, 0.07f};
    static final float[] LED_OFF = {0.12f, 0.15f, 0.20f};

    /** Cores de um tier: chapa estrutural e brilho. */
    public record Palette(float[] plate, float[] accent) {
        float[] shade() {
            return new float[]{plate[0] * 0.7f, plate[1] * 0.7f, plate[2] * 0.7f};
        }
    }

    private static final Palette[] PALETTES = {
            new Palette(new float[]{0.46f, 0.49f, 0.55f}, new float[]{0.27f, 0.62f, 1.00f}),
            new Palette(new float[]{0.44f, 0.55f, 0.46f}, new float[]{0.35f, 0.92f, 0.47f}),
            new Palette(new float[]{0.58f, 0.53f, 0.38f}, new float[]{1.00f, 0.84f, 0.24f}),
            new Palette(new float[]{0.60f, 0.45f, 0.36f}, new float[]{1.00f, 0.50f, 0.16f}),
            new Palette(new float[]{0.52f, 0.42f, 0.62f}, new float[]{0.92f, 0.35f, 1.00f})};

    private IndustrialStyle() {
    }

    /** Paleta do tier de indice {@code tierOrdinal} (ordem de {@code Tier}). */
    public static Palette palette(int tierOrdinal) {
        return PALETTES[Math.max(0, Math.min(PALETTES.length - 1, tierOrdinal))];
    }

    static Vec v(double x, double y, double z) {
        return new Vec(x, y, z);
    }

    /** Cubo de chapa com um quadrado luminoso em cada face. */
    static void node(Mesh.Builder b, Vec c, double s, Palette p) {
        b.box(c, s, s, s, p.plate());
        b.glow(true);
        b.box(c, s + 0.03, s * 0.55, s * 0.55, p.accent());
        b.box(c, s * 0.55, s + 0.03, s * 0.55, p.accent());
        b.box(c, s * 0.55, s * 0.55, s + 0.03, p.accent());
        b.glow(false);
    }

    /** Raio luminoso no plano z = {@code z}, voltado para {@code dirZ} (-1 frente, +1 fundo); {@code size} e a altura. */
    static void bolt(Mesh.Builder b, double cx, double cy, double z, int dirZ, double size, float[] rgb) {
        Vec inside = v(cx, cy, z - dirZ);
        double w = size * 0.22;
        b.glow(true);
        stroke(b, cx + size * 0.20, cy + size * 0.5, cx - size * 0.05, cy, w, z, inside, rgb);
        stroke(b, cx + size * 0.05, cy, cx - size * 0.20, cy - size * 0.5, w, z, inside, rgb);
        b.box(v(cx, cy, z + dirZ * 0.005), size * 0.42, size * 0.1, 0.01, rgb);
        b.glow(false);
    }

    private static void stroke(Mesh.Builder b, double x0, double y0, double x1, double y1, double w, double z, Vec inside, float[] rgb) {
        b.quad(v(x0 - w / 2, y0, z), v(x0 + w / 2, y0, z), v(x1 + w / 2, y1, z), v(x1 - w / 2, y1, z), inside, rgb);
    }

    /** Placa com tela escura e {@code count} barras de LED crescentes; as primeiras {@code lit} acesas. */
    static void ledPad(Mesh.Builder b, double cx, double cy, double z, int dirZ, double width, double height, int count, int lit, Palette p) {
        b.box(v(cx, cy, z), width, height, 0.1, p.plate());
        b.box(v(cx, cy, z + dirZ * 0.03), width - 0.12, height - 0.12, 0.08, SCREEN);
        double slot = (width - 0.2) / count;
        for (int i = 0; i < count; i++) {
            double barH = (height - 0.24) * (count == 1 ? 1.0 : 0.4 + 0.6 * i / (count - 1));
            boolean on = i < lit;
            b.glow(on);
            b.box(v(cx - (width - 0.2) / 2 + slot * (i + 0.5), cy - (height - 0.24) / 2 + barH / 2, z + dirZ * 0.075),
                    slot * 0.55, barH, 0.03, on ? p.accent() : LED_OFF);
            b.glow(false);
        }
    }

    /** Cilindro vertical de {@code sides} lados com tampas. */
    static void cylinder(Mesh.Builder b, double cx, double cz, double y0, double y1, double r, float[] rgb) {
        int sides = 10;
        Vec[] bottom = new Vec[sides];
        Vec[] top = new Vec[sides];
        for (int i = 0; i < sides; i++) {
            double a = Math.PI * 2 * i / sides;
            bottom[i] = v(cx + r * Math.cos(a), y0, cz + r * Math.sin(a));
            top[i] = v(cx + r * Math.cos(a), y1, cz + r * Math.sin(a));
        }
        Vec mid = v(cx, (y0 + y1) / 2, cz);
        b.strip(List.of(bottom, top), rgb);
        b.cap(bottom, v(cx, y0, cz), mid, rgb);
        b.cap(top, v(cx, y1, cz), mid, rgb);
    }
}
