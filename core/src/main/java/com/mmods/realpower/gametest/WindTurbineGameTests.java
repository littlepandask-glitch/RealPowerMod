package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlockEntity;
import com.mmods.realpower.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Testes automaticos da turbina eolica (rodar com {@code gradlew :core:runGameTestServer}). */
@GameTestHolder(RealPower.MOD_ID)
@PrefixGameTestTemplate(false)
public class WindTurbineGameTests {
    private static final int TOWER = 12;
    private static final int BLADE = 6;

    /** Constroi a turbina com controlador virado para norte (-Z); {@code withHub=false} omite o cubo. */
    private static BlockPos build(GameTestHelper helper, boolean withHub) {
        BlockPos base = new BlockPos(10, 1, 10);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        for (int i = 1; i <= TOWER; i++) {
            helper.setBlock(base.above(i), ModBlocks.WIND_TOWER.get().defaultBlockState());
        }
        BlockPos controller = base.above(TOWER + 1);
        helper.setBlock(controller, ModBlocks.WIND_TURBINE_CONTROLLER.get().defaultBlockState()
                .setValue(WindTurbineControllerBlock.FACING, Direction.NORTH));
        if (withHub) {
            BlockPos hub = controller.north();
            helper.setBlock(hub, ModBlocks.WIND_BLADE.get().defaultBlockState());
            for (Direction arm : new Direction[] {Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST}) {
                for (int i = 1; i <= BLADE; i++) {
                    helper.setBlock(hub.relative(arm, i), ModBlocks.WIND_BLADE.get().defaultBlockState());
                }
            }
        }
        return controller;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void turbineFormsAndGeneratesEnergy(GameTestHelper helper) {
        BlockPos controller = build(helper, true);
        helper.runAfterDelay(45, () -> {
            WindTurbineControllerBlockEntity be = (WindTurbineControllerBlockEntity) helper.getBlockEntity(controller);
            helper.assertTrue(be.isFormed(), "turbina deveria estar formada: " + be.lastResult().problems());
            helper.assertTrue(be.towerHeight() == TOWER, "altura da torre = " + be.towerHeight());
            helper.assertTrue(Math.abs(be.bladeRadius() - BLADE) < 1e-9, "raio = " + be.bladeRadius());
            helper.assertTrue(be.powerWatts() > 0.0, "potencia = " + be.powerWatts());
            helper.assertTrue(be.getEnergyOutput().getEnergyStored() > 0, "buffer de energia vazio");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void turbineWithoutHubIsInvalid(GameTestHelper helper) {
        BlockPos controller = build(helper, false);
        helper.runAfterDelay(10, () -> {
            WindTurbineControllerBlockEntity be = (WindTurbineControllerBlockEntity) helper.getBlockEntity(controller);
            helper.assertTrue(!be.isFormed(), "turbina sem cubo nao deveria se formar");
            helper.assertTrue(!be.lastResult().problems().isEmpty(), "deveria listar problemas");
            helper.assertTrue(be.getEnergyOutput().getEnergyStored() == 0, "nao deveria gerar energia");
            helper.succeed();
        });
    }
}
