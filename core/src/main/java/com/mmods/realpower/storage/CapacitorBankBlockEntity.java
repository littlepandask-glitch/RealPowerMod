package com.mmods.realpower.storage;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.data.DataDevice;
import com.mmods.realpower.energy.GeneratorBuffer;
import com.mmods.realpower.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Banco de capacitores: guarda energia (long) e a troca com a rede de cabos pelas portas, limitado pela vazao do tier
 * e pelo limite configurado. Aparece para o computador central como equipamento controlavel (liga/desliga e limite).
 */
public class CapacitorBankBlockEntity extends BlockEntity implements DataDevice {
    private static final int CHECK_INTERVAL = 20;

    private final GeneratorBuffer buffer = new GeneratorBuffer();
    private final Storage storage = new Storage();
    private boolean enabled = true;
    private int limitPercent = 100;

    /** Contabilidade do tick de jogo corrente: quanto ja passou pelas portas (vazao) e o saldo (carga - descarga). */
    private long stamp = Long.MIN_VALUE;
    private long movedThisTick;
    private long netThisTick;
    private long lastNet;
    private int syncedCharge = -1;

    public CapacitorBankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAPACITOR_BANK.get(), pos, state);
        buffer.setCapacity(block().capacity());
    }

    private CapacitorBankBlock block() {
        return (CapacitorBankBlock) getBlockState().getBlock();
    }

    public Tier tier() {
        return block().tier();
    }

    public IEnergyStorage storage() {
        return storage;
    }

    public long storedEnergy() {
        return buffer.stored();
    }

    public long capacity() {
        return buffer.capacity();
    }

    /** Estado de carga (0 a 100), usado pelo desenho e pelos oculos. */
    public int chargePercent() {
        return buffer.capacity() == 0 ? 0 : (int) (buffer.stored() * 100L / buffer.capacity());
    }

    /** Saldo do ultimo tick completo: positivo carregando, negativo descarregando (FE/tick). */
    public long netFlow() {
        return lastNet;
    }

    public long ratePerTick() {
        return tier().maxTransferPerTick() * limitPercent / 100L;
    }

    /** Para testes e comandos: coloca energia direto no banco. */
    public void setStoredEnergy(long value) {
        buffer.setStored(value);
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CapacitorBankBlockEntity bank) {
        bank.account(level);
        long time = level.getGameTime();
        if (time % CHECK_INTERVAL == 0 && state.getBlock() instanceof CapacitorBankBlock block) {
            block.ensureStructure(level, pos, state);
        }
        int charge = bank.chargePercent();
        if (charge != bank.syncedCharge) {
            bank.syncedCharge = charge;
            level.sendBlockUpdated(pos, state, state, 2);
            bank.setChanged();
        }
    }

    /** Abre a contabilidade de um novo tick de jogo, guardando o saldo do anterior. */
    private void account(Level level) {
        long now = level.getGameTime();
        if (now != stamp) {
            lastNet = netThisTick;
            stamp = now;
            movedThisTick = 0L;
            netThisTick = 0L;
        }
    }

    // --- DataDevice ---

    @Override
    public Component deviceLabel() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public Telemetry telemetry() {
        // sem potencia propria: o computador soma o armazenado e deduz o consumo pela variacao dele
        return new Telemetry(true, 0.0, buffer.stored(), buffer.capacity());
    }

    @Override
    public boolean controllable() {
        return true;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setChanged();
    }

    @Override
    public int limit() {
        return limitPercent;
    }

    @Override
    public void setLimit(int percent) {
        limitPercent = Math.max(0, Math.min(100, percent));
        setChanged();
    }

    // --- rede ---

    private final class Storage implements BufferStorage {
        private int move(int amount, boolean simulate, boolean charging) {
            if (level == null || !enabled || amount <= 0) {
                return 0;
            }
            account(level);
            long room = charging ? buffer.capacity() - buffer.stored() : buffer.stored();
            long moved = Math.min(Math.min(amount, room), ratePerTick() - movedThisTick);
            if (moved <= 0L) {
                return 0;
            }
            if (!simulate) {
                movedThisTick += moved;
                if (charging) {
                    buffer.produce(moved);
                    netThisTick += moved;
                } else {
                    buffer.extract(moved, false);
                    netThisTick -= moved;
                }
                setChanged();
            }
            return (int) moved;
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return move(toReceive, simulate, true);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return move(toExtract, simulate, false);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(buffer.stored(), Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(buffer.capacity(), Integer.MAX_VALUE);
        }

        @Override
        public boolean canExtract() {
            return enabled;
        }

        @Override
        public boolean canReceive() {
            return enabled;
        }
    }

    // --- persistencia e sincronizacao ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Stored", buffer.stored());
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Limit", limitPercent);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.setCapacity(block().capacity());
        buffer.setStored(tag.getLong("Stored"));
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        limitPercent = tag.contains("Limit") ? tag.getInt("Limit") : 100;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Charge", chargePercent());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private int clientCharge;

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientCharge = tag.getInt("Charge");
    }

    /** Carga (0 a 100) para o desenho: no servidor e o valor real, no cliente o ultimo sincronizado. */
    public int displayCharge() {
        return level != null && level.isClientSide ? clientCharge : chargePercent();
    }
}
