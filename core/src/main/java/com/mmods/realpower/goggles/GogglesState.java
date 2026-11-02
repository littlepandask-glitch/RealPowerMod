package com.mmods.realpower.goggles;

import java.util.List;
import net.minecraft.network.chat.Component;

/** Linhas atuais do balao dos oculos (lado do cliente); o servidor envia, o HUD desenha. */
public final class GogglesState {
    private static List<Component> lines = List.of();
    private static long updatedAt = Long.MIN_VALUE;

    private GogglesState() {
    }

    public static void set(List<Component> newLines, long gameTime) {
        lines = newLines;
        updatedAt = gameTime;
    }

    /** Linhas a mostrar; vazio se nada chegou recentemente. */
    public static List<Component> lines(long gameTime) {
        return gameTime - updatedAt > 30 ? List.of() : lines;
    }
}
