package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.multiblock.wind.TurbineBlueprint;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlockEntity;
import com.mmods.realpower.multiblock.wind.WindTurbineScan;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Overlay fantasma fixo do projeto da turbina. Cada controlador colocado desenha, enquanto o
 * overlay estiver ligado no menu e a estrutura nao estiver formada, os blocos que ainda faltam
 * (ou estao errados) perto do jogador.
 *
 * <p>Cores: cinza = base, azul = corpo e nacele, ciano = carenagem, branco = helice,
 * vermelho = posicao ocupada por outro bloco (precisa ser removido ou escavado).
 */
@EventBusSubscriber(modid = RealPower.MOD_ID, value = Dist.CLIENT)
public final class ScaffoldGhostRenderer {
    private static final float[] BASE = {0.75f, 0.65f, 0.45f};
    private static final float[] CASING = {0.30f, 0.60f, 1.00f};
    private static final float[] POST = {0.85f, 0.45f, 0.20f};
    private static final float[] LIGHT = {1.00f, 0.90f, 0.30f};
    private static final float[] FAIRING = {0.25f, 0.95f, 0.90f};
    private static final float[] BLADE = {0.95f, 0.95f, 0.95f};
    private static final float[] BLOCKED = {1.00f, 0.15f, 0.15f};

    private static final double INSET = 0.04;
    private static final double VIEW_DISTANCE = 72.0;
    private static final int MAX_BOXES = 2500;
    /** Recalcula os blocos faltantes uma vez por segundo. */
    private static final int REFRESH_TICKS = 20;

    private ScaffoldGhostRenderer() {
    }

    private record Ghost(AABB box, float[] color) {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || WindTurbineControllerBlockEntity.CLIENT_INSTANCES.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double maxSq = VIEW_DISTANCE * VIEW_DISTANCE;

        List<Ghost> ghosts = new ArrayList<>();
        for (WindTurbineControllerBlockEntity turbine : List.copyOf(WindTurbineControllerBlockEntity.CLIENT_INSTANCES)) {
            if (turbine.isRemoved() || !turbine.overlayEnabled() || turbine.isFormed()
                    || !(turbine.getBlockState().getBlock() instanceof WindTurbineControllerBlock)) {
                continue;
            }
            BlockPos origin = turbine.getBlockPos();
            if (origin.distToCenterSqr(camera) > (VIEW_DISTANCE + 130.0) * (VIEW_DISTANCE + 130.0)) {
                continue; // o projeto tem ate ~130 blocos de altura
            }
            refreshCache(level, turbine);
            for (TurbineBlueprint.Part part : turbine.ghostMissing()) {
                if (ghosts.size() >= MAX_BOXES) {
                    break;
                }
                BlockPos pos = part.pos();
                if (pos.distToCenterSqr(camera) > maxSq) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                float[] color = state.canBeReplaced() ? colorOf(part.kind()) : BLOCKED;
                ghosts.add(new Ghost(new AABB(pos).deflate(INSET), color));
            }
        }
        if (ghosts.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        // Um tipo de render por vez: pedir outro buffer ao BufferSource encerra o anterior.
        VertexConsumer fill = buffers.getBuffer(RenderType.debugQuads());
        for (Ghost ghost : ghosts) {
            filledBox(poseStack.last().pose(), fill, ghost.box(), ghost.color()[0], ghost.color()[1], ghost.color()[2], 0.28f);
        }
        buffers.endBatch(RenderType.debugQuads());

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Ghost ghost : ghosts) {
            LevelRenderer.renderLineBox(poseStack, lines, ghost.box(), ghost.color()[0], ghost.color()[1], ghost.color()[2], 0.85f);
        }
        buffers.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    /** Lista as posicoes do projeto que ainda nao tem o bloco certo; refeita a cada {@link #REFRESH_TICKS}. */
    private static void refreshCache(ClientLevel level, WindTurbineControllerBlockEntity turbine) {
        long now = level.getGameTime();
        if (turbine.ghostCacheTime() != Long.MIN_VALUE && now >= turbine.ghostCacheTime()
                && now - turbine.ghostCacheTime() < REFRESH_TICKS) {
            return;
        }
        Direction facing = turbine.getBlockState().getValue(WindTurbineControllerBlock.FACING);
        List<TurbineBlueprint.Part> missing = new ArrayList<>();
        boolean portDone = WindTurbineScan.portMissing(level, turbine.getBlockPos(), facing) == 0;
        for (TurbineBlueprint.Part part : TurbineBlueprint.parts(turbine.getBlockPos(), facing)) {
            if (portDone && TurbineBlueprint.isPortPart(part.kind())) {
                continue;
            }
            if (level.isLoaded(part.pos()) && !level.getBlockState(part.pos()).is(WindTurbineScan.blockFor(part.kind()))) {
                missing.add(part);
            }
        }
        turbine.updateGhostCache(now, missing);
    }

    private static float[] colorOf(TurbineBlueprint.Kind kind) {
        return switch (kind) {
            case BASE -> BASE;
            case CASING -> CASING;
            case LIGHT -> LIGHT;
            case PORT -> POST;
            case FAIRING -> FAIRING;
            case BLADE -> BLADE;
        };
    }

    static void filledBox(Matrix4f pose, VertexConsumer c, AABB b, float r, float g, float bl, float a) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        quad(pose, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, bl, a); // baixo
        quad(pose, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, bl, a); // cima
        quad(pose, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, bl, a); // norte
        quad(pose, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, a); // sul
        quad(pose, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, bl, a); // oeste
        quad(pose, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, bl, a); // leste
    }

    private static void quad(Matrix4f pose, VertexConsumer c,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        c.addVertex(pose, ax, ay, az).setColor(r, g, b, a);
        c.addVertex(pose, bx, by, bz).setColor(r, g, b, a);
        c.addVertex(pose, cx, cy, cz).setColor(r, g, b, a);
        c.addVertex(pose, dx, dy, dz).setColor(r, g, b, a);
    }
}
