package com.mmods.realpower.item;

import com.mmods.realpower.multiblock.hydro.TerraformJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Terraformador: marca uma caixa (clique direito em dois blocos) e a escava ou aterra. Clique direito no ar executa;
 * shift + clique direito no ar alterna escavar/aterrar; shift + clique direito num bloco limpa a selecao.
 */
public class TerraformerItem extends Item {
    public static final int DURABILITY = 2000;

    /** Selecao guardada no item: canto A, canto B (opcional) e o modo. */
    public record Selection(BlockPos a, BlockPos b, boolean fill) {
    }

    public TerraformerItem(Properties properties) {
        super(properties.stacksTo(1).durability(DURABILITY));
    }

    public static Selection selection(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        BlockPos a = tag.contains("A") ? NbtUtils.readBlockPos(tag, "A").orElse(null) : null;
        BlockPos b = tag.contains("B") ? NbtUtils.readBlockPos(tag, "B").orElse(null) : null;
        return new Selection(a, b, tag.getBoolean("Fill"));
    }

    public static void store(ItemStack stack, Selection selection) {
        CompoundTag tag = new CompoundTag();
        if (selection.a() != null) {
            tag.put("A", NbtUtils.writeBlockPos(selection.a()));
        }
        if (selection.b() != null) {
            tag.put("B", NbtUtils.writeBlockPos(selection.b()));
        }
        tag.putBoolean("Fill", selection.fill());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            ItemStack stack = context.getItemInHand();
            Selection current = selection(stack);
            BlockPos pos = context.getClickedPos();
            if (player.isShiftKeyDown()) {
                store(stack, new Selection(null, null, current.fill()));
                player.displayClientMessage(Component.translatable("message.realpower.terraform.cleared"), true);
            } else if (current.a() == null || current.b() != null) {
                store(stack, new Selection(pos, null, current.fill()));
                player.displayClientMessage(Component.translatable("message.realpower.terraform.corner_a", pos.toShortString()), true);
            } else if (!TerraformJob.sizeOk(current.a(), pos)) {
                player.displayClientMessage(Component.translatable("message.realpower.terraform.too_big", TerraformJob.MAX_SIDE), true);
            } else {
                store(stack, new Selection(current.a(), pos, current.fill()));
                player.displayClientMessage(Component.translatable("message.realpower.terraform.corner_b",
                        Math.abs(pos.getX() - current.a().getX()) + 1, Math.abs(pos.getY() - current.a().getY()) + 1,
                        Math.abs(pos.getZ() - current.a().getZ()) + 1,
                        Component.translatable(current.fill() ? "message.realpower.terraform.mode_fill" : "message.realpower.terraform.mode_dig")), true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        Selection current = selection(stack);
        if (player.isShiftKeyDown()) {
            store(stack, new Selection(current.a(), current.b(), !current.fill()));
            player.displayClientMessage(Component.translatable(current.fill() ? "message.realpower.terraform.mode_dig_set"
                    : "message.realpower.terraform.mode_fill_set"), true);
        } else if (current.a() != null && current.b() != null) {
            TerraformJob.start(new TerraformJob((ServerLevel) level, current.a(), current.b(), current.fill(), player.getUUID()));
            player.displayClientMessage(Component.translatable("message.realpower.terraform.started"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.realpower.terraform.need_box"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
