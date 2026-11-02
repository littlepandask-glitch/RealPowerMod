package com.mmods.realpower.goggles;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.cable.CableBlock;
import com.mmods.realpower.cable.CableBlockEntity;
import com.mmods.realpower.cable.CableNetwork;
import com.mmods.realpower.cable.CableNetworks;
import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.generator.AbstractGeneratorBlockEntity;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlockEntity;
import com.mmods.realpower.registry.ModBlocks;
import com.mmods.realpower.registry.ModItems;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Oculos de engenheiro: a cada poucos ticks descreve o bloco observado e manda ao cliente. */
@EventBusSubscriber(modid = RealPower.MOD_ID)
public final class Goggles {
    private static final double RANGE = 8.0;
    private static final Map<UUID, List<Component>> LAST_SENT = new HashMap<>();

    private static boolean curiosChecked;
    private static Method curiosInventory;
    private static Method curiosIsEquipped;

    private Goggles() {
    }

    /** Equipado na cabeca ou no slot de cabeca do Curios (se presente). */
    public static boolean isWearing(Player player) {
        Item goggles = ModItems.ENGINEER_GOGGLES.get();
        return player.getItemBySlot(EquipmentSlot.HEAD).is(goggles) || curiosEquipped(player, goggles);
    }

    /** Compat com Curios por reflexao: sem dependencia de compilacao nem exigencia do mod. */
    private static boolean curiosEquipped(LivingEntity entity, Item item) {
        try {
            if (!curiosChecked) {
                curiosChecked = true;
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                curiosInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
                curiosIsEquipped = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                        .getMethod("isEquipped", Item.class);
            }
            if (curiosInventory == null) {
                return false;
            }
            Optional<?> handler = (Optional<?>) curiosInventory.invoke(null, entity);
            return handler.isPresent() && (boolean) curiosIsEquipped.invoke(handler.get(), item);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            curiosInventory = null;
            return false;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0) {
            return;
        }
        List<Component> lines = List.of();
        if (isWearing(player)) {
            HitResult hit = player.pick(RANGE, 0.0F, false);
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                lines = describe(player.level(), block.getBlockPos());
            }
        }
        List<Component> previous = LAST_SENT.getOrDefault(player.getUUID(), List.of());
        if (!lines.isEmpty() || !previous.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new GogglesPayload(lines));
            LAST_SENT.put(player.getUUID(), lines);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    /** Linhas do balao para o bloco em {@code pos}; vazio se nao ha o que mostrar. */
    public static List<Component> describe(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos origin = pos;
        if (state.getBlock() instanceof PowerStructureBlock) {
            BlockPos base = state.is(ModBlocks.TRANSFORMER_PORT.get()) ? CableBlock.baseOfPort(level, pos)
                    : PowerStructureBlock.findBase(level, pos);
            if (base == null) {
                return List.of();
            }
            origin = base;
            state = level.getBlockState(base);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(state.getBlock().getName());
        if (state.getBlock() instanceof CableBlock && level.getBlockEntity(origin) instanceof CableBlockEntity cable) {
            CableNetwork network = CableNetworks.networkAt(level, origin);
            if (network != null) {
                lines.add(Component.translatable("goggles.realpower.network", network.weakestTier().displayName(), network.cableCount()));
                lines.add(Component.translatable("goggles.realpower.rate", network.rate()));
                lines.add(Component.translatable("goggles.realpower.loss", String.format("%.1f", network.loss() * 100.0),
                        String.format("%.0f", network.effectiveLength())));
                lines.add(Component.translatable("goggles.realpower.buffer", network.storedEnergy(), network.capacity()));
            }
            if (cable.kind().overhead()) {
                lines.add(Component.translatable("goggles.realpower.links", cable.links().size()));
            }
            return lines;
        }
        if (level.getBlockEntity(origin) instanceof AbstractGeneratorBlockEntity generator) {
            lines.add(Component.translatable(generator.isFormed() ? "goggles.realpower.formed" : "goggles.realpower.unformed"));
            if (generator instanceof WindTurbineControllerBlockEntity turbine && generator.isFormed()) {
                lines.add(Component.translatable("goggles.realpower.wind", String.format("%.1f", turbine.windSpeed())));
            }
            lines.add(Component.translatable("goggles.realpower.power", String.format("%.1f", generator.powerWatts() / 1000.0)));
            lines.add(Component.translatable("goggles.realpower.buffer", generator.storedEnergy(), generator.bufferCapacity()));
            return lines;
        }
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, origin, null);
        if (energy != null && energy.getMaxEnergyStored() > 0) {
            lines.add(Component.translatable("goggles.realpower.buffer", energy.getEnergyStored(), energy.getMaxEnergyStored()));
            return lines;
        }
        return List.of();
    }
}
