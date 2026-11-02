package com.mmods.realpower.computer;

import com.mmods.realpower.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Menu do computador central: estado resumido e editor das regras automaticas. Os botoes usam o mecanismo padrao de
 * {@code clickMenuButton}: {@value #ADD} adiciona regra, {@code DELETE + n} remove a regra n e
 * {@code (campo + 1) * FIELD_STRIDE + n} edita o campo (ver {@link ComputerBlockEntity#editRule}) da regra n.
 */
public class ComputerMenu extends AbstractContainerMenu {
    public static final int ADD = 1;
    public static final int DELETE = 900;
    public static final int FIELD_STRIDE = 100;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public ComputerMenu(int containerId, ContainerLevelAccess access, ContainerData data) {
        super(ModMenus.COMPUTER.get(), containerId);
        checkContainerDataCount(data, ComputerBlockEntity.DATA_COUNT);
        this.access = access;
        this.data = data;
        addDataSlots(data);
    }

    /** Lado do cliente: os dados chegam sincronizados pelo servidor. */
    public ComputerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, ContainerLevelAccess.NULL, new SimpleContainerData(ComputerBlockEntity.DATA_COUNT));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == ADD) {
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
                    computer.addRule();
                }
            });
            return true;
        }
        if (id >= DELETE) {
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
                    computer.removeRule(id - DELETE);
                }
            });
            return true;
        }
        if (id >= FIELD_STRIDE) {
            int field = id / FIELD_STRIDE - 1;
            int rule = id % FIELD_STRIDE;
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
                    computer.editRule(rule, field);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof ComputerBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }

    // --- leitura no cliente ---

    public int ruleCount() {
        return data.get(0);
    }

    public boolean redstone() {
        return data.get(1) != 0;
    }

    public int deviceCount() {
        return data.get(2);
    }

    public int storedPercent() {
        return data.get(3);
    }

    public int generationKw() {
        return data.get(4);
    }

    public int deliveredKw() {
        return data.get(5);
    }

    public int deviceLimit() {
        return data.get(6);
    }

    public ControlRule rule(int index) {
        int base = ComputerBlockEntity.HEADER_SLOTS + index * ComputerBlockEntity.RULE_SLOTS;
        ControlRule.Quantity[] quantities = ControlRule.Quantity.values();
        ControlRule.Action[] actions = ControlRule.Action.values();
        return new ControlRule(quantities[Math.floorMod(data.get(base), quantities.length)], data.get(base + 1) != 0,
                data.get(base + 2), actions[Math.floorMod(data.get(base + 3), actions.length)], data.get(base + 4));
    }
}
