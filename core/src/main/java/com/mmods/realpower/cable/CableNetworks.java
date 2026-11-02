package com.mmods.realpower.cable;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Gerenciador das redes de cabos de cada mundo do servidor. Os cabos carregados se registram aqui;
 * quando algum entra, sai ou muda de conexao, as redes sao refeitas (busca em largura) no proximo tick.
 */
@EventBusSubscriber(modid = RealPower.MOD_ID)
public final class CableNetworks {
    private static final Map<Level, LevelNetworks> LEVELS = new WeakHashMap<>();

    private CableNetworks() {
    }

    private static final class LevelNetworks {
        final Map<BlockPos, CableBlockEntity> cables = new HashMap<>();
        final Map<BlockPos, CableNetwork> byPos = new HashMap<>();
        final List<CableNetwork> networks = new ArrayList<>();
        boolean dirty;
    }

    static void register(CableBlockEntity cable) {
        if (cable.getLevel() instanceof ServerLevel level) {
            LevelNetworks nets = LEVELS.computeIfAbsent(level, l -> new LevelNetworks());
            nets.cables.put(cable.getBlockPos(), cable);
            nets.dirty = true;
        }
    }

    static void unregister(CableBlockEntity cable) {
        LevelNetworks nets = cable.getLevel() == null ? null : LEVELS.get(cable.getLevel());
        if (nets != null && nets.cables.remove(cable.getBlockPos(), cable)) {
            nets.dirty = true;
        }
    }

    /** Uma conexao de cabo mudou: refazer as redes. */
    public static void markDirty(Level level) {
        LevelNetworks nets = LEVELS.get(level);
        if (nets != null) {
            nets.dirty = true;
        }
    }

    /** Rede a que o cabo em {@code pos} pertence (atualizada se preciso), ou {@code null}. */
    public static CableNetwork networkAt(Level level, BlockPos pos) {
        LevelNetworks nets = LEVELS.get(level);
        if (nets == null || !(level instanceof ServerLevel server)) {
            return null;
        }
        if (nets.dirty) {
            rebuild(server, nets);
        }
        return nets.byPos.get(pos);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelNetworks nets = LEVELS.get(level);
        if (nets == null) {
            return;
        }
        if (nets.dirty) {
            rebuild(level, nets);
        }
        for (CableNetwork network : nets.networks) {
            network.tick();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            LevelNetworks nets = LEVELS.remove(level);
            if (nets != null) {
                nets.networks.forEach(CableNetwork::discard);
            }
        }
    }

    private static void rebuild(ServerLevel level, LevelNetworks nets) {
        nets.dirty = false;
        // a energia guardada passa para a rede nova que contem o mesmo cabo
        Map<CableNetwork, Boolean> oldNetworks = new IdentityHashMap<>();
        Map<BlockPos, Long> energyByCable = new HashMap<>();
        for (Map.Entry<BlockPos, CableNetwork> entry : nets.byPos.entrySet()) {
            if (oldNetworks.put(entry.getValue(), true) == null) {
                energyByCable.put(entry.getKey(), entry.getValue().stored());
            }
        }
        nets.networks.forEach(CableNetwork::discard);
        nets.networks.clear();
        nets.byPos.clear();

        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos start : nets.cables.keySet()) {
            if (visited.contains(start)) {
                continue;
            }
            Set<BlockPos> members = new HashSet<>();
            List<BlockPos> endpointPositions = new ArrayList<>();
            List<Direction> endpointFaces = new ArrayList<>();
            Tier weakest = Tier.FUTURE;
            long energy = 0L;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                members.add(pos);
                CableBlockEntity cable = nets.cables.get(pos);
                energy += energyByCable.getOrDefault(pos, 0L);
                if (cable.tier().level() < weakest.level()) {
                    weakest = cable.tier();
                }
                BlockState state = level.getBlockState(pos);
                for (Direction direction : Direction.values()) {
                    if (!state.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction))) {
                        continue;
                    }
                    BlockPos neighbor = pos.relative(direction);
                    if (nets.cables.containsKey(neighbor)) {
                        if (visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    } else {
                        endpointPositions.add(neighbor);
                        endpointFaces.add(direction.getOpposite());
                    }
                }
            }
            CableNetwork network = new CableNetwork(level, members, weakest, endpointPositions, endpointFaces, energy);
            nets.networks.add(network);
            for (BlockPos pos : members) {
                nets.byPos.put(pos, network);
            }
        }
    }
}
