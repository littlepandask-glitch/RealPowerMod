package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.api.multiblock.StructureScan;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.generator.AbstractGeneratorBlockEntity;
import com.mmods.realpower.registry.ModBlockEntities;
import com.mmods.realpower.render.TurbineRenderState;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class WindTurbineControllerBlockEntity extends AbstractGeneratorBlockEntity {
    private int towerHeight;
    private double bladeRadius;
    private double windSpeed;

    /** Geometria atualmente "morfada" (blocos de andaime escondidos), para poder desfazer depois. */
    private boolean marked;
    private int markedTower;
    private int[] markedArms = new int[4];
    private Direction markedFacing = Direction.NORTH;

    /** Estado so do cliente: malhas em cache e angulo do rotor. */
    private final TurbineRenderState renderState = new TurbineRenderState();

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
            if (formed) {
                morph(turbine.armLengths());
            } else {
                releaseStructure();
                setControllerFormed(false);
            }
        }
        if (!formed) {
            windSpeed = 0.0;
        }
    }

    // --- morph: esconde os blocos de andaime e deixa o modelo 3D aparecer ---

    private void morph(int[] armLengths) {
        Direction facing = getBlockState().getValue(WindTurbineControllerBlock.FACING);
        boolean sameGeometry = marked && markedTower == towerHeight && markedFacing == facing
                && java.util.Arrays.equals(markedArms, armLengths);
        if (!sameGeometry) {
            if (marked) {
                forEachStructureBlock(pos -> setPartFormed(pos, false));
            }
            marked = true;
            markedTower = towerHeight;
            markedArms = armLengths.clone();
            markedFacing = facing;
        }
        forEachStructureBlock(pos -> setPartFormed(pos, true));
        setControllerFormed(true);
    }

    /** Devolve os blocos de andaime ao aspecto normal (estrutura desfeita ou controlador removido). */
    public void releaseStructure() {
        if (marked) {
            forEachStructureBlock(pos -> setPartFormed(pos, false));
            marked = false;
        }
    }

    private void forEachStructureBlock(Consumer<BlockPos> action) {
        BlockPos origin = getBlockPos();
        for (int i = 1; i <= markedTower; i++) {
            action.accept(origin.below(i));
        }
        BlockPos hub = origin.relative(markedFacing);
        action.accept(hub);
        Direction[] directions = {Direction.UP, Direction.DOWN, markedFacing.getClockWise(), markedFacing.getCounterClockWise()};
        for (int arm = 0; arm < directions.length; arm++) {
            for (int i = 1; i <= markedArms[arm]; i++) {
                action.accept(hub.relative(directions[arm], i));
            }
        }
    }

    private void setPartFormed(BlockPos pos, boolean value) {
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WindStructureBlock && state.getValue(WindStructureBlock.FORMED) != value) {
            level.setBlock(pos, state.setValue(WindStructureBlock.FORMED, value), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private void setControllerFormed(boolean value) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getBlock() instanceof WindTurbineControllerBlock && state.getValue(WindTurbineControllerBlock.FORMED) != value) {
            level.setBlock(worldPosition, state.setValue(WindTurbineControllerBlock.FORMED, value),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // ---

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

    public TurbineRenderState renderState() {
        return renderState;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("TowerHeight", towerHeight);
        tag.putDouble("BladeRadius", bladeRadius);
        tag.putDouble("WindSpeed", windSpeed);
        tag.putBoolean("Marked", marked);
        tag.putInt("MarkedTower", markedTower);
        tag.putIntArray("MarkedArms", markedArms);
        tag.putInt("MarkedFacing", markedFacing.get3DDataValue());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        towerHeight = tag.getInt("TowerHeight");
        bladeRadius = tag.getDouble("BladeRadius");
        windSpeed = tag.getDouble("WindSpeed");
        marked = tag.getBoolean("Marked");
        markedTower = tag.getInt("MarkedTower");
        int[] arms = tag.getIntArray("MarkedArms");
        markedArms = arms.length == 4 ? arms : new int[4];
        markedFacing = Direction.from3DDataValue(tag.getInt("MarkedFacing"));
    }
}
