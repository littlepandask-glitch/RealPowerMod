package com.mmods.realpower.multiblock.wind;

import net.minecraft.core.BlockPos;
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
public class TurbinePowerPortBlockEntity extends BlockEntity {
    private final IEnergyStorage feed = new PortStorage(true);
    private final IEnergyStorage outside = new PortStorage(false);

    public TurbinePowerPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
