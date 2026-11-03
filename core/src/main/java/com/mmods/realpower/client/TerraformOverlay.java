package com.mmods.realpower.client;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.item.TerraformerItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Caixa fantasma da selecao do terraformador: vermelha ao escavar, azul ao aterrar; so o canto A enquanto falta o B. */
@EventBusSubscriber(modid = RealPower.MOD_ID, value = Dist.CLIENT)
public final class TerraformOverlay {
    private static final float[] DIG = {1.00f, 0.35f, 0.25f};
    private static final float[] FILL = {0.30f, 0.62f, 1.00f};

    private TerraformOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem().getItem() instanceof TerraformerItem ? player.getMainHandItem() : player.getOffhandItem();
        if (!(held.getItem() instanceof TerraformerItem)) {
            return;
        }
        TerraformerItem.Selection selection = TerraformerItem.selection(held);
        if (selection.a() == null) {
            return;
        }
        BlockPos b = selection.b() != null ? selection.b() : selection.a();
        AABB box = new AABB(Math.min(selection.a().getX(), b.getX()), Math.min(selection.a().getY(), b.getY()),
                Math.min(selection.a().getZ(), b.getZ()), Math.max(selection.a().getX(), b.getX()) + 1.0,
                Math.max(selection.a().getY(), b.getY()) + 1.0, Math.max(selection.a().getZ(), b.getZ()) + 1.0).deflate(0.02);
        float[] color = selection.fill() ? FILL : DIG;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer fill = buffers.getBuffer(RenderType.debugQuads());
        ScaffoldGhostRenderer.filledBox(pose.last().pose(), fill, box, color[0], color[1], color[2], 0.18f);
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, lines, box, color[0], color[1], color[2], 0.95f);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
