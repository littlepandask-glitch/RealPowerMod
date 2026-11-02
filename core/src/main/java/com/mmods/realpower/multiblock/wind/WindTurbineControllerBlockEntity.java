package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.generator.AbstractGeneratorBlockEntity;
import com.mmods.realpower.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class WindTurbineControllerBlockEntity extends AbstractGeneratorBlockEntity {
    private int towerHeight;
    private double bladeRadius;
    private double windSpeed;

    public WindTurbineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_TURBINE_CONTROLLER.get(), pos, state, ((Tiered) state.getBlock()).tier());
    }

    @Override
    protected StructureScan createScan(Level level, BlockPos pos, BlockState state) {
        return new WindTurbineScan(level, pos, state.getValue(WindTurbineControllerBlock.FACING));
    }

    @Override
    protected void onStructureScanned(StructureScan finished, boolean formed) {
        if (finished instanceof WindTurbineScan turbine) {
            towerHeight = turbine.towerHeight();
            bladeRadius = turbine.bladeRadius();
        }
        if (!formed) {
            windSpeed = 0.0;
        }
    }

    @Override
    protected double currentOutputWatts(Level level, BlockPos pos) {
        windSpeed = WindTurbinePhysics.windSpeed(level, pos, towerHeight);
        return WindTurbinePhysics.powerWatts(bladeRadius, windSpeed, WindTurbinePhysics.powerCoefficient(tier()));
    }

    @Override
    protected double ratedOutputWatts() {
        return WindTurbinePhysics.powerWatts(bladeRadius, WindTurbinePhysics.RATED_SPEED,
                WindTurbinePhysics.powerCoefficient(tier()));
    }

    public void sendStatus(Player player) {
        if (isFormed()) {
            player.displayClientMessage(Component.translatable("message.realpower.wind.status",
                    tier().displayName(), towerHeight, String.format("%.1f", bladeRadius),
                    String.format("%.1f", windSpeed), String.format("%.1f", powerWatts() / 1000.0),
                    storedEnergy(), bufferCapacity()), false);
        } else {
            player.displayClientMessage(Component.translatable("message.realpower.wind.not_formed"), false);
            lastResult().problems().forEach(problem -> player.displayClientMessage(problem, false));
        }
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("TowerHeight", towerHeight);
        tag.putDouble("BladeRadius", bladeRadius);
        tag.putDouble("WindSpeed", windSpeed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        towerHeight = tag.getInt("TowerHeight");
        bladeRadius = tag.getDouble("BladeRadius");
        windSpeed = tag.getDouble("WindSpeed");
    }
}
