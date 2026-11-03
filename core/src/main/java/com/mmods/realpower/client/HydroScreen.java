package com.mmods.realpower.client;

import com.mmods.realpower.menu.HydroMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Tela da casa de forca: queda, vazao, area do lago contra a necessaria e o botao de construir o tanque. */
public class HydroScreen extends AbstractContainerScreen<HydroMenu> {
    private Button tankButton;
    private GateSlider gateSlider;
    /** Primeiro clique arma, o segundo confirma. */
    private boolean armed;

    public HydroScreen(HydroMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 230;
        this.imageHeight = 176;
        this.inventoryLabelY = Integer.MIN_VALUE / 2;
    }

    @Override
    protected void init() {
        super.init();
        gateSlider = addRenderableWidget(new GateSlider(leftPos + 10, topPos + imageHeight - 56, imageWidth - 20, 20, menu.value(13)));
        tankButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (armed) {
                armed = false;
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, HydroMenu.BUTTON_BUILD_TANK);
            } else {
                armed = true;
            }
        }).bounds(leftPos + 10, topPos + imageHeight - 30, imageWidth - 20, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        gateSlider.follow(menu.value(13));
        boolean building = menu.value(10) >= 0;
        tankButton.active = !building;
        if (building) {
            armed = false;
            tankButton.setMessage(Component.translatable("menu.realpower.hydro.building", menu.value(10)));
        } else {
            tankButton.setMessage(Component.translatable(armed ? "menu.realpower.hydro.build_confirm" : "menu.realpower.hydro.build"));
        }
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
        boolean formed = menu.value(0) != 0;
        int area = menu.value(1);
        int needed = menu.value(2);
        line(graphics, 0, Component.translatable(formed ? "menu.realpower.status_formed" : "menu.realpower.hydro.idle"),
                formed ? 0x6EE76E : 0xFFB347);
        line(graphics, 1, Component.translatable("menu.realpower.hydro.head", String.format("%.1f", menu.value(3) / 10.0),
                String.format("%.1f", menu.value(4) / 10.0)), menu.value(3) >= menu.value(4) ? 0xE0E0E0 : 0xFFB347);
        line(graphics, 2, Component.translatable("menu.realpower.hydro.lake", area, needed), area >= needed ? 0xE0E0E0 : 0xFFB347);
        line(graphics, 3, Component.translatable("menu.realpower.hydro.flow", String.format("%.1f", menu.value(11) / 10.0)), 0xE0E0E0);
        line(graphics, 4, Component.translatable("menu.realpower.hydro.power", menu.value(5)), 0xE0E0E0);
        line(graphics, 5, Component.translatable("menu.realpower.hydro.units", menu.value(6)), 0xE0E0E0);
        line(graphics, 6, Component.translatable("menu.realpower.hydro.tank", menu.value(7), menu.value(8), menu.value(9)), 0x9EC8FF);
    }

    /** Abertura da comporta em %: o valor vai ao servidor ao soltar o controle (ou ao usar o teclado). */
    private class GateSlider extends AbstractSliderButton {
        private boolean dragging;

        GateSlider(int x, int y, int width, int height, int percent) {
            super(x, y, width, height, Component.empty(), percent / 100.0);
            updateMessage();
        }

        int percent() {
            return (int) Math.round(value * 100.0);
        }

        /** Acompanha o valor do servidor enquanto o jogador nao esta mexendo no controle. */
        void follow(int percent) {
            if (!dragging && !isFocused() && percent() != percent) {
                value = percent / 100.0;
                updateMessage();
            }
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("menu.realpower.hydro.gate", percent()));
        }

        @Override
        protected void applyValue() {
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            dragging = false;
            send();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (handled) {
                send();
            }
            return handled;
        }

        private void send() {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, HydroMenu.BUTTON_GATE_BASE + percent());
        }
    }

    private void line(GuiGraphics graphics, int row, Component text, int color) {
        graphics.drawString(font, text, 10, 24 + row * 12, color, false);
    }
}
