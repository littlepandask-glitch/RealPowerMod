package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.cable.CableBlock;
import com.mmods.realpower.data.DataCableBlock;
import com.mmods.realpower.data.DataDevice;
import com.mmods.realpower.data.DataNetwork;
import com.mmods.realpower.data.DataPortBlock;
import com.mmods.realpower.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.mmods.realpower.computer.ComputerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RealPower.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DataNetworkGameTests {
    private DataNetworkGameTests() {
    }

    private static BlockState port(Direction facing) {
        return ModBlocks.DATA_PORT.get().defaultBlockState().setValue(DataPortBlock.FACING, facing);
    }

    /** Cabo de dados liga-se aos vizinhos de dados e a varredura alcanca o outro lado. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "data")
    public static void dataCableConnectsAndScans(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), ModBlocks.DATA_CABLE.get().defaultBlockState());
        }
        helper.runAfterDelay(2, () -> {
            BlockState middle = helper.getBlockState(new BlockPos(3, 2, 1));
            if (!middle.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST))
                    || !middle.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST))) {
                helper.fail("cabo de dados nao conectou nos vizinhos");
            }
            DataNetwork network = DataNetwork.scan(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)));
            if (network.size() != 5 || !network.contains(helper.absolutePos(new BlockPos(5, 2, 1)))) {
                helper.fail("varredura devia achar 5 nos, achou " + network.size());
            }
            helper.succeed();
        });
    }

    /** Cabo de dados nao se liga a cabo de energia, e a rede termina onde nao ha no de dados. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "data")
    public static void dataCableIgnoresPowerCable(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.DATA_CABLE.get().defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 1), ModBlocks.NODES.get("cable_basic").get().defaultBlockState());
        helper.setBlock(new BlockPos(3, 2, 1), ModBlocks.DATA_CABLE.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            DataNetwork network = DataNetwork.scan(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)));
            if (network.size() != 1) {
                helper.fail("rede de dados nao devia atravessar cabo de energia: " + network.size());
            }
            helper.succeed();
        });
    }

    /** Porta de dados fixada num equipamento o expoe a rede; nao se liga ao proprio equipamento por tras. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "data")
    public static void dataPortExposesDevice(GameTestHelper helper) {
        BlockPos device = new BlockPos(2, 2, 2);
        helper.setBlock(device, Blocks.BARREL.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 1), port(Direction.NORTH)); // equipamento fica ao sul (atras)
        helper.setBlock(new BlockPos(2, 2, 0), ModBlocks.DATA_CABLE.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            BlockPos portPos = helper.absolutePos(new BlockPos(2, 2, 1));
            if (DataPortBlock.attachedPos(helper.getBlockState(new BlockPos(2, 2, 1)), portPos) == null
                    || !DataPortBlock.attachedPos(helper.getBlockState(new BlockPos(2, 2, 1)), portPos)
                    .equals(helper.absolutePos(device))) {
                helper.fail("porta devia estar presa ao equipamento atras dela");
            }
            DataNetwork network = DataNetwork.scan(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 0)));
            if (network.size() != 2 || !network.contains(portPos)) {
                helper.fail("rede devia ter cabo + porta, tem " + network.size());
            }
            if (!(helper.getBlockState(new BlockPos(2, 2, 0)).getBlock() instanceof DataCableBlock)) {
                helper.fail("cabo sumiu");
            }
            // sem BE que implemente DataDevice, a porta nao expoe nada
            BlockEntity be = helper.getBlockEntity(device);
            if (be instanceof DataDevice || DataDevice.at(helper.getLevel(), helper.absolutePos(device)) != null) {
                helper.fail("barril nao e um DataDevice");
            }
            helper.succeed();
        });
    }

    /** O computador forma 3x1x2 em qualquer direcao, tem 2 portas de dados que se ligam ao cabo, e quebrar uma celula o remove. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "data")
    public static void computerStructureInEveryFacing(GameTestHelper helper) {
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos base = helper.absolutePos(new BlockPos(3 + index * 5, 2, 4));
            helper.getLevel().setBlock(base, ModBlocks.COMPUTER_BASIC.get().defaultBlockState().setValue(ComputerBlock.FACING, facing), 3);
            ComputerBlock block = ModBlocks.COMPUTER_BASIC.get();
            BlockState state = helper.getLevel().getBlockState(base);
            int ports = 0;
            for (BlockPos cell : block.structureCells(base, state)) {
                Block placed = helper.getLevel().getBlockState(cell).getBlock();
                if (placed == ModBlocks.COMPUTER_PORT.get()) {
                    ports++;
                } else if (placed != ModBlocks.COMPUTER_STRUCTURE.get()) {
                    helper.fail("celula inesperada em " + cell + ": " + placed);
                }
            }
            if (ports != 2 || block.structureCells(base, state).size() != 5) {
                helper.fail("computador virado para " + facing + " devia ter 5 celulas, 2 portas: " + ports);
            }
            // cabo de dados encostado na ponta de uma porta conecta
            BlockPos beside = com.mmods.realpower.cable.CableBlock.rotate(base, facing, -2, 0, 0);
            helper.getLevel().setBlock(beside, ModBlocks.DATA_CABLE.get().defaultBlockState(), 3);
            DataNetwork network = DataNetwork.scan(helper.getLevel(), block.portCells(base, state));
            if (!network.contains(beside)) {
                helper.fail("cabo de dados ao lado da porta nao entrou na rede (" + facing + ")");
            }
            // quebrar uma celula derruba o conjunto
            helper.getLevel().destroyBlock(block.structureCells(base, state).get(3), false);
            if (!helper.getLevel().getBlockState(base).isAir()) {
                helper.fail("quebrar uma celula devia remover o computador");
            }
            index++;
        }
        helper.succeed();
    }
}
