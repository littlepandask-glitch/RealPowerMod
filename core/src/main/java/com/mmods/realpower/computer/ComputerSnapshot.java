package com.mmods.realpower.computer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

/** Retrato do que o computador sabe num instante (equipamentos e historicos); e o que as telas recebem. */
public record ComputerSnapshot(int networkSize, int deviceLimit, List<Device> devices,
                               float[] generationFine, float[] generationCoarse,
                               float[] deliveredFine, float[] deliveredCoarse,
                               float[] storedFine, float[] storedCoarse) {
    public record Device(BlockPos port, Component name, boolean online, double powerWatts, long stored, long capacity,
                         boolean controllable, boolean enabled, int limit) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ComputerSnapshot> CODEC = StreamCodec.of(
            ComputerSnapshot::write, ComputerSnapshot::read);

    private static void write(RegistryFriendlyByteBuf buf, ComputerSnapshot s) {
        buf.writeVarInt(s.networkSize);
        buf.writeVarInt(s.deviceLimit);
        buf.writeVarInt(s.devices.size());
        for (Device d : s.devices) {
            buf.writeBlockPos(d.port);
            ComponentSerialization.STREAM_CODEC.encode(buf, d.name);
            buf.writeBoolean(d.online);
            buf.writeDouble(d.powerWatts);
            buf.writeVarLong(d.stored);
            buf.writeVarLong(d.capacity);
            buf.writeBoolean(d.controllable);
            buf.writeBoolean(d.enabled);
            buf.writeVarInt(d.limit);
        }
        for (float[] series : s.series()) {
            buf.writeVarInt(series.length);
            for (float value : series) {
                buf.writeFloat(value);
            }
        }
    }

    private static ComputerSnapshot read(RegistryFriendlyByteBuf buf) {
        int networkSize = buf.readVarInt();
        int deviceLimit = buf.readVarInt();
        int count = buf.readVarInt();
        List<Device> devices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            devices.add(new Device(buf.readBlockPos(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readBoolean(),
                    buf.readDouble(), buf.readVarLong(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
        }
        float[][] series = new float[6][];
        for (int i = 0; i < 6; i++) {
            series[i] = new float[buf.readVarInt()];
            for (int j = 0; j < series[i].length; j++) {
                series[i][j] = buf.readFloat();
            }
        }
        return new ComputerSnapshot(networkSize, deviceLimit, devices, series[0], series[1], series[2], series[3], series[4], series[5]);
    }

    private float[][] series() {
        return new float[][] {generationFine, generationCoarse, deliveredFine, deliveredCoarse, storedFine, storedCoarse};
    }
}
