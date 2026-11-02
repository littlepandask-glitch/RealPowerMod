package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.api.multiblock.MultiblockController;
import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.multiblock.ValidationResult;
import com.mmods.realpower.energy.OutputEnergyBuffer;
import com.mmods.realpower.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class WindTurbineControllerBlockEntity extends BlockEntity implements MultiblockController {
    /** Intervalo entre revalidacoes automaticas. TODO: notificar o controlador quando um bloco da estrutura mudar. */
    public static final int REVALIDATE_INTERVAL = 100;
    public static final int PHYSICS_INTERVAL = 20;
    public static final int BUFFER_CAPACITY = 2_000_000;

    private final OutputEnergyBuffer energy = new OutputEnergyBuffer(BUFFER_CAPACITY);

    private boolean formed;
    private boolean revalidate = true;
    private ValidationResult lastResult = ValidationResult.invalid(java.util.List.of());
    private StructureScan scan;
    private long age;

    private int towerHeight;
    private double bladeRadius;
    private double windSpeed;
    private double powerWatts;
    private double fePerTick;
    private double carry;

    public WindTurbineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_TURBINE_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WindTurbineControllerBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        age++;

        if (scan == null && (revalidate || age % REVALIDATE_INTERVAL == 0)) {
            revalidate = false;
            scan = new WindTurbineScan(level, pos, state.getValue(WindTurbineControllerBlock.FACING));
        }
        if (scan != null && scan.advance(StructureScan.DEFAULT_BLOCK_BUDGET)) {
            finishScan();
        }

        if (formed && age % PHYSICS_INTERVAL == 0) {
            windSpeed = WindTurbinePhysics.windSpeed(level, pos, towerHeight);
            powerWatts = WindTurbinePhysics.powerWatts(bladeRadius, windSpeed);
            fePerTick = powerWatts / WindTurbinePhysics.WATTS_PER_FE_TICK;
            setChanged();
        }

        if (formed && fePerTick > 0.0) {
            carry += fePerTick;
            int whole = (int) carry;
            carry -= whole;
            energy.produce(whole);
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
        if (finished instanceof WindTurbineScan turbine) {
            towerHeight = turbine.towerHeight();
            bladeRadius = turbine.bladeRadius();
        }
        if (!formed) {
            windSpeed = 0.0;
            powerWatts = 0.0;
            fePerTick = 0.0;
        }
        setChanged();
    }

    private void pushEnergy(Level level, BlockPos pos) {
        if (energy.getEnergyStored() <= 0) {
            return;
        }
        for (Direction direction : Direction.values()) {
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (target != null && target.canReceive()) {
                int offered = energy.extractEnergy(energy.getEnergyStored(), true);
                energy.extractEnergy(target.receiveEnergy(offered, false), false);
                if (energy.getEnergyStored() <= 0) {
                    return;
                }
            }
        }
    }

    public void sendStatus(Player player) {
        if (formed) {
            player.displayClientMessage(Component.translatable("message.realpower.wind.status",
                    towerHeight, String.format("%.1f", bladeRadius), String.format("%.1f", windSpeed),
                    String.format("%.1f", powerWatts / 1000.0)), false);
        } else {
            player.displayClientMessage(Component.translatable("message.realpower.wind.not_formed"), false);
            lastResult.problems().forEach(problem -> player.displayClientMessage(problem, false));
        }
    }

    public IEnergyStorage getEnergyOutput() {
        return energy;
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

    public int towerHeight() {
        return towerHeight;
    }

    public double bladeRadius() {
        return bladeRadius;
    }

    public double windSpeed() {
        return windSpeed;
    }

    public double powerWatts() {
        return powerWatts;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Formed", formed);
        tag.putInt("TowerHeight", towerHeight);
        tag.putDouble("BladeRadius", bladeRadius);
        tag.putDouble("WindSpeed", windSpeed);
        tag.putDouble("PowerWatts", powerWatts);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("Formed");
        towerHeight = tag.getInt("TowerHeight");
        bladeRadius = tag.getDouble("BladeRadius");
        windSpeed = tag.getDouble("WindSpeed");
        powerWatts = tag.getDouble("PowerWatts");
        fePerTick = powerWatts / WindTurbinePhysics.WATTS_PER_FE_TICK;
        energy.setStored(tag.getInt("Energy"));
    }
}
