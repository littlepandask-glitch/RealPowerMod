package com.mmods.realpower.render;

/**
 * Malha da sala de comando (9 x 5 x 7 m): predio de concreto com porta na frente (-z), janelas, telhado com antena e, por
 * dentro, a mesa de controle ao fundo (+z) com monitores, painel de sinalizacao e luminarias. Referencial local: x lateral,
 * y para cima a partir do piso, origem no centro do piso.
 */
public final class ControlRoomModel {
    private static final float[] WALL = {0.70f, 0.70f, 0.67f};
    private static final float[] WALL_DARK = {0.50f, 0.50f, 0.48f};
    private static final float[] FLOOR = {0.30f, 0.31f, 0.33f};
    private static final float[] ROOF = {0.27f, 0.30f, 0.33f};
    private static final float[] GLASS = {0.14f, 0.20f, 0.28f};
    private static final float[] STEEL = {0.33f, 0.36f, 0.40f};
    private static final float[] DESK = {0.20f, 0.22f, 0.26f};
    private static final float[] SCREEN = {0.06f, 0.12f, 0.16f};
    private static final float[] CHAIR = {0.12f, 0.13f, 0.15f};
    private static final float[] LAMP = {0.95f, 0.96f, 0.92f};
    private static final float[] GREEN = {0.25f, 0.90f, 0.40f};
    private static final float[] AMBER = {1.00f, 0.72f, 0.18f};
    private static final float[] RED = {1.00f, 0.22f, 0.18f};

    private ControlRoomModel() {
    }

    private static Vec v(double x, double y, double z) {
        return new Vec(x, y, z);
    }

    public static Mesh room(float[] dataAccent) {
        Mesh.Builder b = new Mesh.Builder();
        // piso e laje do telhado
        b.box(v(0, 0.5, 0), 9, 1.0, 7, FLOOR);
        b.box(v(0, 4.5, 0), 9.6, 1.0, 7.6, ROOF);
        b.box(v(0, 5.04, 0), 9.8, 0.08, 7.8, WALL_DARK);
        // paredes: fundo, laterais e frente com a porta (vao de 1 m no centro, 2 m de altura)
        b.box(v(0, 2.5, 3), 9, 3, 1, WALL);
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * 4, 2.5, 0), 1, 3, 7, WALL);
            b.box(v(sx * 2.5, 2.5, -3), 4, 3, 1, WALL);
        }
        b.box(v(0, 3.5, -3), 1, 1, 1, WALL);
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * 0.53, 2.0, -3.52), 0.06, 2.0, 0.08, WALL_DARK); // batentes da porta
        }
        // janelas: faixa de vidro nas laterais e no fundo
        for (int s = -1; s <= 1; s += 2) {
            for (double z = -1.5; z <= 1.6; z += 1.5) {
                b.box(v(s * 4.52, 2.7, z), 0.08, 1.1, 1.0, GLASS);
            }
            b.box(v(s * 2.5, 2.7, -3.52), 3.0, 1.1, 0.08, GLASS);
        }
        for (double x = -2.5; x <= 2.6; x += 2.5) {
            b.box(v(x, 2.7, 3.52), 1.4, 1.1, 0.08, GLASS);
        }
        // telhado: antena e ventilacao
        b.beam(v(-3.5, 5.1, 2.5), v(-3.5, 8.0, 2.5), 0.12, STEEL);
        b.beam(v(-3.5, 6.6, 2.5), v(-2.7, 6.6, 2.5), 0.06, STEEL);
        b.glow(true);
        b.box(v(-3.5, 8.05, 2.5), 0.18, 0.18, 0.18, RED);
        b.glow(false);
        b.box(v(2.5, 5.5, 1.5), 2.0, 0.8, 1.6, STEEL);
        // portas de dados nos dois lados, no piso (celulas das pontas)
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * 4, 0.5, 0), 1.02, 1.02, 1.02, STEEL);
            b.glow(true);
            b.box(v(sx * 4.52, 0.5, 0), 0.06, 0.6, 0.6, dataAccent);
            b.box(v(sx * 3.48, 0.5, 0), 0.06, 0.6, 0.6, dataAccent);
            b.glow(false);
        }
        // interior: mesa de controle ao fundo com monitores e cadeiras
        b.box(v(0, 1.5, 2), 7, 1.0, 1, DESK);
        b.box(v(0, 2.05, 2.05), 7.2, 0.1, 1.2, STEEL);
        for (int k = -1; k <= 1; k++) {
            double x = k * 2.2;
            b.box(v(x, 2.6, 2.3), 1.7, 0.95, 0.1, CHAIR);
            b.glow(true);
            b.box(v(x, 2.6, 2.24), 1.5, 0.78, 0.04, SCREEN);
            b.box(v(x - 0.3, 2.75, 2.21), 0.6, 0.05, 0.02, dataAccent);
            b.box(v(x + 0.2, 2.55, 2.21), 0.8, 0.05, 0.02, GREEN);
            b.box(v(x - 0.1, 2.35, 2.21), 0.5, 0.05, 0.02, AMBER);
            b.glow(false);
        }
        for (int k = -1; k <= 1; k++) {
            b.box(v(k * 2.2, 1.45, 0.9), 0.6, 0.1, 0.6, CHAIR);
            b.box(v(k * 2.2, 1.75, 0.6), 0.6, 0.55, 0.08, CHAIR);
            b.box(v(k * 2.2, 1.22, 0.9), 0.1, 0.45, 0.1, STEEL);
        }
        // painel de sinalizacao na parede do fundo e fileira de LEDs
        b.box(v(0, 3.2, 2.47), 6.4, 0.6, 0.06, CHAIR);
        b.glow(true);
        for (int i = -7; i <= 7; i++) {
            b.box(v(i * 0.4, 3.2, 2.43), 0.2, 0.2, 0.03, i % 5 == 0 ? RED : i % 2 == 0 ? GREEN : AMBER);
        }
        // luminarias do teto
        for (int sx = -1; sx <= 1; sx += 2) {
            b.box(v(sx * 2.0, 3.94, 0), 0.3, 0.06, 4.5, LAMP);
        }
        b.glow(false);
        return b.build();
    }
}
