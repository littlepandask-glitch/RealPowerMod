package com.mmods.realpower.multiblock.wind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Projeto padrao da turbina eolica em escala real (1 bloco = 1 metro). O controlador fica no
 * centro da base, em (0,0,0), com o chao logo abaixo; a frente do rotor aponta para a
 * direcao {@code facing} do controlador.
 *
 * <p>Coordenadas locais: {@code u} cresce para a frente (lado do rotor), {@code v} para a
 * direita de quem olha para a frente e {@code y} para cima.
 * <ul>
 *   <li>base: laje de 21x21x3 (a camada superior ao nivel do controlador, duas enterradas);</li>
 *   <li>corpo: torre oca de 80 blocos, cônica (diametro externo 11 na base, 7 no topo), com porta
 *       de 3x3 na parte de tras;</li>
 *   <li>nacele: caixa oca de 15x9x7 no topo, com abertura no piso para subir da torre;</li>
 *   <li>carenagem do rotor: casca elipsoidal a frente da nacele;</li>
 *   <li>helices: 3 pas de 47 blocos a 120 graus, com corda que afina em direcao a ponta.</li>
 * </ul>
 */
public final class TurbineBlueprint {
    public enum Kind { BASE, CASING, LIGHT, FAIRING, BLADE }

    /** Posicao relativa ao controlador. */
    public record Local(int u, int v, int y, Kind kind, boolean tower) {
        public Local(int u, int v, int y, Kind kind) {
            this(u, v, y, kind, false);
        }
    }

    /** Posicao no mundo. */
    public record Part(BlockPos pos, Kind kind, int mask) {
    }

    /** Mascara de colisao completa (todas as 9 sub-colunas 3x3 do bloco). */
    public static final int FULL_MASK = 511;
    /** Espessura da parede do corpo, em metros. */
    public static final double TOWER_WALL = 1.2;

    public static final int SLAB_HALF = 10;
    public static final int SLAB_DEPTH = 2;
    public static final int TOWER_HEIGHT = 80;
    public static final double TOWER_BASE_RADIUS = 5.5;
    public static final double TOWER_TOP_RADIUS = 3.5;
    public static final int NACELLE_BACK = -6;
    public static final int NACELLE_FRONT = 8;
    public static final int NACELLE_HALF_WIDTH = 4;
    /** Escotilha do piso da nacele: blocos com u^2 + v^2 ate este valor ficam vazados. */
    public static final int HATCH_RADIUS_SQ = 7;
    /** Primeira luminaria interna (altura em blocos) e distancia vertical entre niveis. */
    public static final int LIGHT_FIRST = 6;
    public static final int LIGHT_SPACING = 8;
    public static final int NACELLE_FLOOR = TOWER_HEIGHT + 1;
    public static final int NACELLE_ROOF = NACELLE_FLOOR + 6;
    /** Altura do centro do cubo do rotor acima do controlador. */
    public static final int HUB_Y = (NACELLE_FLOOR + NACELLE_ROOF) / 2;
    /** Posicao do plano do rotor a frente do controlador. */
    public static final int HUB_U = NACELLE_FRONT + 2;
    public static final double SPINNER_RADIUS = 3.8;
    public static final double SPINNER_HALF_LENGTH = 6.0;
    public static final double BLADE_ROOT = 3.0;
    public static final double BLADE_LENGTH = 47.0;

    private static final List<Local> LOCAL = build();
    private static final EnumMap<Kind, Integer> COUNTS = countByKind();

    private TurbineBlueprint() {
    }

    /** Todas as posicoes do projeto, no mundo, para um controlador em {@code controller} virado para {@code facing}. */
    public static List<Part> parts(BlockPos controller, Direction facing) {
        Direction right = facing.getClockWise();
        List<Part> parts = new ArrayList<>(LOCAL.size());
        for (Local local : LOCAL) {
            int mask = FULL_MASK;
            if (local.tower()) {
                // colisao do corpo: sub-colunas 3x3 (eixos do mundo) cujo centro cai dentro da parede
                mask = 0;
                double outer = towerRadius(local.y()) + 0.5;
                for (int ix = 0; ix < 3; ix++) {
                    for (int iz = 0; iz < 3; iz++) {
                        double wx = (ix - 1) / 3.0;
                        double wz = (iz - 1) / 3.0;
                        double ou = facing.getStepX() * wx + facing.getStepZ() * wz;
                        double ov = right.getStepX() * wx + right.getStepZ() * wz;
                        if (inWall(local.u() + ou, local.v() + ov, outer)) {
                            mask |= 1 << (ix * 3 + iz);
                        }
                    }
                }
            }
            parts.add(new Part(controller
                    .offset(facing.getStepX() * local.u(), 0, facing.getStepZ() * local.u())
                    .offset(right.getStepX() * local.v(), local.y(), right.getStepZ() * local.v()), local.kind(), mask));
        }
        return parts;
    }

    public static int count(Kind kind) {
        return COUNTS.get(kind);
    }

    public static int totalBlocks() {
        return LOCAL.size();
    }

    private static boolean inWall(double u, double v, double outer) {
        double d = Math.sqrt(u * u + v * v);
        return d <= outer + 1e-9 && d >= outer - TOWER_WALL - 1e-9;
    }

    /** O bloco existe no corpo se alguma sub-coluna dele cai dentro da parede. */
    private static boolean towerCell(int u, int v, int y) {
        double outer = towerRadius(y) + 0.5;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (inWall(u + (i - 1) / 3.0, v + (j - 1) / 3.0, outer)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Raio externo da torre no nivel {@code y} (1 a 80). */
    public static double towerRadius(int y) {
        return TOWER_BASE_RADIUS + (TOWER_TOP_RADIUS - TOWER_BASE_RADIUS) * (y - 1) / (TOWER_HEIGHT - 1.0);
    }

    /** Comprimento da corda (m) da pa na posicao radial {@code s}. */
    public static double chord(double s) {
        double cmax = Math.max(0.9, Math.min(4.2, BLADE_LENGTH * 0.075));
        double f = (s - BLADE_ROOT) / (BLADE_LENGTH - BLADE_ROOT);
        return cmax * (f < 0.18 ? 0.6 + 0.4 * (f / 0.18) : 1.0 - 0.8 * ((f - 0.18) / 0.82));
    }

    private static List<Local> build() {
        Map<Long, Local> cells = new HashMap<>();
        List<Local> order = new ArrayList<>();

        // base: laje 21x21x3; o controlador ocupa (0,0,0)
        for (int u = -SLAB_HALF; u <= SLAB_HALF; u++) {
            for (int v = -SLAB_HALF; v <= SLAB_HALF; v++) {
                for (int y = -SLAB_DEPTH; y <= 0; y++) {
                    if (u == 0 && v == 0 && y == 0) {
                        continue;
                    }
                    put(cells, order, new Local(u, v, y, Kind.BASE));
                }
            }
        }

        // corpo: aneis de espessura ~1 bloco; porta 3x3 atras
        for (int y = 1; y <= TOWER_HEIGHT; y++) {
            int reach = (int) Math.ceil(towerRadius(y) + 0.5) + 1;
            for (int u = -reach; u <= reach; u++) {
                for (int v = -reach; v <= reach; v++) {
                    if (!towerCell(u, v, y)) {
                        continue;
                    }
                    if (y <= 3 && Math.abs(v) <= 1 && u < 0) {
                        continue; // porta
                    }
                    put(cells, order, new Local(u, v, y, Kind.CASING, true));
                }
            }
        }

        // nacele: casca 15x9x7, com abertura no piso sobre o interior da torre
        for (int u = NACELLE_BACK; u <= NACELLE_FRONT; u++) {
            for (int v = -NACELLE_HALF_WIDTH; v <= NACELLE_HALF_WIDTH; v++) {
                for (int y = NACELLE_FLOOR; y <= NACELLE_ROOF; y++) {
                    boolean shell = u == NACELLE_BACK || u == NACELLE_FRONT || Math.abs(v) == NACELLE_HALF_WIDTH
                            || y == NACELLE_FLOOR || y == NACELLE_ROOF;
                    boolean hatch = y == NACELLE_FLOOR && u * u + v * v <= HATCH_RADIUS_SQ;
                    if (shell && !hatch) {
                        put(cells, order, new Local(u, v, y, Kind.CASING));
                    }
                }
            }
        }

        // luminarias: 4 por nivel, coladas na parede interna, a cada LIGHT_SPACING m; 2 no teto da nacele
        for (int y = LIGHT_FIRST; y <= TOWER_HEIGHT - 2; y += LIGHT_SPACING) {
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                int k = 1;
                while (k < 20 && !towerCell(d[0] * (k + 1), d[1] * (k + 1), y)) {
                    k++;
                }
                put(cells, order, new Local(d[0] * k, d[1] * k, y, Kind.LIGHT));
            }
        }
        put(cells, order, new Local(-3, 0, NACELLE_ROOF - 1, Kind.LIGHT));
        put(cells, order, new Local(4, 0, NACELLE_ROOF - 1, Kind.LIGHT));

        // carenagem do rotor: casca elipsoidal em torno do eixo do rotor
        int radius = (int) Math.ceil(SPINNER_RADIUS);
        for (int u = NACELLE_FRONT + 1; u <= HUB_U + (int) SPINNER_HALF_LENGTH; u++) {
            for (int v = -radius; v <= radius; v++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    double value = (v * v + dy * dy) / (SPINNER_RADIUS * SPINNER_RADIUS)
                            + Math.pow((u - HUB_U) / SPINNER_HALF_LENGTH, 2);
                    if (value <= 1.0 && value >= 0.55) {
                        put(cells, order, new Local(u, v, HUB_Y + dy, Kind.FAIRING));
                    }
                }
            }
        }

        // helices: 3 pas no plano do rotor; a carenagem tem prioridade sobre a raiz
        int reach = (int) Math.ceil(BLADE_LENGTH) + 1;
        for (int i = 0; i < 3; i++) {
            double phi = Math.toRadians(i * 120.0);
            double rv = Math.sin(phi);
            double ry = Math.cos(phi);
            double tv = Math.cos(phi);
            double ty = -Math.sin(phi);
            for (int v = -reach; v <= reach; v++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    double s = v * rv + dy * ry;
                    double t = v * tv + dy * ty;
                    if (s < BLADE_ROOT || s > BLADE_LENGTH) {
                        continue;
                    }
                    if (Math.abs(t) > Math.max(0.5, chord(s) / 2.0)) {
                        continue;
                    }
                    putIfAbsent(cells, order, new Local(HUB_U, v, HUB_Y + dy, Kind.BLADE));
                    if (s < 8.0) {
                        putIfAbsent(cells, order, new Local(HUB_U + 1, v, HUB_Y + dy, Kind.BLADE));
                    }
                }
            }
        }
        return List.copyOf(order);
    }

    private static long key(Local l) {
        return ((long) (l.u() + 512) << 40) | ((long) (l.v() + 512) << 20) | (l.y() + 512);
    }

    private static void put(Map<Long, Local> cells, List<Local> order, Local local) {
        if (cells.put(key(local), local) == null) {
            order.add(local);
        }
    }

    private static void putIfAbsent(Map<Long, Local> cells, List<Local> order, Local local) {
        if (cells.putIfAbsent(key(local), local) == null) {
            order.add(local);
        }
    }

    private static EnumMap<Kind, Integer> countByKind() {
        EnumMap<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            counts.put(kind, 0);
        }
        for (Local local : LOCAL) {
            counts.merge(local.kind(), 1, Integer::sum);
        }
        return counts;
    }
}
