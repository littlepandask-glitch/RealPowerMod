package com.mmods.realpower.gametest;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.cable.CableBlock;
import com.mmods.realpower.computer.ComputerBlock;
import com.mmods.realpower.computer.ComputerBlockEntity;
import com.mmods.realpower.data.DataDevice;
import com.mmods.realpower.data.DataPortBlock;
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
import net.minecraft.world.level.block.state.BlockState;
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
            helper.assertTrue(formed(helper, casingPos), "corpo deveria estar formado");

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

    /** Porta de dados no controlador expoe a turbina; desligar zera a producao e o limite a escala. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "turbinedata")
    public static void dataPortControlsTurbine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = build(helper, ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get());
        BlockPos portPos = null;
        Direction facing = null;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(controller.relative(direction)).isAir()) {
                portPos = controller.relative(direction);
                facing = direction;
                break;
            }
        }
        helper.assertTrue(portPos != null, "controlador sem face livre");
        level.setBlock(portPos, ModBlocks.DATA_PORT.get().defaultBlockState().setValue(DataPortBlock.FACING, facing), 3);
        final BlockPos port = portPos;
        helper.runAfterDelay(SCAN_TICKS, () -> {
            DataDevice device = DataPortBlock.deviceOf(level, port, level.getBlockState(port));
            helper.assertTrue(device != null, "porta devia achar o controlador");
            DataDevice.Telemetry on = device.telemetry();
            helper.assertTrue(on.online() && on.powerWatts() > 0.0, "turbina devia estar produzindo: " + on);
            device.setLimit(50);
            helper.assertTrue(Math.abs(device.telemetry().powerWatts() - on.powerWatts() / 2.0) < 1e-6, "limite de 50% devia dividir a potencia");
            device.setEnabled(false);
            helper.assertTrue(device.telemetry().powerWatts() == 0.0, "desligada devia produzir 0");
            long stored = device.telemetry().stored();
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(device.telemetry().stored() <= stored, "desligada nao devia encher o buffer");
                device.setEnabled(true);
                device.setLimit(100);
                helper.assertTrue(device.telemetry().powerWatts() > 0.0, "religada devia produzir");
                helper.succeed();
            });
        });
    }

    /** Turbina -> porta de dados -> cabos de dados -> computador: amostra, historico e comando de desligar. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "turbinecomputer")
    public static void computerSupervisesTurbineOverDataNetwork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = build(helper, ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get());
        BlockPos portPos0 = TurbineBlueprint.portPos(controller, Direction.NORTH, 0);
        BlockPos port = null;
        BlockPos computerBase = null;
        Direction computerFacing = null;
        search:
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos first = portPos0.relative(d, 1);
            BlockPos last = portPos0.relative(d, 4);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (int lx = -1; lx <= 1; lx += 2) {
                    BlockPos base = last.subtract(CableBlock.rotate(BlockPos.ZERO, facing, lx, 0, 0));
                    BlockState candidate = ModBlocks.COMPUTER_BASIC.get().defaultBlockState().setValue(ComputerBlock.FACING, facing);
                    boolean free = level.getBlockState(base).isAir() && level.getBlockState(first).isAir()
                            && level.getBlockState(portPos0.relative(d, 2)).isAir()
                            && level.getBlockState(portPos0.relative(d, 3)).isAir();
                    for (BlockPos cell : ((ComputerBlock) candidate.getBlock()).structureCells(base, candidate)) {
                        free &= level.getBlockState(cell).isAir();
                    }
                    if (free) {
                        port = first;
                        computerBase = base;
                        computerFacing = facing;
                        level.setBlock(first, ModBlocks.DATA_PORT.get().defaultBlockState().setValue(DataPortBlock.FACING, d), 3);
                        level.setBlock(portPos0.relative(d, 2), ModBlocks.DATA_CABLE.get().defaultBlockState(), 3);
                        level.setBlock(portPos0.relative(d, 3), ModBlocks.DATA_CABLE.get().defaultBlockState(), 3);
                        level.setBlock(base, candidate, 3);
                        break search;
                    }
                }
            }
        }
        helper.assertTrue(computerBase != null, "sem espaco livre para montar o teste");
        final BlockPos portPos = port;
        final BlockPos base = computerBase;
        helper.runAfterDelay(SCAN_TICKS + 60, () -> {
            ComputerBlockEntity computer = (ComputerBlockEntity) level.getBlockEntity(base);
            helper.assertTrue(computer.devices().size() == 1, "computador devia ver 1 equipamento: " + computer.devices().size());
            ComputerBlockEntity.DeviceView view = computer.devices().get(0);
            helper.assertTrue(view.port().equals(portPos) && view.telemetry().online() && view.controllable(),
                    "equipamento inesperado: " + view);
            helper.assertTrue(computer.generation().latest() > 0.0F, "historico de geracao vazio");
            helper.assertTrue(computer.commandEnabled(portPos, false), "comando de desligar recusado");
        });
        helper.runAfterDelay(SCAN_TICKS + 100, () -> {
            ComputerBlockEntity computer = (ComputerBlockEntity) level.getBlockEntity(base);
            helper.assertTrue(computer.generation().latest() == 0.0F, "desligada, a geracao devia cair a 0: " + computer.generation().latest());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "turbinecorner")
    public static void powerPortWorksInAnyCorner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = build(helper, ModBlocks.WIND_TURBINE_CONTROLLER_BASIC.get());
        int other = TurbineBlueprint.PORT_CORNER_COUNT - 1;
        BlockPos oldTop = TurbineBlueprint.portPos(controller, Direction.NORTH, 0);
        BlockPos newTop = TurbineBlueprint.portPos(controller, Direction.NORTH, other);
        level.setBlock(oldTop, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(newTop, ModBlocks.TURBINE_POWER_PORT.get().defaultBlockState(), 2);
        helper.runAfterDelay(SCAN_TICKS, () -> {
            WindTurbineControllerBlockEntity be = turbine(helper, controller);
            helper.assertTrue(be.isFormed(), "turbina com porta em outro canto deveria formar: " + be.lastResult().problems());
            level.setBlock(newTop, Blocks.AIR.defaultBlockState(), 2);
            be.requestRevalidation();
            helper.runAfterDelay(SCAN_TICKS, () -> {
                helper.assertTrue(!turbine(helper, controller).isFormed(), "sem nenhuma porta a turbina nao deveria formar");
                helper.succeed();
            });
        });
    }
}
