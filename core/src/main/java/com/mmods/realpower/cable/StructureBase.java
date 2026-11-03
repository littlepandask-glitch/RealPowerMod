package com.mmods.realpower.cable;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Bloco base de um multibloco de celulas {@link PowerStructureBlock} (poste, torre, transformador, computador). */
public interface StructureBase {
    /** Se este bloco base de fato forma um conjunto (um cabo comum, por exemplo, nao forma). */
    boolean hasStructure();

    /** Celulas, sem o proprio bloco base, que o conjunto ocupa. */
    List<BlockPos> structureCells(BlockPos base, BlockState state);

    /** Se {@code cell} e uma das celulas do conjunto; estruturas grandes devem sobrescrever com aritmetica. */
    default boolean containsCell(BlockPos base, BlockState state, BlockPos cell) {
        return structureCells(base, state).contains(cell);
    }

    /** Estado (orientacao) que o conjunto tera ao ser posto por um jogador olhando para {@code look}, sem checar espaco. */
    BlockState footprintState(Direction look);

    /** Caixa que o conjunto ocupa, incluindo o bloco base. */
    default BoundingBox footprint(BlockPos base, BlockState state) {
        BoundingBox box = new BoundingBox(base);
        for (BlockPos cell : structureCells(base, state)) {
            box.encapsulate(cell);
        }
        return box;
    }

    /**
     * Posicao do bloco base para que o canto proximo-esquerdo da pegada (visto pelo jogador que olha para {@code look}) caia em
     * {@code corner}: a pegada se estende para a frente e para a direita do jogador, a partir do chao.
     */
    default BlockPos baseForCorner(BlockPos corner, Direction look) {
        BoundingBox box = footprint(BlockPos.ZERO, footprintState(look));
        Direction right = look.getClockWise();
        int sx = look.getStepX() != 0 ? look.getStepX() : right.getStepX();
        int sz = look.getStepZ() != 0 ? look.getStepZ() : right.getStepZ();
        return corner.offset(-(sx > 0 ? box.minX() : box.maxX()), -box.minY(), -(sz > 0 ? box.minZ() : box.maxZ()));
    }
}
