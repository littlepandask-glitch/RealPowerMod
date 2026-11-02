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
            Variant.CODEC.fieldOf("variant").forGetter(block -> block.variant)
    ).apply(instance, PowerStructureBlock::new));

    private static final VoxelShape POLE = Block.box(5.5, 0, 5.5, 10.5, 16, 10.5);
    private static final VoxelShape TOWER = Block.box(4, 0, 4, 12, 16, 12);

    /** Forma do preenchimento: poste (fino e solido), torre (so selecao) ou volume cheio (transformador). */
    public enum Variant implements net.minecraft.util.StringRepresentable {
        POLE("pole"), TOWER("tower"), BULK("bulk"), PORT("port");

        public static final Codec<Variant> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Variant::values);
        private final String id;

        Variant(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    private final Variant variant;

    public PowerStructureBlock(Properties properties, Variant variant) {
        super(properties);
        this.variant = variant;
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
        return switch (variant) {
            case POLE -> POLE;
            case TOWER -> TOWER;
            case BULK, PORT -> Shapes.block();
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return variant == Variant.TOWER ? Shapes.empty() : getShape(state, level, pos, context);
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
        // procura o bloco base cujo conjunto contem esta celula e o quebra (ele remove o resto)
        for (int dy = 0; dy < 40; dy++) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos candidate = pos.offset(dx, -dy, dz);
                    BlockState candidateState = level.getBlockState(candidate);
                    if (candidateState.getBlock() instanceof CableBlock cable && cable.kind().overhead()
                            && cable.structureCells(candidate, candidateState).contains(pos)) {
                        level.destroyBlock(candidate, true);
                        return;
                    }
                }
            }
        }
    }
}
