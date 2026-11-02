package com.mmods.realpower.screen;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.computer.ComputerSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: dados exibidos por uma tela (mestre do painel); {@code snapshot} nulo = sem computador ligado. */
public record ScreenPayload(BlockPos screen, ComputerSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<ScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealPower.MOD_ID, "screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.screen);
                buf.writeBoolean(payload.snapshot != null);
                if (payload.snapshot != null) {
                    ComputerSnapshot.CODEC.encode(buf, payload.snapshot);
                }
            },
            buf -> new ScreenPayload(buf.readBlockPos(), buf.readBoolean() ? ComputerSnapshot.CODEC.decode(buf) : null));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
