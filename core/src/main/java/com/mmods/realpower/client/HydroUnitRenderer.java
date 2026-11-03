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
    private static final Map<Integer, Mesh> GATES = new HashMap<>();
    /** Por unidade: {angulo, ultimo instante, abertura da comporta}. */
    private static final Map<HydroUnitBlockEntity, double[]> SPIN = new WeakHashMap<>();
    /** Ciclos por tick da espuma da saida. */
    private static final double FOAM_SPEED = 0.045;

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
        Mesh gate = GATES.computeIfAbsent(key, k -> HydroModel.gate(size[0], size[1], size[2], turbineY));
        Mesh rotor = ROTORS.computeIfAbsent(key, k -> HydroModel.rotor(size[0], size[1], size[2], turbineY));

        double now = unit.getLevel().getGameTime() + partialTick;
        double[] spin = SPIN.computeIfAbsent(unit, u -> new double[]{0.0, now, unit.gateOpening(), Math.floor(now)});
        spin[0] = (spin[0] + Math.max(0.0, now - spin[1]) * FULL_SPEED * unit.runFraction()) % 360.0;
        spin[2] += (unit.gateOpening() - spin[2]) * Math.min(1.0, 0.08 * Math.max(0.0, now - spin[1]));
        spin[1] = now;
        if (unit.runFraction() > 0.0 && Math.floor(now) > spin[3]) {
            spin[3] = Math.floor(now);
            splash(unit, size, unit.getBlockState().getValue(HydroUnitBlock.FACING).toYRot());
        }

        int lit = LevelRenderer.getLightColor(unit.getLevel(), unit.getBlockPos().above(size[1]));
        var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - unit.getBlockState().getValue(HydroUnitBlock.FACING).toYRot()));
        PowerLineRenderer.draw(building, pose, consumer, lit);
        pose.pushPose();
        pose.translate(0.0, spin[2] * HydroModel.gateTravel(size[1], turbineY), 0.0);
        PowerLineRenderer.draw(gate, pose, consumer, lit);
        pose.popPose();
        pose.translate(0.0, 0.0, HydroModel.axisZ(size[2]));
        pose.mulPose(Axis.YP.rotationDegrees((float) spin[0]));
        PowerLineRenderer.draw(rotor, pose, consumer, lit);
        if (unit.runFraction() > 0.0) {
            double strength = unit.runFraction();
            pose.popPose();
            pose.pushPose();
            pose.translate(0.5, 0.0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - unit.getBlockState().getValue(HydroUnitBlock.FACING).toYRot()));
            double phase = (now * FOAM_SPEED) % 1.0;
            PowerLineRenderer.draw(HydroModel.outflow(size[0], size[2], phase, strength), pose, consumer, lit);
        }
        pose.popPose();
    }

    /** Respingos e bolhas na queda d'agua da saida (coordenadas do mundo; a unidade olha para {@code yRot} a jusante). */
    private static void splash(HydroUnitBlockEntity unit, int[] size, float yRot) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        double theta = Math.toRadians(180.0 - yRot);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double tail = Math.max(1.0, size[0] / 2.0 - 1.6);
        var random = level.random;
        for (int i = 0; i < 3; i++) {
            double lx = (random.nextDouble() * 2 - 1) * tail;
            double lz = -size[2] / 2.0 - 0.2 - random.nextDouble() * 2.0;
            double wx = unit.getBlockPos().getX() + 0.5 + lx * cos + lz * sin;
            double wz = unit.getBlockPos().getZ() + 0.5 - lx * sin + lz * cos;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, wx, unit.getBlockPos().getY() + 0.15, wz, 0.0, 0.05, 0.0);
        }
        if (random.nextInt(3) == 0) {
            double lz = -size[2] / 2.0 - 0.3;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                    unit.getBlockPos().getX() + 0.5 + lz * sin, unit.getBlockPos().getY() + 0.5, unit.getBlockPos().getZ() + 0.5 + lz * cos,
                    0.0, 0.02, 0.0);
        }
    }

    @Override
    public AABB getRenderBoundingBox(HydroUnitBlockEntity unit) {
        int[] size = unit.getBlockState().getBlock() instanceof HydroUnitBlock block ? HydroUnitBlock.size(block.tier()) : new int[]{7, 9, 9};
        double reach = Math.max(size[0], size[2]) / 2.0 + 1.5;
        return new AABB(unit.getBlockPos()).inflate(reach + HydroModel.OUTFLOW_LENGTH, 0.0, reach + HydroModel.OUTFLOW_LENGTH)
                .expandTowards(0, size[1] + 1.5, 0);
    }
}
