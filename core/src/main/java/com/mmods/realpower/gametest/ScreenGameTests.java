package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.computer.ComputerSnapshot;
import com.mmods.realpower.registry.ModBlocks;
import com.mmods.realpower.screen.ScreenBlock;
import com.mmods.realpower.screen.ScreenBlockEntity;
import com.mmods.realpower.screen.ScreenGroup;
import com.mmods.realpower.screen.ScreenLayout;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RealPower.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScreenGameTests {
    private ScreenGameTests() {
    }

    private static BlockState screen(Direction facing) {
        return ModBlocks.SCREEN.get().defaultBlockState().setValue(ScreenBlock.FACING, facing);
    }

    /** Monta um painel w x h com o canto inferior esquerdo (visto de frente) em {@code origin}. */
    private static void panel(ServerLevel level, BlockPos origin, Direction facing, int w, int h) {
        Direction right = ScreenGroup.right(facing);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                level.setBlock(origin.relative(right, x).above(y), screen(facing), 3);
            }
        }
    }

    /** Blocos coplanares formam um painel e o mestre e o canto superior esquerdo, em qualquer direcao. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "screen")
    public static void screensFormRectangleWithTopLeftMaster(GameTestHelper helper) {
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos origin = helper.absolutePos(new BlockPos(4 + index * 5, 2, 4));
            panel(helper.getLevel(), origin, facing, 3, 2);
            Direction right = ScreenGroup.right(facing);
            BlockPos expected = origin.above(1);
            for (BlockPos pos : new ScreenGroup(expected, facing, 3, 2).positions()) {
                ScreenGroup group = ScreenGroup.of(helper.getLevel(), pos);
                if (group.width() != 3 || group.height() != 2 || !group.master().equals(expected)) {
                    helper.fail("painel " + facing + " em " + pos + ": " + group + ", esperado mestre " + expected);
                }
            }
            index++;
        }
        helper.succeed();
    }

    /** Conjunto que nao e retangulo cheio vira telas 1x1. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "screen")
    public static void nonRectangularScreensFallBackToSingles(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().setBlock(origin, screen(Direction.SOUTH), 3);
        helper.getLevel().setBlock(origin.east(), screen(Direction.SOUTH), 3);
        helper.getLevel().setBlock(origin.above(), screen(Direction.SOUTH), 3);
        ScreenGroup group = ScreenGroup.of(helper.getLevel(), origin);
        helper.assertTrue(group.width() == 1 && group.height() == 1 && group.master().equals(origin), "L devia virar 1x1: " + group);
        helper.succeed();
    }

    /** Cliques nas abas e no botao de janela mudam o estado do mestre. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "screen")
    public static void clicksSwitchPagesAndWindow(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        panel(helper.getLevel(), origin, Direction.SOUTH, 3, 2);
        BlockPos master = origin.above(1);
        ScreenBlockEntity be = (ScreenBlockEntity) helper.getLevel().getBlockEntity(master);
        ScreenGroup group = ScreenGroup.of(helper.getLevel(), master);
        int width = ScreenLayout.pixelWidth(group);
        be.click(group, width / ScreenLayout.PAGES * 2 + 5, 4);
        helper.assertTrue(be.page() == ScreenLayout.PAGE_GRAPHS, "pagina = " + be.page());
        be.click(group, width - 40, ScreenLayout.TAB_HEIGHT + 5);
        helper.assertTrue(be.window() == 1, "janela devia alternar: " + be.window());
        be.click(group, 5, 4);
        helper.assertTrue(be.page() == ScreenLayout.PAGE_OVERVIEW, "voltou a visao geral: " + be.page());
        helper.succeed();
    }

    /** O retrato sobrevive a ida e volta pelo codec de rede. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "screen")
    public static void snapshotCodecRoundTrip(GameTestHelper helper) {
        ComputerSnapshot original = new ComputerSnapshot(7, 8, List.of(new ComputerSnapshot.Device(new BlockPos(1, 2, 3),
                Component.translatable("block.realpower.screen"), true, 12345.5, 100L, 400L, true, false, 70)),
                new float[] {1, 2, 3}, new float[] {4}, new float[] {5, 6}, new float[0], new float[] {7}, new float[] {8, 9});
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ComputerSnapshot.CODEC.encode(buf, original);
        ComputerSnapshot copy = ComputerSnapshot.CODEC.decode(buf);
        helper.assertTrue(copy.networkSize() == 7 && copy.deviceLimit() == 8 && copy.devices().equals(original.devices()),
                "dados do retrato mudaram: " + copy);
        helper.assertTrue(java.util.Arrays.equals(copy.generationFine(), original.generationFine())
                && copy.deliveredCoarse().length == 0 && java.util.Arrays.equals(copy.storedCoarse(), original.storedCoarse()),
                "series mudaram");
        helper.succeed();
    }
}
