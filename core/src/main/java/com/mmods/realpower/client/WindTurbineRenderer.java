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
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
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

    private static final TurbineModel.StructureSpec STRUCTURE = new TurbineModel.StructureSpec(
            TurbineBlueprint.TOWER_BASE_RADIUS + 0.5, TurbineBlueprint.TOWER_TOP_RADIUS + 0.5,
            TurbineBlueprint.TOWER_HEIGHT, TurbineBlueprint.TOWER_WALL, 1.5, 3.0,
            -TurbineBlueprint.NACELLE_BACK, TurbineBlueprint.NACELLE_FRONT, TurbineBlueprint.NACELLE_HALF_WIDTH,
            TurbineBlueprint.NACELLE_FLOOR, TurbineBlueprint.NACELLE_ROOF, TurbineBlueprint.HATCH_RADIUS_SQ);

    /** Luz de posicao: pisca a cada 1,5 s, acesa por 0,5 s. */
    private static final int BLINK_PERIOD = 30;
    private static final int BLINK_ON = 10;

    @Override
    public void render(WindTurbineControllerBlockEntity turbine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!turbine.isFormed() || turbine.getLevel() == null) {
            return;
        }
        Level level = turbine.getLevel();
        Direction facing = turbine.getBlockState().getValue(WindTurbineControllerBlock.FACING);
        double time = level.getGameTime() + partialTick;
        double angle = turbine.renderState().advance(time, turbine.windSpeed(), ROTOR.tipRadius());
        boolean blink = time % BLINK_PERIOD < BLINK_ON;
        BlockPos origin = turbine.getBlockPos();

        // o controlador fica dentro da torre (escuro): a luz externa vem de um ponto livre a frente do rotor
        BlockPos outside = origin
                .relative(facing, TurbineBlueprint.HUB_U + (int) TurbineBlueprint.SPINNER_HALF_LENGTH + 2)
                .above(TurbineBlueprint.HUB_Y);
        int outsideLight = LevelRenderer.getLightColor(level, outside);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));

        // corpo e nacele (x = lateral, z = para tras); o interior usa a luz do proprio mundo
        poseStack.pushPose();
        for (TurbineModel.Section section : turbine.renderState().structure(STRUCTURE)) {
            int light = section.interior() ? interiorLight(level, origin, facing, section) : outsideLight;
            draw(section.mesh(), poseStack, consumer, light);
        }
        if (blink) {
            draw(turbine.renderState().structureLights(), poseStack, consumer, LightTexture.FULL_BRIGHT);
        }
        poseStack.popPose();

        // rotor: o centro do cubo fica HUB_U blocos a frente (-Z local) e HUB_Y acima do controlador
        poseStack.translate(0.0, TurbineBlueprint.HUB_Y, -TurbineBlueprint.HUB_U);
        poseStack.mulPose(Axis.ZP.rotation((float) angle));
        draw(turbine.renderState().rotorMesh(ROTOR), poseStack, consumer, outsideLight);
        if (blink) {
            draw(turbine.renderState().rotorLights(ROTOR), poseStack, consumer, LightTexture.FULL_BRIGHT);
        }
        poseStack.popPose();
    }

    private static int interiorLight(Level level, BlockPos origin, Direction facing, TurbineModel.Section section) {
        BlockPos pos = origin
                .relative(facing, (int) Math.round(-section.lz()))
                .relative(facing.getClockWise(), (int) Math.round(section.lx()))
                .above((int) Math.floor(section.ly() + 0.5));
        return LevelRenderer.getLightColor(level, pos);
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

    /** O controlador pode estar fora da vista (secao oculta) enquanto o rotor, la em cima, esta visivel. */
    @Override
    public boolean shouldRenderOffScreen(WindTurbineControllerBlockEntity turbine) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
