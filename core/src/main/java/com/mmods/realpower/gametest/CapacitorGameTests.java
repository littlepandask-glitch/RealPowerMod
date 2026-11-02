package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.cable.CableKind;
import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.registry.ModBlocks;
import com.mmods.realpower.storage.CapacitorBankBlock;
import com.mmods.realpower.storage.CapacitorBankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Testes do banco de capacitores: multibloco, carga com o excedente da rede, descarga na demanda e controle. */
@GameTestHolder(RealPower.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CapacitorGameTests {
    private static final int TICKS = 40;

    private CapacitorGameTests() {
    }

    private static CapacitorBankBlock basic() {
        return ModBlocks.CAPACITOR_BANKS.get(Tier.BASIC).get();
    }

    private static BlockPos placeBank(ServerLevel level, BlockPos base, Direction facing) {
        level.setBlock(base, basic().defaultBlockState().setValue(CapacitorBankBlock.FACING, facing), 3);
        return base;
    }

    private static Block cable() {
        return ModBlocks.node(CableKind.CABLE, Tier.BASIC).get();
    }

    /** O galpao ocupa o volume do tier, com 4 portas no chao, em qualquer direcao, e some ao quebrar a base. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "capacitor")
    public static void bankFormsStructureWithFourPorts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos base = helper.absolutePos(new BlockPos(3 + index * 5, 2, 4));
            placeBank(level, base, facing);
            BlockState state = level.getBlockState(base);
            CapacitorBankBlock block = (CapacitorBankBlock) state.getBlock();
            int ports = 0;
            for (BlockPos cell : block.structureCells(base, state)) {
                Block found = level.getBlockState(cell).getBlock();
                helper.assertTrue(found instanceof PowerStructureBlock, "celula " + cell + " = " + found);
                if (found == ModBlocks.CAPACITOR_PORT.get()) {
                    ports++;
                    helper.assertTrue(block.portCells(base, state).contains(cell) && cell.getY() == base.getY(), "porta fora do lugar: " + cell);
                }
            }
            helper.assertTrue(ports == 4, "portas = " + ports + " em " + facing);
            helper.assertTrue(block.structureCells(base, state).size() == 3 * 3 * 2 - 1, "volume do tier basico");
            index++;
        }
        BlockPos base = helper.absolutePos(new BlockPos(3, 2, 4));
        BlockState state = level.getBlockState(base);
        BlockPos cell = ((CapacitorBankBlock) state.getBlock()).structureCells(base, state).get(0);
        level.destroyBlock(cell, false);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockState(base).isAir(), "quebrar uma celula devia derrubar a base");
            helper.succeed();
        });
    }

    /** Banco ligado numa ponta e um gerador (alimentador) na outra: o excedente carrega o banco. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "capacitor")
    public static void surplusChargesBank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = placeBank(level, helper.absolutePos(new BlockPos(8, 2, 6)), Direction.NORTH);
        for (int x = 2; x <= 6; x++) {
            level.setBlock(helper.absolutePos(new BlockPos(x, 2, 6)), cable().defaultBlockState(), 3);
        }
        BlockPos first = helper.absolutePos(new BlockPos(2, 2, 6));
        helper.onEachTick(() -> {
            IEnergyStorage port = level.getCapability(Capabilities.EnergyStorage.BLOCK, first, null);
            if (port != null) {
                port.receiveEnergy(100_000, false);
            }
        });
        helper.runAfterDelay(TICKS, () -> {
            CapacitorBankBlockEntity bank = (CapacitorBankBlockEntity) level.getBlockEntity(base);
            long stored = bank.storedEnergy();
            helper.assertTrue(stored > 0, "o banco devia carregar com o excedente");
            helper.assertTrue(stored <= 512L * (TICKS + 1), "acima da vazao do tier: " + stored);
            helper.succeed();
        });
    }

    /** Banco cheio e consumidor na rede sem gerador: o banco cobre a demanda, ate o limite da vazao. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "capacitor")
    public static void bankCoversDemandWhenNoGenerator(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = placeBank(level, helper.absolutePos(new BlockPos(6, 2, 6)), Direction.NORTH);
        ((CapacitorBankBlockEntity) level.getBlockEntity(base)).setStoredEnergy(1_000_000L);
        BlockPos link = base.east(2);
        BlockPos sink = base.east(3);
        level.setBlock(sink, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        level.setBlock(link, cable().defaultBlockState(), 3);
        TestEnergySink.RECEIVED.put(sink, 0L);
        helper.runAfterDelay(TICKS, () -> {
            CapacitorBankBlockEntity bank = (CapacitorBankBlockEntity) level.getBlockEntity(base);
            long received = TestEnergySink.RECEIVED.get(sink);
            helper.assertTrue(received > 0, "o consumidor devia receber do banco");
            helper.assertTrue(received <= 512L * (TICKS + 1), "acima da vazao do tier: " + received);
            helper.assertTrue(bank.storedEnergy() < 1_000_000L, "o banco devia descarregar");
            helper.succeed();
        });
    }

    /** Desligado, o banco nao troca energia com a rede; com limite de 50% a vazao cai pela metade. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "capacitor")
    public static void disabledBankIsIdleAndLimitScalesRate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos off = placeBank(level, helper.absolutePos(new BlockPos(6, 2, 3)), Direction.NORTH);
        BlockPos half = placeBank(level, helper.absolutePos(new BlockPos(6, 2, 10)), Direction.NORTH);
        BlockPos[] sinks = new BlockPos[2];
        BlockPos[] banks = {off, half};
        for (int i = 0; i < 2; i++) {
            ((CapacitorBankBlockEntity) level.getBlockEntity(banks[i])).setStoredEnergy(1_000_000L);
            sinks[i] = banks[i].east(3);
            level.setBlock(sinks[i], Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            level.setBlock(banks[i].east(2), cable().defaultBlockState(), 3);
            TestEnergySink.RECEIVED.put(sinks[i], 0L);
        }
        ((CapacitorBankBlockEntity) level.getBlockEntity(off)).setEnabled(false);
        ((CapacitorBankBlockEntity) level.getBlockEntity(half)).setLimit(50);
        helper.runAfterDelay(TICKS, () -> {
            helper.assertTrue(TestEnergySink.RECEIVED.get(sinks[0]) == 0L, "banco desligado entregou energia");
            long received = TestEnergySink.RECEIVED.get(sinks[1]);
            helper.assertTrue(received > 0 && received <= 256L * (TICKS + 1), "limite de 50% = " + received);
            helper.succeed();
        });
    }
}
