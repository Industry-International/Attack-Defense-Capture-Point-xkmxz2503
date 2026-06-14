package com.xkmxz.attack_defense_capture_point_xkmxz.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 世界级据点区域渲染器 — 从 {@link ClientCaptureDataCache} 读取客户端缓存的据点数据，<br>
 * 在每个据点的实际中心坐标绘制圆形范围轮廓。
 * <p>
 * 数据来源：服务端通过 {@link com.xkmxz.attack_defense_capture_point_xkmxz.network.CaptureDataSyncPayload}
 * 推送到客户端缓存。单机和联机均可用。
 * <p>
 * 需要在 ClientModEvents 中通过 {@code NeoForge.EVENT_BUS.register(CapturePointWorldRenderer.class)} 注册。
 */
public final class CapturePointWorldRenderer {

    private static final int SEGMENTS = 32;

    private CapturePointWorldRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;

        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 从客户端缓存读取数据（单机和联机构通过网络包同步）
        var points = ClientCaptureDataCache.INSTANCE.getPoints();
        if (points.isEmpty()) return;

        var camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        var frustum = event.getFrustum();

        var poseStack = event.getPoseStack();
        var bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.LINES);

        for (var entry : points.values()) {
            if (!entry.showRange()) continue;

            // 以据点的实际中心坐标渲染，而非方块位置
            double cx = entry.pos().getX() + 0.5;
            double cy = entry.pos().getY() + 0.5;
            double cz = entry.pos().getZ() + 0.5;
            double radius = entry.radius();
            int color = entry.displayColor();

            // 视锥体裁剪：检查据点的包围盒是否在视野内
            double r = radius + 1;
            var aabb = new AABB(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
            if (!frustum.isVisible(aabb)) continue;

            float a = ((color >> 24) & 0xFF) / 255.0f;
            float red = ((color >> 16) & 0xFF) / 255.0f;
            float green = ((color >> 8) & 0xFF) / 255.0f;
            float blue = (color & 0xFF) / 255.0f;

            poseStack.pushPose();
            // 将渲染原点从相机位置平移到据点中心
            poseStack.translate(cx - camPos.x, cy - camPos.y, cz - camPos.z);

            Matrix4f matrix = poseStack.last().pose();

            // 绘制水平圆环（32 段）
            double angleStep = 2.0 * Math.PI / SEGMENTS;
            for (int i = 0; i < SEGMENTS; i++) {
                double angle1 = i * angleStep;
                double angle2 = (i + 1) * angleStep;

                float x1 = (float) (radius * Math.cos(angle1));
                float z1 = (float) (radius * Math.sin(angle1));
                float x2 = (float) (radius * Math.cos(angle2));
                float z2 = (float) (radius * Math.sin(angle2));

                consumer.addVertex(matrix, x1, 0, z1)
                        .setColor(red, green, blue, a)
                        .setNormal(0, 1, 0);
                consumer.addVertex(matrix, x2, 0, z2)
                        .setColor(red, green, blue, a)
                        .setNormal(0, 1, 0);
            }

            // 绘制垂直辅助标记（4条短竖线指示上下范围）
            float vh = 0.5f;
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI / 2;
                float vx = (float) (radius * Math.cos(angle));
                float vz = (float) (radius * Math.sin(angle));

                consumer.addVertex(matrix, vx, -vh, vz)
                        .setColor(red, green, blue, a * 0.5f)
                        .setNormal(0, 1, 0);
                consumer.addVertex(matrix, vx, vh, vz)
                        .setColor(red, green, blue, a * 0.5f)
                        .setNormal(0, 1, 0);
            }

            poseStack.popPose();
        }

        // 提交线条批次
        bufferSource.endBatch(RenderType.LINES);
    }
}
