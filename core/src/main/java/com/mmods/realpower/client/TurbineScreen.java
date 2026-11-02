package com.mmods.realpower.client;

import com.mmods.realpower.menu.TurbineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Tela provisoria do controlador: estado da turbina e botao que liga/desliga o overlay de construcao. */
public class TurbineScreen extends AbstractContainerScreen<TurbineMenu> {
    private Button overlayButton;
    private Button autobuildButton;
    /** Primeiro clique arma, o segundo confirma. */
    private boolean autobuildArmed;

    public TurbineScreen(TurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 160;
        this.inventoryLabelY = Integer.MIN_VALUE / 2; // sem inventario do jogador
    }

    @Override
    protected void init() {
        super.init();
        overlayButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TurbineMenu.BUTTON_TOGGLE_OVERLAY))
                .bounds(leftPos + 10, topPos + imageHeight - 30, imageWidth - 20, 20)
                .build());
        autobuildButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    if (autobuildArmed) {
                        autobuildArmed = false;
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TurbineMenu.BUTTON_AUTOBUILD);
                    } else {
                        autobuildArmed = true;
                    }
                })
                .bounds(leftPos + 10, topPos + imageHeight - 56, imageWidth - 20, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        overlayButton.setMessage(Component.translatable(menu.overlayEnabled()
                ? "menu.realpower.overlay_on" : "menu.realpower.overlay_off"));
        boolean can = menu.canAutobuild();
        if (!can) {
            autobuildArmed = false;
        }
        autobuildButton.active = can;
        autobuildButton.setMessage(Component.translatable(autobuildArmed
                ? "menu.realpower.autobuild_confirm" : "menu.realpower.autobuild"));
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF202428);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFF3B434B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 8, 0xFFFFFF, false);
        int y = 24;
        if (menu.formed()) {
            graphics.drawString(font, Component.translatable("menu.realpower.status_formed"), 10, y, 0x6EE76E, false);
            graphics.drawString(font, Component.translatable("menu.realpower.wind", String.format("%.1f", menu.windSpeed())), 10, y + 12, 0xE0E0E0, false);
            graphics.drawString(font, Component.translatable("menu.realpower.power", menu.powerKw()), 10, y + 24, 0xE0E0E0, false);
            graphics.drawString(font, Component.translatable("menu.realpower.buffer", menu.bufferPercent()), 10, y + 36, 0xE0E0E0, false);
        } else {
            graphics.drawString(font, Component.translatable("menu.realpower.status_incomplete"), 10, y, 0xFFB347, false);
            graphics.drawString(font, Component.translatable("menu.realpower.missing", menu.missingBlocks()), 10, y + 12, 0xE0E0E0, false);
        }
    }
}
