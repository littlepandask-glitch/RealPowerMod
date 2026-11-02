package com.mmods.realpower.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Buffer de saida de um gerador. Usa {@code long} internamente porque geradores em
 * escala real passam facilmente do limite de {@code int}; para outros mods ele e
 * exposto como {@link IEnergyStorage} via {@link #asFE()}, com valores limitados a int.
 */
public class GeneratorBuffer {
    private static final double BASE_SECONDS = 5.0;
    private static final double EFFICIENCY_SECONDS = 15.0;

    private final IEnergyStorage feView = new FEView();
    private long capacity;
    private long stored;

    /**
     * Capacidade para uma potencia nominal: de 5 s (eficiencia 0) a 20 s (eficiencia 1) de producao.
     * Geradores mais eficientes guardam mais.
     */
    public static long capacityFor(double ratedFePerTick, double efficiency) {
        double seconds = BASE_SECONDS + EFFICIENCY_SECONDS * efficiency;
        return Math.max(1L, (long) Math.ceil(ratedFePerTick * seconds * 20.0));
    }

    public void setCapacity(long capacity) {
        this.capacity = Math.max(0L, capacity);
        this.stored = Math.min(stored, this.capacity);
    }

    public long capacity() {
        return capacity;
    }

    public long stored() {
        return stored;
    }

    public void setStored(long value) {
        stored = Math.max(0L, Math.min(capacity, value));
    }

    /** Uso interno do gerador; descarta o excedente quando o buffer enche. */
    public void produce(long amount) {
        stored = Math.min(capacity, stored + Math.max(0L, amount));
    }

    public long extract(long max, boolean simulate) {
        long extracted = Math.min(stored, Math.max(0L, max));
        if (!simulate) {
            stored -= extracted;
        }
        return extracted;
    }

    /** Visao FE para outros mods: so extracao, valores limitados a {@link Integer#MAX_VALUE}. */
    public IEnergyStorage asFE() {
        return feView;
    }

    private final class FEView implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return (int) extract(toExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(stored, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(capacity, Integer.MAX_VALUE);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
