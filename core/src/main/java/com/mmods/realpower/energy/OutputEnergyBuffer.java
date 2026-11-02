package com.mmods.realpower.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** Buffer de saida de um gerador: so produz internamente e so permite extracao externa. */
public class OutputEnergyBuffer implements IEnergyStorage {
    private final int capacity;
    private int stored;

    public OutputEnergyBuffer(int capacity) {
        this.capacity = capacity;
    }

    /** Uso interno do gerador; descarta o excedente quando o buffer enche. */
    public void produce(int amount) {
        stored = (int) Math.min((long) capacity, (long) stored + amount);
    }

    public void setStored(int value) {
        stored = Math.max(0, Math.min(capacity, value));
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        int extracted = Math.min(stored, Math.max(0, toExtract));
        if (!simulate) {
            stored -= extracted;
        }
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return stored;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
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
