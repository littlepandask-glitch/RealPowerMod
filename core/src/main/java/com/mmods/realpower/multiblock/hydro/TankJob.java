package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Constroi o tanque de uma barragem em fatias por tick: laje da parede (concreto da barragem) ate a crista, paredes laterais e de
 * fundo (concreto cinza, que a varredura trata como terreno firme), piso e a agua. Preserva maquinas, estruturas e blocos
 * indestrutiveis. A construcao e gratuita: e uma obra civil, nao um item.
 */
@EventBusSubscriber(modid = RealPower.MOD_ID)
public final class TankJob {
    public static final int BLOCKS_PER_TICK = 4000;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<TankJob> JOBS = new ArrayList<>();

    private enum Kind { DAM, WALL, FLOOR, WATER }

    /** Caixa em coordenadas locais (inclusivas) e o que se poe nela. */
    private record Part(Kind kind, int x0, int x1, int z0, int z1, int y0, int y1) {
        long volume() {
            return (long) (x1 - x0 + 1) * (z1 - z0 + 1) * (y1 - y0 + 1);
        }
    }

    private final ServerLevel level;
    private final TankPlan plan;
    private final UUID playerId;
    private final List<Part> parts = new ArrayList<>();
    private final long total;
    private int part;
    private long visited;
    private int cx;
    private int cz;
    private int cy;
    private boolean started;

    private TankJob(ServerLevel level, TankPlan plan, UUID playerId) {
        this.level = level;
        this.plan = plan;
        this.playerId = playerId;
        int out = TankPlan.WALL;
        int top = plan.depth();
        parts.add(new Part(Kind.DAM, plan.lxMin(), plan.lxMax(), plan.slabFirst(), plan.lzFirst() - 1, 0, top));
        parts.add(new Part(Kind.WALL, plan.lxMin() - out, plan.lxMin() - 1, plan.slabFirst(), plan.lzLast() + out, 0, top));
        parts.add(new Part(Kind.WALL, plan.lxMax() + 1, plan.lxMax() + out, plan.slabFirst(), plan.lzLast() + out, 0, top));
        parts.add(new Part(Kind.WALL, plan.lxMin(), plan.lxMax(), plan.lzLast() + 1, plan.lzLast() + out, 0, top));
        parts.add(new Part(Kind.FLOOR, plan.lxMin(), plan.lxMax(), plan.lzFirst(), plan.lzLast(), -1, -1));
        parts.add(new Part(Kind.WATER, plan.lxMin(), plan.lxMax(), plan.lzFirst(), plan.lzLast(), 0, plan.depth() - 1));
        this.total = parts.stream().mapToLong(Part::volume).sum();
    }

    /** Obra para um plano dado (usada pelo inicio normal e pelos testes). */
    public static TankJob of(ServerLevel level, TankPlan plan) {
        return new TankJob(level, plan, null);
    }

    /** Inicia a obra do tanque da unidade em {@code base}; {@code false} se ja ha uma obra dessa barragem em andamento. */
    public static boolean start(ServerLevel level, BlockPos base, BlockState state, UUID playerId) {
        TankJob job = new TankJob(level, TankPlan.of(level, base, state), playerId);
        synchronized (JOBS) {
            if (JOBS.stream().anyMatch(other -> other.level == level && other.plan.units() > 0 && other.sameDam(job))) {
                return false;
            }
            JOBS.add(job);
        }
        return true;
    }

    private boolean sameDam(TankJob other) {
        return plan.base().equals(other.plan.base()) || other.plan.world(other.plan.lxMin(), other.plan.lzFirst(), 0)
                .equals(plan.world(plan.lxMin(), plan.lzFirst(), 0));
    }

    /** Andamento (0 a 100) da obra da unidade, ou -1 se nao ha obra. */
    public static int percent(ServerLevel level, BlockPos base) {
        synchronized (JOBS) {
            for (TankJob job : JOBS) {
                if (job.level == level && job.plan.base().equals(base)) {
                    return (int) Math.min(100, job.visited * 100 / Math.max(1, job.total));
                }
            }
        }
        return -1;
    }

    /** Trabalha ate {@code budget} posicoes; {@code true} ao terminar. */
    public boolean advance(int budget) {
        if (!started) {
            started = true;
            cx = parts.get(0).x0();
            cz = parts.get(0).z0();
            cy = parts.get(0).y0();
        }
        int done = 0;
        while (done < budget && part < parts.size()) {
            Part p = parts.get(part);
            apply(p.kind(), plan.world(cx, cz, cy));
            done++;
            visited++;
            if (++cx > p.x1()) {
                cx = p.x0();
                if (++cz > p.z1()) {
                    cz = p.z0();
                    if (++cy > p.y1() && ++part < parts.size()) {
                        Part next = parts.get(part);
                        cx = next.x0();
                        cz = next.z0();
                        cy = next.y0();
                    }
                }
            }
        }
        if (part >= parts.size()) {
            Player player = playerId == null ? null : level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.realpower.tank.done"), true);
            }
            return true;
        }
        return false;
    }

    private static boolean protectedBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity() || state.getBlock() instanceof PowerStructureBlock
                || state.getBlock() instanceof DamBlock;
    }

    private void apply(Kind kind, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return;
        }
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // carrega (ou gera) o trecho
        BlockState state = level.getBlockState(pos);
        if (protectedBlock(level, pos, state)) {
            return;
        }
        boolean free = state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
        switch (kind) {
            case DAM -> {
                if (free) {
                    level.setBlock(pos, ModBlocks.DAM_CONCRETE.get().defaultBlockState(), FLAGS);
                }
            }
            case WALL -> level.setBlock(pos, Blocks.GRAY_CONCRETE.defaultBlockState(), FLAGS);
            case FLOOR -> {
                if (free) {
                    level.setBlock(pos, Blocks.GRAY_CONCRETE.defaultBlockState(), FLAGS);
                }
            }
            case WATER -> {
                if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        synchronized (JOBS) {
            JOBS.removeIf(job -> job.advance(BLOCKS_PER_TICK));
        }
    }
}
