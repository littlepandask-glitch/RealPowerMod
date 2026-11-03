package com.mmods.realpower.item;

import com.mmods.realpower.cable.StructureBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Item de um multibloco: o ponto clicado e o canto proximo-esquerdo da pegada (ela se estende para a frente e para a direita do
 * jogador) e nao o centro. O overlay {@code PlacementPreviewRenderer} mostra a area antes de colocar.
 */
public class StructureBlockItem extends BlockItem {
    public StructureBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Base do conjunto para este contexto, ou {@code null} se o bloco nao e um multibloco. */
    public BlockPos baseFor(BlockPlaceContext context) {
        if (getBlock() instanceof StructureBase structure && structure.hasStructure()) {
            return structure.baseForCorner(context.getClickedPos(), context.getHorizontalDirection());
        }
        return null;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        BlockPos base = baseFor(context);
        if (base != null && !base.equals(context.getClickedPos())) {
            if (!context.getLevel().getBlockState(base).canBeReplaced()) {
                return InteractionResult.FAIL;
            }
            context = BlockPlaceContext.at(context, base, context.getClickedFace());
        }
        return super.place(context);
    }
}
