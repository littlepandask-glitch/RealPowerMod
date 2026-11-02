package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.cable.CableNetwork;
import com.mmods.realpower.cable.CableNetworks;
import com.mmods.realpower.registry.ModBlocks;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Testes da rede de cabos: vazao, elo mais fraco, perda na linha e divisao da rede. */
@GameTestHolder(RealPower.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableGameTests {
    private static final int TICKS = 40;

    /** Linha de cabos ao longo de +x com um consumidor de teste depois do ultimo. Devolve a posicao do consumidor. */
    private static BlockPos line(GameTestHelper helper, int row, Block... cables) {
        ServerLevel level = helper.getLevel();
        BlockPos sink = helper.absolutePos(new BlockPos(2 + cables.length, 2, row));
        level.setBlock(sink, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        TestEnergySink.RECEIVED.put(sink, 0L);
        for (int i = 0; i < cables.length; i++) {
            level.setBlock(helper.absolutePos(new BlockPos(2 + i, 2, row)), cables[i].defaultBlockState(), 3);
        }
        return sink;
    }

    /** Empurra muita energia na primeira ponta todo tick e soma o que a rede aceitou. */
    private static AtomicLong feed(GameTestHelper helper, int row) {
        AtomicLong accepted = new AtomicLong();
        BlockPos first = helper.absolutePos(new BlockPos(2, 2, row));
        helper.onEachTick(() -> {
            IEnergyStorage port = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, first, null);
            if (port != null) {
                accepted.addAndGet(port.receiveEnergy(1_000_000, false));
            }
        });
        return accepted;
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void basicCableLimitsRateAndLosesEnergy(GameTestHelper helper) {
        Block basic = ModBlocks.CABLE_BASIC.get();
        BlockPos sink = line(helper, 1, basic, basic, basic, basic, basic, basic);
        AtomicLong accepted = feed(helper, 1);
        helper.runAfterDelay(TICKS, () -> {
            long received = TestEnergySink.RECEIVED.get(sink);
            helper.assertTrue(received > 0, "nada chegou ao consumidor");
            helper.assertTrue(received <= 512L * (TICKS + 1), "acima da vazao do tier basico: " + received);
            helper.assertTrue(received < accepted.get(), "sem perda: recebeu " + received + " de " + accepted.get());
            CableNetwork network = CableNetworks.networkAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 1)));
            helper.assertTrue(network != null && network.cableCount() == 6, "rede deveria ter 6 cabos");
            helper.assertTrue(network.loss() > 0.0, "perda = " + network.loss());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void industrialCableCarriesMoreThanBasic(GameTestHelper helper) {
        Block industrial = ModBlocks.CABLE_INDUSTRIAL.get();
        BlockPos sink = line(helper, 3, industrial, industrial, industrial, industrial);
        feed(helper, 3);
        helper.runAfterDelay(TICKS, () -> {
            long received = TestEnergySink.RECEIVED.get(sink);
            helper.assertTrue(received > 512L * TICKS, "industrial deveria passar mais que o basico: " + received);
            helper.assertTrue(received <= 4096L * (TICKS + 1), "acima da vazao industrial: " + received);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void weakestCableSetsNetworkRate(GameTestHelper helper) {
        Block industrial = ModBlocks.CABLE_INDUSTRIAL.get();
        Block basic = ModBlocks.CABLE_BASIC.get();
        BlockPos sink = line(helper, 5, industrial, industrial, basic, industrial, industrial);
        feed(helper, 5);
        helper.runAfterDelay(TICKS, () -> {
            long received = TestEnergySink.RECEIVED.get(sink);
            helper.assertTrue(received > 0 && received <= 512L * (TICKS + 1), "elo basico deveria limitar: " + received);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void breakingACableSplitsTheNetwork(GameTestHelper helper) {
        Block basic = ModBlocks.CABLE_BASIC.get();
        BlockPos sink = line(helper, 7, basic, basic, basic, basic);
        feed(helper, 7);
        BlockPos middle = helper.absolutePos(new BlockPos(4, 2, 7));
        helper.runAfterDelay(10, () -> {
            helper.getLevel().setBlock(middle, Blocks.AIR.defaultBlockState(), 3);
            TestEnergySink.RECEIVED.put(sink, 0L);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(TestEnergySink.RECEIVED.get(sink) == 0L,
                    "energia passou por cabo quebrado: " + TestEnergySink.RECEIVED.get(sink));
            CableNetwork left = CableNetworks.networkAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 7)));
            helper.assertTrue(left != null && left.cableCount() == 2, "rede esquerda deveria ter 2 cabos");
            helper.succeed();
        });
    }
}
