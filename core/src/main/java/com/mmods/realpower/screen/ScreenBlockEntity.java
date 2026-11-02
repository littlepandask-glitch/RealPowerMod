package com.mmods.realpower.screen;

import com.mmods.realpower.cable.PowerStructureBlock;
import com.mmods.realpower.computer.ComputerBlockEntity;
import com.mmods.realpower.computer.ComputerPortBlock;
import com.mmods.realpower.computer.ComputerSnapshot;
import com.mmods.realpower.data.DataNetwork;
import com.mmods.realpower.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estado de uma tela. Quem comanda e o bloco mestre do painel: acha o computador pela rede de dados uma vez por
 * segundo, manda o retrato aos jogadores proximos e trata cliques. Pagina, janela do grafico e rolagem ficam salvas.
 */
public class ScreenBlockEntity extends BlockEntity {
    public static final int UPDATE_INTERVAL = 20;
    public static final double VIEW_RANGE = 48.0;
    private static final int LIMIT_STEP = 10;

    private int page;
    private int window;
    private int scroll;

    private ScreenGroup group;
    private long groupTime = Long.MIN_VALUE;
    private ComputerSnapshot lastSnapshot;
    private BlockPos computerPos;
    private long age;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCREEN.get(), pos, state);
    }

    /** Painel a que este bloco pertence (recalculado a cada segundo, no servidor e no cliente). */
    public ScreenGroup group() {
        long now = level == null ? 0 : level.getGameTime();
        if (group == null || now - groupTime >= UPDATE_INTERVAL) {
            group = ScreenGroup.of(level, worldPosition);
            groupTime = now;
        }
        return group;
    }

    public int page() {
        return page;
    }

    public int window() {
        return window;
    }

    public int scroll() {
        return scroll;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ScreenBlockEntity screen) {
        if (++screen.age % UPDATE_INTERVAL != 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        ScreenGroup group = screen.group();
        if (!group.master().equals(pos)) {
            return;
        }
        screen.refresh(server, group);
        PacketDistributor.sendToPlayersNear(server, null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                VIEW_RANGE, new ScreenPayload(pos, screen.lastSnapshot));
    }

    /** Acha o computador ligado ao painel pela rede de dados e guarda o retrato atual. */
    private void refresh(ServerLevel level, ScreenGroup group) {
        computerPos = null;
        lastSnapshot = null;
        DataNetwork network = DataNetwork.scan(level, group.positions());
        for (BlockPos port : network.nodesOf(level, ComputerPortBlock.class)) {
            BlockPos base = PowerStructureBlock.findBase(level, port);
            if (base != null && level.getBlockEntity(base) instanceof ComputerBlockEntity computer) {
                computerPos = base;
                lastSnapshot = computer.snapshot();
                return;
            }
        }
    }

    private ComputerBlockEntity computer() {
        return level != null && computerPos != null && level.getBlockEntity(computerPos) instanceof ComputerBlockEntity c ? c : null;
    }

    /** Clique em ({@code px}, {@code py}) pixels do painel, contados do canto superior esquerdo. */
    public void click(ScreenGroup group, double px, double py) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (lastSnapshot == null) {
            refresh(server, group);
        }
        for (ScreenLayout.Button button : ScreenLayout.buttons(page, window, scroll,
                ScreenLayout.pixelWidth(group), ScreenLayout.pixelHeight(group), lastSnapshot)) {
            if (button.contains(px, py)) {
                apply(group, button);
                break;
            }
        }
    }

    private void apply(ScreenGroup group, ScreenLayout.Button button) {
        switch (button.kind()) {
            case TAB -> {
                page = button.arg();
                scroll = 0;
            }
            case WINDOW -> window = 1 - window;
            case SCROLL -> {
                int rows = ScreenLayout.visibleRows(ScreenLayout.pixelHeight(group));
                int devices = lastSnapshot == null ? 0 : lastSnapshot.devices().size();
                scroll = Math.max(0, Math.min(Math.max(0, devices - rows), scroll + button.arg()));
            }
            case TOGGLE, LIMIT_DOWN, LIMIT_UP -> {
                ComputerBlockEntity computer = computer();
                ComputerSnapshot.Device device = lastSnapshot == null || button.arg() >= lastSnapshot.devices().size()
                        ? null : lastSnapshot.devices().get(button.arg());
                if (computer == null || device == null || !device.port().equals(button.port())) {
                    return;
                }
                switch (button.kind()) {
                    case TOGGLE -> computer.commandEnabled(device.port(), !device.enabled());
                    case LIMIT_DOWN -> computer.commandLimit(device.port(), device.limit() - LIMIT_STEP);
                    default -> computer.commandLimit(device.port(), device.limit() + LIMIT_STEP);
                }
            }
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        // o proximo retrato ja reflete o comando
        if (level instanceof ServerLevel server) {
            refresh(server, group);
            PacketDistributor.sendToPlayersNear(server, null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, VIEW_RANGE, new ScreenPayload(worldPosition, lastSnapshot));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Page", page);
        tag.putInt("Window", window);
        tag.putInt("Scroll", scroll);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        page = tag.getInt("Page");
        window = tag.getInt("Window");
        scroll = tag.getInt("Scroll");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
