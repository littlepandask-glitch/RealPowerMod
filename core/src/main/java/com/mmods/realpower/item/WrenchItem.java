package com.mmods.realpower.item;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.data.DataDevice;
import com.mmods.realpower.data.DataPortBlock;
import net.minecraft.network.chat.Component;
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
 * para ligar/desligar equipamentos controlaveis (geradores, direto ou por porta de dados).
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Sem shift: liga/desliga o equipamento controlavel tocado (direto ou por uma porta de dados). */
    private static InteractionResult toggleDevice(Level level, BlockPos pos, BlockState state, Player player) {
        DataDevice device = state.getBlock() instanceof DataPortBlock ? DataPortBlock.deviceOf(level, pos, state) : DataDevice.at(level, pos);
        if (device == null || !device.controllable()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            device.setEnabled(!device.enabled());
            player.displayClientMessage(Component.translatable(device.enabled() ? "goggles.realpower.control_on"
                    : "goggles.realpower.control_off", device.limit()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!player.isShiftKeyDown()) {
            return toggleDevice(level, pos, state, player);
        }
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
