package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.energy.GeneratorBuffer;
import com.mmods.realpower.energy.PowerScale;
import com.mmods.realpower.multiblock.wind.WindStructureBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlockEntity;
import com.mmods.realpower.multiblock.wind.WindTurbinePhysics;
import com.mmods.realpower.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
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
    private static BlockPos build(GameTestHelper helper, BlockPos base, Block controllerBlock, boolean withHub) {
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        for (int i = 1; i <= TOWER; i++) {
            helper.setBlock(base.above(i), ModBlocks.WIND_TOWER.get().defaultBlockState());
        }
        BlockPos controller = base.above(TOWER + 1);
        helper.setBlock(controller, controllerBlock.defaultBlockState()
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

    private static WindTurbineControllerBlockEntity turbine(GameTestHelper helper, BlockPos controller) {
        return (WindTurbineControllerBlockEntity) helper.getBlockEntity(controller);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void turbineFormsAndGeneratesEnergy(GameTestHelper helper) {
        BlockPos controller = build(helper, new BlockPos(10, 1, 10),
                ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get(), true);
        helper.runAfterDelay(45, () -> {
            WindTurbineControllerBlockEntity be = turbine(helper, controller);
            helper.assertTrue(be.isFormed(), "turbina deveria estar formada: " + be.lastResult().problems());
            helper.assertTrue(be.tier() == Tier.BASIC, "tier = " + be.tier());
            helper.assertTrue(be.towerHeight() == TOWER, "altura da torre = " + be.towerHeight());
            helper.assertTrue(Math.abs(be.bladeRadius() - BLADE) < 1e-9, "raio = " + be.bladeRadius());
            helper.assertTrue(be.powerWatts() > 0.0, "potencia = " + be.powerWatts());
            helper.assertTrue(be.storedEnergy() > 0L, "buffer de energia vazio");
            helper.assertTrue(be.storedEnergy() <= be.bufferCapacity(), "buffer acima da capacidade");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void turbineWithoutHubIsInvalid(GameTestHelper helper) {
        BlockPos controller = build(helper, new BlockPos(10, 1, 10),
                ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get(), false);
        helper.runAfterDelay(10, () -> {
            WindTurbineControllerBlockEntity be = turbine(helper, controller);
            helper.assertTrue(!be.isFormed(), "turbina sem cubo nao deveria se formar");
            helper.assertTrue(!be.lastResult().problems().isEmpty(), "deveria listar problemas");
            helper.assertTrue(be.storedEnergy() == 0L, "nao deveria gerar energia");
            helper.succeed();
        });
    }

    /** Tier maior = mais eficiente = buffer maior para a mesma geometria. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void higherTierHasLargerBuffer(GameTestHelper helper) {
        BlockPos basic = build(helper, new BlockPos(10, 1, 6),
                ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get(), true);
        BlockPos industrial = build(helper, new BlockPos(10, 1, 14),
                ModBlocks.WIND_TURBINE_CONTROLLER_INDUSTRIAL.get(), true);
        helper.runAfterDelay(10, () -> {
            WindTurbineControllerBlockEntity b = turbine(helper, basic);
            WindTurbineControllerBlockEntity i = turbine(helper, industrial);
            helper.assertTrue(b.isFormed() && i.isFormed(), "ambas deveriam estar formadas");
            helper.assertTrue(i.tier() == Tier.INDUSTRIAL, "tier = " + i.tier());
            helper.assertTrue(i.bufferCapacity() > b.bufferCapacity(),
                    "industrial " + i.bufferCapacity() + " deveria superar basico " + b.bufferCapacity());

            double ratedWatts = WindTurbinePhysics.powerWatts(BLADE, WindTurbinePhysics.RATED_SPEED,
                    WindTurbinePhysics.powerCoefficient(Tier.BASIC));
            long expected = GeneratorBuffer.capacityFor(PowerScale.toFePerTick(ratedWatts), Tier.BASIC.efficiency());
            helper.assertTrue(b.bufferCapacity() == expected,
                    "capacidade basico " + b.bufferCapacity() + " != esperado " + expected);
            helper.succeed();
        });
    }

    /** Ao formar, os blocos de andaime viram "formados" (escondidos); ao desfazer, voltam ao normal. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void structureMorphsAndReleases(GameTestHelper helper) {
        BlockPos base = new BlockPos(10, 1, 10);
        BlockPos controller = build(helper, base, ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get(), true);
        BlockPos towerBlock = base.above(5);
        BlockPos hub = controller.north();
        BlockPos bladeTip = hub.above(BLADE);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(helper.getBlockState(towerBlock).getValue(WindStructureBlock.FORMED), "torre deveria estar formada");
            helper.assertTrue(helper.getBlockState(hub).getValue(WindStructureBlock.FORMED), "cubo deveria estar formado");
            helper.assertTrue(helper.getBlockState(bladeTip).getValue(WindStructureBlock.FORMED), "ponta da pa deveria estar formada");
            helper.assertTrue(helper.getBlockState(controller).getValue(WindTurbineControllerBlock.FORMED), "controlador deveria estar formado");

            // quebra um bloco da torre e revalida: tudo deve voltar ao normal
            helper.setBlock(base.above(3), Blocks.AIR.defaultBlockState());
            turbine(helper, controller).requestRevalidation();
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!turbine(helper, controller).isFormed(), "turbina deveria ter se desfeito");
            helper.assertTrue(!helper.getBlockState(towerBlock).getValue(WindStructureBlock.FORMED), "torre deveria voltar ao normal");
            helper.assertTrue(!helper.getBlockState(bladeTip).getValue(WindStructureBlock.FORMED), "pa deveria voltar ao normal");
            helper.assertTrue(!helper.getBlockState(controller).getValue(WindTurbineControllerBlock.FORMED), "controlador deveria voltar ao normal");
            helper.succeed();
        });
    }

    /** Remover o controlador devolve todos os blocos de andaime ao estado normal. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void removingControllerReleasesStructure(GameTestHelper helper) {
        BlockPos base = new BlockPos(10, 1, 10);
        BlockPos controller = build(helper, base, ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get(), true);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(helper.getBlockState(base.above(2)).getValue(WindStructureBlock.FORMED), "torre deveria estar formada");
            helper.destroyBlock(controller);
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(helper.getBlockState(controller).isAir(), "controlador deveria ter sido removido");
            helper.assertTrue(!helper.getBlockState(base.above(2)).getValue(WindStructureBlock.FORMED), "torre deveria voltar ao normal");
            helper.assertTrue(!helper.getBlockState(controller.north()).getValue(WindStructureBlock.FORMED), "cubo deveria voltar ao normal");
            helper.succeed();
        });
    }
}
