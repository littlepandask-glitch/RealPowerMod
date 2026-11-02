package com.mmods.realpower.computer;

import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.data.DataNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/** Porta de dados do computador central: celula do volume onde os cabos de dados se ligam (desenhada pelo bloco base). */
public class ComputerPortBlock extends PowerStructureBlock implements DataNode {
    public static final MapCodec<ComputerPortBlock> CODEC = simpleCodec(ComputerPortBlock::new);

    public ComputerPortBlock(Properties properties) {
        super(properties, Variant.PORT);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
