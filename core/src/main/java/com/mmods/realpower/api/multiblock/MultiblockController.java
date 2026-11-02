package com.mmods.realpower.api.multiblock;

/** Contrato de um bloco controlador de multibloco. Addons e dashboards leem estruturas por aqui. */
public interface MultiblockController {
    /** A estrutura esta formada e operando. */
    boolean isFormed();

    /** Pede uma nova validacao assim que possivel (ex.: depois de o jogador alterar a estrutura). */
    void requestRevalidation();

    /** Ultimo resultado de validacao; nunca nulo. */
    ValidationResult lastResult();
}
