package com.mmods.realpower.cable;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Cabo: so existe para entrar e sair da rede quando o chunk carrega/descarrega e expor a entrada de energia. */
public class CableBlockEntity extends BlockEntity implements Tiered {
    /** Entrada de energia da rede: aceita e nunca entrega (a entrega e feita pela propria rede). */
    private final IEnergyStorage port = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            CableNetwork network = level == null ? null : CableNetworks.networkAt(level, worldPosition);
            return network == null ? 0 : network.accept(toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            CableNetwork network = level == null ? null : CableNetworks.networkAt(level, worldPosition);
            return network == null ? 0 : (int) Math.min(Integer.MAX_VALUE, network.storedEnergy());
        }

        @Override
        public int getMaxEnergyStored() {
            CableNetwork network = level == null ? null : CableNetworks.networkAt(level, worldPosition);
            return network == null ? 0 : (int) Math.min(Integer.MAX_VALUE, network.capacity());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state);
    }

    @Override
    public Tier tier() {
        return ((Tiered) getBlockState().getBlock()).tier();
    }

    public IEnergyStorage energyPort() {
        return port;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        CableNetworks.register(this);
    }

    @Override
    public void onChunkUnloaded() {
        CableNetworks.unregister(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        CableNetworks.unregister(this);
        super.setRemoved();
    }
}
