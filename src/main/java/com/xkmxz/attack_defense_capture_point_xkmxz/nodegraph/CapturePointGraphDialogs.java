package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * 节点图操作对话框辅助类 - 提供创建据点/区域的输入对话框。
 * 操作结果通过通知气泡（右侧滑入）展示，不再发送到聊天栏。
 */
public final class CapturePointGraphDialogs {

    private static final int DIALOG_W = 320;
    private static final int DIALOG_H = 160;
    private static final int BG_COLOR = 0xFF1A1A2E;

    private CapturePointGraphDialogs() {}

    // ====== 创建据点 ======

    public static void openCreatePointDialog(Level level) {
        openInputDialog(
                Component.translatable("gui.capture_point_graph.dialog.create_point.title"),
                Component.translatable("gui.capture_point_graph.dialog.create_point.label"),
                "",
                (name) -> {
                    if (name.isEmpty()) {
                        ToastNotification.push(ToastNotification.Type.ERROR,
                                Component.translatable("toast.capture_point.create.empty"));
                        reopen(level);
                        return;
                    }
                    // 直接 Java 调用创建据点
                    var mgr = getManager(level);
                    if (mgr != null && !mgr.getPoints().containsKey(name)) {
                        var player = Minecraft.getInstance().player;
                        var pos = player != null ? player.blockPosition() : net.minecraft.core.BlockPos.ZERO;
                        mgr.addOrUpdatePoint(name, pos);
                    }
                    ToastNotification.push(ToastNotification.Type.SUCCESS,
                            Component.translatable("toast.capture_point.create.success", name));
                    reopen(level);
                },
                level
        );
    }

    // ====== 创建区域 ======

    public static void openCreateZoneDialog(Level level) {
        openInputDialog(
                Component.translatable("gui.capture_point_graph.dialog.create_zone.title"),
                Component.translatable("gui.capture_point_graph.dialog.create_zone.label"),
                "",
                (name) -> {
                    if (name.isEmpty()) {
                        ToastNotification.push(ToastNotification.Type.ERROR,
                                Component.translatable("toast.capture_zone.create.empty"));
                        reopen(level);
                        return;
                    }
                    // 直接 Java 调用创建区域
                    var mgr = getManager(level);
                    if (mgr != null && !mgr.getZones().containsKey(name)) {
                        mgr.createZone(name, null);
                    }
                    ToastNotification.push(ToastNotification.Type.SUCCESS,
                            Component.translatable("toast.capture_zone.create.success", name));
                    reopen(level);
                },
                level
        );
    }

    // ====== 删除确认 ======

    public static void openDeleteConfirmDialog(Level level, String name, boolean isZone) {
        // 安全检查：名称不能为空
        if (name == null || name.isEmpty()) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.translatable("toast.delete.name_missing"));
            reopen(level);
            return;
        }

        var mc = Minecraft.getInstance();

        var root = new UIElement()
                .layout(l -> l.width(DIALOG_W).height(DIALOG_H)
                        .paddingAll(10).gapAll(8))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(BG_COLOR)));

        var titleText = Component.translatable(
                isZone ? "gui.capture_point_graph.dialog.delete_zone.title"
                        : "gui.capture_point_graph.dialog.delete_point.title");
        var title = new Label().setText(titleText);
        title.layout(l -> l.widthPercent(100).heightAuto());

        var msgText = Component.translatable(
                isZone ? "gui.capture_point_graph.dialog.delete_zone.message"
                        : "gui.capture_point_graph.dialog.delete_point.message",
                name);
        var msg = new Label().setText(msgText);
        msg.layout(l -> l.widthPercent(100).heightAuto());

        // 按钮行
        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(30)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .gapAll(8).paddingAll(4));

        // 确认删除
        var confirmBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.confirm"));
        confirmBtn.layout(l -> l.flex(1).heightPercent(100));
        confirmBtn.setOnClick(e -> {
            // 直接 Java 调用删除
            var mgr = getManager(level);
            if (mgr != null) {
                if (isZone) {
                    mgr.removeZone(name);
                } else {
                    mgr.removePoint(name);
                }
            }
            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable(
                            isZone ? "toast.capture_zone.delete.success" : "toast.capture_point.delete.success",
                            name));
            mc.setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        // 取消
        var cancelBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.flex(1).heightPercent(100));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        btnRow.addChildren(confirmBtn, cancelBtn);

        var spacer = new UIElement().layout(l -> l.flex(1));
        root.addChildren(title, msg, spacer, btnRow);

        var ui = ModularUI.of(UI.of(root));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_graph.dialog.title")));
    }

    // ====== 高级配置对话框 ======

    /**
     * 打开据点高级配置对话框，可编辑据点的所有配置项：
     * owner、radius、displayColor、showRange。
     */
    public static void openAdvancedConfigDialog(Level level, String pointName) {
        var mgr = getManager(level);
        if (mgr == null) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.literal("Cannot access server data"));
            return;
        }
        var entry = mgr.getPoints().get(pointName);
        if (entry == null) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.translatable("toast.capture_point_block.point_not_found", pointName));
            return;
        }

        var mc = Minecraft.getInstance();
        int bg = 0xFF1A1A2E;

        // 可变状态（用于内部追踪用户修改）
        String[] currentOwner = { entry.owner() != null ? entry.owner() : "" };
        double[] currentRadius = { entry.radius() };
        boolean[] currentShowRange = { entry.showRange() };
        int[] currentDisplayColor = { entry.displayColor() };

        // 计算对话框高度
        int panelW = 360;
        int colorSwatchSize = 40;
        int colorGap = 6;
        int colorCols = 4;
        int colorRows = (int) Math.ceil(8.0 / colorCols);
        int colorGridH = colorRows * (colorSwatchSize + colorGap);
        int panelH = 30 + 40 + 30 + 30 + 30 + 30 + colorGridH + 40;

        var root = new UIElement()
                .layout(l -> l.width(panelW).height(panelH).paddingAll(12).gapAll(6)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(bg)));

        // ---- 标题 ----
        var titleLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.title", pointName));
        titleLabel.layout(l -> l.widthPercent(100).heightAuto());
        titleLabel.textStyle(s -> s.fontSize(12.0f).textColor(0xFFEEEEEE));
        root.addChildren(titleLabel);

        // ---- 只读信息：名称 + 位置 ----
        BlockPos pos = entry.pos();
        var infoLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.info",
                        pointName, pos.getX(), pos.getY(), pos.getZ()));
        infoLabel.layout(l -> l.widthPercent(100).heightAuto());
        infoLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFAAAAAA));
        root.addChildren(infoLabel);

        root.addChildren(new UIElement().layout(l -> l.height(4))); // 间距

        // ---- Owner 输入 ----
        var ownerLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.owner"));
        ownerLabel.layout(l -> l.widthPercent(100).heightAuto());
        ownerLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        root.addChildren(ownerLabel);

        var ownerField = new TextField();
        ownerField.layout(l -> l.widthPercent(100).height(24));
        ownerField.textFieldStyle(s -> {
            s.textColor(0xFFFFFFFF);
            s.fontSize(13.0f);
        });
        ownerField.setValue(currentOwner[0], false);
        root.addChildren(ownerField);

        // ---- Radius 输入 ----
        var radiusLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.radius"));
        radiusLabel.layout(l -> l.widthPercent(100).heightAuto());
        radiusLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        root.addChildren(radiusLabel);

        var radiusField = new TextField();
        radiusField.layout(l -> l.width(120).height(24));
        radiusField.textFieldStyle(s -> {
            s.textColor(0xFFFFFFFF);
            s.fontSize(13.0f);
        });
        radiusField.setValue(String.valueOf((int) currentRadius[0]), false);
        root.addChildren(radiusField);

        // ---- Show Range 切换按钮 ----
        var showRangeLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.show_range"));
        showRangeLabel.layout(l -> l.widthPercent(100).heightAuto());
        showRangeLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        root.addChildren(showRangeLabel);

        var toggleBtn = new Button();
        toggleBtn.layout(l -> l.width(100).height(24));
        toggleBtn.setOnClick(e -> {
            currentShowRange[0] = !currentShowRange[0];
            updateToggleButtonText(toggleBtn, currentShowRange[0]);
        });
        updateToggleButtonText(toggleBtn, currentShowRange[0]);
        root.addChildren(toggleBtn);

        // ---- 颜色选择器 ----
        var colorLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.color"));
        colorLabel.layout(l -> l.widthPercent(100).heightAuto());
        colorLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        root.addChildren(colorLabel);

        // 当前选中的颜色预览
        var colorPreview = new UIElement()
                .layout(l -> l.width(60).height(20))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(currentDisplayColor[0])));
        root.addChildren(colorPreview);

        // 颜色网格
        var grid = new UIElement()
                .layout(l -> l.widthPercent(100).heightAuto()
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(colorGap));
        grid.style(s -> s.backgroundTexture(new ColorRectTexture(0x00000000)));

        for (int color : PRESET_COLORS) {
            var swatch = new UIElement()
                    .layout(l -> l.width(colorSwatchSize).height(colorSwatchSize))
                    .style(s -> s.background(Sprites.BORDER)
                            .backgroundTexture(new ColorRectTexture(color)));
            int selectedColor = color;
            swatch.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, ev -> {
                currentDisplayColor[0] = selectedColor;
                // 更新预览色块
                colorPreview.style(s -> s.backgroundTexture(new ColorRectTexture(selectedColor)));
            });
            grid.addChildren(swatch);
        }
        root.addChildren(grid);

        // ---- 间距 ----
        root.addChildren(new UIElement().layout(l -> l.flex(1)));

        // ---- 底部按钮行 ----
        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(30)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(8));

        // 保存按钮
        var saveBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.advanced_config.save"));
        saveBtn.layout(l -> l.flex(1).heightPercent(100));
        saveBtn.setOnClick(e -> {
            // 收集用户输入
            String newOwner = ownerField.getText().trim();
            double newRadius;
            try {
                newRadius = Double.parseDouble(radiusField.getText().trim());
                if (newRadius < 1 || newRadius > 100) {
                    ToastNotification.push(ToastNotification.Type.ERROR,
                            Component.translatable("toast.capture_point_block.radius_invalid"));
                    return;
                }
            } catch (NumberFormatException ex) {
                ToastNotification.push(ToastNotification.Type.ERROR,
                        Component.translatable("toast.capture_point_block.radius_invalid"));
                return;
            }

            // 批量写回 CaptureManager
            var manager = getManager(level);
            if (manager != null) {
                manager.setPointOwner(pointName, newOwner.isEmpty() ? null : newOwner);
                manager.setPointRadius(pointName, newRadius);
                manager.setPointShowRange(pointName, currentShowRange[0]);
                manager.setPointDisplayColor(pointName, currentDisplayColor[0]);
            }

            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_graph.advanced_config.saved", pointName));
            mc.setScreen(null);
            reopen(level);
        });

        // 取消按钮
        var cancelBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.flex(1).heightPercent(100));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            reopen(level);
        });

        btnRow.addChildren(saveBtn, cancelBtn);
        root.addChildren(btnRow);

        // 居中容器
        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.title", pointName)));
    }

    /**
     * 更新切换按钮的显示文本。
     */
    private static void updateToggleButtonText(Button btn, boolean enabled) {
        btn.setText(Component.translatable(
                enabled ? "gui.capture_point_graph.dialog.advanced_config.toggle.on"
                        : "gui.capture_point_graph.dialog.advanced_config.toggle.off"));
    }

    /** 预设颜色列表（与 CapturePointBlockEntity 保持一致） */
    private static final int[] PRESET_COLORS = {
            0xFFFF4444, // 红
            0xFFFF9800, // 橙
            0xFFFFEB3B, // 黄
            0xFF4CAF50, // 绿
            0xFF2196F3, // 蓝
            0xFF9C27B0, // 紫
            0xFFFFFFFF, // 白
            0xFF000000  // 黑
    };

    // ====== 内部工具 ======

    private static void openInputDialog(
            Component titleText,
            Component labelText,
            String defaultValue,
            java.util.function.Consumer<String> onConfirm,
            Level level
    ) {
        var mc = Minecraft.getInstance();
        var root = new UIElement()
                .layout(l -> l.width(DIALOG_W).height(DIALOG_H)
                        .paddingAll(10).gapAll(8))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(BG_COLOR)));

        var title = new Label().setText(titleText);
        title.layout(l -> l.widthPercent(100).heightAuto());

        var label = new Label().setText(labelText);
        label.layout(l -> l.widthPercent(100).heightAuto());

        // 文本输入框
        var textField = new TextField();
        textField.layout(l -> l.widthPercent(100).height(28));
        textField.textFieldStyle(s -> {
            s.textColor(0xFFFFFFFF);
            s.fontSize(14.0f);
        });
        textField.setValue(defaultValue, false);

        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(30)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .gapAll(8).paddingAll(4));

        // 确认
        var confirmBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.confirm"));
        confirmBtn.layout(l -> l.flex(1).heightPercent(100));
        confirmBtn.setOnClick(e -> {
            var inputText = textField.getText().trim();
            onConfirm.accept(inputText);
        });

        // 取消
        var cancelBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.flex(1).heightPercent(100));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        btnRow.addChildren(confirmBtn, cancelBtn);
        var spacer = new UIElement().layout(l -> l.flex(1));
        root.addChildren(title, label, textField, spacer, btnRow);

        var ui = ModularUI.of(UI.of(root));
        mc.setScreen(new ModularUIScreen(ui, titleText));
    }

    /**
     * 重新打开节点图屏幕。
     */
    private static void reopen(Level level) {
        Minecraft.getInstance().setScreen(null);
        new CapturePointGraphScreen(level).open();
    }

    /**
     * 获取服务端 CaptureManager（单机可用，服务端回退到命令）。
     */
    @org.jetbrains.annotations.Nullable
    private static com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager getManager(Level level) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            return com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager.get(sl);
        }
        var mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager.get(
                    mc.getSingleplayerServer().getLevel(level.dimension()));
        }
        return null;
    }
}
