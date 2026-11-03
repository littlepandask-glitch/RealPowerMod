package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.multiblock.hydro.DamScan;
import com.mmods.realpower.multiblock.hydro.HydroPhysics;
import com.mmods.realpower.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Testes da represa: fisica da queda e varredura da parede da barragem. */
@GameTestHolder(RealPower.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HydroGameTests {
    private static final int WALL_Z = 5;
    private static final int BASE_Y = 2;

    private HydroGameTests() {
    }

    /** Vale de 11 m de largura entre duas margens de pedra, parede de modulos ate y=7 e lago ate y=6 a montante (norte). */
    private static DamScan valley(GameTestHelper helper, boolean water) {
        ServerLevel level = helper.getLevel();
        for (int y = BASE_Y; y <= 8; y++) {
            for (int z = 0; z <= 6; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(0, y, z)), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(helper.absolutePos(new BlockPos(12, y, z)), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        for (int x = 1; x <= 11; x++) {
            for (int y = BASE_Y; y <= 7; y++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, y, WALL_Z)),
                        (y == 7 ? ModBlocks.DAM_CREST : ModBlocks.DAM_CONCRETE).get().defaultBlockState(), 2);
            }
            if (water) {
                for (int y = BASE_Y; y <= 6; y++) {
                    for (int z = 1; z <= 4; z++) {
                        level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.WATER.defaultBlockState(), 2);
                    }
                }
            }
        }
        return new DamScan(level, helper.absolutePos(new BlockPos(6, BASE_Y, WALL_Z - 1)), Direction.NORTH, helper.absolutePos(new BlockPos(0, BASE_Y, 0)).getY(), pos -> false);
    }

    private static void run(DamScan scan) {
        if (!scan.advance(Integer.MAX_VALUE)) {
            throw new IllegalStateException("varredura nao terminou");
        }
    }

    /** P = rho g Q H eta: a unidade avancada com queda de 80 m e lago grande entrega cerca de 494 MW. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "hydro")
    public static void physicsFollowsHeadFlowAndEfficiency(GameTestHelper helper) {
        double watts = HydroPhysics.watts(Tier.ADVANCED, 80.0, 1_000_000.0);
        helper.assertTrue(Math.abs(watts - 9810.0 * 700.0 * 80.0 * 0.9) < 1.0, "watts = " + watts);
        helper.assertTrue(HydroPhysics.watts(Tier.ADVANCED, 10.0, 1_000_000.0) == 0.0, "queda abaixo do minimo nao gera");
        helper.assertTrue(HydroPhysics.watts(Tier.ADVANCED, 500.0, 1_000_000.0) == HydroPhysics.watts(Tier.ADVANCED, 120.0, 1_000_000.0),
                "queda e limitada ao maximo de projeto");
        helper.assertTrue(HydroPhysics.flow(Tier.HIGH_VOLTAGE, 1000.0) == 20.0, "lago pequeno limita a vazao: " + HydroPhysics.flow(Tier.HIGH_VOLTAGE, 1000.0));
        helper.succeed();
    }

    /** Parede fechando o vale: valida, mede o nivel do lago e a superficie. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "hydro")
    public static void closedValleyIsValid(GameTestHelper helper) {
        DamScan scan = valley(helper, true);
        run(scan);
        helper.assertTrue(scan.result().valid(), "devia fechar: " + scan.result().problems());
        helper.assertTrue(scan.lakeSurfaceY() == helper.absolutePos(new BlockPos(0, 7, 0)).getY(), "nivel = " + scan.lakeSurfaceY());
        helper.assertTrue(scan.lakeArea() == 11 * 4, "area = " + scan.lakeArea());
        helper.succeed();
    }

    /** Um buraco na parede ou uma margem aberta reprovam a barragem. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "hydro")
    public static void breachesInvalidateTheDam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DamScan hole = valley(helper, true);
        level.setBlock(helper.absolutePos(new BlockPos(3, 4, WALL_Z)), Blocks.AIR.defaultBlockState(), 2);
        run(hole);
        helper.assertTrue(!hole.result().valid(), "buraco na parede devia reprovar");
        level.setBlock(helper.absolutePos(new BlockPos(3, 4, WALL_Z)), ModBlocks.DAM_CONCRETE.get().defaultBlockState(), 2);

        level.setBlock(helper.absolutePos(new BlockPos(12, 3, WALL_Z)), Blocks.AIR.defaultBlockState(), 2);
        DamScan bank = new DamScan(level, helper.absolutePos(new BlockPos(6, BASE_Y, WALL_Z - 1)), Direction.NORTH, helper.absolutePos(new BlockPos(0, BASE_Y, 0)).getY(), pos -> false);
        run(bank);
        helper.assertTrue(!bank.result().valid(), "margem aberta devia reprovar");
        helper.succeed();
    }

    /** Sem agua na tomada nao ha lago. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "hydro")
    public static void dryIntakeIsInvalid(GameTestHelper helper) {
        DamScan scan = valley(helper, false);
        run(scan);
        helper.assertTrue(!scan.result().valid(), "sem agua devia reprovar");
        helper.succeed();
    }
}
