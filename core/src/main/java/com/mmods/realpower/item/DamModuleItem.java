package com.mmods.realpower.item;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.multiblock.hydro.DamCrestBlock;
import com.mmods.realpower.multiblock.hydro.HydroLayout;
import com.mmods.realpower.multiblock.hydro.HydroUnitBlock;
import com.mmods.realpower.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Modulo pronto da barragem, na escala da casa de forca do tier: largura igual a da unidade, espessura igual a da parede dela.
 * Um item coloca o trecho inteiro de muro (ou de crista, com guarda-corpo so nas bordas externas).
 */
public class DamModuleItem extends Item {
    public enum Kind { WALL, CREST }

    /** Um modulo: tipo, tier da casa de forca que ele acompanha e dimensoes (espessura x comprimento x altura). */
    public record Module(Kind kind, Tier tier, int thickness, int length, int height) {
        public String id() {
            return (kind == Kind.WALL ? "dam_wall_" : "dam_crest_") + tier.getSerializedName();
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

    private final Module module;

    public DamModuleItem(Properties properties, Module module) {
        super(properties);
        this.module = module;
    }

    public Module module() {
        return module;
    }

    /**
     * Celulas do modulo com o canto proximo-esquerdo e inferior em {@code origin}: a espessura corre para a frente de quem olha
     * para {@code facing} e o comprimento para a direita dele.
     */
    public static List<BlockPos> cells(BlockPos origin, Direction facing, Module module) {
        List<BlockPos> cells = new ArrayList<>(module.cubes());
        Direction lateral = facing.getClockWise();
        for (int t = 0; t < module.thickness(); t++) {
            for (int l = 0; l < module.length(); l++) {
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
        DamCrestBlock.Rail rail = along == module.thickness() - 1 ? DamCrestBlock.Rail.of(facing)
                : along == 0 ? DamCrestBlock.Rail.of(facing.getOpposite()) : DamCrestBlock.Rail.NONE;
        return ModBlocks.DAM_CREST.get().defaultBlockState().setValue(DamCrestBlock.RAIL, rail);
    }

    /** Coloca o modulo; devolve a chave de traducao da falha ou {@code null} se deu certo. */
    public static String place(Level level, BlockPos origin, Direction facing, Module module) {
        List<BlockPos> cells = cells(origin, facing, module);
        for (BlockPos cell : cells) {
            if (level.isOutsideBuildHeight(cell) || !level.getBlockState(cell).canBeReplaced()) {
                return "message.realpower.dam_module.blocked";
            }
        }
        for (BlockPos cell : cells) {
            level.setBlock(cell, stateAt(origin, facing, module, cell), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        return null;
    }

    /** Caixa (em celulas, inclusive) que o modulo ocupa para um canto em {@code origin}. */
    public static net.minecraft.world.level.levelgen.structure.BoundingBox bounds(BlockPos origin, Direction facing, Module module) {
        BlockPos far = origin.relative(facing, module.thickness() - 1).relative(facing.getClockWise(), module.length() - 1)
                .above(module.height() - 1);
        return net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(origin, far);
    }

    /** Clique direito num bloco: o clique e o canto proximo-esquerdo do modulo, que se estende para a frente e para a direita. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            BlockPos origin = new net.minecraft.world.item.context.BlockPlaceContext(context).getClickedPos();
            String failure = place(context.getLevel(), origin, player.getDirection(), module);
            if (failure != null) {
                player.displayClientMessage(Component.translatable(failure), true);
            } else if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
