package com.xkmxz.attack_defense_capture_point_xkmxz.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xkmxz.attack_defense_capture_point_xkmxz.block.entity.CapturePointBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * 据点方块渲染器 — 方块本身由 MODEl 渲染，此处留空。
 * 圆圈范围渲染已迁移至 {@link CapturePointWorldRenderer} 独立完成。
 */
public class CapturePointBlockEntityRenderer implements BlockEntityRenderer<CapturePointBlockEntity> {

    public CapturePointBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CapturePointBlockEntity blockEntity, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        // 圆圈绘制已移至 CapturePointWorldRenderer
    }

    @Override
    public boolean shouldRenderOffScreen(CapturePointBlockEntity blockEntity) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
