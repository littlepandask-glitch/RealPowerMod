package com.mmods.realpower.multiblock.hydro;

/**
 * Geometria da casa de forca em celulas (referencial local: lx lateral, lz para montante, y para cima; origem no centro do
 * chao). Define o que e solido (piso, parede da barragem, salao e telhado) e o que e oco (apara de jusante e o ar acima do
 * telhado), e serve ao modelo 3D para que o que se ve seja o que se pisa.
 */
public final class HydroLayout {
    /** Profundidade (celulas) do patio de jusante, a frente do salao, onde ficam as portas de energia. */
    public static final int APRON = 2;

    private HydroLayout() {
    }

    /** Espessura (celulas) da parede da barragem na face de montante. */
    public static int wallCells(int depth) {
        return depth <= 9 ? 3 : depth <= 13 ? 5 : 7;
    }

    /** Altura do telhado do salao (indice da celula do telhado). */
    public static int hallHeight(int height) {
        return (int) Math.floor(height * 0.72);
    }

    /** Largura (celulas, impar) da janela da fachada do salao. */
    public static int openingCells(int width) {
        int n = Math.round(width * 0.62F);
        return n % 2 == 0 ? n + 1 : n;
    }

    /** Posicao z (referencial do modelo) do eixo da turbina, no meio do salao. */
    public static double axisZ(int depth) {
        return (APRON - wallCells(depth)) / 2.0;
    }

    /** Raio da caixa espiral da turbina. */
    public static double spiralRadius(int width, int depth) {
        double hallLength = depth - wallCells(depth) - APRON;
        return Math.max(1.0, Math.min(width, hallLength) * 0.30);
    }

    /** Ultima camada de agua da camara e da janela (a janela vai do eixo da turbina ate a verga). */
    public static int topWater(int height) {
        return hallHeight(height) - 2;
    }

    /** Nivel da agua (camada mais alta) na camara do rotor e na janela para uma abertura da comporta de 0 a 1. */
    public static int waterLevel(int height, double opening) {
        return opening <= 0.0 ? 0 : 1 + (int) Math.round(Math.min(1.0, opening) * (topWater(height) - 1));
    }

    /** Ultima fileira (lz) da camara do rotor, que ocupa o salao, na largura da janela, da fachada ate a caixa espiral. */
    private static int chamberEnd(int width, int depth) {
        return Math.min((depth - 1) / 2 - wallCells(depth), (int) Math.ceil(axisZ(depth) + spiralRadius(width, depth)));
    }

    /**
     * Celulas do conduto (onde pode haver agua): a tomada (parede de montante), o tubo forcado no eixo da turbina, a camara do rotor
     * (o salao na largura da janela), a janela da fachada e, la fora, a cortina que cai da janela e o patio de jusante.
     */
    public static boolean conduit(int width, int height, int depth, int lx, int lz, int y) {
        int halfD = (depth - 1) / 2;
        int hall = -halfD + APRON;
        int axis = height * 3 / 10;
        int top = topWater(height);
        if (Math.abs(lx) > (width - 1) / 2 || lz < -halfD || lz > halfD) {
            return false;
        }
        boolean window = Math.abs(lx) <= (openingCells(width) - 1) / 2;
        if (lz < hall) {
            return y == 1 || window && lz == hall - 1 && y <= top;
        }
        if (lz == hall) {
            return window && y >= axis && y <= top;
        }
        if (lz <= chamberEnd(width, depth)) {
            return window && y >= 1 && y <= top;
        }
        return lx == 0 && (y == axis && lz < halfD || lz == halfD && y >= 2 && y <= axis);
    }

    /** Ha agua nesta celula do conduto com a comporta aberta a {@code opening} (0 a 1): o tubo forcado enche logo, o resto sobe com o nivel. */
    public static boolean conduitWater(int width, int height, int depth, int lx, int lz, int y, double opening) {
        if (opening <= 0.0 || !conduit(width, height, depth, lx, lz, y)) {
            return false;
        }
        return lz > chamberEnd(width, depth) || y <= waterLevel(height, opening);
    }

    /** Celula da porta de energia: na parede lateral do salao, no fundo dele (longe do canal de fuga, fora do caminho da agua). */
    public static boolean port(int width, int depth, int lx, int lz, int y) {
        return y == 1 && Math.abs(lx) == (width - 1) / 2 && lz == (depth - 1) / 2 - wallCells(depth);
    }

    public static boolean solid(int width, int height, int depth, int lx, int lz, int y) {
        int halfD = (depth - 1) / 2;
        if (y == 0 || lz > halfD - wallCells(depth)) {
            return true;
        }
        return lz >= -halfD + APRON && y <= hallHeight(height);
    }
}
