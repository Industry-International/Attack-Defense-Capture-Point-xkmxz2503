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

    /** 用于在子对话框间传递编辑状态 */
    private record AdvancedConfigState(String pointName, Level level,
                                        String owner, double radius,
                                        boolean showRange, int displayColor) {}

    /**
     * 打开据点高级配置对话框，可编辑据点的所有配置项：
     * owner、radius、displayColor、showRange。
     * 颜色选择器通过"更改颜色"按钮进入独立的子对话框，避免主界面臃肿。
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
        buildAdvancedConfigUI(new AdvancedConfigState(
                pointName, level,
                entry.owner() != null ? entry.owner() : "",
                entry.radius(), entry.showRange(), entry.displayColor()));
    }

    /**
     * 构建高级配置主界面（接收状态，便于从颜色选择器返回时恢复输入）。
     */
    private static void buildAdvancedConfigUI(AdvancedConfigState state) {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int scw = win.getGuiScaledWidth();
        int bg = 0xFF1A1A2E;

        // 自适应宽度：屏幕 55%，最大 380px（紧凑尺寸）
        int panelW = Math.min(scw * 55 / 100, 380);
        // 紧凑的固定高度
        int panelH = 195;

        // 可变状态（颜色通过子对话框修改）
        boolean[] currentShowRange = { state.showRange() };
        int[] currentDisplayColor = { state.displayColor() };

        var root = new UIElement()
                .layout(l -> l.width(panelW).height(panelH).paddingAll(10).gapAll(5)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(bg)));

        // ---- 标题行（名称 + 位置同一行） ----
        var headerRow = new UIElement()
                .layout(l -> l.widthPercent(100).heightAuto()
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6));
        var titleLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.title", state.pointName()));
        titleLabel.layout(l -> l.widthAuto().heightAuto());
        titleLabel.textStyle(s -> s.fontSize(11.0f).textColor(0xFFEEEEEE));

        var pos = getManager(state.level()).getPoints().get(state.pointName()).pos();
        var infoLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.info",
                        pos.getX(), pos.getY(), pos.getZ()));
        infoLabel.layout(l -> l.flex(1).heightAuto());
        infoLabel.textStyle(s -> s.fontSize(9.0f).textColor(0xFF999999));
        headerRow.addChildren(titleLabel, infoLabel);
        root.addChildren(headerRow);

        // ---- 分割线 ----
        root.addChildren(new UIElement()
                .layout(l -> l.widthPercent(100).height(1))
                .style(s -> s.backgroundTexture(new ColorRectTexture(0xFF2A2A4A))));

        // ---- Owner 输入（整行） ----
        var ownerLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.owner"));
        ownerLabel.layout(l -> l.widthPercent(100).heightAuto());
        ownerLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        root.addChildren(ownerLabel);

        var ownerField = new TextField();
        ownerField.layout(l -> l.widthPercent(100).height(22));
        ownerField.textFieldStyle(s -> {
            s.textColor(0xFFFFFFFF);
            s.fontSize(12.0f);
        });
        ownerField.setValue(state.owner(), false);
        root.addChildren(ownerField);

        // ---- 第二行：Radius + Show Range 并排 ----
        var row2 = new UIElement()
                .layout(l -> l.widthPercent(100).heightAuto()
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(8));

        var radiusCol = new UIElement()
                .layout(l -> l.flex(1).heightAuto()
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN).gapAll(2));
        var radiusLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.radius"));
        radiusLabel.layout(l -> l.widthPercent(100).heightAuto());
        radiusLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        var radiusField = new TextField();
        radiusField.layout(l -> l.widthPercent(100).height(22));
        radiusField.textFieldStyle(s -> {
            s.textColor(0xFFFFFFFF);
            s.fontSize(12.0f);
        });
        radiusField.setValue(String.valueOf((int) state.radius()), false);
        radiusCol.addChildren(radiusLabel, radiusField);

        var rangeCol = new UIElement()
                .layout(l -> l.flex(1).heightAuto()
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN).gapAll(2));
        var rangeLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.show_range"));
        rangeLabel.layout(l -> l.widthPercent(100).heightAuto());
        rangeLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        var toggleBtn = new Button();
        toggleBtn.layout(l -> l.widthPercent(100).height(22));
        toggleBtn.setOnClick(e -> {
            currentShowRange[0] = !currentShowRange[0];
            updateToggleButtonText(toggleBtn, currentShowRange[0]);
        });
        updateToggleButtonText(toggleBtn, currentShowRange[0]);
        rangeCol.addChildren(rangeLabel, toggleBtn);

        row2.addChildren(radiusCol, rangeCol);
        root.addChildren(row2);

        // ---- 第三行：颜色预览 + 更改按钮 ----
        var row3 = new UIElement()
                .layout(l -> l.widthPercent(100).heightAuto()
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        var colorLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.color"));
        colorLabel.layout(l -> l.widthAuto().heightAuto());
        colorLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));

        var colorPreview = new UIElement()
                .layout(l -> l.width(22).height(22))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(currentDisplayColor[0])));

        var changeColorBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.advanced_config.change_color"));
        changeColorBtn.layout(l -> l.width(110).height(22));
        changeColorBtn.setOnClick(e -> {
            var currentState = new AdvancedConfigState(
                    state.pointName(), state.level(),
                    ownerField.getText(), Double.parseDouble(radiusField.getText()),
                    currentShowRange[0], currentDisplayColor[0]);
            openColorPickerSubDialog(currentState);
        });

        var spacer3 = new UIElement().layout(l -> l.flex(1));
        row3.addChildren(colorLabel, colorPreview, changeColorBtn, spacer3);
        root.addChildren(row3);

        // ---- 弹性间距 ----
        root.addChildren(new UIElement().layout(l -> l.flex(1)));

        // ---- 底部按钮行 ----
        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(26)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(8));

        var saveBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.advanced_config.save"));
        saveBtn.layout(l -> l.flex(1).heightPercent(100));
        saveBtn.setOnClick(e -> {
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
            var manager = getManager(state.level());
            if (manager != null) {
                manager.setPointOwner(state.pointName(), newOwner.isEmpty() ? null : newOwner);
                manager.setPointRadius(state.pointName(), newRadius);
                manager.setPointShowRange(state.pointName(), currentShowRange[0]);
                manager.setPointDisplayColor(state.pointName(), currentDisplayColor[0]);
            }
            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_graph.advanced_config.saved", state.pointName()));
            mc.setScreen(null);
            reopen(state.level());
        });

        var cancelBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.flex(1).heightPercent(100));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            reopen(state.level());
        });

        btnRow.addChildren(saveBtn, cancelBtn);
        root.addChildren(btnRow);

        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.title", state.pointName())));
    }

    /**
     * 颜色选择子对话框 - 独立的弹出窗口，选择后自动返回主界面。
     */
    private static void openColorPickerSubDialog(AdvancedConfigState state) {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int scw = win.getGuiScaledWidth();

        // 自适应尺寸
        int panelW = Math.min(scw * 50 / 100, 300);
        int colorSwatchSize = Math.min((panelW - 24 - 18) / 4, 36);
        int colorGap = 6;
        int colorGridH = 2 * (colorSwatchSize + colorGap);
        int panelH = 30 + colorGridH + 50;

        var root = new UIElement()
                .layout(l -> l.width(panelW).height(panelH).paddingAll(12).gapAll(8)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        var title = new Label().setText(
                Component.translatable("gui.capture_point_block.dialog.color_picker"));
        title.layout(l -> l.widthPercent(100).heightAuto());
        title.textStyle(s -> s.fontSize(11.0f).textColor(0xFFEEEEEE));
        root.addChildren(title);

        // 当前颜色预览
        var previewRow = new UIElement()
                .layout(l -> l.widthPercent(100).heightAuto()
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6));
        var currentPreview = new UIElement()
                .layout(l -> l.width(24).height(16))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(state.displayColor())));
        var hintLabel = new Label().setText(
                Component.translatable("gui.capture_point_block.dialog.color_picker.hint"));
        hintLabel.layout(l -> l.flex(1).heightAuto());
        hintLabel.textStyle(s -> s.fontSize(9.0f).textColor(0xFF888888));
        previewRow.addChildren(currentPreview, hintLabel);
        root.addChildren(previewRow);

        // 颜色网格
        var grid = new UIElement()
                .layout(l -> l.widthPercent(100).heightAuto()
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(colorGap));
        for (int color : PRESET_COLORS) {
            var swatch = new UIElement()
                    .layout(l -> l.width(colorSwatchSize).height(colorSwatchSize))
                    .style(s -> s.background(Sprites.BORDER)
                            .backgroundTexture(new ColorRectTexture(color)));
            int selectedColor = color;
            swatch.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, ev -> {
                var newState = new AdvancedConfigState(
                        state.pointName(), state.level(),
                        state.owner(), state.radius(),
                        state.showRange(), selectedColor);
                buildAdvancedConfigUI(newState);
            });
            grid.addChildren(swatch);
        }
        root.addChildren(grid);

        var cancelBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.widthPercent(100).height(24));
        cancelBtn.setOnClick(e -> buildAdvancedConfigUI(state));

        root.addChildren(cancelBtn);

        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_block.dialog.color_picker")));
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
