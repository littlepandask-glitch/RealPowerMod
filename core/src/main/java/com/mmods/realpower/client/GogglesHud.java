package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.goggles.GogglesState;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Balao dos oculos de engenheiro: canto superior esquerdo, no estilo dos paineis informativos tipo Waila. */
@EventBusSubscriber(modid = RealPower.MOD_ID, value = Dist.CLIENT)
public final class GogglesHud {
    private GogglesHud() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RealPower.MOD_ID, "goggles"), GogglesHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        List<Component> lines = GogglesState.lines(minecraft.level.getGameTime());
        if (lines.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int x = 10;
        int y = 10;
        int height = lines.size() * (font.lineHeight + 2) + 6;
        graphics.fill(x - 5, y - 4, x + width + 6, y - 4 + height, 0xC0101418);
        graphics.fill(x - 5, y - 4, x - 3, y - 4 + height, 0xFF45C7FF);
        int row = y;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, row, i == 0 ? 0xFFFFFF : 0xB8C4CC, false);
            row += font.lineHeight + 2;
        }
    }
}
