package com.mmods.realpower.multiblock.hydro;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/** Modulo de barragem (1 m de concreto armado): sem logica propria, e lido pela varredura {@link DamScan}. */
public class DamBlock extends Block {
    public static final MapCodec<DamBlock> CODEC = simpleCodec(DamBlock::new);

    public DamBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
