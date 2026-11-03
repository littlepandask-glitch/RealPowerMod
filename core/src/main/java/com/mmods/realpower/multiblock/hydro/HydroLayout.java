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

    /**
     * Celulas do conduto: viram agua quando a comporta abre e levam a agua da tomada (y=2, na parede de montante) por baixo do
     * salao ate o canal de fuga do patio (y=1), de onde ela cai no terreno a jusante. Fechadas, sao solidas ou ocas como o resto.
     */
    public static boolean conduit(int width, int height, int depth, int lx, int lz, int y) {
        int halfD = (depth - 1) / 2;
        int hall = -halfD + APRON;
        if (Math.abs(lx) > (width - 1) / 2 || lz < -halfD || lz > halfD) {
            return false;
        }
        if (lz < hall) {
            // patio de jusante: a agua sai por toda a area livre, na largura inteira da unidade
            return y == 1 || lx == 0 && lz == hall - 1 && y == 2;
        }
        return lx == 0 && y == 2;
    }

    public static boolean solid(int width, int height, int depth, int lx, int lz, int y) {
        int halfD = (depth - 1) / 2;
        if (y == 0 || lz > halfD - wallCells(depth)) {
            return true;
        }
        return lz >= -halfD + APRON && y <= hallHeight(height);
    }
}
