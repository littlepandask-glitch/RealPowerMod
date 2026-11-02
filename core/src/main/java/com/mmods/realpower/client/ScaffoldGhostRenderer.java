package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.multiblock.wind.WindStructureBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineScaffold;
import com.mmods.realpower.registry.ModBlocks;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Overlay fantasma do andaime da turbina. Segurando o controlador, a torre ou a pa:
 * <ul>
 *   <li>mirando um bloco: mostra onde ficaria o andaime minimo com o controlador no ponto de colocacao;</li>
 *   <li>mirando um controlador ja colocado: mostra so os blocos que ainda faltam.</li>
 * </ul>
 * Azul = torre, branco = pa e cubo, laranja = controlador, vermelho = posicao bloqueada por outro bloco.
 */
@EventBusSubscriber(modid = RealPower.MOD_ID, value = Dist.CLIENT)
public final class ScaffoldGhostRenderer {
    private static final float[] TOWER = {0.30f, 0.60f, 1.00f};
    private static final float[] BLADE = {0.95f, 0.95f, 0.95f};
    private static final float[] CONTROLLER = {1.00f, 0.60f, 0.15f};
    private static final float[] BLOCKED = {1.00f, 0.15f, 0.15f};
    private static final double INSET = 0.04;

    private ScaffoldGhostRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Block held = heldRelevantBlock(player);
        if (held == null) {
            return;
        }

        BlockPos anchor;
        Direction facing;
        boolean existingController;
        BlockState target = level.getBlockState(hit.getBlockPos());
        if (target.getBlock() instanceof WindTurbineControllerBlock) {
            anchor = hit.getBlockPos();
            facing = target.getValue(WindTurbineControllerBlock.FACING);
            existingController = true;
        } else if (held instanceof WindTurbineControllerBlock) {
            anchor = target.canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
            facing = player.getDirection().getOpposite();
            existingController = false;
        } else {
            return;
        }

        List<Ghost> ghosts = new ArrayList<>();
        for (WindTurbineScaffold.Part part : WindTurbineScaffold.minimum(anchor, facing)) {
            if (existingController && part.kind() == WindTurbineScaffold.Kind.CONTROLLER) {
                continue;
            }
            BlockState state = level.getBlockState(part.pos());
            if (isCorrect(state, part.kind())) {
                continue;
            }
            float[] color = state.canBeReplaced() ? colorOf(part.kind()) : BLOCKED;
            ghosts.add(new Ghost(new AABB(part.pos()).deflate(INSET), color));
        }
        if (ghosts.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        // Um tipo de render por vez: pedir outro buffer ao BufferSource encerra o anterior.
        VertexConsumer fill = buffers.getBuffer(RenderType.debugQuads());
        for (Ghost ghost : ghosts) {
            filledBox(poseStack.last().pose(), fill, ghost.box(), ghost.color()[0], ghost.color()[1], ghost.color()[2], 0.30f);
        }
        buffers.endBatch(RenderType.debugQuads());

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Ghost ghost : ghosts) {
            LevelRenderer.renderLineBox(poseStack, lines, ghost.box(), ghost.color()[0], ghost.color()[1], ghost.color()[2], 0.9f);
        }
        buffers.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    private record Ghost(AABB box, float[] color) {
    }

    private static Block heldRelevantBlock(Player player) {
        for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof BlockItem item
                    && (item.getBlock() instanceof WindTurbineControllerBlock || item.getBlock() instanceof WindStructureBlock)) {
                return item.getBlock();
            }
        }
        return null;
    }

    private static boolean isCorrect(BlockState state, WindTurbineScaffold.Kind kind) {
        return switch (kind) {
            case CONTROLLER -> state.getBlock() instanceof WindTurbineControllerBlock;
            case TOWER -> state.is(ModBlocks.WIND_TOWER.get());
            case BLADE -> state.is(ModBlocks.WIND_BLADE.get());
        };
    }

    private static float[] colorOf(WindTurbineScaffold.Kind kind) {
        return switch (kind) {
            case CONTROLLER -> CONTROLLER;
            case TOWER -> TOWER;
            case BLADE -> BLADE;
        };
    }

    private static void filledBox(Matrix4f pose, VertexConsumer c, AABB b, float r, float g, float bl, float a) {
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
