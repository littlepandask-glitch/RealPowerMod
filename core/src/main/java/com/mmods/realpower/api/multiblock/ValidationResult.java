package com.mmods.realpower.api.multiblock;

import java.util.List;
import net.minecraft.network.chat.Component;

/** Resultado da validacao de uma estrutura multibloco: valida, ou a lista de problemas encontrados. */
public record ValidationResult(boolean valid, List<Component> problems) {
    private static final ValidationResult OK = new ValidationResult(true, List.of());

    public static ValidationResult ok() {
        return OK;
    }

    public static ValidationResult invalid(List<Component> problems) {
        return new ValidationResult(false, List.copyOf(problems));
    }
}
