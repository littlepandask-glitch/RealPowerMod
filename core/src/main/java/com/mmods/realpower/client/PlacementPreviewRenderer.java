package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.cable.StructureBase;
import com.mmods.realpower.item.DamModuleItem;
import com.mmods.realpower.item.StructureBlockItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Previa da area ocupada ao segurar um multibloco ou um modulo de barragem: caixa verde se cabe, vermelha se algo atrapalha.
 * O ponto apontado e o canto proximo-esquerdo (marcado em amarelo); a area se estende para a frente e para a direita.
 */
@EventBusSubscriber(modid = RealPower.MOD_ID, value = Dist.CLIENT)
public final class PlacementPreviewRenderer {
    private static final int REFRESH_TICKS = 10;

    private static BoundingBox cachedBox;
    private static boolean cachedFree;
    private static BlockPos cachedKey;
    private static Direction cachedLook;
    private static Object cachedItem;
    private static long cachedTime = Long.MIN_VALUE;

    private PlacementPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof StructureBlockItem || stack.getItem() instanceof DamModuleItem) {
                BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
                BlockPos corner = context.getClickedPos();
                refresh(mc.level, stack, corner, context.getHorizontalDirection());
                if (cachedBox != null) {
                    draw(event, mc, corner);
                }
                return;
            }
        }
    }

    private static void refresh(Level level, ItemStack stack, BlockPos corner, Direction look) {
        long now = level.getGameTime();
        if (cachedItem == stack.getItem() && corner.equals(cachedKey) && look == cachedLook && now >= cachedTime && now - cachedTime < REFRESH_TICKS) {
            return;
        }
        cachedItem = stack.getItem();
        cachedKey = corner;
        cachedLook = look;
        cachedTime = now;
        cachedBox = null;
        if (stack.getItem() instanceof DamModuleItem module) {
            cachedBox = DamModuleItem.bounds(corner, look, module.module());
            cachedFree = DamModuleItem.cells(corner, look, module.module()).stream().allMatch(c -> free(level, c));
        } else if (stack.getItem() instanceof StructureBlockItem item && item.getBlock() instanceof StructureBase structure && structure.hasStructure()) {
            BlockPos base = structure.baseForCorner(corner, look);
            var state = structure.footprintState(look);
            cachedBox = structure.footprint(base, state);
            cachedFree = free(level, base) && structure.structureCells(base, state).stream().allMatch(c -> free(level, c));
        }
    }

    private static boolean free(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getBlockState(pos).canBeReplaced();
    }

    private static void draw(RenderLevelStageEvent event, Minecraft mc, BlockPos corner) {
        Vec3 camera = event.getCamera().getPosition();
        AABB area = new AABB(cachedBox.minX(), cachedBox.minY(), cachedBox.minZ(), cachedBox.maxX() + 1, cachedBox.maxY() + 1, cachedBox.maxZ() + 1);
        float r = cachedFree ? 0.20f : 1.00f;
        float g = cachedFree ? 0.90f : 0.18f;
        float b = cachedFree ? 0.35f : 0.18f;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer fill = buffers.getBuffer(RenderType.debugQuads());
        ScaffoldGhostRenderer.filledBox(poseStack.last().pose(), fill, area.deflate(0.02), r, g, b, 0.16f);
        ScaffoldGhostRenderer.filledBox(poseStack.last().pose(), fill, new AABB(corner).deflate(0.05), 1.0f, 0.85f, 0.15f, 0.55f);
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, area, r, g, b, 0.95f);
        LevelRenderer.renderLineBox(poseStack, lines, new AABB(corner).deflate(0.05), 1.0f, 0.85f, 0.15f, 1.0f);
        buffers.endBatch(RenderType.lines());
        poseStack.popPose();
    }
}
