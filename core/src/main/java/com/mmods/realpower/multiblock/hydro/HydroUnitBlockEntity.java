package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.generator.AbstractGeneratorBlockEntity;
import com.mmods.realpower.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mmods.realpower.menu.HydroMenu;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Gerador da casa de forca: potencia pela queda e vazao medidas pela varredura da barragem ({@link DamScan}). */
public class HydroUnitBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
    /** Campos do menu (cada um em 2 slots de 16 bits, pois o canal de dados do menu leva apenas shorts). */
    public static final int INFO_COUNT = 13;
    private final int[] info = new int[INFO_COUNT];
    private double lakeSurfaceY;
    private int lakeArea;
    /** Vazao turbinada em relacao a nominal (0..1), medida pela fisica e enviada ao cliente para a animacao. */
    private double flowFraction;

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

    /** A usina esta gerando: formada, ligada, comporta aberta e ha queda e agua. */
    public boolean generating() {
        return isFormed() && enabled() && limit() > 0 && powerWatts() > 0.0;
    }

    /** Rotacao da turbina (0..1) para a animacao: acompanha a vazao de agua que passa (e a comporta), parada sem geracao. */
    public double runFraction() {
        if (!generating()) {
            return 0.0;
        }
        return 0.12 + 0.88 * Math.min(1.0, flowFraction * limit() / 100.0);
    }

    @Override
    protected void afterPhysics(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        block().setConduit(level, pos, getBlockState(), generating());
    }

    /** Abertura da comporta (0..1): fechada se nao gera, aberta no limite escolhido se gera. */
    public double gateOpening() {
        return generating() ? Math.max(0.15, limit() / 100.0) : 0.0;
    }

    /** Fracao da vazao do lago que cabe a esta unidade entre as da mesma barragem (proporcional a vazao nominal). */
    private double flowShare(Level level, BlockPos pos) {
        double own = HydroPhysics.spec(tier()).ratedFlow();
        return own / Math.max(own, DamUnits.ratedFlow(level, pos, getBlockState()));
    }

    public int lakeArea() {
        return lakeArea;
    }

    @Override
    protected double currentOutputWatts(Level level, BlockPos pos) {
        double area = lakeArea * flowShare(level, pos);
        flowFraction = HydroPhysics.flow(tier(), area) / HydroPhysics.spec(tier()).ratedFlow();
        return HydroPhysics.watts(tier(), grossHead(), area);
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

    /** Recalcula (no servidor) os dados mostrados no menu. */
    public void refreshInfo() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockPos pos = getBlockPos();
        TankPlan plan = TankPlan.of(server, pos, getBlockState());
        double share = flowShare(server, pos);
        info[0] = isFormed() ? 1 : 0;
        info[1] = lakeArea;
        info[2] = plan.neededArea();
        info[3] = (int) Math.round(grossHead() * 10.0);
        info[4] = (int) Math.round(HydroPhysics.spec(tier()).minHead() * 10.0);
        info[5] = (int) Math.round(powerWatts() * limit() / 100.0 / 1000.0);
        info[6] = plan.units();
        info[7] = plan.width();
        info[8] = plan.length();
        info[9] = plan.depth();
        info[10] = TankJob.percent(server, pos);
        info[11] = (int) Math.round(HydroPhysics.flow(tier(), lakeArea * share) * 10.0);
        info[12] = enabled() ? 1 : 0;
    }

    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            int value = info[index / 2];
            return index % 2 == 0 ? value & 0xFFFF : value >> 16;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return INFO_COUNT * 2;
        }
    };

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        refreshInfo();
        return new HydroMenu(containerId, inventory, ContainerLevelAccess.create(level, worldPosition), menuData);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("LakeSurface", lakeSurfaceY);
        tag.putInt("LakeArea", lakeArea);
        tag.putDouble("FlowFraction", flowFraction);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lakeSurfaceY = tag.getDouble("LakeSurface");
        lakeArea = tag.getInt("LakeArea");
        flowFraction = tag.getDouble("FlowFraction");
    }
}
