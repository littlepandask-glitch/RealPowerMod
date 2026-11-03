package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.cable.PowerStructureBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Tarefa de terraformacao em andamento: escava (remove) ou aterra (preenche) uma caixa em fatias por tick, sem soltar itens
 * (evita inundar o servidor). Blocos de maquinas (com entidade de bloco, estruturas do mod) e indestrutiveis sao preservados.
 */
@EventBusSubscriber(modid = RealPower.MOD_ID)
public final class TerraformJob {
    public static final int MAX_SIDE = 64;
    public static final int BLOCKS_PER_TICK = 1000;
    /** Blocos alterados por ponto de desgaste do item. */
    public static final int BLOCKS_PER_DAMAGE = 64;
    /** Materiais de aterro aceitos, em ordem de preferencia. */
    private static final Item[] FILL_ITEMS = {Items.STONE, Items.COBBLESTONE, Items.DIRT};

    private static final List<TerraformJob> JOBS = new ArrayList<>();

    private final ServerLevel level;
    private final BlockPos min;
    private final BlockPos max;
    private final boolean fill;
    private final UUID playerId;
    private int x;
    private int y;
    private int z;
    private int changed;
    private boolean done;

    public TerraformJob(ServerLevel level, BlockPos a, BlockPos b, boolean fill, UUID playerId) {
        this.level = level;
        this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        this.fill = fill;
        this.playerId = playerId;
        this.x = min.getX();
        this.y = min.getY();
        this.z = min.getZ();
    }

    /** Tamanho valido: cada lado entre 1 e {@link #MAX_SIDE}. */
    public static boolean sizeOk(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) < MAX_SIDE && Math.abs(a.getY() - b.getY()) < MAX_SIDE
                && Math.abs(a.getZ() - b.getZ()) < MAX_SIDE;
    }

    public static void start(TerraformJob job) {
        synchronized (JOBS) {
            JOBS.add(job);
        }
    }

    public boolean isDone() {
        return done;
    }

    public int changed() {
        return changed;
    }

    /** Trabalha ate {@code budget} posicoes; devolve {@code true} ao terminar. */
    public boolean advance(int budget) {
        Player player = playerId == null ? null : level.getServer().getPlayerList().getPlayer(playerId);
        int visited = 0;
        while (visited < budget && !done) {
            BlockPos pos = new BlockPos(x, y, z);
            visited++;
            if (level.isLoaded(pos) && !level.isOutsideBuildHeight(pos) && !step(pos, player)) {
                done = true;
                break;
            }
            if (++x > max.getX()) {
                x = min.getX();
                if (++z > max.getZ()) {
                    z = min.getZ();
                    if (++y > max.getY()) {
                        done = true;
                    }
                }
            }
        }
        if (done && player != null) {
            player.displayClientMessage(Component.translatable("message.realpower.terraform.done", changed), true);
        }
        return done;
    }

    /** Altera uma posicao; {@code false} interrompe a tarefa (sem material ou item gasto). */
    private boolean step(BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        boolean creative = player != null && player.getAbilities().instabuild;
        if (fill) {
            if (!(state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()) || state.hasBlockEntity()) {
                return true;
            }
            Block material = takeFillMaterial(player, creative);
            if (material == null) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.realpower.terraform.no_material"), true);
                }
                return false;
            }
            level.setBlock(pos, material.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        } else {
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity()
                    || state.getBlock() instanceof PowerStructureBlock) {
                return true;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        changed++;
        if (changed % BLOCKS_PER_DAMAGE == 0 && player != null && !creative) {
            return wear(player);
        }
        return true;
    }

    /** Gasta um ponto de desgaste da ferramenta nas maos; {@code false} se ela quebrou. */
    private static boolean wear(Player player) {
        ItemStack held = player.getMainHandItem().getItem() instanceof com.mmods.realpower.item.TerraformerItem
                ? player.getMainHandItem() : player.getOffhandItem();
        if (!(held.getItem() instanceof com.mmods.realpower.item.TerraformerItem)) {
            return true;
        }
        held.hurtAndBreak(1, (ServerLevel) player.level(), player instanceof ServerPlayer sp ? sp : null, item -> { });
        return !held.isEmpty();
    }

    /** Consome um item de aterro do inventario e devolve o bloco correspondente; {@code null} se faltar. */
    private static Block takeFillMaterial(Player player, boolean creative) {
        if (player == null) {
            return Blocks.STONE;
        }
        for (Item item : FILL_ITEMS) {
            Block block = ((BlockItem) item).getBlock();
            if (creative) {
                return block;
            }
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(item)) {
                    stack.shrink(1);
                    return block;
                }
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        synchronized (JOBS) {
            JOBS.removeIf(job -> job.advance(BLOCKS_PER_TICK));
        }
    }
}
