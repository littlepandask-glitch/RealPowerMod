package com.mmods.realpower.client;

import com.mmods.realpower.cable.CableBlockEntity;
import com.mmods.realpower.cable.CableKind;
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

    /** Pontos de fixacao dos fios (x, y em fracao do bloco), alinhados as travessas dos modelos. */
    private static double[][] anchors(CableKind kind) {
        if (kind == CableKind.TOWER) {
            return new double[][]{{-0.05, 0.5}, {1.05, 0.5}, {-0.25, 0.25}, {1.25, 0.25}, {-0.45, 0.0}, {1.45, 0.0}};
        }
        return new double[][]{{0.5 - 0.375, 0.85}, {0.5, 0.95}, {0.5 + 0.375, 0.85}};
    }

    @Override
    public void render(CableBlockEntity cable, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (cable.links().isEmpty() || !cable.kind().overhead() || cable.getLevel() == null) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        BlockPos from = cable.getBlockPos();
        double[][] fromAnchors = anchors(cable.kind());
        for (BlockPos to : cable.links()) {
            if (from.asLong() > to.asLong() || !(cable.getLevel().getBlockEntity(to) instanceof CableBlockEntity other)) {
                continue; // cada linha e desenhada por um so dos extremos
            }
            double[][] toAnchors = anchors(other.kind());
            int wires = Math.min(fromAnchors.length, toAnchors.length);
            for (int w = 0; w < wires; w++) {
                // ancoras simetricas: a esquerda de um lado liga a esquerda do outro
                double[] fa = fromAnchors[w];
                double[] ta = toAnchors[w];
                Vec3 a = new Vec3(fa[0], fa[1], 0.5);
                Vec3 b = new Vec3(to.getX() - from.getX() + ta[0], to.getY() - from.getY() + ta[1], to.getZ() - from.getZ() + 0.5);
                double sag = SAG_PER_BLOCK * b.subtract(a).length();
                Vec3 previous = a;
                for (int i = 1; i <= SEGMENTS; i++) {
                    double t = i / (double) SEGMENTS;
                    Vec3 point = a.add(b.subtract(a).scale(t)).add(0, -sag * 4.0 * t * (1.0 - t), 0);
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
