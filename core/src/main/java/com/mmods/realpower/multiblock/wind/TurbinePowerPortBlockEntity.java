package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.data.DataDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Porta de energia da turbina. O controlador entrega a energia aqui ({@link #feed()}) e a porta a repassa aos
 * cabos e consumidores encostados nela. Para o mundo ela so se mostra como conectavel: nao aceita energia de fora.
 */
public class TurbinePowerPortBlockEntity extends BlockEntity implements DataDevice {
    private final IEnergyStorage feed = new PortStorage(true);
    private final IEnergyStorage outside = new PortStorage(false);

    /** Controlador da turbina a que esta porta pertence (gravado quando a estrutura se forma). */
    private BlockPos controller;

    public TurbinePowerPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void bindController(BlockPos controller) {
        if (!controller.equals(this.controller)) {
            this.controller = controller.immutable();
            setChanged();
        }
    }

    /** A porta de dados fixada aqui supervisiona a turbina inteira, nao so este bloco. */
    private DataDevice turbine() {
        return controller != null && level != null && level.isLoaded(controller)
                && level.getBlockEntity(controller) instanceof DataDevice device ? device : null;
    }

    @Override
    public Object identity() {
        DataDevice device = turbine();
        return device == null ? this : device.identity();
    }

    @Override
    public Component deviceLabel() {
        DataDevice device = turbine();
        return device == null ? getBlockState().getBlock().getName() : device.deviceLabel();
    }

    @Override
    public Telemetry telemetry() {
        DataDevice device = turbine();
        return device == null ? new Telemetry(false, 0.0, 0L, 0L) : device.telemetry();
    }

    @Override
    public boolean controllable() {
        DataDevice device = turbine();
        return device != null && device.controllable();
    }

    @Override
    public boolean enabled() {
        DataDevice device = turbine();
        return device == null || device.enabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        DataDevice device = turbine();
        if (device != null) {
            device.setEnabled(enabled);
        }
    }

    @Override
    public int limit() {
        DataDevice device = turbine();
        return device == null ? 100 : device.limit();
    }

    @Override
    public void setLimit(int percent) {
        DataDevice device = turbine();
        if (device != null) {
            device.setLimit(percent);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controller != null) {
            tag.putLong("Controller", controller.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }

    /** Lado do controlador: tudo que receber sai pelos vizinhos da porta. */
    public IEnergyStorage feed() {
        return feed;
    }

    /** Lado do mundo: conectavel mas sem aceitar nem entregar energia. */
    public IEnergyStorage outsideView() {
        return outside;
    }

    private final class PortStorage implements IEnergyStorage {
        private final boolean fromController;

        private PortStorage(boolean fromController) {
            this.fromController = fromController;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (!fromController || level == null || level.isClientSide) {
                return 0;
            }
            int remaining = toReceive;
            for (Direction direction : Direction.values()) {
                if (remaining <= 0) {
                    break;
                }
                IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                        worldPosition.relative(direction), direction.getOpposite());
                if (target != null && target.canReceive()) {
                    remaining -= target.receiveEnergy(remaining, simulate);
                }
            }
            return toReceive - remaining;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return fromController;
        }
    }
}
