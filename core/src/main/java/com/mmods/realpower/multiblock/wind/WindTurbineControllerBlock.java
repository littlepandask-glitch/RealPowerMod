package com.mmods.realpower.multiblock.wind;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.api.tier.Tiered;
import com.mmods.realpower.generator.AbstractGeneratorBlockEntity;
import com.mmods.realpower.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Nacele da turbina: valida a estrutura e gera energia. A frente do bloco e o lado do rotor. */
public class WindTurbineControllerBlock extends BaseEntityBlock implements Tiered {
    public static final MapCodec<WindTurbineControllerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Tier.CODEC.fieldOf("tier").forGetter(WindTurbineControllerBlock::tier)
    ).apply(instance, WindTurbineControllerBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Tier tier;

    public WindTurbineControllerBlock(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public Tier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // o rotor aponta para onde o jogador olha; a porta da torre fica as suas costas
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindTurbineControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.WIND_TURBINE_CONTROLLER.get(),
                AbstractGeneratorBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WindTurbineControllerBlockEntity turbine) {
            turbine.releaseStructure();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WindTurbineControllerBlockEntity turbine) {
            turbine.requestRevalidation();
            player.openMenu(turbine, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
