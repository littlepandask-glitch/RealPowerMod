package com.mmods.realpower.multiblock.wind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

/**
 * Construcao automatica: coloca os blocos que faltam no projeto, tirando-os do inventario do
 * jogador e de baus proximos ao controlador (no criativo nao consome nada). So ocupa posicoes
 * substituiveis ou de terreno comum (a laje fica enterrada); blocos indestrutiveis ou com dados
 * (baus, maquinas) no caminho continuam la e aparecem como faltando.
 */
public final class TurbineAutobuild {
    /** Raio (em blocos) em torno do controlador onde inventarios contam. */
    public static final int CONTAINER_RADIUS = 8;

    private TurbineAutobuild() {
    }

    /** Posicoes livres do projeto que ainda nao tem o bloco certo. */
    public static List<TurbineBlueprint.Part> placeable(Level level, BlockPos controller, Direction facing) {
        List<TurbineBlueprint.Part> list = new ArrayList<>();
        boolean portDone = WindTurbineScan.portMissing(level, controller, facing) == 0;
        for (TurbineBlueprint.Part part : TurbineBlueprint.parts(controller, facing)) {
            if (!level.isLoaded(part.pos()) || portDone && TurbineBlueprint.isPortPart(part.kind())) {
                continue;
            }
            var state = level.getBlockState(part.pos());
            if (!state.is(WindTurbineScan.blockFor(part.kind())) && overwritable(level, part.pos(), state)) {
                list.add(part);
            }
        }
        return list;
    }

    /**
     * Ar, plantas e terreno (a laje fica enterrada) podem ser trocados; blocos indestrutiveis e
     * blocos com inventario/dados (baus, maquinas) nunca sao apagados.
     */
    private static boolean overwritable(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return state.canBeReplaced() || (state.getDestroySpeed(level, pos) >= 0.0F && level.getBlockEntity(pos) == null);
    }

    private static EnumMap<TurbineBlueprint.Kind, Integer> countByKind(List<TurbineBlueprint.Part> parts) {
        EnumMap<TurbineBlueprint.Kind, Integer> counts = new EnumMap<>(TurbineBlueprint.Kind.class);
        for (TurbineBlueprint.Part part : parts) {
            counts.merge(part.kind(), 1, Integer::sum);
        }
        return counts;
    }

    private static List<IItemHandler> sources(Player player, Level level, BlockPos controller) {
        Map<IItemHandler, Boolean> found = new IdentityHashMap<>();
        found.put(new PlayerMainInvWrapper(player.getInventory()), true);
        for (BlockPos pos : BlockPos.betweenClosed(controller.offset(-CONTAINER_RADIUS, -CONTAINER_RADIUS, -CONTAINER_RADIUS),
                controller.offset(CONTAINER_RADIUS, CONTAINER_RADIUS, CONTAINER_RADIUS))) {
            if (level.getBlockEntity(pos) != null) {
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null) {
                    found.put(handler, true);
                }
            }
        }
        return new ArrayList<>(found.keySet());
    }

    private static int count(List<IItemHandler> handlers, Item item) {
        int total = 0;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static void take(List<IItemHandler> handlers, Item item, int amount) {
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots() && amount > 0; slot++) {
                if (handler.getStackInSlot(slot).is(item)) {
                    amount -= handler.extractItem(slot, amount, false).getCount();
                }
            }
        }
    }

    /** Ha algo a construir e o jogador (ou os baus proximos) tem todos os blocos necessarios? */
    public static boolean canBuild(Player player, Level level, BlockPos controller, Direction facing) {
        List<TurbineBlueprint.Part> parts = placeable(level, controller, facing);
        if (parts.isEmpty()) {
            return false;
        }
        if (player.getAbilities().instabuild) {
            return true;
        }
        List<IItemHandler> handlers = sources(player, level, controller);
        for (Map.Entry<TurbineBlueprint.Kind, Integer> need : countByKind(parts).entrySet()) {
            if (count(handlers, WindTurbineScan.blockFor(need.getKey()).asItem()) < need.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Constroi tudo (se {@link #canBuild}); devolve quantos blocos foram colocados. */
    public static int build(Player player, Level level, BlockPos controller, Direction facing) {
        if (!canBuild(player, level, controller, facing)) {
            return 0;
        }
        List<TurbineBlueprint.Part> parts = placeable(level, controller, facing);
        if (!player.getAbilities().instabuild) {
            List<IItemHandler> handlers = sources(player, level, controller);
            for (Map.Entry<TurbineBlueprint.Kind, Integer> need : countByKind(parts).entrySet()) {
                take(handlers, WindTurbineScan.blockFor(need.getKey()).asItem(), need.getValue());
            }
        }
        for (TurbineBlueprint.Part part : parts) {
            level.setBlock(part.pos(), WindTurbineScan.blockFor(part.kind()).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return parts.size();
    }
}
