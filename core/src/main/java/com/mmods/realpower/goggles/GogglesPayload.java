package com.mmods.realpower.goggles;

import com.mmods.realpower.RealPower;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: linhas do balao dos oculos para o bloco observado (lista vazia = nada). */
public record GogglesPayload(List<Component> lines) implements CustomPacketPayload {
    public static final Type<GogglesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RealPower.MOD_ID, "goggles"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GogglesPayload> CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()).map(GogglesPayload::new, GogglesPayload::lines);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
