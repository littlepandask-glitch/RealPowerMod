package com.mmods.realpower.computer;

import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.data.DataDevice;
import com.mmods.realpower.data.DataNetwork;
import com.mmods.realpower.energy.PowerScale;
import com.mmods.realpower.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Computador central: a cada segundo varre a rede de dados, le a telemetria dos equipamentos ligados e alimenta os
 * historicos (geracao, consumo, energia armazenada) que as telas exibem. Tambem repassa comandos aos equipamentos.
 */
public class ComputerBlockEntity extends BlockEntity {
    public static final int SAMPLE_INTERVAL = 20;

    /** Retrato de um equipamento na ultima varredura; {@code port} identifica-o (posicao da porta de dados). */
    public record DeviceView(BlockPos port, Component name, DataDevice.Telemetry telemetry, boolean controllable,
                             boolean enabled, int limit) {
    }

    private final Metric generation = new Metric();
    private final Metric delivered = new Metric();
    private final Metric stored = new Metric();

    private List<DeviceView> devices = List.of();
    private Map<BlockPos, DataDevice> byPort = Map.of();
    private int networkSize;
    private long age;
    private long previousStored = -1;

    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPUTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity computer) {
        computer.age++;
        if (computer.age % SAMPLE_INTERVAL == 0 && state.getBlock() instanceof ComputerBlock block) {
            block.ensureStructure(level, pos, state);
            computer.sample(level, pos, state, block);
        }
    }

    private void sample(Level level, BlockPos pos, BlockState state, ComputerBlock block) {
        DataNetwork network = DataNetwork.scan(level, block.portCells(pos, state));
        networkSize = network.size();
        List<DeviceView> views = new ArrayList<>();
        Map<BlockPos, DataDevice> ports = new LinkedHashMap<>();
        double watts = 0.0;
        long storedNow = 0;
        Set<Object> seen = new HashSet<>();
        for (Map.Entry<BlockPos, DataDevice> entry : network.devices().entrySet()) {
            if (views.size() >= block.maxDevices()) {
                break;
            }
            DataDevice device = entry.getValue();
            if (!seen.add(device.identity())) {
                continue; // varias portas de dados do mesmo equipamento
            }
            DataDevice.Telemetry telemetry = device.telemetry();
            views.add(new DeviceView(entry.getKey(), device.deviceLabel(), telemetry, device.controllable(),
                    device.enabled(), device.limit()));
            ports.put(entry.getKey(), device);
            watts += telemetry.powerWatts();
            storedNow += telemetry.stored();
        }
        devices = List.copyOf(views);
        byPort = ports;

        double generated = PowerScale.toFePerTick(watts);
        // o que saiu dos geradores = o produzido menos o que ficou retido nos buffers neste intervalo
        double grown = previousStored < 0 ? 0.0 : (storedNow - previousStored) / (double) SAMPLE_INTERVAL;
        previousStored = storedNow;
        generation.add((float) generated);
        delivered.add((float) Math.max(0.0, generated - grown));
        stored.add((float) storedNow);
        setChanged();
    }

    // --- leitura (telas, goggles) ---

    public List<DeviceView> devices() {
        return devices;
    }

    public int networkSize() {
        return networkSize;
    }

    public Metric generation() {
        return generation;
    }

    public Metric delivered() {
        return delivered;
    }

    public Metric stored() {
        return stored;
    }

    public Tier tier() {
        return getBlockState().getBlock() instanceof ComputerBlock block ? block.tier() : Tier.BASIC;
    }

    // --- comandos ---

    /** Liga ou desliga o equipamento da porta {@code port}; falso se ele nao e (mais) controlavel. */
    public boolean commandEnabled(BlockPos port, boolean enabled) {
        DataDevice device = byPort.get(port);
        if (device == null || !device.controllable()) {
            return false;
        }
        device.setEnabled(enabled);
        return true;
    }

    public boolean commandLimit(BlockPos port, int percent) {
        DataDevice device = byPort.get(port);
        if (device == null || !device.controllable()) {
            return false;
        }
        device.setLimit(percent);
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        generation.save(tag, "Generation");
        delivered.save(tag, "Delivered");
        stored.save(tag, "Stored");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        generation.load(tag, "Generation");
        delivered.load(tag, "Delivered");
        stored.load(tag, "Stored");
    }
}
