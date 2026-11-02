package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.generator.AbstractGeneratorBlockEntity;
import com.mmods.realpower.menu.TurbineMenu;
import com.mmods.realpower.registry.ModBlockEntities;
import com.mmods.realpower.render.TurbineRenderState;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class WindTurbineControllerBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
    /** Instancias carregadas no cliente: o overlay fantasma desenha o projeto de cada uma. */
    public static final Set<WindTurbineControllerBlockEntity> CLIENT_INSTANCES =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    private boolean overlayEnabled = true;
    private int missingBlocks = TurbineBlueprint.totalBlocks();
    private double windSpeed;

    /** Pas e carenagem escondidas (viram o modelo 3D); guardado para poder desfazer. */
    private boolean marked;
    private Direction markedFacing = Direction.NORTH;

    /** Estado so do cliente: malha em cache, angulo do rotor e cache do overlay. */
    private final TurbineRenderState renderState = new TurbineRenderState();
    private long ghostCacheTime = Long.MIN_VALUE;
    private List<TurbineBlueprint.Part> ghostMissing = List.of();

    /** Dados mostrados no menu: ligado, formado, blocos faltando, vento x10, potencia kW, buffer %. */
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> overlayEnabled ? 1 : 0;
                case 1 -> isFormed() ? 1 : 0;
                case 2 -> Math.min(missingBlocks, Short.MAX_VALUE);
                case 3 -> (int) Math.round(windSpeed * 10.0);
                case 4 -> (int) Math.min(Short.MAX_VALUE, Math.round(powerWatts() / 1000.0));
                case 5 -> bufferCapacity() == 0 ? 0 : (int) (storedEnergy() * 100L / bufferCapacity());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return TurbineMenu.DATA_COUNT;
        }
    };

    public WindTurbineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_TURBINE_CONTROLLER.get(), pos, state, ((Tiered) state.getBlock()).tier());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CLIENT_INSTANCES.add(this);
        }
    }

    /** Tambem ao chegar por pacote de chunk no cliente, onde onLoad pode nao ocorrer. */
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && level.isClientSide) {
            CLIENT_INSTANCES.add(this);
        }
    }

    @Override
    public void setRemoved() {
        CLIENT_INSTANCES.remove(this);
        super.setRemoved();
    }

    /** Entrega a energia as portas completas dos cantos (em qualquer uma que exista). */
    private final IEnergyStorage ports = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            Direction facing = getBlockState().getValue(WindTurbineControllerBlock.FACING);
            int remaining = toReceive;
            for (int corner = 0; corner < TurbineBlueprint.PORT_CORNER_COUNT && remaining > 0; corner++) {
                BlockPos pos = TurbineBlueprint.portPos(getBlockPos(), facing, corner);
                if (level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TurbinePowerPortBlockEntity port) {
                    remaining -= port.feed().receiveEnergy(remaining, simulate);
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
    protected StructureScan createScan(Level level, BlockPos pos, BlockState state) {
        return new WindTurbineScan(level, pos, state.getValue(WindTurbineControllerBlock.FACING));
    }

    @Override
    protected void onStructureScanned(StructureScan finished, boolean formed) {
        if (finished instanceof WindTurbineScan scan) {
            missingBlocks = scan.missingTotal();
        }
        if (formed) {
            morph();
        } else {
            releaseStructure();
            windSpeed = 0.0;
        }
    }

    // --- morph: esconde carenagem e helices para o rotor 3D aparecer no lugar ---

    private void morph() {
        Direction facing = getBlockState().getValue(WindTurbineControllerBlock.FACING);
        if (marked && markedFacing != facing) {
            releaseStructure();
        }
        boolean fresh = !marked;
        marked = true;
        markedFacing = facing;
        forEachFormedBlock(part -> setPartFormed(part.pos(), true, part.mask()));
        if (fresh) {
            celebrate(facing);
        }
    }

    /** Devolve carenagem e helices ao aspecto normal (estrutura desfeita ou controlador removido). */
    public void releaseStructure() {
        if (marked) {
            forEachFormedBlock(part -> setPartFormed(part.pos(), false, TurbineBlueprint.FULL_MASK));
            marked = false;
        }
    }

    /** Tudo que vira modelo 3D: corpo, nacele, carenagem e helices (a base continua sendo bloco). */
    private void forEachFormedBlock(Consumer<TurbineBlueprint.Part> action) {
        for (TurbineBlueprint.Part part : TurbineBlueprint.parts(getBlockPos(), markedFacing)) {
            if (part.kind() != TurbineBlueprint.Kind.BASE) {
                action.accept(part);
            }
        }
    }

    /** Efeito leve ao completar a estrutura: faiscas no controlador e no cubo do rotor. */
    private void celebrate(Direction facing) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockPos pos = getBlockPos();
        server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 14, 0.5, 0.6, 0.5, 0.03);
        BlockPos hub = pos.relative(facing, TurbineBlueprint.HUB_U + 3).above(TurbineBlueprint.HUB_Y);
        server.sendParticles(ParticleTypes.END_ROD, hub.getX() + 0.5, hub.getY() + 0.5, hub.getZ() + 0.5, 14, 1.5, 1.5, 1.5, 0.03);
    }

    private void setPartFormed(BlockPos pos, boolean value, int mask) {
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WindStructureBlock)) {
            return;
        }
        BlockState next = state.setValue(WindStructureBlock.FORMED, value);
        if (state.getBlock() instanceof TurbineCasingBlock) {
            next = next.setValue(TurbineCasingBlock.MASK, mask);
        }
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // ---

    @Override
    protected double currentOutputWatts(Level level, BlockPos pos) {
        windSpeed = WindTurbinePhysics.windSpeed(level, pos, WindTurbinePhysics.HUB_HEIGHT);
        return WindTurbinePhysics.powerWatts(WindTurbinePhysics.ROTOR_RADIUS, windSpeed,
                WindTurbinePhysics.powerCoefficient(tier()));
    }

    @Override
    protected double ratedOutputWatts() {
        return WindTurbinePhysics.powerWatts(WindTurbinePhysics.ROTOR_RADIUS, WindTurbinePhysics.RATED_SPEED,
                WindTurbinePhysics.powerCoefficient(tier()));
    }

    // --- overlay e menu ---

    public boolean overlayEnabled() {
        return overlayEnabled;
    }

    public void setOverlayEnabled(boolean value) {
        overlayEnabled = value;
        setChanged();
        syncToClient();
    }

    public int missingBlocks() {
        return missingBlocks;
    }

    public double windSpeed() {
        return windSpeed;
    }

    public TurbineRenderState renderState() {
        return renderState;
    }

    public long ghostCacheTime() {
        return ghostCacheTime;
    }

    public List<TurbineBlueprint.Part> ghostMissing() {
        return ghostMissing;
    }

    public void updateGhostCache(long time, List<TurbineBlueprint.Part> missing) {
        ghostCacheTime = time;
        ghostMissing = missing;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.realpower.wind_turbine", tier().displayName());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TurbineMenu(containerId, inventory, ContainerLevelAccess.create(level, worldPosition), menuData);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("OverlayEnabled", overlayEnabled);
        tag.putInt("MissingBlocks", missingBlocks);
        tag.putDouble("WindSpeed", windSpeed);
        tag.putBoolean("Marked", marked);
        tag.putInt("MarkedFacing", markedFacing.get3DDataValue());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        overlayEnabled = !tag.contains("OverlayEnabled") || tag.getBoolean("OverlayEnabled");
        missingBlocks = tag.contains("MissingBlocks") ? tag.getInt("MissingBlocks") : TurbineBlueprint.totalBlocks();
        windSpeed = tag.getDouble("WindSpeed");
        marked = tag.getBoolean("Marked");
        markedFacing = Direction.from3DDataValue(tag.getInt("MarkedFacing"));
    }
}
