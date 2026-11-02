package com.mmods.realpower.api.multiblock;

/**
 * Varredura incremental de uma estrutura. Estruturas em escala real tem milhares de
 * blocos, entao a varredura consome um orcamento de leituras de bloco por tick em vez
 * de rodar tudo de uma vez.
 */
public interface StructureScan {
    /** Orcamento padrao de leituras de bloco por tick. */
    int DEFAULT_BLOCK_BUDGET = 256;

    /**
     * Avanca a varredura lendo no maximo {@code blockBudget} blocos.
     *
     * @return {@code true} quando a varredura terminou
     */
    boolean advance(int blockBudget);

    /** {@code true} se a varredura parou porque encontrou um chunk descarregado; deve ser repetida depois. */
    boolean isUnloaded();

    /** Resultado final; so e valido depois de {@link #advance} retornar {@code true}. */
    ValidationResult result();
}
