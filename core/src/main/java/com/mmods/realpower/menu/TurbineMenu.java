package com.mmods.realpower.menu;

import com.mmods.realpower.multiblock.wind.TurbineAutobuild;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlockEntity;
import com.mmods.realpower.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Menu provisorio do controlador da turbina: mostra o estado e liga/desliga o overlay de construcao. */
public class TurbineMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 6;
    public static final int BUTTON_TOGGLE_OVERLAY = 0;
    public static final int BUTTON_AUTOBUILD = 1;
    private static final int AUTOBUILD_CHECK_TICKS = 20;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Player player;
    /** 1 quando ha blocos suficientes (inventario, baus proximos ou criativo) para a construcao automatica. */
    private final DataSlot autobuild = DataSlot.standalone();
    private int checkCountdown;

    /** Lado do servidor. */
    public TurbineMenu(int containerId, Inventory inventory, ContainerLevelAccess access, ContainerData data) {
        super(ModMenus.WIND_TURBINE.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);
        this.access = access;
        this.data = data;
        this.player = inventory.player;
        addDataSlots(data);
        addDataSlot(autobuild);
    }

    /** Servidor: reavalia a disponibilidade de blocos uma vez por segundo (a checagem varre o projeto). */
    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide && checkCountdown-- <= 0) {
            checkCountdown = AUTOBUILD_CHECK_TICKS;
            autobuild.set(access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof WindTurbineControllerBlockEntity turbine
                    && !turbine.isFormed()
                    && TurbineAutobuild.canBuild(player, level, pos, turbine.getBlockState().getValue(WindTurbineControllerBlock.FACING)),
                    false) ? 1 : 0);
        }
        super.broadcastChanges();
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
        if (id == BUTTON_AUTOBUILD) {
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof WindTurbineControllerBlockEntity turbine) {
                    int placed = TurbineAutobuild.build(player, level, pos, turbine.getBlockState().getValue(WindTurbineControllerBlock.FACING));
                    player.displayClientMessage(Component.translatable(placed > 0
                            ? "message.realpower.autobuild.done" : "message.realpower.autobuild.failed", placed), true);
                    turbine.requestRevalidation();
                    checkCountdown = 0;
                }
            });
            return true;
        }
        return false;
    }

    public boolean canAutobuild() {
        return autobuild.get() != 0;
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
