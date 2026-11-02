package com.mmods.realpower.cable;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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

    /** Linha aerea entre dois nos de topo de poste/torre. */
    private record Link(BlockPos a, BlockPos b, double length, double factor) {
    }

    private static final int MAX_LINKS_PER_NODE = 3;

    /** Liga cada poste/torre de topo aos mais proximos que estejam no alcance de ambos. */
    private static Map<BlockPos, List<Link>> computeLinks(ServerLevel level, LevelNetworks nets) {
        List<CableBlockEntity> tops = new ArrayList<>();
        for (CableBlockEntity cable : nets.cables.values()) {
            if (cable.kind().overhead() && !level.getBlockState(cable.getBlockPos().above()).is(cable.getBlockState().getBlock())) {
                tops.add(cable);
            }
        }
        Map<BlockPos, List<Link>> byNode = new HashMap<>();
        Set<Link> seen = new HashSet<>();
        for (CableBlockEntity a : tops) {
            List<Link> candidates = new ArrayList<>();
            for (CableBlockEntity b : tops) {
                if (a == b) {
                    continue;
                }
                double distance = Math.sqrt(a.getBlockPos().distSqr(b.getBlockPos()));
                if (distance <= Math.min(a.kind().linkRange(), b.kind().linkRange())) {
                    candidates.add(new Link(a.getBlockPos(), b.getBlockPos(), distance,
                            Math.max(a.kind().overheadFactor(), b.kind().overheadFactor())));
                }
            }
            candidates.sort(Comparator.comparingDouble(Link::length));
            for (Link link : candidates.subList(0, Math.min(MAX_LINKS_PER_NODE, candidates.size()))) {
                boolean ordered = link.a().asLong() < link.b().asLong();
                Link canonical = ordered ? link : new Link(link.b(), link.a(), link.length(), link.factor());
                if (seen.add(canonical)) {
                    byNode.computeIfAbsent(canonical.a(), k -> new ArrayList<>()).add(canonical);
                    byNode.computeIfAbsent(canonical.b(), k -> new ArrayList<>()).add(canonical);
                }
            }
        }
        return byNode;
    }

    /**
     * Comprimento equivalente: cada corrida de cabo/rele enterrado conta o seu vao (soma das tres dimensoes da
     * caixa que a contem) dividido por (1 + reles); cada linha aerea conta seu comprimento vezes o fator aereo.
     */
    private static double effectiveLength(Set<BlockPos> members, Map<BlockPos, CableBlockEntity> cables,
                                          Map<BlockPos, List<Link>> links) {
        double total = 0.0;
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos start : members) {
            CableBlockEntity first = cables.get(start);
            if (first.kind().overhead() || visited.contains(start)) {
                continue;
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            int relays = 0;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
                if (cables.get(pos).kind() == CableKind.RELAY) {
                    relays++;
                }
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    CableBlockEntity other = cables.get(next);
                    if (other != null && !other.kind().overhead() && members.contains(next) && visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
            total += ((maxX - minX) + (maxY - minY) + (maxZ - minZ) + 1) / (1.0 + relays);
        }
        Set<Link> counted = new HashSet<>();
        for (BlockPos pos : members) {
            for (Link link : links.getOrDefault(pos, List.of())) {
                if (counted.add(link)) {
                    total += link.length() * link.factor();
                }
            }
        }
        return total;
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

        Map<BlockPos, List<Link>> links = computeLinks(level, nets);
        for (CableBlockEntity cable : nets.cables.values()) {
            List<BlockPos> targets = new ArrayList<>();
            for (Link link : links.getOrDefault(cable.getBlockPos(), List.of())) {
                targets.add(link.a().equals(cable.getBlockPos()) ? link.b() : link.a());
            }
            cable.setLinks(targets);
        }

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
                for (Link link : links.getOrDefault(pos, List.of())) {
                    BlockPos other = link.a().equals(pos) ? link.b() : link.a();
                    if (visited.add(other)) {
                        queue.add(other);
                    }
                }
            }
            CableNetwork network = new CableNetwork(level, members, weakest, endpointPositions, endpointFaces, energy,
                    effectiveLength(members, nets.cables, links));
            nets.networks.add(network);
            for (BlockPos pos : members) {
                nets.byPos.put(pos, network);
            }
        }
    }
}
