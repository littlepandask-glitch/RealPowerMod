package com.mmods.realpower.cable;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.registry.ModBlockEntities;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
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

    /** Linhas aereas deste no (so o poste/torre do topo de cada pilha tem), sincronizadas para o desenho dos fios. */
    private List<BlockPos> links = List.of();

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CABLE.get(), pos, state);
    }

    @Override
    public Tier tier() {
        return ((Tiered) getBlockState().getBlock()).tier();
    }

    public CableKind kind() {
        return ((CableBlock) getBlockState().getBlock()).kind();
    }

    public List<BlockPos> links() {
        return links;
    }

    void setLinks(List<BlockPos> newLinks) {
        if (!links.equals(newLinks)) {
            links = List.copyOf(newLinks);
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLongArray("Links", links.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        links = Arrays.stream(tag.getLongArray("Links")).mapToObj(BlockPos::of).toList();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
