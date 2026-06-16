package com.xkmxz.attack_defense_capture_point_xkmxz.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xkmxz.attack_defense_capture_point_xkmxz.Attack_defense_capture_point_xkmxz;
import com.xkmxz.attack_defense_capture_point_xkmxz.network.CaptureDataSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Map;

/**
 * 据点占领进度条 HUD 覆盖层。
 * 参考 占点-1.0.1 的 JinDuTiaoOverlay，适配到我们的数据架构。
 * 当玩家靠近正在占领的据点时，在屏幕上方显示占领进度条。
 */
@EventBusSubscriber(modid = Attack_defense_capture_point_xkmxz.MODID, value = Dist.CLIENT)
public class CaptureProgressOverlay {

    private static final ResourceLocation GREEN_BG = ResourceLocation.fromNamespaceAndPath(
            Attack_defense_capture_point_xkmxz.MODID, "textures/screens/green_bg.png");
    private static final ResourceLocation GREEN = ResourceLocation.fromNamespaceAndPath(
            Attack_defense_capture_point_xkmxz.MODID, "textures/screens/green.png");
    private static final ResourceLocation RED_BG = ResourceLocation.fromNamespaceAndPath(
            Attack_defense_capture_point_xkmxz.MODID, "textures/screens/red_bg.png");
    private static final ResourceLocation RED = ResourceLocation.fromNamespaceAndPath(
            Attack_defense_capture_point_xkmxz.MODID, "textures/screens/red.png");
    private static final ResourceLocation YELLOW_BG = ResourceLocation.fromNamespaceAndPath(
            Attack_defense_capture_point_xkmxz.MODID, "textures/screens/yellow_bg.png");
    private static final ResourceLocation YELLOW = ResourceLocation.fromNamespaceAndPath(
            Attack_defense_capture_point_xkmxz.MODID, "textures/screens/yellow.png");

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_X_OFFSET = -90; // center - 90
    private static final int BAR_Y = 20;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        var cache = ClientCaptureDataCache.INSTANCE;
        var points = cache.getPoints();
        if (points.isEmpty()) return;

        var playerPos = player.position();
        // 查找最近的、处于占领中或已被占领的据点
        Map.Entry<String, CaptureDataSyncPayload.PointRenderData> nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (var entry : points.entrySet()) {
            var data = entry.getValue();
            if (!data.showRange()) continue;
            double dist = playerPos.distanceToSqr(data.pos().getX() + 0.5, data.pos().getY() + 0.5, data.pos().getZ() + 0.5);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = Map.entry(entry.getKey(), data);
            }
        }

        if (nearest == null) return;

        var data = nearest.getValue();
        double radius = data.radius();
        if (playerPos.distanceToSqr(data.pos().getX() + 0.5, data.pos().getY() + 0.5, data.pos().getZ() + 0.5) > radius * radius) {
            return; // 不在范围内，不显示
        }

        var guiGraphics = event.getGuiGraphics();
        int screenWidth = guiGraphics.guiWidth();
        int x = screenWidth / 2 + BAR_X_OFFSET;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 根据占领状态选择颜色
        boolean captured = data.captured();
        boolean isPlayerTeam = captured && player.getTeam() != null
                && player.getTeam().getName().equals(data.ownerTeam());

        Component statusText;
        ResourceLocation bgTexture;
        ResourceLocation fgTexture;
        int progressWidth;
        String pointName = nearest.getKey();

        // 检测是否处于清除阶段（ownerTeam 存在但 captured=false，说明正在被清除）
        boolean isNeutralizing = !captured && data.ownerTeam() != null && data.capturingTeam() != null
                && !data.ownerTeam().equals(data.capturingTeam());

        // 检测是否处于恢复阶段（ownerTeam 存在，captured=false，无人在占领中）
        boolean isRecovering = !captured && data.ownerTeam() != null && data.capturingTeam() == null;

        if (captured && isPlayerTeam) {
            // 已被我方占领 → 绿色满条
            bgTexture = GREEN_BG;
            fgTexture = GREEN;
            progressWidth = BAR_WIDTH;
            statusText = Component.translatable("hud.capture_progress.friendly_owned", pointName);
        } else if (captured) {
            // 已被敌方占领
            bgTexture = RED_BG;
            fgTexture = RED;
            progressWidth = BAR_WIDTH;
            statusText = Component.translatable("hud.capture_progress.enemy_owned", pointName);
        } else if (isNeutralizing) {
            // 正在被清除（进度从 100 下降到 0）
            boolean isOurTeam = player.getTeam() != null && player.getTeam().getName().equals(data.capturingTeam());
            if (isOurTeam) {
                bgTexture = GREEN_BG;
                fgTexture = GREEN;
                statusText = Component.translatable("hud.capture_progress.friendly_neutralizing", pointName, data.captureProgress());
            } else {
                bgTexture = RED_BG;
                fgTexture = RED;
                statusText = Component.translatable("hud.capture_progress.enemy_neutralizing", pointName, data.captureProgress());
            }
            progressWidth = (int) ((double) data.captureProgress() / 100.0 * BAR_WIDTH);
        } else if (isRecovering) {
            // 正在恢复中（进度缓慢回升到 100，无人争夺）
            boolean isOurTeam = player.getTeam() != null && player.getTeam().getName().equals(data.ownerTeam());
            if (isOurTeam) {
                bgTexture = GREEN_BG;
                fgTexture = GREEN;
                statusText = Component.translatable("hud.capture_progress.friendly_recovering", pointName, data.captureProgress());
            } else {
                bgTexture = RED_BG;
                fgTexture = RED;
                statusText = Component.translatable("hud.capture_progress.enemy_recovering", pointName, data.captureProgress());
            }
            progressWidth = (int) ((double) data.captureProgress() / 100.0 * BAR_WIDTH);
        } else if (data.capturingTeam() != null) {
            // 正在被占领（进度从 0 上升到 100）
            boolean isOurTeam = player.getTeam() != null && player.getTeam().getName().equals(data.capturingTeam());
            if (isOurTeam) {
                bgTexture = GREEN_BG;
                fgTexture = GREEN;
                statusText = Component.translatable("hud.capture_progress.friendly_capturing", pointName, data.captureProgress());
            } else {
                bgTexture = RED_BG;
                fgTexture = RED;
                statusText = Component.translatable("hud.capture_progress.enemy_capturing", pointName, data.captureProgress());
            }
            progressWidth = (int) ((double) data.captureProgress() / 100.0 * BAR_WIDTH);
        } else {
            // 空闲（无人占领）
            bgTexture = YELLOW_BG;
            fgTexture = YELLOW;
            progressWidth = 0;
            statusText = Component.translatable("hud.capture_progress.idle", pointName);
        }

        // 绘制背景条
        guiGraphics.blit(bgTexture, x, BAR_Y, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        // 绘制前景进度
        if (progressWidth > 0) {
            guiGraphics.blit(fgTexture, x, BAR_Y, 0, 0, progressWidth, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        }
        // 绘制文字
        guiGraphics.drawString(mc.font, statusText, x, BAR_Y - 11, 0xFFFFFF, false);
        // 队伍信息
        Component teamInfo = null;
        if (data.capturingTeam() != null) {
            teamInfo = Component.translatable("hud.capture_progress.capturing_team", data.capturingTeam());
        } else if (data.ownerTeam() != null) {
            teamInfo = Component.translatable("hud.capture_progress.owner_team", data.ownerTeam());
        }
        if (teamInfo != null) {
            guiGraphics.drawString(mc.font, teamInfo, x, BAR_Y + 14, 0xCCCCCC, false);
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
