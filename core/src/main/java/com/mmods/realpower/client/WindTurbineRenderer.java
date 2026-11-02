package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.multiblock.wind.TurbineBlueprint;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Desenha o rotor 3D giratorio no lugar da carenagem e das helices quando a estrutura esta formada. */
public class WindTurbineRenderer implements BlockEntityRenderer<WindTurbineControllerBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealPower.MOD_ID, "textures/entity/wind_turbine.png");

    /** Dimensoes do rotor: a carenagem vai do bloco atras do plano do rotor ate a ponta do cone. */
    private static final TurbineModel.RotorSpec ROTOR = new TurbineModel.RotorSpec(
            TurbineBlueprint.SPINNER_RADIUS,
            TurbineBlueprint.SPINNER_HALF_LENGTH,
            TurbineBlueprint.HUB_U - (TurbineBlueprint.NACELLE_FRONT + 1) + 0.5,
            TurbineBlueprint.SPINNER_HALF_LENGTH,
            TurbineBlueprint.BLADE_ROOT,
            TurbineBlueprint.BLADE_LENGTH + 0.5);

    @Override
    public void render(WindTurbineControllerBlockEntity turbine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!turbine.isFormed() || turbine.getLevel() == null) {
            return;
        }
        Direction facing = turbine.getBlockState().getValue(WindTurbineControllerBlock.FACING);
        double angle = turbine.renderState().advance(
                turbine.getLevel().getGameTime() + partialTick, turbine.windSpeed(), ROTOR.tipRadius());

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        // o centro do cubo fica HUB_U blocos a frente (-Z local) e HUB_Y blocos acima do controlador
        poseStack.translate(0.0, TurbineBlueprint.HUB_Y, -TurbineBlueprint.HUB_U);
        poseStack.mulPose(Axis.ZP.rotation((float) angle));
        draw(turbine.renderState().rotorMesh(ROTOR), poseStack, consumer, packedLight);
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

    /** O rotor fica muito acima e a frente do controlador: a caixa cobre o raio das pas em torno do cubo. */
    @Override
    public AABB getRenderBoundingBox(WindTurbineControllerBlockEntity turbine) {
        BlockPos p = turbine.getBlockPos();
        double reach = TurbineBlueprint.BLADE_LENGTH + TurbineBlueprint.HUB_U + 4.0;
        return new AABB(p.getX() - reach, p.getY(), p.getZ() - reach,
                p.getX() + reach + 1.0, p.getY() + TurbineBlueprint.HUB_Y + TurbineBlueprint.BLADE_LENGTH + 4.0,
                p.getZ() + reach + 1.0);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
