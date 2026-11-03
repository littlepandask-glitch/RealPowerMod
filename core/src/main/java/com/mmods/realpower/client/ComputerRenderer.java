package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.computer.ComputerBlock;
import com.mmods.realpower.computer.ComputerBlockEntity;
import com.mmods.realpower.computer.ControlRoomBlock;
import com.mmods.realpower.render.ControlRoomModel;
import com.mmods.realpower.render.Mesh;
import com.mmods.realpower.render.PowerStructureModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Desenha o rack do computador central (3 x 1 x 2 m) com as portas de dados nas pontas. */
public class ComputerRenderer implements BlockEntityRenderer<ComputerBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealPower.MOD_ID, "textures/entity/wind_turbine.png");
    private static final float[] DATA_ACCENT = {0.24f, 0.86f, 0.78f};

    private static Mesh mesh;
    private static Mesh roomMesh;

    @Override
    public void render(ComputerBlockEntity computer, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (computer.getLevel() == null || !(computer.getBlockState().getBlock() instanceof ComputerBlock)) {
            return;
        }
        boolean room = computer.getBlockState().getBlock() instanceof ControlRoomBlock;
        if (mesh == null) {
            mesh = PowerStructureModel.computer(DATA_ACCENT);
        }
        if (room && roomMesh == null) {
            roomMesh = ControlRoomModel.room(DATA_ACCENT);
        }
        int lit = LevelRenderer.getLightColor(computer.getLevel(), computer.getBlockPos().above());
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - computer.getBlockState().getValue(ComputerBlock.FACING).toYRot()));
        PowerLineRenderer.draw(room ? roomMesh : mesh, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), lit);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ComputerBlockEntity computer) {
        if (computer.getBlockState().getBlock() instanceof ControlRoomBlock) {
            return new AABB(computer.getBlockPos()).inflate(5.5, 0.0, 5.5).expandTowards(0, 6.5, 0);
        }
        return new AABB(computer.getBlockPos()).inflate(2.0, 0.0, 2.0).expandTowards(0, 2.5, 0);
    }
}
