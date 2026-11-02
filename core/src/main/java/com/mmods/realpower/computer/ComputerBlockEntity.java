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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Computador central: a cada segundo varre a rede de dados, le a telemetria dos equipamentos ligados e alimenta os
 * historicos (geracao, consumo, energia armazenada) que as telas exibem. Tambem repassa comandos aos equipamentos.
 */
public class ComputerBlockEntity extends BlockEntity implements MenuProvider {
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

    private final List<ControlRule> rules = new ArrayList<>();
    private boolean redstone;
    private int storedPercent;
    private double generationKw;
    private double deliveredKw;

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

        long capacityNow = 0;
        for (DeviceView view : devices) {
            capacityNow += view.telemetry().capacity();
        }
        storedPercent = capacityNow == 0 ? 0 : (int) (storedNow * 100L / capacityNow);
        generationKw = generated;
        deliveredKw = Math.max(0.0, generated - grown);
        applyRules(level, pos);
        setChanged();
    }

    // --- regras automaticas ---

    private void applyRules(Level level, BlockPos pos) {
        boolean wantRedstone = false;
        for (ControlRule rule : rules) {
            if (!rule.holds(generationKw, deliveredKw, storedPercent)) {
                continue;
            }
            switch (rule.action()) {
                case OFF -> byPort.values().forEach(d -> {
                    if (d.controllable()) {
                        d.setEnabled(false);
                    }
                });
                case ON -> byPort.values().forEach(d -> {
                    if (d.controllable()) {
                        d.setEnabled(true);
                    }
                });
                case LIMIT -> byPort.values().forEach(d -> {
                    if (d.controllable()) {
                        d.setLimit(rule.arg());
                    }
                });
                case REDSTONE -> wantRedstone = true;
            }
        }
        if (wantRedstone != redstone) {
            redstone = wantRedstone;
            level.updateNeighborsAt(pos, getBlockState().getBlock());
        }
    }

    public boolean redstone() {
        return redstone;
    }

    public List<ControlRule> rules() {
        return rules;
    }

    /** Edicao de regras pelo menu: {@code field} 0 grandeza, 1 comparador, 2/3 limiar +/-, 4 acao, 5/6 parametro +/-. */
    public void editRule(int index, int field) {
        if (index < 0 || index >= rules.size()) {
            return;
        }
        ControlRule rule = rules.get(index);
        ControlRule.Quantity[] quantities = ControlRule.Quantity.values();
        ControlRule.Action[] actions = ControlRule.Action.values();
        rules.set(index, switch (field) {
            case 0 -> rule.withQuantity(quantities[(rule.quantity().ordinal() + 1) % quantities.length]);
            case 1 -> rule.withAbove(!rule.above());
            case 2 -> rule.withThreshold(rule.threshold() + rule.quantity().step);
            case 3 -> rule.withThreshold(rule.threshold() - rule.quantity().step);
            case 4 -> rule.withAction(actions[(rule.action().ordinal() + 1) % actions.length]);
            case 5 -> rule.withArg(rule.arg() + 10);
            case 6 -> rule.withArg(rule.arg() - 10);
            default -> rule;
        });
        setChanged();
    }

    public void addRule() {
        if (rules.size() < ControlRule.MAX_RULES) {
            rules.add(ControlRule.initial());
            setChanged();
        }
    }

    public void removeRule(int index) {
        if (index >= 0 && index < rules.size()) {
            rules.remove(index);
            setChanged();
        }
    }

    // --- menu ---

    public static final int HEADER_SLOTS = 7;
    public static final int RULE_SLOTS = 5;
    public static final int DATA_COUNT = HEADER_SLOTS + ControlRule.MAX_RULES * RULE_SLOTS;

    private static int clampShort(double value) {
        return (int) Math.max(0, Math.min(Short.MAX_VALUE, Math.round(value)));
    }

    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            if (index < HEADER_SLOTS) {
                return switch (index) {
                    case 0 -> rules.size();
                    case 1 -> redstone ? 1 : 0;
                    case 2 -> devices.size();
                    case 3 -> storedPercent;
                    case 4 -> clampShort(generationKw);
                    case 5 -> clampShort(deliveredKw);
                    default -> getBlockState().getBlock() instanceof ComputerBlock block ? block.maxDevices() : 0;
                };
            }
            int rule = (index - HEADER_SLOTS) / RULE_SLOTS;
            if (rule >= rules.size()) {
                return 0;
            }
            ControlRule r = rules.get(rule);
            return switch ((index - HEADER_SLOTS) % RULE_SLOTS) {
                case 0 -> r.quantity().ordinal();
                case 1 -> r.above() ? 1 : 0;
                case 2 -> r.threshold();
                case 3 -> r.action().ordinal();
                default -> r.arg();
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.realpower.computer", tier().displayName());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ComputerMenu(containerId, ContainerLevelAccess.create(level, worldPosition), menuData);
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

    /** Retrato atual para enviar as telas. */
    public ComputerSnapshot snapshot() {
        List<ComputerSnapshot.Device> list = new ArrayList<>();
        for (DeviceView view : devices) {
            DataDevice.Telemetry t = view.telemetry();
            list.add(new ComputerSnapshot.Device(view.port(), view.name(), t.online(), t.powerWatts(), t.stored(), t.capacity(),
                    view.controllable(), view.enabled(), view.limit()));
        }
        int limit = getBlockState().getBlock() instanceof ComputerBlock block ? block.maxDevices() : 0;
        return new ComputerSnapshot(networkSize, limit, list, generation.fine(), generation.coarse(),
                delivered.fine(), delivered.coarse(), stored.fine(), stored.coarse());
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
        ListTag list = new ListTag();
        rules.forEach(rule -> list.add(rule.save()));
        tag.put("Rules", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        generation.load(tag, "Generation");
        delivered.load(tag, "Delivered");
        stored.load(tag, "Stored");
        rules.clear();
        for (Tag entry : tag.getList("Rules", Tag.TAG_COMPOUND)) {
            if (rules.size() < ControlRule.MAX_RULES) {
                rules.add(ControlRule.load((CompoundTag) entry));
            }
        }
    }
}
