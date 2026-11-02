package com.mmods.realpower.item;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.cable.PowerStructureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chave inglesa: shift + clique direito remove blocos do mod (devolvendo o item). O clique sem shift e reservado
 * para configuracoes avancadas dos blocos, previstas para etapas futuras.
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(RealPower.MOD_ID)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BlockPos target = state.getBlock() instanceof PowerStructureBlock ? PowerStructureBlock.findBase(level, pos) : pos;
            level.destroyBlock(target != null ? target : pos, !player.isCreative(), player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
