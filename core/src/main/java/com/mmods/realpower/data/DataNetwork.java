package com.mmods.realpower.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Varredura da rede de dados a partir de um no (o computador): busca em largura pelos blocos {@link DataNode}.
 * Sem estado global: quem precisa (o computador) varre de tempos em tempos, com limite de tamanho.
 */
public final class DataNetwork {
    /** Maximo de nos visitados numa varredura. */
    public static final int MAX_NODES = 1024;

    private final Set<BlockPos> nodes;
    private final Map<BlockPos, DataDevice> devices;

    private DataNetwork(Set<BlockPos> nodes, Map<BlockPos, DataDevice> devices) {
        this.nodes = nodes;
        this.devices = devices;
    }

    /** Varre a rede que contem {@code start}; blocos em chunks descarregados encerram aquele ramo. */
    public static DataNetwork scan(Level level, BlockPos start) {
        return scan(level, List.of(start));
    }

    /** Varre a uniao das redes alcancadas a partir de varios pontos (ex.: as portas de um mesmo computador). */
    public static DataNetwork scan(Level level, Collection<BlockPos> starts) {
        Set<BlockPos> seen = new HashSet<>();
        Map<BlockPos, DataDevice> devices = new LinkedHashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : starts) {
            if (seen.add(start)) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty() && seen.size() <= MAX_NODES) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DataPortBlock) {
                DataDevice device = DataPortBlock.deviceOf(level, pos, state);
                if (device != null) {
                    devices.put(pos, device);
                }
            }
            if (!(state.getBlock() instanceof DataNode node)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (seen.contains(next) || !node.connects(state, side) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                if (nextState.getBlock() instanceof DataNode other && other.connects(nextState, side.getOpposite())) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return new DataNetwork(seen, devices);
    }

    public boolean contains(BlockPos pos) {
        return nodes.contains(pos);
    }

    public int size() {
        return nodes.size();
    }

    /** Equipamentos alcancaveis, pela posicao da porta de dados que os expoe. */
    public Map<BlockPos, DataDevice> devices() {
        return devices;
    }

    /** Posicoes de todos os nos de um tipo de bloco (ex.: telas). */
    public List<BlockPos> nodesOf(Level level, Class<?> blockType) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : nodes) {
            if (blockType.isInstance(level.getBlockState(pos).getBlock())) {
                found.add(pos);
            }
        }
        return found;
    }
}
