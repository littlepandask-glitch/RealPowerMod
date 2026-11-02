package com.mmods.realpower.client;

import com.mmods.realpower.computer.ComputerMenu;
import com.mmods.realpower.computer.ControlRule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Tela do computador central: resumo da rede e editor das regras automaticas (botoes ciclicos por campo). */
public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {
    private static final int ROW_Y = 58;
    private static final int ROW_HEIGHT = 20;

    /** Botoes de uma linha de regra. */
    private record Row(Button quantity, Button comparator, Button thresholdDown, Button thresholdUp, Button action,
                       Button argDown, Button argUp, Button delete) {
        List<Button> all() {
            return List.of(quantity, comparator, thresholdDown, thresholdUp, action, argDown, argUp, delete);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private Button addButton;

    public ComputerScreen(ComputerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 292;
        this.imageHeight = ROW_Y + ControlRule.MAX_RULES * ROW_HEIGHT + 30;
        this.inventoryLabelY = Integer.MIN_VALUE / 2;
    }

    private Button button(int x, int y, int width, Component label, int id) {
        return addRenderableWidget(Button.builder(label, b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                .bounds(leftPos + x, topPos + y, width, 18).build());
    }

    /** Id de botao de menu que edita o campo {@code field} da regra {@code rule}. */
    private static int field(int field, int rule) {
        return (field + 1) * ComputerMenu.FIELD_STRIDE + rule;
    }

    @Override
    protected void init() {
        super.init();
        rows.clear();
        for (int r = 0; r < ControlRule.MAX_RULES; r++) {
            int y = ROW_Y + r * ROW_HEIGHT;
            rows.add(new Row(
                    button(8, y, 62, Component.empty(), field(0, r)),
                    button(72, y, 16, Component.empty(), field(1, r)),
                    button(90, y, 14, Component.literal("-"), field(3, r)),
                    button(138, y, 14, Component.literal("+"), field(2, r)),
                    button(156, y, 50, Component.empty(), field(4, r)),
                    button(208, y, 14, Component.literal("-"), field(6, r)),
                    button(250, y, 14, Component.literal("+"), field(5, r)),
                    button(270, y, 14, Component.literal("x"), ComputerMenu.DELETE + r)));
        }
        addButton = button(8, imageHeight - 24, imageWidth - 16, Component.translatable("menu.realpower.computer.add_rule"),
                ComputerMenu.ADD);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int count = menu.ruleCount();
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            boolean visible = r < count;
            row.all().forEach(b -> b.visible = visible);
            if (visible) {
                ControlRule rule = menu.rule(r);
                row.quantity().setMessage(Component.translatable("menu.realpower.computer.quantity." + rule.quantity().ordinal()));
                row.comparator().setMessage(Component.literal(rule.above() ? ">" : "<"));
                row.action().setMessage(Component.translatable("menu.realpower.computer.action." + rule.action().ordinal()));
                boolean limit = rule.action() == ControlRule.Action.LIMIT;
                row.argDown().visible = limit;
                row.argUp().visible = limit;
            }
        }
        addButton.active = count < ControlRule.MAX_RULES;
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
        graphics.drawString(font, title, 8, 7, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("menu.realpower.computer.summary", menu.deviceCount(),
                menu.deviceLimit(), menu.generationKw(), menu.deliveredKw(), menu.storedPercent()), 8, 20, 0xE0E0E0, false);
        graphics.drawString(font, Component.translatable(menu.redstone() ? "menu.realpower.computer.redstone_on"
                : "menu.realpower.computer.redstone_off"), 8, 32, menu.redstone() ? 0xFF6060 : 0xA0A0A0, false);
        graphics.drawString(font, Component.translatable("menu.realpower.computer.rules"), 8, 46, 0xB0C8E0, false);
        for (int r = 0; r < menu.ruleCount(); r++) {
            ControlRule rule = menu.rule(r);
            int y = ROW_Y + r * ROW_HEIGHT + 5;
            String unit = rule.quantity() == ControlRule.Quantity.STORED ? "%" : "kW";
            graphics.drawString(font, rule.threshold() + unit, 107, y, 0xFFFFFF, false);
            if (rule.action() == ControlRule.Action.LIMIT) {
                graphics.drawString(font, rule.arg() + "%", 225, y, 0xFFFFFF, false);
            }
        }
    }
}
