package com.mmods.realpower.generator;

import com.mmods.realpower.api.multiblock.MultiblockController;
import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.multiblock.ValidationResult;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.energy.GeneratorBuffer;
import com.mmods.realpower.energy.PowerScale;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Base de todo gerador multibloco: ciclo de validacao incremental, producao, buffer
 * proporcional a eficiencia do tier e envio de FE aos vizinhos.
 */
public abstract class AbstractGeneratorBlockEntity extends BlockEntity implements MultiblockController, Tiered {
    /** Intervalo entre revalidacoes automaticas. TODO: notificar o controlador quando um bloco da estrutura mudar. */
    public static final int REVALIDATE_INTERVAL = 100;
    public static final int PHYSICS_INTERVAL = 20;

    private final Tier tier;
    private final GeneratorBuffer buffer = new GeneratorBuffer();

    private boolean formed;
    private boolean revalidate = true;
    private ValidationResult lastResult = ValidationResult.invalid(List.of());
    private StructureScan scan;
    private long age;

    private double powerWatts;
    private double fePerTick;
    private double carry;

    protected AbstractGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Tier tier) {
        super(type, pos, state);
        this.tier = tier;
    }

    // --- pontos de extensao para cada tipo de gerador ---

    protected abstract StructureScan createScan(Level level, BlockPos pos, BlockState state);

    /** Chamado quando uma varredura termina (e nao foi interrompida por chunk descarregado). */
    protected abstract void onStructureScanned(StructureScan finished, boolean formed);

    /** Potencia eletrica atual (W) com as condicoes de agora. */
    protected abstract double currentOutputWatts(Level level, BlockPos pos);

    /** Potencia nominal (W) da estrutura formada; base do tamanho do buffer. */
    protected abstract double ratedOutputWatts();

    // ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractGeneratorBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        age++;

        if (scan == null && (revalidate || age % REVALIDATE_INTERVAL == 0)) {
            revalidate = false;
            scan = createScan(level, pos, state);
        }
        if (scan != null && scan.advance(StructureScan.DEFAULT_BLOCK_BUDGET)) {
            finishScan();
        }

        if (formed && age % PHYSICS_INTERVAL == 0) {
            powerWatts = currentOutputWatts(level, pos);
            fePerTick = PowerScale.toFePerTick(powerWatts);
            setChanged();
        }

        if (formed && fePerTick > 0.0) {
            carry += fePerTick;
            long whole = (long) carry;
            carry -= whole;
            buffer.produce(whole);
        }
        pushEnergy(level, pos);
    }

    private void finishScan() {
        StructureScan finished = scan;
        scan = null;
        if (finished.isUnloaded()) {
            return; // tenta de novo no proximo intervalo, sem alterar o estado atual
        }
        lastResult = finished.result();
        formed = lastResult.valid();
        onStructureScanned(finished, formed);
        if (formed) {
            double ratedFe = PowerScale.toFePerTick(ratedOutputWatts());
            buffer.setCapacity(GeneratorBuffer.capacityFor(ratedFe, tier.efficiency()));
        } else {
            powerWatts = 0.0;
            fePerTick = 0.0;
        }
        setChanged();
    }

    private void pushEnergy(Level level, BlockPos pos) {
        if (buffer.stored() <= 0L) {
            return;
        }
        for (Direction direction : Direction.values()) {
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (target != null && target.canReceive()) {
                int offered = (int) Math.min(Integer.MAX_VALUE, buffer.extract(Integer.MAX_VALUE, true));
                buffer.extract(target.receiveEnergy(offered, false), false);
                if (buffer.stored() <= 0L) {
                    return;
                }
            }
        }
    }

    public IEnergyStorage getEnergyOutput() {
        return buffer.asFE();
    }

    public long storedEnergy() {
        return buffer.stored();
    }

    public long bufferCapacity() {
        return buffer.capacity();
    }

    public double powerWatts() {
        return powerWatts;
    }

    @Override
    public Tier tier() {
        return tier;
    }

    @Override
    public boolean isFormed() {
        return formed;
    }

    @Override
    public void requestRevalidation() {
        revalidate = true;
    }

    @Override
    public ValidationResult lastResult() {
        return lastResult;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Formed", formed);
        tag.putDouble("PowerWatts", powerWatts);
        tag.putLong("Capacity", buffer.capacity());
        tag.putLong("Energy", buffer.stored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("Formed");
        powerWatts = tag.getDouble("PowerWatts");
        fePerTick = PowerScale.toFePerTick(powerWatts);
        buffer.setCapacity(tag.getLong("Capacity"));
        buffer.setStored(tag.getLong("Energy"));
    }
}
