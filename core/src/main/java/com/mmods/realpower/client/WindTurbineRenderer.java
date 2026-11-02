package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlockEntity;
import com.mmods.realpower.render.Mesh;
import com.mmods.realpower.render.TurbineModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Desenha o modelo 3D da turbina no lugar dos blocos de andaime quando a estrutura esta formada. */
public class WindTurbineRenderer implements BlockEntityRenderer<WindTurbineControllerBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealPower.MOD_ID, "textures/entity/wind_turbine.png");

    @Override
    public void render(WindTurbineControllerBlockEntity turbine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!turbine.isFormed() || turbine.getLevel() == null) {
            return;
        }
        Direction facing = turbine.getBlockState().getValue(WindTurbineControllerBlock.FACING);
        double tipRadius = turbine.bladeRadius() + 0.5;
        TurbineModel.Dims dims = new TurbineModel.Dims(turbine.towerHeight(), tipRadius);
        double angle = turbine.renderState().advance(
                turbine.getLevel().getGameTime() + partialTick, turbine.windSpeed(), tipRadius);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        draw(turbine.renderState().staticMesh(dims), poseStack, consumer, packedLight);

        poseStack.pushPose();
        poseStack.translate(0.0, 0.0, TurbineModel.HUB_Z);
        poseStack.mulPose(Axis.ZP.rotation((float) angle));
        draw(turbine.renderState().rotorMesh(dims), poseStack, consumer, packedLight);
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void draw(Mesh mesh, PoseStack poseStack, VertexConsumer consumer, int light) {
        PoseStack.Pose pose = poseStack.last();
        for (int q = 0; q < mesh.quadCount(); q++) {
            float nx = mesh.normals[q * 3];
            float ny = mesh.normals[q * 3 + 1];
            float nz = mesh.normals[q * 3 + 2];
            for (int v = 0; v < 4; v++) {
                int i = q * 12 + v * 3;
                consumer.addVertex(pose, mesh.positions[i], mesh.positions[i + 1], mesh.positions[i + 2])
                        .setColor(mesh.colors[q * 3], mesh.colors[q * 3 + 1], mesh.colors[q * 3 + 2], 1.0F)
                        .setUv(0.5F, 0.5F)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    /** O modelo 3D ocupa muito mais que um bloco: a caixa de renderizacao cobre torre e rotor. */
    @Override
    public AABB getRenderBoundingBox(WindTurbineControllerBlockEntity turbine) {
        if (!turbine.isFormed()) {
            return new AABB(turbine.getBlockPos());
        }
        double reach = turbine.bladeRadius() + 8.0;
        BlockPos p = turbine.getBlockPos();
        return new AABB(p.getX() - reach, p.getY() - turbine.towerHeight() - 1.0, p.getZ() - reach,
                p.getX() + reach + 1.0, p.getY() + reach + 2.0, p.getZ() + reach + 1.0);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
