package com.mmods.realpower.client;

import com.mmods.realpower.computer.ComputerAlarms;
import com.mmods.realpower.computer.ComputerSnapshot;
import com.mmods.realpower.screen.ScreenBlock;
import com.mmods.realpower.screen.ScreenBlockEntity;
import com.mmods.realpower.screen.ScreenData;
import com.mmods.realpower.screen.ScreenGroup;
import com.mmods.realpower.screen.ScreenLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Desenha o painel de telas (mestre do retangulo): abas, geradores, graficos de consumo e alarmes. */
public class ScreenRenderer implements BlockEntityRenderer<ScreenBlockEntity> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int BG = 0xFF0B1620;
    private static final int PANEL = 0xFF14283A;
    private static final int TAB_ON = 0xFF1E5A78;
    private static final int TAB_OFF = 0xFF132434;
    private static final int TEXT = 0xFFD7E6F0;
    private static final int MUTED = 0xFF7F95A6;
    private static final int GEN = 0xFF45E0A0;
    private static final int OUT = 0xFFFFC04A;
    private static final int STORE = 0xFF4AB8FF;
    private static final int BAD = 0xFFFF5A5A;
    private static final int WARN = 0xFFFFC04A;
    private static final String[] TAB_KEYS = {"overview", "devices", "graphs", "alarms"};

    private static final float TEXT_LIFT = 0.4F;

    /** Estado de desenho de um quadro: matriz do painel, buffers e fonte. */
    private record Canvas(Matrix4f matrix, VertexConsumer quads, MultiBufferSource buffers, Font font) {
        void rect(float x0, float y0, float x1, float y1, float z, int argb) {
            quads.addVertex(matrix, x0, y0, z).setColor(argb);
            quads.addVertex(matrix, x0, y1, z).setColor(argb);
            quads.addVertex(matrix, x1, y1, z).setColor(argb);
            quads.addVertex(matrix, x1, y0, z).setColor(argb);
            quads.addVertex(matrix, x0, y0, z).setColor(argb);
            quads.addVertex(matrix, x1, y0, z).setColor(argb);
            quads.addVertex(matrix, x1, y1, z).setColor(argb);
            quads.addVertex(matrix, x0, y1, z).setColor(argb);
        }

        void textAt(Component text, float x, float y, int color, float scale) {
            Matrix4f scaled = new Matrix4f(matrix).translate(x, y, -TEXT_LIFT).scale(scale, scale, 1.0F);
            font.drawInBatch(text, 0, 0, color, false, scaled, buffers, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        }

        int width(Component text) {
            return font.width(text);
        }
    }

    @Override
    public void render(ScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (screen.getLevel() == null) {
            return;
        }
        ScreenGroup group = screen.group();
        if (!group.master().equals(screen.getBlockPos())) {
            return;
        }
        int width = ScreenLayout.pixelWidth(group);
        int height = ScreenLayout.pixelHeight(group);
        float front = -0.5F + ScreenBlock.THICKNESS / 16.0F + 0.002F;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-group.facing().toYRot()));
        pose.translate(-0.5, 0.5, front);
        float scale = 1.0F / ScreenLayout.PX_PER_BLOCK;
        pose.scale(scale, -scale, -scale);
        // z do painel cresce para dentro da placa; "levantar" um elemento e usar z negativo

        Canvas canvas = new Canvas(pose.last().pose(), buffers.getBuffer(RenderType.gui()), buffers, Minecraft.getInstance().font);
        ComputerSnapshot snapshot = ScreenData.get(group.master(), screen.getLevel().getGameTime());
        canvas.rect(0, 0, width, height, 0.0F, BG);
        drawTabs(canvas, screen.page(), width);
        if (snapshot == null) {
            Component none = Component.translatable("screen.realpower.no_signal");
            canvas.textAt(none, (width - canvas.width(none)) / 2.0F, height / 2.0F, BAD, 1.0F);
        } else {
            switch (screen.page()) {
                case ScreenLayout.PAGE_DEVICES -> drawDevices(canvas, screen, snapshot, width, height);
                case ScreenLayout.PAGE_GRAPHS -> drawGraphs(canvas, screen, snapshot, width, height);
                case ScreenLayout.PAGE_ALARMS -> drawAlarms(canvas, snapshot, width, height);
                default -> drawOverview(canvas, snapshot, width, height);
            }
            drawButtons(canvas, screen, snapshot, width, height);
        }
        pose.popPose();
    }

    private static void drawTabs(Canvas c, int page, int width) {
        int tabWidth = width / ScreenLayout.PAGES;
        for (int i = 0; i < ScreenLayout.PAGES; i++) {
            c.rect(i * tabWidth + 1, 1, (i + 1) * tabWidth - 1, ScreenLayout.TAB_HEIGHT - 1, -0.1F, i == page ? TAB_ON : TAB_OFF);
            Component label = Component.translatable("screen.realpower.tab." + TAB_KEYS[i]);
            c.textAt(label, i * tabWidth + Math.max(2, (tabWidth - c.width(label) * 0.75F) / 2.0F), 3, TEXT, 0.75F);
        }
    }

    private static void drawButtons(Canvas c, ScreenBlockEntity screen, ComputerSnapshot snapshot, int width, int height) {
        for (ScreenLayout.Button button : ScreenLayout.buttons(screen.page(), screen.window(), screen.scroll(), width, height, snapshot)) {
            if (button.kind() == ScreenLayout.Kind.TAB) {
                continue;
            }
            Component label;
            int color = TAB_ON;
            switch (button.kind()) {
                case TOGGLE -> {
                    boolean on = snapshot.devices().get(button.arg()).enabled();
                    label = Component.translatable(on ? "screen.realpower.on" : "screen.realpower.off");
                    color = on ? 0xFF1E7A4A : 0xFF7A2A2A;
                }
                case LIMIT_DOWN -> label = Component.literal("-");
                case LIMIT_UP -> label = Component.literal("+");
                case SCROLL -> label = Component.literal(button.arg() < 0 ? "<" : ">");
                default -> label = Component.translatable("screen.realpower.window." + screen.window());
            }
            c.rect(button.x(), button.y(), button.x() + button.w(), button.y() + button.h(), -0.1F, color);
            c.textAt(label, button.x() + (button.w() - c.width(label) * 0.75F) / 2.0F, button.y() + (button.h() - 6.5F) / 2.0F, TEXT, 0.75F);
        }
    }

    // ---------- paginas ----------

    private static Component power(double fePerTick) {
        double kw = fePerTick; // 1 FE/t = 1 kW
        return Component.literal(kw >= 1000.0 ? String.format("%.2f MW", kw / 1000.0) : String.format("%.1f kW", kw));
    }

    private static float last(float[] series) {
        return series.length == 0 ? 0.0F : series[series.length - 1];
    }

    private static void drawOverview(Canvas c, ComputerSnapshot s, int width, int height) {
        int y = ScreenLayout.TAB_HEIGHT + 4;
        c.textAt(Component.translatable("screen.realpower.devices", s.devices().size(), s.deviceLimit(), s.networkSize()), 4, y, MUTED, 0.75F);
        y += 10;
        long stored = 0;
        long capacity = 0;
        for (ComputerSnapshot.Device d : s.devices()) {
            stored += d.stored();
            capacity += d.capacity();
        }
        statLine(c, "generation", power(last(s.generationFine())), GEN, 4, y);
        statLine(c, "delivered", power(last(s.deliveredFine())), OUT, 4, y + 12);
        statLine(c, "stored", Component.literal(capacity == 0 ? "-" : String.format("%d%%", stored * 100L / capacity)), STORE, 4, y + 24);
        int graphTop = y + 38;
        if (height - graphTop > 20) {
            graph(c, s.generationFine(), 4, graphTop, width - 4, height - 4, GEN, 0.0F);
        }
    }

    private static void statLine(Canvas c, String key, Component value, int color, int x, int y) {
        c.rect(x, y + 1, x + 3, y + 8, -0.1F, color);
        c.textAt(Component.translatable("screen.realpower." + key), x + 7, y, MUTED, 0.75F);
        c.textAt(value, x + 62, y, TEXT, 0.75F);
    }

    private static void drawDevices(Canvas c, ScreenBlockEntity screen, ComputerSnapshot s, int width, int height) {
        int rows = ScreenLayout.visibleRows(height);
        List<ComputerSnapshot.Device> devices = s.devices();
        for (int row = 0; row < rows && screen.scroll() + row < devices.size(); row++) {
            ComputerSnapshot.Device d = devices.get(screen.scroll() + row);
            int y = ScreenLayout.TAB_HEIGHT + 2 + row * ScreenLayout.ROW_HEIGHT;
            c.rect(1, y, width - 1, y + ScreenLayout.ROW_HEIGHT - 2, -0.05F, PANEL);
            c.textAt(d.name(), 4, y + 2, d.online() ? TEXT : BAD, 0.75F);
            Component info = d.online()
                    ? Component.translatable("screen.realpower.device_info", power(d.powerWatts() / 1000.0), d.limit())
                    : Component.translatable("screen.realpower.offline");
            c.textAt(info, 4, y + 10, MUTED, 0.65F);
            if (d.capacity() > 0) {
                float fill = Math.min(1.0F, d.stored() / (float) d.capacity());
                c.rect(4, y + ScreenLayout.ROW_HEIGHT - 4, width - 66, y + ScreenLayout.ROW_HEIGHT - 3, -0.1F, TAB_OFF);
                c.rect(4, y + ScreenLayout.ROW_HEIGHT - 4, 4 + (width - 70) * fill, y + ScreenLayout.ROW_HEIGHT - 3, -0.15F, STORE);
            }
        }
    }

    private static void drawGraphs(Canvas c, ScreenBlockEntity screen, ComputerSnapshot s, int width, int height) {
        boolean coarse = screen.window() == 1;
        int top = ScreenLayout.TAB_HEIGHT + 4;
        int areaHeight = (height - top - 4) / 3;
        float[][] series = {coarse ? s.generationCoarse() : s.generationFine(), coarse ? s.deliveredCoarse() : s.deliveredFine(),
                coarse ? s.storedCoarse() : s.storedFine()};
        String[] keys = {"generation", "delivered", "stored"};
        int[] colors = {GEN, OUT, STORE};
        for (int i = 0; i < 3; i++) {
            int y0 = top + i * areaHeight;
            c.textAt(Component.translatable("screen.realpower." + keys[i]), 4, y0, colors[i], 0.65F);
            Component now = i == 2 ? Component.literal(String.format("%d FE", (long) last(series[i]))) : power(last(series[i]));
            c.textAt(now, width - 52, y0, TEXT, 0.65F);
            graph(c, series[i], 4, y0 + 8, width - 4, y0 + areaHeight - 2, colors[i], 0.0F);
        }
    }

    private static void drawAlarms(Canvas c, ComputerSnapshot s, int width, int height) {
        List<ComputerAlarms.Alarm> alarms = ComputerAlarms.of(s);
        int y = ScreenLayout.TAB_HEIGHT + 4;
        for (ComputerAlarms.Alarm alarm : alarms) {
            if (y > height - 10) {
                break;
            }
            int color = alarm.level() == ComputerAlarms.Level.ERROR ? BAD : alarm.level() == ComputerAlarms.Level.WARNING ? WARN : MUTED;
            c.rect(3, y + 1, 6, y + 7, -0.1F, color);
            c.textAt(alarm.text(), 10, y, TEXT, 0.7F);
            y += 10;
        }
    }

    /** Grafico de area com escala automatica (0 a maximo) dentro do retangulo. */
    private static void graph(Canvas c, float[] values, float x0, float y0, float x1, float y1, int color, float min) {
        c.rect(x0, y0, x1, y1, -0.02F, PANEL);
        if (values.length < 2) {
            return;
        }
        float max = min + 1.0F;
        for (float v : values) {
            max = Math.max(max, v);
        }
        int columns = Math.max(2, Math.min(values.length, (int) (x1 - x0)));
        float columnWidth = (x1 - x0) / columns;
        int fillColor = (color & 0x00FFFFFF) | 0x66000000;
        float previousTop = Float.NaN;
        for (int i = 0; i < columns; i++) {
            int from = i * values.length / columns;
            int to = Math.max(from + 1, (i + 1) * values.length / columns);
            float sum = 0;
            for (int j = from; j < to; j++) {
                sum += values[j];
            }
            float level = (sum / (to - from) - min) / (max - min);
            float top = y1 - (y1 - y0 - 1) * Math.max(0.0F, Math.min(1.0F, level));
            float x = x0 + i * columnWidth;
            c.rect(x, top, x + columnWidth, y1, -0.05F, fillColor);
            float from2 = Float.isNaN(previousTop) ? top : Math.min(previousTop, top);
            float to2 = Float.isNaN(previousTop) ? top + 1 : Math.max(previousTop, top) + 1;
            c.rect(x, from2, x + columnWidth, to2, -0.1F, color);
            previousTop = top;
        }
    }

    @Override
    public AABB getRenderBoundingBox(ScreenBlockEntity screen) {
        ScreenGroup group = screen.group();
        return new AABB(group.master()).inflate(ScreenGroup.MAX_WIDTH, ScreenGroup.MAX_HEIGHT, ScreenGroup.MAX_WIDTH);
    }
}
