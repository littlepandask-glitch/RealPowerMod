package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.cable.CableBlock;
import com.mmods.realpower.cable.CableBlockEntity;
import com.mmods.realpower.cable.CableKind;
import com.mmods.realpower.render.Mesh;
import com.mmods.realpower.render.PowerStructureModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Desenha postes e torres 3D em escala real e os fios (catenaria) entre os que estao ligados. */
public class PowerLineRenderer implements BlockEntityRenderer<CableBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealPower.MOD_ID, "textures/entity/wind_turbine.png");
    private static final int SEGMENTS = 24;
    private static final double SAG_PER_BLOCK = 0.025;
    private static final float[][] ACCENT = {
            {0.27f, 0.78f, 1.00f}, {0.35f, 0.92f, 0.47f}, {1.00f, 0.84f, 0.24f}, {1.00f, 0.55f, 0.16f}, {0.92f, 0.35f, 1.00f}};

    private static final Map<Integer, Mesh> MESHES = new HashMap<>();

    private static Mesh meshFor(CableBlockEntity cable) {
        CableKind kind = cable.kind();
        Tier tier = cable.tier();
        int[] size = kind.size(tier);
        return MESHES.computeIfAbsent(kind.ordinal() * 8 + tier.ordinal(), k -> switch (kind) {
            case TOWER -> PowerStructureModel.tower(kind.height(), ACCENT[tier.ordinal()]);
            case TRANSFORMER -> PowerStructureModel.transformer(size[0], size[1], size[2], ACCENT[tier.ordinal()]);
            default -> PowerStructureModel.pole(kind.height(), tier == Tier.BASIC, ACCENT[tier.ordinal()]);
        });
    }

    private static double[][] anchors(CableBlockEntity cable) {
        CableKind kind = cable.kind();
        return switch (kind) {
            case TRANSFORMER -> PowerStructureModel.transformerAnchors(kind.size(cable.tier())[0], kind.size(cable.tier())[2]);
            case TOWER -> PowerStructureModel.towerAnchors(kind.height());
            default -> PowerStructureModel.poleAnchors(kind.height());
        };
    }

    /** Rotacao da estrutura em graus (o eixo z local aponta no sentido da linha). */
    private static float yaw(CableBlockEntity cable) {
        return 180.0F - cable.getBlockState().getValue(CableBlock.FACING).toYRot();
    }

    /** Posicao de um ponto de fixacao em coordenadas do mundo. */
    private static Vec3 anchorWorld(CableBlockEntity cable, double[] local) {
        Vector3f p = new Vector3f((float) local[0], 0.0F, (float) local[2]).rotateY((float) Math.toRadians(yaw(cable)));
        BlockPos pos = cable.getBlockPos();
        return new Vec3(pos.getX() + 0.5 + p.x, pos.getY() + local[1], pos.getZ() + 0.5 + p.z);
    }

    @Override
    public void render(CableBlockEntity cable, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (!cable.kind().overhead() || cable.getLevel() == null) {
            return;
        }
        BlockPos from = cable.getBlockPos();
        int lightTop = LevelRenderer.getLightColor(cable.getLevel(), from.above(cable.kind().size(cable.tier())[2]));

        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw(cable)));
        draw(meshFor(cable), pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), lightTop);
        pose.popPose();

        if (cable.links().isEmpty()) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        double[][] fromAnchors = anchors(cable);
        for (BlockPos to : cable.links()) {
            if (from.asLong() > to.asLong() || !(cable.getLevel().getBlockEntity(to) instanceof CableBlockEntity other)
                    || !other.kind().overhead()) {
                continue; // cada linha e desenhada por um so dos extremos
            }
            double[][] toAnchors = anchors(other);
            Vec3[] fromWorld = sortedAnchors(cable, fromAnchors, other.getBlockPos());
            Vec3[] toWorld = sortedAnchors(other, toAnchors, from);
            int wires = Math.min(fromWorld.length, toWorld.length);
            for (int w = 0; w < wires; w++) {
                Vec3 a = fromWorld[w].subtract(from.getX(), from.getY(), from.getZ());
                Vec3 b = toWorld[w].subtract(from.getX(), from.getY(), from.getZ());
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

    /** Pontos de fixacao no mundo, ordenados lateralmente em relacao a linha entre as duas estruturas (ordem igual nas duas pontas). */
    private static Vec3[] sortedAnchors(CableBlockEntity cable, double[][] local, BlockPos lineEnd) {
        BlockPos own = cable.getBlockPos();
        Vec3 from = Vec3.atBottomCenterOf(own);
        Vec3 to = Vec3.atBottomCenterOf(lineEnd);
        // a direcao da linha e sempre vista do extremo de menor posicao, para as duas pontas ordenarem igual
        boolean flip = own.asLong() > lineEnd.asLong();
        double dx = flip ? from.x - to.x : to.x - from.x;
        double dz = flip ? from.z - to.z : to.z - from.z;
        Vec3[] world = new Vec3[local.length];
        for (int i = 0; i < local.length; i++) {
            world[i] = anchorWorld(cable, local[i]);
        }
        java.util.Arrays.sort(world, java.util.Comparator
                .comparingDouble((Vec3 p) -> Math.round((p.x * -dz + p.z * dx) / Math.hypot(dx, dz) * 100.0))
                .thenComparingDouble(p -> p.y));
        return world;
    }

    static void draw(Mesh mesh, PoseStack pose, VertexConsumer consumer, int light) {
        PoseStack.Pose last = pose.last();
        for (int q = 0; q < mesh.quadCount(); q++) {
            float nx = mesh.normals[q * 3];
            float ny = mesh.normals[q * 3 + 1];
            float nz = mesh.normals[q * 3 + 2];
            for (int v = 0; v < 4; v++) {
                int i = q * 12 + v * 3;
                consumer.addVertex(last, mesh.positions[i], mesh.positions[i + 1], mesh.positions[i + 2])
                        .setColor(mesh.colors[q * 3], mesh.colors[q * 3 + 1], mesh.colors[q * 3 + 2], 1.0F)
                        .setUv(0.5F, 0.5F)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(last, nx, ny, nz);
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
        int h = cable.kind().size(cable.tier())[2];
        AABB box = new AABB(cable.getBlockPos()).inflate(12.0, 0.0, 12.0).expandTowards(0, h + 3, 0);
        for (BlockPos link : cable.links()) {
            box = box.minmax(new AABB(link).inflate(12.0, 0.0, 12.0).expandTowards(0, h + 3, 0));
        }
        return box;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRenderOffScreen(CableBlockEntity cable) {
        return cable.kind().overhead();
    }
}
