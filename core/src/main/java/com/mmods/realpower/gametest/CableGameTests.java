package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.cable.CableKind;
import com.mmods.realpower.cable.CableNetwork;
import com.mmods.realpower.cable.CableNetworks;
import com.mmods.realpower.registry.ModBlocks;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.mmods.realpower.cable.CableBlock;
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

    /** Poste/torre com as travessas ao longo de x (a linha de testes corre em +x). */
    private static BlockState alongX(Block block) {
        return block.defaultBlockState().setValue(CableBlock.FACING, Direction.EAST);
    }

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
        Block basic = ModBlocks.node(CableKind.CABLE, Tier.BASIC).get();
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
        Block industrial = ModBlocks.node(CableKind.CABLE, Tier.INDUSTRIAL).get();
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
        Block industrial = ModBlocks.node(CableKind.CABLE, Tier.INDUSTRIAL).get();
        Block basic = ModBlocks.node(CableKind.CABLE, Tier.BASIC).get();
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
        Block basic = ModBlocks.node(CableKind.CABLE, Tier.BASIC).get();
        BlockPos sink = line(helper, 7, basic, basic, basic, basic);
        feed(helper, 7);
        BlockPos middle = helper.absolutePos(new BlockPos(4, 2, 7));
        helper.runAfterDelay(10, () -> {
            helper.getLevel().setBlock(middle, Blocks.AIR.defaultBlockState(), 3);
        });
        // a energia ja armazenada no lado do consumidor ainda pode escoar logo apos a quebra; zera depois disso
        helper.runAfterDelay(16, () -> TestEnergySink.RECEIVED.put(sink, 0L));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(TestEnergySink.RECEIVED.get(sink) == 0L,
                    "energia passou por cabo quebrado: " + TestEnergySink.RECEIVED.get(sink));
            CableNetwork left = CableNetworks.networkAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 7)));
            helper.assertTrue(left != null && left.cableCount() == 2, "rede esquerda deveria ter 2 cabos");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void relayShortensTheLossyRun(GameTestHelper helper) {
        Block cable = ModBlocks.node(CableKind.CABLE, Tier.BASIC).get();
        Block relay = ModBlocks.node(CableKind.RELAY, Tier.BASIC).get();
        Block[] plain = new Block[20];
        java.util.Arrays.fill(plain, cable);
        Block[] withRelays = plain.clone();
        withRelays[6] = relay;
        withRelays[13] = relay;
        line(helper, 9, plain);
        line(helper, 11, withRelays);
        helper.runAfterDelay(10, () -> {
            CableNetwork bare = CableNetworks.networkAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 9)));
            CableNetwork relayed = CableNetworks.networkAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 11)));
            helper.assertTrue(bare != null && relayed != null, "redes nao formadas");
            helper.assertTrue(relayed.loss() < bare.loss() / 2.0,
                    "rele deveria reduzir a perda: " + relayed.loss() + " vs " + bare.loss());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void poleLineCarriesEnergyWithLittleLoss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block cable = ModBlocks.node(CableKind.CABLE, Tier.BASIC).get();
        Block pole = ModBlocks.node(CableKind.POLE, Tier.BASIC).get();
        BlockPos sink = helper.absolutePos(new BlockPos(24, 2, 13));
        level.setBlock(sink, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        TestEnergySink.RECEIVED.put(sink, 0L);
        level.setBlock(helper.absolutePos(new BlockPos(2, 2, 13)), cable.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(3, 2, 13)), alongX(pole), 3);
        level.setBlock(helper.absolutePos(new BlockPos(23, 2, 13)), alongX(pole), 3);
        feed(helper, 13);
        helper.runAfterDelay(TICKS, () -> {
            CableNetwork network = CableNetworks.networkAt(level, helper.absolutePos(new BlockPos(2, 2, 13)));
            helper.assertTrue(network != null && network.cableCount() >= 3, "rede deveria ter 3 nos ligados pela linha aerea");
            helper.assertTrue(network.loss() < 0.15, "linha aerea deveria perder pouco: " + network.loss());
            helper.assertTrue(TestEnergySink.RECEIVED.get(sink) > 0, "nada chegou pela linha aerea");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void linesNeverCrossThroughANeighbour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block pole = ModBlocks.node(CableKind.POLE, Tier.BASIC).get();
        BlockPos[] poles = {helper.absolutePos(new BlockPos(2, 2, 17)), helper.absolutePos(new BlockPos(12, 2, 17)),
                helper.absolutePos(new BlockPos(22, 2, 17))};
        for (BlockPos at : poles) {
            level.setBlock(at, alongX(pole), 3);
        }
        helper.runAfterDelay(10, () -> {
            var first = (com.mmods.realpower.cable.CableBlockEntity) level.getBlockEntity(poles[0]);
            var middle = (com.mmods.realpower.cable.CableBlockEntity) level.getBlockEntity(poles[1]);
            helper.assertTrue(first.links().contains(poles[1]) && middle.links().contains(poles[2]), "vizinhos proximos deveriam ligar");
            helper.assertTrue(!first.links().contains(poles[2]), "linha atravessou o poste do meio");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void towerOccupiesAFullColumn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 2, 19));
        level.setBlock(base, ModBlocks.node(CableKind.TOWER, Tier.HIGH_VOLTAGE).get().defaultBlockState(), 3);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getBlockState(base.above(CableKind.TOWER.height() - 1)).is(ModBlocks.TOWER_STRUCTURE.get()),
                    "a coluna da torre nao foi preenchida");
            level.destroyBlock(base.above(10), false);
            helper.assertTrue(level.getBlockState(base).isAir(), "quebrar a coluna deveria derrubar a torre");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void towersDoNotLinkSideways(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block tower = ModBlocks.node(CableKind.TOWER, Tier.HIGH_VOLTAGE).get();
        BlockPos a = helper.absolutePos(new BlockPos(2, 2, 25));
        BlockPos b = helper.absolutePos(new BlockPos(2, 2, 45));
        BlockPos c = helper.absolutePos(new BlockPos(22, 2, 25));
        // as travessas de a e b correm ao longo de z (frente-fundo): b esta na linha, c esta de lado
        for (BlockPos at : new BlockPos[]{a, b, c}) {
            level.setBlock(at, tower.defaultBlockState(), 3);
        }
        helper.runAfterDelay(10, () -> {
            var first = (com.mmods.realpower.cable.CableBlockEntity) level.getBlockEntity(a);
            helper.assertTrue(first.links().contains(b), "torres alinhadas ao eixo deveriam ligar");
            helper.assertTrue(!first.links().contains(c), "torre ao lado nao deveria ligar");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void transformerGrowsWithTier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(6, 2, 27));
        level.setBlock(base, ModBlocks.node(CableKind.TRANSFORMER, Tier.FUTURE).get().defaultBlockState(), 3);
        helper.runAfterDelay(5, () -> {
            int[] size = CableKind.TRANSFORMER.size(Tier.FUTURE);
            helper.assertTrue(size[0] > CableKind.TRANSFORMER.size(Tier.INDUSTRIAL)[0], "tier maior deveria ser maior");
            helper.assertTrue(level.getBlockState(base.offset(0, size[2] - 1, 0)).is(ModBlocks.TRANSFORMER_STRUCTURE.get()),
                    "volume do transformador nao preenchido");
            level.destroyBlock(base.offset(0, 1, 1), false);
            helper.assertTrue(level.getBlockState(base).isAir() && level.getBlockState(base.offset(0, 2, 0)).isAir(),
                    "quebrar uma parte deveria remover o transformador inteiro");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void transformerFeedsOverheadLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos transformer = helper.absolutePos(new BlockPos(4, 2, 21));
        BlockPos pole = helper.absolutePos(new BlockPos(22, 2, 21));
        BlockPos sink = helper.absolutePos(new BlockPos(23, 2, 21));
        level.setBlock(sink, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        TestEnergySink.RECEIVED.put(sink, 0L);
        level.setBlock(helper.absolutePos(new BlockPos(2, 2, 21)), ModBlocks.node(CableKind.CABLE, Tier.INDUSTRIAL).get().defaultBlockState(), 3);
        level.setBlock(transformer, ModBlocks.node(CableKind.TRANSFORMER, Tier.INDUSTRIAL).get().defaultBlockState(), 3);
        level.setBlock(pole, alongX(ModBlocks.node(CableKind.POLE, Tier.INDUSTRIAL).get()), 3);
        feed(helper, 21);
        helper.runAfterDelay(TICKS, () -> {
            CableNetwork network = CableNetworks.networkAt(level, transformer);
            helper.assertTrue(network != null && network.cableCount() >= 3, "cabo na porta + transformador + poste deveriam formar uma rede");
            helper.assertTrue(TestEnergySink.RECEIVED.get(sink) > 0, "nada chegou pela linha aerea");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void cablesConnectToTransformerPortsInEveryFacing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block transformer = ModBlocks.node(CableKind.TRANSFORMER, Tier.INDUSTRIAL).get();
        Block cable = ModBlocks.node(CableKind.CABLE, Tier.INDUSTRIAL).get();
        BlockPos base = helper.absolutePos(new BlockPos(8, 2, 31));
        java.util.List<Direction> facings = java.util.List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
        for (int i = 0; i < facings.size(); i++) {
            Direction facing = facings.get(i);
            int step = i;
            helper.runAfterDelay(step * 10L, () -> {
                BlockPos where = base.offset(0, 0, 0);
                level.setBlock(where, transformer.defaultBlockState().setValue(CableBlock.FACING, facing), 3);
            });
            helper.runAfterDelay(step * 10L + 3, () -> {
                BlockState state = level.getBlockState(base);
                for (BlockPos port : ((CableBlock) state.getBlock()).portCells(base, state)) {
                    helper.assertTrue(level.getBlockState(port).is(ModBlocks.TRANSFORMER_PORT.get()),
                            "celula de porta ausente " + facing + " " + port);
                    Direction out = port.getX() != base.getX() ? (port.getX() > base.getX() ? Direction.EAST : Direction.WEST)
                            : (port.getZ() > base.getZ() ? Direction.SOUTH : Direction.NORTH);
                    BlockPos at = port.relative(out);
                    level.setBlock(at, cable.defaultBlockState(), 3);
                }
            });
            helper.runAfterDelay(step * 10L + 8, () -> {
                BlockState state = level.getBlockState(base);
                for (BlockPos port : ((CableBlock) state.getBlock()).portCells(base, state)) {
                    Direction out = port.getX() != base.getX() ? (port.getX() > base.getX() ? Direction.EAST : Direction.WEST)
                            : (port.getZ() > base.getZ() ? Direction.SOUTH : Direction.NORTH);
                    BlockState cableState = level.getBlockState(port.relative(out));
                    helper.assertTrue(cableState.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(out.getOpposite())),
                            "cabo nao conectou na porta (" + facing + ")");
                    level.setBlock(port.relative(out), Blocks.AIR.defaultBlockState(), 3);
                }
                level.destroyBlock(base, false);
                if (step == 3) {
                    helper.succeed();
                }
            });
        }
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void gogglesDescribeNetworkParts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cable = helper.absolutePos(new BlockPos(2, 2, 33));
        BlockPos base = helper.absolutePos(new BlockPos(6, 2, 33));
        level.setBlock(cable, ModBlocks.node(CableKind.CABLE, Tier.INDUSTRIAL).get().defaultBlockState(), 3);
        level.setBlock(base, ModBlocks.node(CableKind.TRANSFORMER, Tier.INDUSTRIAL).get().defaultBlockState(), 3);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(com.mmods.realpower.goggles.Goggles.describe(level, cable).size() >= 4, "balao do cabo sem dados");
            helper.assertTrue(com.mmods.realpower.goggles.Goggles.describe(level, base.offset(1, 1, 1)).size() >= 4,
                    "balao de uma celula do transformador deveria descrever o conjunto");
            helper.assertTrue(com.mmods.realpower.goggles.Goggles.describe(level, base.offset(5, 0, 0)).isEmpty(), "ar nao tem balao");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "cable")
    public static void turbinePortFeedsAdjacentCable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos port = helper.absolutePos(new BlockPos(2, 2, 15));
        BlockPos sink = helper.absolutePos(new BlockPos(4, 2, 15));
        level.setBlock(sink, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        TestEnergySink.RECEIVED.put(sink, 0L);
        level.setBlock(port, ModBlocks.TURBINE_POWER_PORT.get().defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(3, 2, 15)),
                ModBlocks.node(CableKind.CABLE, Tier.BASIC).get().defaultBlockState(), 3);
        helper.onEachTick(() -> ((com.mmods.realpower.multiblock.wind.TurbinePowerPortBlockEntity) level.getBlockEntity(port))
                .feed().receiveEnergy(1000, false));
        helper.runAfterDelay(TICKS, () -> {
            helper.assertTrue(TestEnergySink.RECEIVED.get(sink) > 0, "a porta nao entregou energia ao cabo");
            helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, port, null).receiveEnergy(100, false) == 0,
                    "a porta nao deveria aceitar energia de fora");
            helper.succeed();
        });
    }
}
