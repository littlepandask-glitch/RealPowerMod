package com.mmods.realpower.cable;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.storage.BufferStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Um conjunto de cabos conectados. A rede e um pool: fontes (geradores, bancos de capacitores)
 * empurram energia para dentro pelos cabos e a rede a entrega aos consumidores encostados nela.
 *
 * <p>Limites reais simplificados: a vazao por tick e a do cabo mais fraco (o condutor mais fino
 * limita a corrente) e a perda cresce com o comprimento equivalente da linha (ver {@link CableNetworks}).
 */
public final class CableNetwork {
    /** Perda maxima por transmissao. */
    private static final double MAX_LOSS = 0.5;
    /** O buffer da rede cobre alguns ticks de vazao, o bastante para absorver a ordem dos ticks. */
    private static final long BUFFER_TICKS = 2L;

    private final Set<BlockPos> cables;
    private final Tier weakestTier;
    private final long rate;
    private final long capacity;
    private final double loss;
    private final double effectiveLength;
    private final List<BlockCapabilityCache<IEnergyStorage, Direction>> endpoints = new ArrayList<>();

    private long stored;
    private long acceptedThisTick;
    private int rotation;
    private boolean discarded;

    CableNetwork(ServerLevel level, Set<BlockPos> cables, Tier weakestTier, List<BlockPos> endpointPositions,
                 List<Direction> endpointFaces, long inheritedEnergy, double effectiveLength) {
        this.cables = cables;
        this.weakestTier = weakestTier;
        this.rate = weakestTier.maxTransferPerTick();
        this.capacity = rate * BUFFER_TICKS;
        this.effectiveLength = effectiveLength;
        this.loss = Math.min(MAX_LOSS, weakestTier.lineLossPerBlock() * effectiveLength);
        this.stored = Math.min(inheritedEnergy, capacity);
        for (int i = 0; i < endpointPositions.size(); i++) {
            endpoints.add(BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level,
                    endpointPositions.get(i), endpointFaces.get(i), () -> !discarded, () -> { }));
        }
    }

    void discard() {
        discarded = true;
    }

    long stored() {
        return stored;
    }

    public int cableCount() {
        return cables.size();
    }

    public Tier weakestTier() {
        return weakestTier;
    }

    /** Vazao maxima da rede em FE/tick. */
    public long rate() {
        return rate;
    }

    /** Comprimento equivalente da linha (m): corridas de cabo enterrado, divididas pelos reles, mais as linhas aereas. */
    public double effectiveLength() {
        return effectiveLength;
    }

    public long capacity() {
        return capacity;
    }

    public long storedEnergy() {
        return stored;
    }

    /** Fracao da energia perdida na transmissao (0 a 0,5). */
    public double loss() {
        return loss;
    }

    /** Recebe energia de uma fonte, respeitando a vazao do tick e o espaco no buffer. */
    int accept(int amount, boolean simulate) {
        long room = Math.min(rate - acceptedThisTick, capacity - stored);
        int accepted = (int) Math.max(0L, Math.min(amount, room));
        if (!simulate) {
            stored += accepted;
            acceptedThisTick += accepted;
        }
        return accepted;
    }

    /**
     * Uma passada por tick, depois de todos os geradores: reservas (bancos de capacitores) cobrem a demanda que a energia
     * da rede nao atende, os consumidores recebem e o que sobrar carrega as reservas.
     */
    void tick() {
        acceptedThisTick = 0;
        List<IEnergyStorage> sinks = new ArrayList<>();
        List<IEnergyStorage> reserves = new ArrayList<>();
        for (BlockCapabilityCache<IEnergyStorage, Direction> endpoint : endpoints) {
            IEnergyStorage storage = endpoint.getCapability();
            if (storage == null) {
                continue;
            }
            if (storage instanceof BufferStorage) {
                reserves.add(storage);
            } else if (storage.canReceive()) {
                sinks.add(storage);
            }
        }
        double keep = 1.0 - loss;
        if (!sinks.isEmpty() && !reserves.isEmpty()) {
            drawFromReserves(sinks, reserves, keep);
        }
        deliver(sinks, keep);
        if (!reserves.isEmpty()) {
            for (IEnergyStorage reserve : reserves) {
                if (stored <= 0L) {
                    break;
                }
                stored -= reserve.receiveEnergy((int) Math.min(Integer.MAX_VALUE, stored), false);
            }
        }
    }

    /** Puxa das reservas o que falta para os consumidores aceitarem tudo o que a vazao permite. */
    private void drawFromReserves(List<IEnergyStorage> sinks, List<IEnergyStorage> reserves, double keep) {
        long demand = 0L;
        for (IEnergyStorage sink : sinks) {
            demand += (long) Math.ceil(sink.receiveEnergy((int) Math.min(Integer.MAX_VALUE, rate), true) / keep);
        }
        long missing = Math.min(Math.min(demand, rate) - stored, capacity - stored);
        for (IEnergyStorage reserve : reserves) {
            if (missing <= 0L) {
                break;
            }
            long drawn = reserve.extractEnergy((int) Math.min(Integer.MAX_VALUE, missing), false);
            stored += drawn;
            missing -= drawn;
        }
    }

    private void deliver(List<IEnergyStorage> sinks, double keep) {
        long budget = Math.min(rate, stored);
        int count = sinks.size();
        if (budget <= 0L || count == 0) {
            return;
        }
        for (int i = 0; i < count && budget > 0L; i++) {
            // o ponto de partida gira a cada tick para repartir por igual quando a energia e escassa
            IEnergyStorage sink = sinks.get((i + rotation) % count);
            long share = budget / (count - i);
            if (share <= 0L) {
                share = budget;
            }
            int offered = (int) Math.min(Integer.MAX_VALUE, Math.floor(share * keep));
            int accepted = sink.receiveEnergy(offered, false);
            long used = Math.min(budget, (long) Math.ceil(accepted / keep));
            stored -= used;
            budget -= used;
        }
        rotation++;
    }
}
