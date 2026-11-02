package com.mmods.realpower.data;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Bloco que participa da rede de dados (cabo, porta, computador, tela). */
public interface DataNode {
    /** Se o no se liga ao vizinho que esta no lado {@code side} (visto do proprio bloco). */
    default boolean connects(BlockState state, Direction side) {
        return true;
    }
}
