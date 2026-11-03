package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.multiblock.hydro.DamScan;
import com.mmods.realpower.cable.CableKind;
import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.multiblock.hydro.HydroPhysics;
import com.mmods.realpower.multiblock.hydro.HydroUnitBlock;
import com.mmods.realpower.multiblock.hydro.HydroUnitBlockEntity;
import com.mmods.realpower.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

    private static BlockState unit(Tier tier, Direction facing) {
        return ModBlocks.HYDRO_UNITS.get(tier).get().defaultBlockState().setValue(HydroUnitBlock.FACING, facing);
    }

    /** A casa de forca forma o volume dos 3 tiers nas 4 direcoes: celulas, 2 portas no chao, base achavel de qualquer canto. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "hydro")
    public static void unitFormsAndBreaksInAllDirections(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 2, 8));
        for (int dx = -16; dx <= 16; dx += 16) {
            for (int dz = -16; dz <= 16; dz += 16) {
                level.getChunk(base.offset(dx, 0, dz)); // a maior unidade passa dos chunks carregados pelo teste
            }
        }
        for (Tier tier : ModBlocks.HYDRO_UNITS.keySet()) {
            int[] size = HydroUnitBlock.size(tier);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState state = unit(tier, facing);
                HydroUnitBlock block = (HydroUnitBlock) state.getBlock();
                var cells = block.structureCells(base, state);
                for (BlockPos cell : cells) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2); // a unidade passa dos limites (barreiras) do modelo de teste
                }
                level.setBlock(base, state, 3);
                String what = tier + " " + facing;
                helper.assertTrue(cells.size() == size[0] * size[1] * size[2] - 1, what + ": celulas = " + cells.size());
                long ports = 0;
                for (BlockPos cell : cells) {
                    helper.assertTrue(level.getBlockState(cell).getBlock() instanceof PowerStructureBlock, what + ": falta celula em " + cell + " = " + level.getBlockState(cell) + " carregada=" + level.isLoaded(cell));
                    helper.assertTrue(block.containsCell(base, state, cell), what + ": containsCell recusou " + cell);
                    if (level.getBlockState(cell).is(ModBlocks.HYDRO_PORT.get())) {
                        ports++;
                        helper.assertTrue(cell.getY() == base.getY(), what + ": porta fora do chao");
                    }
                }
                helper.assertTrue(ports == 2, what + ": portas = " + ports);
                BlockPos corner = cells.get(cells.size() - 1);
                helper.assertTrue(base.equals(PowerStructureBlock.findBase(level, corner)), what + ": findBase do canto " + corner);
                helper.assertTrue(!block.containsCell(base, state, base.above(size[1])), what + ": acima do volume");
                level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
                for (BlockPos cell : cells) {
                    helper.assertTrue(level.getBlockState(cell).isAir(), what + ": sobrou celula em " + cell);
                }
            }
        }
        helper.succeed();
    }

    /**
     * Integracao: casa de forca encaixada numa parede que fecha o vale, lago a montante (sul): forma, gera pela queda e entrega
     * pela porta ao cabo; a comporta (limite) a 0% para a entrega.
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "hydro")
    public static void unitInClosedValleyDeliversPower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockState state = unit(Tier.INDUSTRIAL, Direction.NORTH);
        // margens de pedra nos dois lados da unidade (x=4 e x=12), do chao ate acima do lago, e fundo do lago em z=17
        for (int y = 1; y <= 15; y++) {
            for (int z = 12; z <= 17; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(4, y, z)), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(helper.absolutePos(new BlockPos(12, y, z)), Blocks.STONE.defaultBlockState(), 2);
            }
            for (int x = 5; x <= 11; x++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, y, 17)), Blocks.STONE.defaultBlockState(), 2);
                if (y > 10) { // acima da unidade, a parede continua em modulos ate a crista
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, 12)),
                            (y == 15 ? ModBlocks.DAM_CREST : ModBlocks.DAM_CONCRETE).get().defaultBlockState(), 2);
                }
            }
        }
        for (int x = 5; x <= 11; x++) {
            for (int y = 2; y <= 13; y++) {
                for (int z = 13; z <= 16; z++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
        level.setBlock(base, state, 3);
        BlockPos cable = helper.absolutePos(new BlockPos(5, 2, 3));
        BlockPos sink = helper.absolutePos(new BlockPos(5, 2, 2));
        level.setBlock(sink, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        level.setBlock(cable, ModBlocks.node(CableKind.CABLE, Tier.INDUSTRIAL).get().defaultBlockState(), 3);
        TestEnergySink.RECEIVED.put(sink, 0L);

        HydroUnitBlockEntity unit = (HydroUnitBlockEntity) level.getBlockEntity(base);
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(unit.isFormed(), "casa de forca devia formar: " + unit.lastResult().problems());
            helper.assertTrue(unit.grossHead() >= 10.0, "queda = " + unit.grossHead());
            helper.assertTrue(unit.powerWatts() > 0.0, "sem potencia");
            helper.assertTrue(TestEnergySink.RECEIVED.get(sink) > 0L, "nada chegou ao consumidor pela porta");
            unit.setLimit(0);
        });
        helper.runAfterDelay(240, () -> TestEnergySink.RECEIVED.put(sink, 0L));
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(TestEnergySink.RECEIVED.get(sink) == 0L, "comporta fechada ainda entregou " + TestEnergySink.RECEIVED.get(sink));
            helper.succeed();
        });
    }
}
