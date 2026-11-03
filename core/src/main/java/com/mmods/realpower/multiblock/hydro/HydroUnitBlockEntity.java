package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.generator.AbstractGeneratorBlockEntity;
import com.mmods.realpower.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Gerador da casa de forca: potencia pela queda e vazao medidas pela varredura da barragem ({@link DamScan}). */
public class HydroUnitBlockEntity extends AbstractGeneratorBlockEntity {
    private double lakeSurfaceY;
    private int lakeArea;

    public HydroUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HYDRO_UNIT.get(), pos, state, ((Tiered) state.getBlock()).tier());
    }

    private HydroUnitBlock block() {
        return (HydroUnitBlock) getBlockState().getBlock();
    }

    @Override
    protected StructureScan createScan(Level level, BlockPos pos, BlockState state) {
        block().ensureStructure(level, pos, state);
        HydroUnitBlock block = block();
        BlockPos intake = block.intakePos(pos, state);
        return new DamScan(level, intake, HydroUnitBlock.upstream(state), pos.getY(),
                cell -> block.containsCell(pos, state, cell) || cell.equals(pos));
    }

    @Override
    protected void onStructureScanned(StructureScan finished, boolean formed) {
        if (finished instanceof DamScan scan) {
            lakeSurfaceY = scan.lakeSurfaceY();
            lakeArea = scan.lakeArea();
        }
        if (!formed) {
            lakeSurfaceY = 0.0;
            lakeArea = 0;
        }
    }

    /** Queda bruta (m): da superficie do lago ao eixo da turbina; 0 sem lago (o Y do mundo pode ser negativo). */
    public double grossHead() {
        return lakeArea <= 0 ? 0.0 : Math.max(0.0, lakeSurfaceY - (getBlockPos().getY() + HydroUnitBlock.turbineHeight(tier())));
    }

    /** Carga da turbina (0..1) para a animacao: potencia entregue em relacao a nominal. */
    public double runFraction() {
        if (!isFormed() || !enabled()) {
            return 0.0;
        }
        return Math.min(1.0, powerWatts() * limit() / 100.0 / Math.max(1.0, ratedOutputWatts()));
    }

    /** Abertura da comporta (0..1): fechada desligada, aberta no limite escolhido. */
    public double gateOpening() {
        return enabled() ? limit() / 100.0 : 0.0;
    }

    public int lakeArea() {
        return lakeArea;
    }

    @Override
    protected double currentOutputWatts(Level level, BlockPos pos) {
        return HydroPhysics.watts(tier(), grossHead(), lakeArea);
    }

    @Override
    protected double ratedOutputWatts() {
        HydroPhysics.Spec spec = HydroPhysics.spec(tier());
        return HydroPhysics.watts(tier(), spec.maxHead(), spec.ratedFlow() / HydroPhysics.FLOW_PER_SURFACE_BLOCK);
    }

    /** Entrega a energia aos cabos encostados nas portas de energia da casa de forca. */
    private final IEnergyStorage ports = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (level == null) {
                return 0;
            }
            int remaining = toReceive;
            BlockPos base = getBlockPos();
            BlockState state = getBlockState();
            for (BlockPos port : block().portCells(base, state)) {
                for (Direction side : Direction.values()) {
                    BlockPos next = port.relative(side);
                    if (remaining <= 0 || !level.isLoaded(next) || block().containsCell(base, state, next)) {
                        continue;
                    }
                    IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, next, side.getOpposite());
                    if (target != null && target.canReceive()) {
                        remaining -= target.receiveEnergy(remaining, simulate);
                    }
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
            return true;
        }
    };

    @Override
    protected IEnergyStorage extraOutput(Level level) {
        return ports;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("LakeSurface", lakeSurfaceY);
        tag.putInt("LakeArea", lakeArea);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lakeSurfaceY = tag.getDouble("LakeSurface");
        lakeArea = tag.getInt("LakeArea");
    }
}
