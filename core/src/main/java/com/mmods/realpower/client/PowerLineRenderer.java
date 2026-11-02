package com.mmods.realpower.client;

import com.mmods.realpower.cable.CableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Desenha os fios (3 condutores com catenaria) entre postes/torres ligados. */
public class PowerLineRenderer implements BlockEntityRenderer<CableBlockEntity> {
    private static final int SEGMENTS = 16;
    private static final double SAG_PER_BLOCK = 0.03;
    private static final double SPREAD = 0.4;

    @Override
    public void render(CableBlockEntity cable, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (cable.links().isEmpty() || !cable.kind().overhead()) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        BlockPos from = cable.getBlockPos();
        double anchorY = cable.kind() == com.mmods.realpower.cable.CableKind.TOWER ? 0.95 : 0.85;
        for (BlockPos to : cable.links()) {
            if (from.asLong() > to.asLong()) {
                continue; // cada linha e desenhada por um so dos extremos
            }
            Vec3 a = new Vec3(0.5, anchorY, 0.5);
            Vec3 b = new Vec3(to.getX() - from.getX() + 0.5, to.getY() - from.getY() + anchorY, to.getZ() - from.getZ() + 0.5);
            Vec3 along = b.subtract(a);
            double horizontal = Math.sqrt(along.x * along.x + along.z * along.z);
            Vec3 side = horizontal < 1e-6 ? new Vec3(1, 0, 0) : new Vec3(-along.z / horizontal, 0, along.x / horizontal);
            double sag = SAG_PER_BLOCK * along.length();
            for (int wire = -1; wire <= 1; wire++) {
                Vec3 offset = side.scale(wire * SPREAD);
                Vec3 previous = a.add(offset);
                for (int i = 1; i <= SEGMENTS; i++) {
                    double t = i / (double) SEGMENTS;
                    Vec3 point = a.add(along.scale(t)).add(offset).add(0, -sag * 4.0 * t * (1.0 - t), 0);
                    segment(lines, pose.last(), previous, point);
                    previous = point;
                }
            }
        }
    }

    private static void segment(VertexConsumer lines, PoseStack.Pose pose, Vec3 p, Vec3 q) {
        Matrix4f matrix = pose.pose();
        Vec3 normal = q.subtract(p).normalize();
        lines.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setColor(28, 28, 32, 255)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        lines.addVertex(matrix, (float) q.x, (float) q.y, (float) q.z).setColor(28, 28, 32, 255)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    @Override
    public AABB getRenderBoundingBox(CableBlockEntity cable) {
        AABB box = new AABB(cable.getBlockPos()).inflate(1.0);
        for (BlockPos link : cable.links()) {
            box = box.minmax(new AABB(link).inflate(1.0));
        }
        return box;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
