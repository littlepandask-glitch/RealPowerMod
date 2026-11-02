package com.mmods.realpower.gametest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Consumidor ilimitado usado so nos GameTests: um bloco de ouro que aceita toda a energia e a contabiliza por posicao. */
public final class TestEnergySink {
    static final Map<BlockPos, Long> RECEIVED = new ConcurrentHashMap<>();

    private TestEnergySink() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) -> {
            BlockPos key = pos.immutable();
            return new IEnergyStorage() {
                @Override
                public int receiveEnergy(int toReceive, boolean simulate) {
                    if (!simulate) {
                        RECEIVED.merge(key, (long) toReceive, Long::sum);
                    }
                    return toReceive;
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
                    return Integer.MAX_VALUE;
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
        }, Blocks.GOLD_BLOCK);
    }
}
