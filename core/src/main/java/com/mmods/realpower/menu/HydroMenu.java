package com.mmods.realpower.menu;

import com.mmods.realpower.multiblock.hydro.HydroUnitBlock;
import com.mmods.realpower.multiblock.hydro.HydroUnitBlockEntity;
import com.mmods.realpower.multiblock.hydro.TankJob;
import com.mmods.realpower.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Menu da casa de forca: estado da usina e a obra do tanque (lago) do tamanho que a barragem precisa. */
public class HydroMenu extends AbstractContainerMenu {
    public static final int BUTTON_BUILD_TANK = 0;
    private static final int REFRESH_TICKS = 20;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Player player;
    private int countdown;

    /** Lado do servidor. */
    public HydroMenu(int containerId, Inventory inventory, ContainerLevelAccess access, ContainerData data) {
        super(ModMenus.HYDRO_UNIT.get(), containerId);
        checkContainerDataCount(data, HydroUnitBlockEntity.INFO_COUNT * 2);
        this.access = access;
        this.data = data;
        this.player = inventory.player;
        addDataSlots(data);
    }

    /** Lado do cliente: os dados chegam sincronizados pelo servidor. */
    public HydroMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, ContainerLevelAccess.NULL, new SimpleContainerData(HydroUnitBlockEntity.INFO_COUNT * 2));
    }

    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide && countdown-- <= 0) {
            countdown = REFRESH_TICKS;
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof HydroUnitBlockEntity unit) {
                    unit.refreshInfo();
                }
            });
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_BUILD_TANK) {
            return false;
        }
        access.execute((level, pos) -> {
            if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof HydroUnitBlockEntity unit) {
                boolean started = TankJob.start(server, pos, unit.getBlockState(), player.getUUID());
                player.displayClientMessage(Component.translatable(started
                        ? "message.realpower.tank.started" : "message.realpower.tank.running"), true);
                countdown = 0;
            }
        });
        return true;
    }

    /** Campo {@code i} de {@link HydroUnitBlockEntity#refreshInfo()} (32 bits em dois slots de 16). */
    public int value(int i) {
        return (data.get(2 * i) & 0xFFFF) | (data.get(2 * i + 1) << 16);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof HydroUnitBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 400.0, true);
    }
}
