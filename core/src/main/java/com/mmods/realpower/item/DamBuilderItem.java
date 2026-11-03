package com.mmods.realpower.item;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.multiblock.hydro.DamCrestBlock;
import com.mmods.realpower.multiblock.hydro.HydroLayout;
import com.mmods.realpower.multiblock.hydro.HydroUnitBlock;
import com.mmods.realpower.registry.ModBlocks;
import com.mmods.realpower.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Construtor de modulos da barragem: poe de uma vez um trecho de muro (ou de crista) na escala de cada casa de forca - largura
 * igual a da unidade, espessura igual a da parede dela. Consome os cubos do inventario (de graca no criativo). Clique direito
 * num bloco coloca o trecho (o jogador olha para o lago: a espessura corre nessa direcao); agachado + clique no ar troca o modulo.
 */
public class DamBuilderItem extends Item {
    public static final int DURABILITY = 500;

    public enum Kind { WALL, CREST }

    /** Um modulo: tipo, tier da casa de forca que ele acompanha e dimensoes (espessura x comprimento x altura). */
    public record Module(Kind kind, Tier tier, int thickness, int length, int height) {
        public Component label() {
            return Component.translatable(kind == Kind.WALL ? "message.realpower.dam_builder.wall" : "message.realpower.dam_builder.crest",
                    tier.displayName(), thickness, length, height);
        }

        public int cubes() {
            return thickness * length * height;
        }
    }

    public static final List<Module> MODULES = buildModules();

    private static List<Module> buildModules() {
        List<Module> list = new ArrayList<>();
        for (Tier tier : ModBlocks.HYDRO_UNITS.keySet()) {
            int[] size = HydroUnitBlock.size(tier);
            int thickness = HydroLayout.wallCells(size[2]);
            list.add(new Module(Kind.WALL, tier, thickness, size[0], size[1] * 4 / 9));
            list.add(new Module(Kind.CREST, tier, thickness, size[0], 1));
        }
        return List.copyOf(list);
    }

    public DamBuilderItem(Properties properties) {
        super(properties.stacksTo(1).durability(DURABILITY));
    }

    public static int mode(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Math.floorMod(tag.getInt("Mode"), MODULES.size());
    }

    public static Module module(ItemStack stack) {
        return MODULES.get(mode(stack));
    }

    /** Celulas do modulo para um clique em {@code origin} (centro da base) olhando para {@code facing}, com o guarda-corpo da crista. */
    public static List<BlockPos> cells(BlockPos origin, Direction facing, Module module) {
        List<BlockPos> cells = new ArrayList<>(module.cubes());
        Direction lateral = facing.getClockWise();
        for (int t = -(module.thickness() - 1) / 2; t <= module.thickness() / 2; t++) {
            for (int l = -(module.length() - 1) / 2; l <= module.length() / 2; l++) {
                for (int h = 0; h < module.height(); h++) {
                    cells.add(origin.relative(facing, t).relative(lateral, l).above(h));
                }
            }
        }
        return cells;
    }

    private static BlockState stateAt(BlockPos origin, Direction facing, Module module, BlockPos cell) {
        if (module.kind() == Kind.WALL) {
            return ModBlocks.DAM_CONCRETE.get().defaultBlockState();
        }
        int along = (cell.getX() - origin.getX()) * facing.getStepX() + (cell.getZ() - origin.getZ()) * facing.getStepZ();
        int edge = (module.thickness() - 1) / 2;
        DamCrestBlock.Rail rail = along == edge ? DamCrestBlock.Rail.of(facing)
                : along == -edge ? DamCrestBlock.Rail.of(facing.getOpposite()) : DamCrestBlock.Rail.NONE;
        return ModBlocks.DAM_CREST.get().defaultBlockState().setValue(DamCrestBlock.RAIL, rail);
    }

    private static Item cubeItem(Module module) {
        return module.kind() == Kind.WALL ? ModItems.DAM_CONCRETE.get() : ModItems.DAM_CREST.get();
    }

    /** Coloca o modulo; devolve a mensagem de falha (chave de traducao) ou {@code null} se deu certo. */
    public static String place(Level level, Player player, BlockPos origin, Direction facing, Module module) {
        List<BlockPos> cells = cells(origin, facing, module);
        for (BlockPos cell : cells) {
            if (level.isOutsideBuildHeight(cell) || !level.getBlockState(cell).canBeReplaced()) {
                return "message.realpower.dam_builder.blocked";
            }
        }
        boolean free = player == null || player.getAbilities().instabuild;
        Item item = cubeItem(module);
        if (!free) {
            int have = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(item)) {
                    have += stack.getCount();
                }
            }
            if (have < module.cubes()) {
                return "message.realpower.dam_builder.missing";
            }
            int left = module.cubes();
            for (ItemStack stack : player.getInventory().items) {
                if (left > 0 && stack.is(item)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        for (BlockPos cell : cells) {
            level.setBlock(cell, stateAt(origin, facing, module, cell), Block_UPDATE);
        }
        return null;
    }

    private static final int Block_UPDATE = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            Module module = module(context.getItemInHand());
            BlockPos origin = context.getClickedPos().relative(context.getClickedFace());
            String failure = place(context.getLevel(), player, origin, player.getDirection(), module);
            if (failure != null) {
                player.displayClientMessage(Component.translatable(failure, module.cubes()), true);
            } else {
                context.getItemInHand().hurtAndBreak(1, (net.minecraft.server.level.ServerLevel) context.getLevel(),
                        (net.minecraft.server.level.ServerPlayer) player, item -> { });
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player.isShiftKeyDown()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Mode", (mode(stack) + 1) % MODULES.size());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            Module next = module(stack);
            player.displayClientMessage(Component.translatable("message.realpower.dam_builder.mode", next.label(), next.cubes()), true);
        } else if (!level.isClientSide) {
            Module current = module(stack);
            player.displayClientMessage(Component.translatable("message.realpower.dam_builder.mode", current.label(), current.cubes()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
