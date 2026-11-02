package com.mmods.realpower.cable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bloco de preenchimento da coluna de um poste ou torre: invisivel (a estrutura 3D e desenhada pelo bloco base) e
 * ligado a ele; quebrar qualquer parte quebra o conjunto. O poste e solido e fino; a torre so mantem a selecao.
 */
public class PowerStructureBlock extends Block {
    public static final MapCodec<PowerStructureBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.BOOL.fieldOf("solid").forGetter(block -> block.solid)
    ).apply(instance, PowerStructureBlock::new));

    private static final VoxelShape POLE = Block.box(5.5, 0, 5.5, 10.5, 16, 10.5);
    private static final VoxelShape TOWER = Block.box(4, 0, 4, 12, 16, 12);

    private final boolean solid;

    public PowerStructureBlock(Properties properties, boolean solid) {
        super(properties);
        this.solid = solid;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return solid ? POLE : TOWER;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return solid ? POLE : Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (state.is(newState.getBlock()) || level.isClientSide) {
            return;
        }
        // procura o bloco base abaixo e o quebra (ele remove o resto da coluna)
        for (int d = 1; d < 64; d++) {
            BlockState below = level.getBlockState(pos.below(d));
            if (below.getBlock() instanceof CableBlock cable && cable.kind().overhead()) {
                level.destroyBlock(pos.below(d), true);
                return;
            }
            if (!(below.getBlock() instanceof PowerStructureBlock)) {
                return;
            }
        }
    }
}
