package com.mmods.realpower.cable;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Bloco base de um multibloco de celulas {@link PowerStructureBlock} (poste, torre, transformador, computador). */
public interface StructureBase {
    /** Se este bloco base de fato forma um conjunto (um cabo comum, por exemplo, nao forma). */
    boolean hasStructure();

    /** Celulas, sem o proprio bloco base, que o conjunto ocupa. */
    List<BlockPos> structureCells(BlockPos base, BlockState state);
}
