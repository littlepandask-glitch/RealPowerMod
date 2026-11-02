package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.render.Mesh;
import com.mmods.realpower.render.PowerStructureModel;
import com.mmods.realpower.storage.CapacitorBankBlock;
import com.mmods.realpower.storage.CapacitorBankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Desenha o galpao do banco de capacitores; as barras de LED da frente acompanham a carga (passos de 5%). */
public class CapacitorBankRenderer implements BlockEntityRenderer<CapacitorBankBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealPower.MOD_ID, "textures/entity/wind_turbine.png");
    private static final int STEPS = 20;
    private static final Map<Tier, float[]> ACCENT = Map.of(
            Tier.BASIC, new float[]{0.27f, 0.78f, 1.0f}, Tier.INDUSTRIAL, new float[]{0.35f, 0.92f, 0.47f},
            Tier.HIGH_VOLTAGE, new float[]{1.0f, 0.84f, 0.24f}, Tier.ADVANCED, new float[]{1.0f, 0.55f, 0.16f},
            Tier.FUTURE, new float[]{0.92f, 0.35f, 1.0f});

    private static final Map<Long, Mesh> MESHES = new HashMap<>();

    @Override
    public void render(CapacitorBankBlockEntity bank, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (bank.getLevel() == null || !(bank.getBlockState().getBlock() instanceof CapacitorBankBlock block)) {
            return;
        }
        int step = Math.min(STEPS, bank.displayCharge() * STEPS / 100);
        Tier tier = block.tier();
        Mesh mesh = MESHES.computeIfAbsent((long) tier.ordinal() * (STEPS + 1) + step, key -> {
            int[] size = CapacitorBankBlock.size(tier);
            return PowerStructureModel.capacitorBank(size[0], size[1], size[2], step, STEPS, ACCENT.get(tier));
        });
        int lit = LevelRenderer.getLightColor(bank.getLevel(), bank.getBlockPos().above());
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bank.getBlockState().getValue(CapacitorBankBlock.FACING).toYRot()));
        PowerLineRenderer.draw(mesh, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), lit);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(CapacitorBankBlockEntity bank) {
        int[] size = bank.getBlockState().getBlock() instanceof CapacitorBankBlock block ? CapacitorBankBlock.size(block.tier()) : new int[]{3, 3, 2};
        return new AABB(bank.getBlockPos()).inflate(size[0] / 2.0 + 0.5, 0.0, size[1] / 2.0 + 0.5).expandTowards(0, size[2] + 0.5, 0);
    }
}
