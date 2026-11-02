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
import net.minecraft.world.phys.Vec3;
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

    /** Ponto de fixacao dos fios (topo da estrutura) de um poste/torre. */
    private static Vec3 top(CableBlockEntity cable) {
        BlockPos pos = cable.getBlockPos();
        return new Vec3(pos.getX() + 0.5, pos.getY() + cable.kind().anchorHeight(cable.tier()), pos.getZ() + 0.5);
    }

    /** Postes e torres so se ligam ao longo do eixo das travessas (ate 30 graus); de lado, nunca. */
    private static boolean alongAxis(CableBlockEntity node, Vec3 direction) {
        if (!node.kind().linksAlongAxisOnly()) {
            return true;
        }
        Direction facing = node.getBlockState().getValue(CableBlock.FACING);
        double length = Math.hypot(direction.x, direction.z);
        return length < 1e-6
                || Math.abs(direction.x * facing.getStepX() + direction.z * facing.getStepZ()) / length >= 0.866;
    }

    /**
     * Liga so vizinhos proximos: dois postes/torres se ligam quando estao no alcance e nenhum terceiro esta mais
     * perto dos dois que eles entre si (grafo de vizinhanca relativa); assim o fio nunca passa "por tras" de outro.
     */
    private static Map<BlockPos, List<Link>> computeLinks(ServerLevel level, LevelNetworks nets) {
        List<CableBlockEntity> nodes = new ArrayList<>();
        for (CableBlockEntity cable : nets.cables.values()) {
            if (cable.kind().overhead()) {
                nodes.add(cable);
            }
        }
        int n = nodes.size();
        double[][] distance = new double[n][n];
        boolean[][] reachable = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d = top(nodes.get(i)).distanceTo(top(nodes.get(j)));
                distance[i][j] = d;
                distance[j][i] = d;
                Vec3 dir = top(nodes.get(j)).subtract(top(nodes.get(i)));
                boolean ok = alongAxis(nodes.get(i), dir) && alongAxis(nodes.get(j), dir)
                        && d <= Math.min(nodes.get(i).kind().linkRange(), nodes.get(j).kind().linkRange())
                        && d >= Math.max(nodes.get(i).kind().minLink(), nodes.get(j).kind().minLink());
                reachable[i][j] = ok;
                reachable[j][i] = ok;
            }
        }
        Map<BlockPos, List<Link>> byNode = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!reachable[i][j]) {
                    continue;
                }
                boolean blocked = false;
                for (int k = 0; k < n && !blocked; k++) {
                    blocked = k != i && k != j && reachable[i][k] && reachable[k][j]
                            && distance[i][k] < distance[i][j] - 1e-6 && distance[k][j] < distance[i][j] - 1e-6;
                }
                if (blocked) {
                    continue;
                }
                CableBlockEntity a = nodes.get(i);
                CableBlockEntity b = nodes.get(j);
                boolean ordered = a.getBlockPos().asLong() < b.getBlockPos().asLong();
                Link link = new Link(ordered ? a.getBlockPos() : b.getBlockPos(), ordered ? b.getBlockPos() : a.getBlockPos(),
                        distance[i][j], Math.max(a.kind().overheadFactor(), b.kind().overheadFactor()));
                byNode.computeIfAbsent(link.a(), k -> new ArrayList<>()).add(link);
                byNode.computeIfAbsent(link.b(), k -> new ArrayList<>()).add(link);
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

        // celulas de porta de transformador -> bloco base (a rede trata a porta como o proprio transformador)
        Map<BlockPos, BlockPos> portOwner = new HashMap<>();
        for (CableBlockEntity cable : nets.cables.values()) {
            if (cable.kind() == CableKind.TRANSFORMER) {
                for (BlockPos port : ((CableBlock) cable.getBlockState().getBlock()).portCells(cable.getBlockPos(), cable.getBlockState())) {
                    portOwner.put(port, cable.getBlockPos());
                }
            }
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
                    if (portOwner.containsKey(neighbor)) {
                        neighbor = portOwner.get(neighbor);
                        if (visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    } else if (nets.cables.containsKey(neighbor)) {
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
                if (cable.kind() == CableKind.TRANSFORMER) {
                    // cabos encostados nas portas do transformador entram na mesma rede
                    for (BlockPos port : ((CableBlock) state.getBlock()).portCells(pos, state)) {
                        for (Direction direction : Direction.values()) {
                            BlockPos outside = port.relative(direction);
                            if (nets.cables.containsKey(outside) && visited.add(outside)) {
                                queue.add(outside);
                            }
                        }
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
