package com.mmods.realpower.screen;

import com.mmods.realpower.computer.ComputerSnapshot;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Ultimos dados recebidos por cada tela (lado do cliente). */
public final class ScreenData {
    /** Ticks sem atualizacao ate a tela acusar falta de sinal. */
    public static final long STALE_TICKS = 60;

    private record Entry(ComputerSnapshot snapshot, long time) {
    }

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();

    private ScreenData() {
    }

    public static void put(BlockPos screen, ComputerSnapshot snapshot, long gameTime) {
        ENTRIES.put(screen.immutable(), new Entry(snapshot, gameTime));
    }

    /** Dados recentes da tela, ou null se nunca chegaram, estao velhos ou ela nao tem computador. */
    public static ComputerSnapshot get(BlockPos screen, long gameTime) {
        Entry entry = ENTRIES.get(screen);
        return entry == null || gameTime - entry.time > STALE_TICKS ? null : entry.snapshot;
    }
}
