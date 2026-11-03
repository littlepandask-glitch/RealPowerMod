package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.multiblock.hydro.HydroUnitBlock;
import com.mmods.realpower.multiblock.hydro.HydroUnitBlockEntity;
import com.mmods.realpower.render.HydroModel;
import com.mmods.realpower.render.IndustrialStyle;
import com.mmods.realpower.render.Mesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Desenha a casa de forca (concreto, tomada d'agua, salao) e o rotor do gerador, que gira conforme a carga. */
public class HydroUnitRenderer implements BlockEntityRenderer<HydroUnitBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealPower.MOD_ID, "textures/entity/wind_turbine.png");
    /** Graus por tick do rotor a plena carga (cerca de 1 volta por segundo). */
    private static final double FULL_SPEED = 18.0;
    private static final Map<Integer, Mesh> BUILDINGS = new HashMap<>();
    private static final Map<Integer, Mesh> ROTORS = new HashMap<>();
    /** Por unidade: {angulo, ultimo instante}. */
    private static final Map<HydroUnitBlockEntity, double[]> SPIN = new WeakHashMap<>();

    @Override
    public void render(HydroUnitBlockEntity unit, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (unit.getLevel() == null || !(unit.getBlockState().getBlock() instanceof HydroUnitBlock block)) {
            return;
        }
        int[] size = HydroUnitBlock.size(block.tier());
        int turbineY = HydroUnitBlock.turbineHeight(block.tier());
        int key = block.tier().ordinal();
        Mesh building = BUILDINGS.computeIfAbsent(key, k -> HydroModel.powerhouse(size[0], size[1], size[2], turbineY,
                IndustrialStyle.palette(block.tier().ordinal())));
        Mesh rotor = ROTORS.computeIfAbsent(key, k -> HydroModel.rotor(size[0], size[1], size[2], turbineY));

        double now = unit.getLevel().getGameTime() + partialTick;
        double[] spin = SPIN.computeIfAbsent(unit, u -> new double[]{0.0, now});
        spin[0] = (spin[0] + Math.max(0.0, now - spin[1]) * FULL_SPEED * unit.runFraction()) % 360.0;
        spin[1] = now;

        int lit = LevelRenderer.getLightColor(unit.getLevel(), unit.getBlockPos().above(size[1]));
        var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - unit.getBlockState().getValue(HydroUnitBlock.FACING).toYRot()));
        PowerLineRenderer.draw(building, pose, consumer, lit);
        pose.translate(0.0, 0.0, HydroModel.axisZ(size[2]));
        pose.mulPose(Axis.YP.rotationDegrees((float) spin[0]));
        PowerLineRenderer.draw(rotor, pose, consumer, lit);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(HydroUnitBlockEntity unit) {
        int[] size = unit.getBlockState().getBlock() instanceof HydroUnitBlock block ? HydroUnitBlock.size(block.tier()) : new int[]{7, 9, 9};
        double reach = Math.max(size[0], size[2]) / 2.0 + 1.5;
        return new AABB(unit.getBlockPos()).inflate(reach, 0.0, reach).expandTowards(0, size[1] + 1.5, 0);
    }
}
