package com.mmods.realpower.screen;

import com.mmods.realpower.computer.ComputerSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Geometria da interface das telas, em pixels do painel ({@value #PX_PER_BLOCK} por bloco, y para baixo). Servidor
 * (para tratar cliques) e cliente (para desenhar) usam as mesmas formulas, entao os botoes sempre coincidem.
 */
public final class ScreenLayout {
    public static final int PX_PER_BLOCK = 64;
    public static final int TAB_HEIGHT = 12;
    public static final int PAGES = 4;
    public static final int ROW_HEIGHT = 18;

    public static final int PAGE_OVERVIEW = 0;
    public static final int PAGE_DEVICES = 1;
    public static final int PAGE_GRAPHS = 2;
    public static final int PAGE_ALARMS = 3;

    public enum Kind { TAB, WINDOW, SCROLL, TOGGLE, LIMIT_DOWN, LIMIT_UP }

    /** Botao clicavel; {@code arg} e a pagina (TAB), o sentido (SCROLL) ou o indice do equipamento (demais). */
    public record Button(int x, int y, int w, int h, Kind kind, int arg, BlockPos port) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private ScreenLayout() {
    }

    public static int pixelWidth(ScreenGroup group) {
        return group.width() * PX_PER_BLOCK;
    }

    public static int pixelHeight(ScreenGroup group) {
        return group.height() * PX_PER_BLOCK;
    }

    /** Quantas linhas de equipamento cabem na pagina de geradores. */
    public static int visibleRows(int height) {
        return Math.max(1, (height - TAB_HEIGHT - 16) / ROW_HEIGHT);
    }

    public static List<Button> buttons(int page, int window, int scroll, int width, int height, ComputerSnapshot snapshot) {
        List<Button> list = new ArrayList<>();
        int tabWidth = width / PAGES;
        for (int i = 0; i < PAGES; i++) {
            list.add(new Button(i * tabWidth, 0, tabWidth, TAB_HEIGHT, Kind.TAB, i, null));
        }
        if (page == PAGE_GRAPHS) {
            list.add(new Button(width - 48, TAB_HEIGHT + 2, 46, 10, Kind.WINDOW, 0, null));
        } else if (page == PAGE_DEVICES && snapshot != null) {
            int rows = visibleRows(height);
            List<ComputerSnapshot.Device> devices = snapshot.devices();
            for (int row = 0; row < rows && scroll + row < devices.size(); row++) {
                ComputerSnapshot.Device device = devices.get(scroll + row);
                if (!device.controllable()) {
                    continue;
                }
                int y = TAB_HEIGHT + 2 + row * ROW_HEIGHT + 3;
                int index = scroll + row;
                list.add(new Button(width - 62, y, 24, 12, Kind.TOGGLE, index, device.port()));
                list.add(new Button(width - 36, y, 12, 12, Kind.LIMIT_DOWN, index, device.port()));
                list.add(new Button(width - 22, y, 12, 12, Kind.LIMIT_UP, index, device.port()));
            }
            if (snapshot.devices().size() > rows) {
                list.add(new Button(width - 32, height - 12, 14, 10, Kind.SCROLL, -1, null));
                list.add(new Button(width - 16, height - 12, 14, 10, Kind.SCROLL, 1, null));
            }
        }
        return list;
    }
}
