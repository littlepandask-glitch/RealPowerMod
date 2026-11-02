package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.energy.GeneratorBuffer;
import com.mmods.realpower.multiblock.wind.TurbineAutobuild;
import com.mmods.realpower.multiblock.wind.TurbineBlueprint;
import com.mmods.realpower.multiblock.wind.WindStructureBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlockEntity;
import com.mmods.realpower.multiblock.wind.WindTurbineScan;
import com.mmods.realpower.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Testes automaticos da turbina eolica (rodar com {@code gradlew :core:runGameTestServer}). */
@GameTestHolder(RealPower.MOD_ID)
@PrefixGameTestTemplate(false)
public class WindTurbineGameTests {
    /** O projeto padrao e grande (~4900 blocos); o scan le 256 blocos por tick. */
    private static final int SCAN_TICKS = 80;

    /**
     * Constroi o projeto inteiro com o controlador virado para norte, direto no mundo (posicoes
     * absolutas: o helper giraria estados e posicoes conforme a rotacao do teste).
     */
    private static BlockPos build(GameTestHelper helper, Block controllerBlock) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = helper.absolutePos(new BlockPos(10, 3, 10));
        level.setBlock(controller, controllerBlock.defaultBlockState()
                .setValue(WindTurbineControllerBlock.FACING, Direction.NORTH), 3);
        for (TurbineBlueprint.Part part : TurbineBlueprint.parts(controller, Direction.NORTH)) {
            level.setBlock(part.pos(), WindTurbineScan.blockFor(part.kind()).defaultBlockState(), 2);
        }
        return controller;
    }

    private static WindTurbineControllerBlockEntity turbine(GameTestHelper helper, BlockPos controller) {
        return (WindTurbineControllerBlockEntity) helper.getLevel().getBlockEntity(controller);
    }

    private static boolean formed(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockState(pos).getValue(WindStructureBlock.FORMED);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void standardTurbineFormsMorphsAndReleases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = build(helper, ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get());
        BlockPos fairing = null;
        BlockPos casing = null;
        for (TurbineBlueprint.Part part : TurbineBlueprint.parts(controller, Direction.NORTH)) {
            if (fairing == null && part.kind() == TurbineBlueprint.Kind.FAIRING) {
                fairing = part.pos();
            }
            if (casing == null && part.kind() == TurbineBlueprint.Kind.CASING && part.pos().getY() == controller.getY() + 20) {
                casing = part.pos();
            }
        }
        final BlockPos fairingPos = fairing;
        final BlockPos casingPos = casing;

        helper.runAfterDelay(SCAN_TICKS, () -> {
            WindTurbineControllerBlockEntity be = turbine(helper, controller);
            helper.assertTrue(be.isFormed(), "turbina deveria estar formada: " + be.lastResult().problems());
            helper.assertTrue(be.tier() == Tier.BASIC, "tier = " + be.tier());
            helper.assertTrue(be.powerWatts() > 0.0, "potencia = " + be.powerWatts());
            helper.assertTrue(be.storedEnergy() <= be.bufferCapacity(), "buffer acima da capacidade");
            helper.assertTrue(formed(helper, fairingPos), "carenagem deveria estar formada");

            // tirar um bloco do corpo desfaz a estrutura
            level.setBlock(casingPos, Blocks.AIR.defaultBlockState(), 3);
            be.requestRevalidation();
        });
        helper.runAfterDelay(SCAN_TICKS * 2 + 40, () -> {
            WindTurbineControllerBlockEntity be = turbine(helper, controller);
            helper.assertTrue(!be.isFormed(), "turbina deveria ter se desfeito");
            helper.assertTrue(!formed(helper, fairingPos), "carenagem deveria voltar ao normal");
            helper.assertTrue(be.missingBlocks() == 1, "faltando = " + be.missingBlocks());

            // repor o bloco refaz; quebrar o controlador libera tudo
            level.setBlock(casingPos, ModBlocks.TURBINE_CASING.get().defaultBlockState(), 3);
            be.requestRevalidation();
        });
        helper.runAfterDelay(SCAN_TICKS * 3 + 80, () -> {
            helper.assertTrue(turbine(helper, controller).isFormed(), "turbina deveria formar de novo");
            level.destroyBlock(controller, false);
        });
        helper.runAfterDelay(SCAN_TICKS * 3 + 100, () -> {
            helper.assertTrue(!formed(helper, fairingPos), "carenagem deveria ser liberada");
            helper.succeed();
        });
    }

    /** Autobuild no criativo constroi o projeto inteiro a partir de so o controlador. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "autobuild")
    public static void autobuildCreativeFormsTurbine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = helper.absolutePos(new BlockPos(10, 3, 10));
        level.setBlock(controller, ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get().defaultBlockState()
                .setValue(WindTurbineControllerBlock.FACING, Direction.NORTH), 3);
        for (TurbineBlueprint.Part part : TurbineBlueprint.parts(controller, Direction.NORTH)) {
            level.getChunk(part.pos()); // o teste precisa dos chunks carregados, como no jogo normal
        }
        for (TurbineBlueprint.Part part : TurbineBlueprint.parts(controller, Direction.NORTH)) {
            if (!level.getBlockState(part.pos()).isAir()) {
                level.setBlock(part.pos(), Blocks.AIR.defaultBlockState(), 2); // bordas do teste e restos de outros testes grandes
            }
        }
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        helper.assertTrue(TurbineAutobuild.canBuild(player, level, controller, Direction.NORTH), "criativo deveria poder construir");
        int placed = TurbineAutobuild.build(player, level, controller, Direction.NORTH);
        helper.assertTrue(placed == TurbineBlueprint.totalBlocks(), "colocados = " + placed);
        turbine(helper, controller).requestRevalidation();
        helper.runAfterDelay(SCAN_TICKS, () -> {
            helper.assertTrue(turbine(helper, controller).isFormed(), "deveria formar apos autobuild");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void blueprintHasExpectedShape(GameTestHelper helper) {
        helper.assertTrue(TurbineBlueprint.totalBlocks() > 4000, "total = " + TurbineBlueprint.totalBlocks());
        for (TurbineBlueprint.Kind kind : TurbineBlueprint.Kind.values()) {
            helper.assertTrue(TurbineBlueprint.count(kind) > 0, "sem blocos do tipo " + kind);
        }
        // base 21x21x3 sem o controlador
        helper.assertTrue(TurbineBlueprint.count(TurbineBlueprint.Kind.BASE) == 21 * 21 * 3 - 1
                        || TurbineBlueprint.count(TurbineBlueprint.Kind.BASE) > 21 * 21 * 3 - 1,
                "base = " + TurbineBlueprint.count(TurbineBlueprint.Kind.BASE));
        helper.succeed();
    }

    /** Eficiencia maior = buffer maior para a mesma potencia nominal. */
    @GameTest(template = "empty")
    public static void bufferGrowsWithEfficiency(GameTestHelper helper) {
        long basic = GeneratorBuffer.capacityFor(1000.0, Tier.BASIC.efficiency());
        long industrial = GeneratorBuffer.capacityFor(1000.0, Tier.INDUSTRIAL.efficiency());
        helper.assertTrue(industrial > basic, "industrial " + industrial + " deveria superar basico " + basic);
        helper.succeed();
    }
}
