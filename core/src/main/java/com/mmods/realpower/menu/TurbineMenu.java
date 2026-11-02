package com.mmods.realpower.menu;

import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlockEntity;
import com.mmods.realpower.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Menu provisorio do controlador da turbina: mostra o estado e liga/desliga o overlay de construcao. */
public class TurbineMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 6;
    public static final int BUTTON_TOGGLE_OVERLAY = 0;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Lado do servidor. */
    public TurbineMenu(int containerId, Inventory inventory, ContainerLevelAccess access, ContainerData data) {
        super(ModMenus.WIND_TURBINE.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);
        this.access = access;
        this.data = data;
        addDataSlots(data);
    }

    /** Lado do cliente: os dados chegam sincronizados pelo servidor. */
    public TurbineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, ContainerLevelAccess.NULL, new SimpleContainerData(DATA_COUNT));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_TOGGLE_OVERLAY) {
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof WindTurbineControllerBlockEntity turbine) {
                    turbine.setOverlayEnabled(!turbine.overlayEnabled());
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
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof WindTurbineControllerBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }

    public boolean overlayEnabled() {
        return data.get(0) != 0;
    }

    public boolean formed() {
        return data.get(1) != 0;
    }

    public int missingBlocks() {
        return data.get(2);
    }

    public double windSpeed() {
        return data.get(3) / 10.0;
    }

    public int powerKw() {
        return data.get(4);
    }

    public int bufferPercent() {
        return data.get(5);
    }
}
