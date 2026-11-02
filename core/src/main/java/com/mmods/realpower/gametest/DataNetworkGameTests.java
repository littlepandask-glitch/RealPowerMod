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
}
