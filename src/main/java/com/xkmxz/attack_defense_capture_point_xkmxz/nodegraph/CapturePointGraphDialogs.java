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
import com.xkmxz.attack_defense_capture_point_xkmxz.block.entity.CapturePointBlockEntity;
import com.xkmxz.attack_defense_capture_point_xkmxz.gui.CapturePointTheme;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.ICaptureDataAccess;
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

    // ====== 创建判断器 ======

    public static void openCreateDecisionDialog(Level level) {
        openInputDialog(
                Component.translatable("gui.capture_point_graph.dialog.create_decision.title"),
                Component.translatable("gui.capture_point_graph.dialog.create_decision.label"),
                "",
                (name) -> {
                    if (name.isEmpty()) {
                        ToastNotification.push(ToastNotification.Type.ERROR,
                                Component.translatable("toast.capture_decision.create.empty"));
                        reopen(level);
                        return;
                    }
                    // 判断器节点不需要写入 CaptureManager，
                    // 它只在节点图中存在，保存时通过 buildSnapshotFromGraph 处理条件路由
                    ToastNotification.push(ToastNotification.Type.SUCCESS,
                            Component.translatable("toast.capture_decision.create.success", name));
                    reopen(level);
                },
                level
        );
    }

    // ====== 删除确认 ======

    /**
     * 打开删除确认对话框。
     * @param level 世界
     * @param name 节点名称
     * @param type 节点类型: "point" / "zone" / "decision" / "condition" / "gate" / "action" / "constant"
     */
    public static void openDeleteConfirmDialog(Level level, String name, String type) {
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
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        String titleLangKey;
        String msgLangKey;
        String toastLangKey;

        if ("zone".equals(type)) {
            titleLangKey = "gui.capture_point_graph.dialog.delete_zone.title";
            msgLangKey = "gui.capture_point_graph.dialog.delete_zone.message";
            toastLangKey = "toast.capture_zone.delete.success";
        } else if ("decision".equals(type)) {
            titleLangKey = "gui.capture_point_graph.dialog.delete_decision.title";
            msgLangKey = "gui.capture_point_graph.dialog.delete_decision.message";
            toastLangKey = "toast.capture_decision.delete.success";
        } else if ("condition".equals(type) || "gate".equals(type) || "action".equals(type) || "constant".equals(type)) {
            titleLangKey = "gui.capture_point_graph.dialog.delete_decision.title";
            msgLangKey = "gui.capture_point_graph.dialog.delete_decision.message";
            toastLangKey = "toast.capture_decision.delete.success";
        } else {
            titleLangKey = "gui.capture_point_graph.dialog.delete_point.title";
            msgLangKey = "gui.capture_point_graph.dialog.delete_point.message";
            toastLangKey = "toast.capture_point.delete.success";
        }

        var titleText = Component.translatable(titleLangKey);
        var title = new Label().setText(titleText);
        title.layout(l -> l.widthPercent(100).heightAuto());

        var msgText = Component.translatable(msgLangKey, name);
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
            var access = getManager(level);
            if (access != null) {
                if ("zone".equals(type)) {
                    access.removeZone(name);
                } else if ("point".equals(type)) {
                    access.removePoint(name);
                } else {
                    var captureManager = getRawManager(level);
                    if (captureManager != null) {
                        deleteLogicNode(captureManager, name);
                    }
                }
            }
            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable(toastLangKey, name));
            reopen(level);
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

    private static void deleteLogicNode(CaptureManager mgr, String name) {
        var layouts = new java.util.LinkedHashMap<>(mgr.getNodeLayouts());
        var decisions = new java.util.LinkedHashMap<>(mgr.getDecisionNodes());
        var nodeOpts = new java.util.LinkedHashMap<>(mgr.getNodeOptions());
        var wires = new java.util.ArrayList<>(mgr.getGraphWires());

        layouts.remove(name);
        decisions.remove(name);
        nodeOpts.remove(name);
        wires.removeIf(w -> name.equals(w.fromNode()) || name.equals(w.toNode()));

        mgr.applyGraphSnapshotWithLayout(
                new java.util.LinkedHashMap<>(mgr.getPoints()),
                new java.util.LinkedHashMap<>(mgr.getZones()),
                layouts,
                decisions,
                nodeOpts,
                wires,
                mgr.getViewState()
        );
    }

    @org.jetbrains.annotations.Nullable
    private static CaptureManager getRawManager(Level level) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            return CaptureManager.get(sl);
        }
        var mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            var sl = mc.getSingleplayerServer().getLevel(level.dimension());
            if (sl != null) return CaptureManager.get(sl);
        }
        return null;
    }

    // ====== 高级配置对话框 ======

    /** 用于在子对话框间传递编辑状态 */
    private record AdvancedConfigState(String pointName, Level level,
                                        boolean captured, double radius,
                                        boolean showRange, int displayColor) {}

    /**
     * 打开据点高级配置对话框，可编辑据点的所有配置项：
     * captured、radius、displayColor、showRange。
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
                entry.captured(),
                entry.radius(), entry.showRange(), entry.displayColor()));
    }

    /**
     * 构建高级配置主界面 - 严格排版，所有高度显式分配，间距充足。
     */
    private static void buildAdvancedConfigUI(AdvancedConfigState state) {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int scw = win.getGuiScaledWidth();
        int bg = 0xFF1A1A2E;
        int pad = 10;

        // 自适应宽度：屏幕 55%，最大 380px
        int panelW = Math.min(scw * 55 / 100, 380);

        // === 严格高度计算 ===
        int hHeader   = 16;   // 标题行
        int hInfo     = 14;   // 位置信息行
        int hSep      = 1;    // 分割线
        int hLabel    = 14;   // 标签行高 (fontSize 10)
        int hField    = 24;   // 输入框/按钮高
        int hRow2     = hLabel + hField; // Radius+Range 两列共用 (38)
        int hColorRow = 24;   // 颜色预览行
        int hBottom   = 28;   // 底部按钮行

        int gapBig    = 8;    // 段落间大间距
        int gapSmall  = 4;    // 标签与控件间小间距

        // panelH = pad + hHeader + gapSmall + hInfo + gapSmall + hSep + gapBig
        //          + hLabel + gapSmall + hField + gapBig + hRow2 + gapBig
        //          + hColorRow + gapBig + hBottom + pad
        int panelH = pad + hHeader + gapSmall + hInfo + gapSmall + hSep + gapBig
                     + hLabel + gapSmall + hField + gapBig + hRow2 + gapBig
                     + hColorRow + gapBig + hBottom + pad; // = 241

        // 可变状态（颜色通过子对话框修改）
        boolean[] currentCaptured = { state.captured() };
        boolean[] currentShowRange = { state.showRange() };
        int[] currentDisplayColor = { state.displayColor() };

        var root = new UIElement()
                .layout(l -> l.width(panelW).height(panelH).paddingAll(pad)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(bg)));

        // ---- 标题行 ----
        var titleLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.title", state.pointName()));
        titleLabel.layout(l -> l.widthPercent(100).height(hHeader));
        titleLabel.textStyle(s -> s.fontSize(11.0f).textColor(0xFFEEEEEE));
        root.addChildren(titleLabel);

        // 间距
        root.addChildren(new UIElement().layout(l -> l.widthPercent(100).height(gapSmall)));

        // ---- 位置信息行 ----
        var pos = getManager(state.level()).getPoints().get(state.pointName()).pos();
        var infoLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.info",
                        pos.getX(), pos.getY(), pos.getZ()));
        infoLabel.layout(l -> l.widthPercent(100).height(hInfo));
        infoLabel.textStyle(s -> s.fontSize(9.0f).textColor(0xFF999999));
        root.addChildren(infoLabel);

        // 间距
        root.addChildren(new UIElement().layout(l -> l.widthPercent(100).height(gapSmall)));

        // ---- 分割线 ----
        root.addChildren(new UIElement()
                .layout(l -> l.widthPercent(100).height(hSep))
                .style(s -> s.backgroundTexture(new ColorRectTexture(0xFF2A2A4A))));

        // 间距
        root.addChildren(new UIElement().layout(l -> l.widthPercent(100).height(gapBig)));

        // ---- Captured 状态 ----
        var capturedLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.captured"));
        capturedLabel.layout(l -> l.widthPercent(100).height(hLabel));
        capturedLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        root.addChildren(capturedLabel);

        var capturedBtn = new Button();
        capturedBtn.layout(l -> l.widthPercent(100).height(hField));
        capturedBtn.setOnClick(e -> {
            currentCaptured[0] = !currentCaptured[0];
            updateToggleButtonText(capturedBtn, currentCaptured[0]);
        });
        updateToggleButtonText(capturedBtn, currentCaptured[0]);
        root.addChildren(capturedBtn);

        // 间距
        root.addChildren(new UIElement().layout(l -> l.widthPercent(100).height(gapBig)));

        // ---- 第二行：Radius + Show Range 并排 ----
        var row2 = new UIElement()
                .layout(l -> l.widthPercent(100).height(hRow2)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(8));

        var radiusCol = new UIElement()
                .layout(l -> l.flex(1).heightPercent(100)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN));
        var radiusLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.radius"));
        radiusLabel.layout(l -> l.widthPercent(100).flex(1));
        radiusLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        var radiusField = new TextField();
        radiusField.layout(l -> l.widthPercent(100).flex(1));
        radiusField.textFieldStyle(s -> {
            s.textColor(0xFFFFFFFF);
            s.fontSize(12.0f);
        });
        radiusField.setValue(String.valueOf((int) state.radius()), false);
        radiusCol.addChildren(radiusLabel, radiusField);

        var rangeCol = new UIElement()
                .layout(l -> l.flex(1).heightPercent(100)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN));
        var rangeLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.show_range"));
        rangeLabel.layout(l -> l.widthPercent(100).flex(1));
        rangeLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        var toggleBtn = new Button();
        toggleBtn.layout(l -> l.widthPercent(100).flex(1));
        toggleBtn.setOnClick(e -> {
            currentShowRange[0] = !currentShowRange[0];
            updateToggleButtonText(toggleBtn, currentShowRange[0]);
        });
        updateToggleButtonText(toggleBtn, currentShowRange[0]);
        rangeCol.addChildren(rangeLabel, toggleBtn);

        row2.addChildren(radiusCol, rangeCol);
        root.addChildren(row2);

        // 间距
        root.addChildren(new UIElement().layout(l -> l.widthPercent(100).height(gapBig)));

        // ---- 第三行：颜色/色块/更改按钮（全部显式尺寸，无任何 auto） ----
        var row3 = new UIElement()
                .layout(l -> l.widthPercent(100.0f).height((float) hColorRow)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .gapAll(6.0f)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        var colorLabel = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.advanced_config.color"));
        colorLabel.layout(l -> l.width(85.0f).height((float) hColorRow));
        colorLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        var colorPreview = new UIElement()
                .layout(l -> l.width((float) hColorRow).height((float) hColorRow))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(currentDisplayColor[0])));
        var changeColorBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.advanced_config.change_color"));
        changeColorBtn.layout(l -> l.width(100.0f).height((float) hColorRow));
        changeColorBtn.setOnClick(e -> {
            var currentState = new AdvancedConfigState(
                    state.pointName(), state.level(),
                    currentCaptured[0], Double.parseDouble(radiusField.getText()),
                    currentShowRange[0], currentDisplayColor[0]);
            openColorPickerSubDialog(currentState);
        });
        var flexSpacer = new UIElement().layout(l -> l.flex(1.0f));
        row3.addChildren(colorLabel, colorPreview, flexSpacer, changeColorBtn);
        root.addChildren(row3);

        // 间距
        root.addChildren(new UIElement().layout(l -> l.widthPercent(100).height(gapBig)));

        // ---- 底部按钮行 ----
        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(hBottom)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(8));

        var saveBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.advanced_config.save"));
        saveBtn.layout(l -> l.flex(1).heightPercent(100));
        saveBtn.setOnClick(e -> {
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
                manager.setPointCaptured(state.pointName(), currentCaptured[0]);
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
     * 颜色选择子对话框 - 全部显式 float 尺寸，无 auto。
     */
    private static void openColorPickerSubDialog(AdvancedConfigState state) {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int scw = win.getGuiScaledWidth();
        float pad = 12.0f;

        // 自适应尺寸（计算用 int，布局用 float）
        float panelW = Math.min(scw * 50 / 100, 300);
        float swatchSize = Math.min((panelW - 24 - 18) / 4, 36);
        float gapS = 6.0f;
        float gridH = 2.0f * (swatchSize + gapS);

        float hTitle = 16.0f;
        float hPreview = 18.0f;
        float hCancel = 24.0f;
        float gapBig = 8.0f;
        float gapSmall = 6.0f;

        float panelH = pad + hTitle + gapBig + hPreview + gapSmall + gridH + gapSmall + hCancel + pad;

        var root = new UIElement()
                .layout(l -> l.width(panelW).height(panelH).paddingAll(pad)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        // ---- 标题 ----
        var title = new Label().setText(
                Component.translatable("gui.capture_point_block.dialog.color_picker"));
        title.layout(l -> l.widthPercent(100.0f).height(hTitle));
        title.textStyle(s -> s.fontSize(11.0f).textColor(0xFFEEEEEE));
        root.addChildren(title);

        // ---- 当前颜色预览行 ----
        var previewRow = new UIElement()
                .layout(l -> l.widthPercent(100.0f).height(hPreview)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .gapAll(gapSmall)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        var currentPreview = new UIElement()
                .layout(l -> l.width(24.0f).height(hPreview))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(state.displayColor())));
        var hintLabel = new Label().setText(
                Component.translatable("gui.capture_point_block.dialog.color_picker.hint"));
        hintLabel.layout(l -> l.width(180.0f).height(hPreview));
        hintLabel.textStyle(s -> s.fontSize(9.0f).textColor(0xFF888888));
        var previewSpacer = new UIElement().layout(l -> l.flex(1.0f));
        previewRow.addChildren(currentPreview, previewSpacer, hintLabel);
        root.addChildren(previewRow);

        // ---- 颜色网格 ----
        var grid = new UIElement()
                .layout(l -> l.widthPercent(100.0f).height(gridH)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP)
                        .gapAll(gapS));
        for (int color : PRESET_COLORS) {
            var swatch = new UIElement()
                    .layout(l -> l.width(swatchSize).height(swatchSize))
                    .style(s -> s.background(Sprites.BORDER)
                            .backgroundTexture(new ColorRectTexture(color)));
            int selectedColor = color;
            swatch.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, ev -> {
                var newState = new AdvancedConfigState(
                        state.pointName(), state.level(),
                        state.captured(), state.radius(),
                        state.showRange(), selectedColor);
                buildAdvancedConfigUI(newState);
            });
            grid.addChildren(swatch);
        }
        root.addChildren(grid);

        // ---- 取消按钮 ----
        var cancelBtn = new Button()
                .setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.widthPercent(100.0f).height(hCancel));
        cancelBtn.setOnClick(e -> buildAdvancedConfigUI(state));
        root.addChildren(cancelBtn);

        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100.0f).heightPercent(100.0f).paddingAll(0).gapAll(0)
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



    // ====== 编辑模式：编辑属性对话框 ======

    /**
     * 在编辑模式下打开节点属性编辑对话框。
     * 据点节点可编辑：name、owner、radius、displayColor、showRange
     * 区域节点可编辑：name、requiredZone、edit_points、description
     * 所有变更直接通过 CaptureManager Java API 回写。
     */
    public static void openEditPropertiesDialog(Level level, String nodeName, String nodeType) {
        var mc = Minecraft.getInstance();
        var mgr = getManager(level);
        if (mgr == null) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.literal("Cannot access server data"));
            return;
        }

        int panelW = 340;

        if ("point".equals(nodeType)) {
            // === 据点节点编辑 ===
            var entry = mgr.getPoints().get(nodeName);
            if (entry == null) {
                ToastNotification.push(ToastNotification.Type.ERROR,
                        Component.translatable("toast.capture_point_block.point_not_found", nodeName));
                return;
            }

            int pad = 10;
            int hTitle = 16;
            int hSep = 1;
            int hLabel = 12;
            int hField = 24;
            int hBtn = 28;
            int gapBig = 6;
            int gapSmall = 3;

            // 行数：title + sep + (name/owner/radius) 3行 + colorRow + (showRange/position) 2行 + btnRow
            int panelH = pad + hTitle + gapSmall + hSep + gapBig
                    + 5 * (hLabel + gapSmall + hField + gapBig)
                    + hBtn + pad;

            boolean[] currentShowRange = { entry.showRange() };
            int[] currentColor = { entry.displayColor() };

            var root = new UIElement()
                    .layout(l -> l.width(panelW).height(panelH).paddingAll(pad)
                            .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                    .style(s -> s.background(Sprites.BORDER)
                            .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

            // 标题
            var title = new Label().setText(
                    Component.translatable("gui.capture_point_graph.dialog.edit_properties.point", nodeName));
            title.layout(l -> l.widthPercent(100).height(hTitle));
            title.textStyle(s -> s.fontSize(11.0f).textColor(0xFFEEEEEE));
            root.addChildren(title);

            // 分割线
            root.addChildren(new UIElement().layout(l -> l.widthPercent(100).height(hSep))
                    .style(s -> s.backgroundTexture(new ColorRectTexture(0xFF2A2A4A))));

            // --- Name ---
            root.addChildren(fieldLabel("gui.capture_point_graph.dialog.edit_properties.name", hLabel));
            var nameField = new TextField();
            nameField.layout(l -> l.widthPercent(100).height(hField));
            nameField.textFieldStyle(s -> { s.textColor(0xFFFFFFFF); s.fontSize(12.0f); });
            nameField.setValue(nodeName, false);
            root.addChildren(nameField);

            // --- Captured ---
            root.addChildren(fieldLabel("gui.capture_point_graph.dialog.advanced_config.captured", hLabel));
            boolean[] currentCaptured = { entry.captured() };
            var capturedBtn = new Button();
            capturedBtn.layout(l -> l.widthPercent(100).height(hField));
            capturedBtn.setOnClick(e -> {
                currentCaptured[0] = !currentCaptured[0];
                updateToggleText(capturedBtn, currentCaptured[0]);
            });
            updateToggleText(capturedBtn, currentCaptured[0]);
            root.addChildren(capturedBtn);

            // --- Radius ---
            root.addChildren(fieldLabel("gui.capture_point_graph.dialog.advanced_config.radius", hLabel));
            var radiusField = new TextField();
            radiusField.layout(l -> l.widthPercent(100).height(hField));
            radiusField.textFieldStyle(s -> { s.textColor(0xFFFFFFFF); s.fontSize(12.0f); });
            radiusField.setValue(String.valueOf((int) entry.radius()), false);
            root.addChildren(radiusField);

            // --- Color Row ---
            var colorLabel = new Label().setText(
                    Component.translatable("gui.capture_point_graph.dialog.advanced_config.color"));
            colorLabel.layout(l -> l.widthPercent(100).height(hLabel));
            colorLabel.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
            root.addChildren(colorLabel);

            var colorRow = new UIElement()
                    .layout(l -> l.widthPercent(100).height(hField)
                            .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6)
                            .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
            var colorPreview = new UIElement()
                    .layout(l -> l.width(hField).height(hField))
                    .style(s -> s.background(Sprites.BORDER)
                            .backgroundTexture(new ColorRectTexture(currentColor[0])));
            var changeColorBtn = new Button()
                    .setText(Component.translatable("gui.capture_point_graph.dialog.advanced_config.change_color"));
            changeColorBtn.layout(l -> l.width(120).height(hField));
            changeColorBtn.setOnClick(e -> {
                var newColor = openColorPickerInline(mc, currentColor[0]);
                currentColor[0] = newColor;
                colorPreview.style(s -> s.backgroundTexture(new ColorRectTexture(newColor)));
            });
            colorRow.addChildren(colorPreview, changeColorBtn);
            root.addChildren(colorRow);

            // --- Show Range Toggle ---
            root.addChildren(fieldLabel("gui.capture_point_graph.dialog.advanced_config.show_range", hLabel));
            var toggleBtn = new Button();
            toggleBtn.layout(l -> l.widthPercent(100).height(hField));
            toggleBtn.setOnClick(e -> {
                currentShowRange[0] = !currentShowRange[0];
                updateToggleText(toggleBtn, currentShowRange[0]);
            });
            updateToggleText(toggleBtn, currentShowRange[0]);
            root.addChildren(toggleBtn);

            // --- Bottom Buttons ---
            var btnRow = new UIElement()
                    .layout(l -> l.widthPercent(100).height(hBtn)
                            .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6));
            var saveBtn = new Button()
                    .setText(Component.translatable("gui.capture_point_graph.dialog.advanced_config.save"));
            saveBtn.layout(l -> l.flex(1).heightPercent(100));
            saveBtn.setOnClick(e -> {
                String newName = nameField.getText().trim();
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

                // 回写 CaptureManager
                var manager = getManager(level);
                if (manager != null) {
                    // 如果名称变了，需要创建新据点并删除旧的
                    if (!newName.equals(nodeName) && !newName.isEmpty()) {
                        // 先创建新名称的据点（复制坐标）
                        if (!manager.getPoints().containsKey(newName)) {
                            manager.addOrUpdatePoint(newName, entry.pos());
                        }
                        // 复制其他属性
                        manager.setPointCaptured(newName, currentCaptured[0]);
                        manager.setPointRadius(newName, newRadius);
                        manager.setPointShowRange(newName, currentShowRange[0]);
                        manager.setPointDisplayColor(newName, currentColor[0]);
                        // 删除旧名称据点
                        manager.removePoint(nodeName);
                    } else {
                        manager.setPointCaptured(nodeName, currentCaptured[0]);
                        manager.setPointRadius(nodeName, newRadius);
                        manager.setPointShowRange(nodeName, currentShowRange[0]);
                        manager.setPointDisplayColor(nodeName, currentColor[0]);
                    }
                }
                ToastNotification.push(ToastNotification.Type.SUCCESS,
                        Component.translatable("toast.capture_point_graph.advanced_config.saved", newName.isEmpty() ? nodeName : newName));
                mc.setScreen(null);
                reopen(level);
            });

            var cancelBtn = new Button()
                    .setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
            cancelBtn.layout(l -> l.flex(1).heightPercent(100));
            cancelBtn.setOnClick(e -> { mc.setScreen(null); reopen(level); });
            btnRow.addChildren(saveBtn, cancelBtn);
            root.addChildren(btnRow);

            var wrap = new UIElement()
                    .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                            .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                            .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
            wrap.addChildren(root);

            var ui = ModularUI.of(UI.of(wrap));
            mc.setScreen(new ModularUIScreen(ui,
                    Component.translatable("gui.capture_point_graph.dialog.edit_properties.point", nodeName)));

        } else if ("zone".equals(nodeType)) {
            // === 区域节点编辑 ===
            var entry = mgr.getZones().get(nodeName);
            if (entry == null) {
                ToastNotification.push(ToastNotification.Type.ERROR,
                        Component.translatable("command.capturepoint.error.zone_not_found", nodeName));
                return;
            }

            int pad = 10;
            int hTitle = 16;
            int hSep = 1;
            int hLabel = 12;
            int hField = 24;
            int hBtn = 28;
            int gapBig = 6;
            int gapSmall = 3;

            int panelH = pad + hTitle + gapSmall + hSep + gapBig
                    + 4 * (hLabel + gapSmall + hField + gapBig)
                    + hBtn + pad;

            var root = new UIElement()
                    .layout(l -> l.width(panelW).height(panelH).paddingAll(pad)
                            .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                    .style(s -> s.background(Sprites.BORDER)
                            .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

            // 标题
            var title = new Label().setText(
                    Component.translatable("gui.capture_point_graph.dialog.edit_properties.zone", nodeName));
            title.layout(l -> l.widthPercent(100).height(hTitle));
            title.textStyle(s -> s.fontSize(11.0f).textColor(0xFFEEEEEE));
            root.addChildren(title);

            root.addChildren(new UIElement().layout(l -> l.widthPercent(100).height(hSep))
                    .style(s -> s.backgroundTexture(new ColorRectTexture(0xFF2A2A4A))));

            // --- Name ---
            root.addChildren(fieldLabel("gui.capture_point_graph.dialog.edit_properties.name", hLabel));
            var nameField = new TextField();
            nameField.layout(l -> l.widthPercent(100).height(hField));
            nameField.textFieldStyle(s -> { s.textColor(0xFFFFFFFF); s.fontSize(12.0f); });
            nameField.setValue(nodeName, false);
            root.addChildren(nameField);

            // --- Required Zone ---
            root.addChildren(fieldLabel("node.capture_zone.option.required_zone", hLabel));
            var reqZoneField = new TextField();
            reqZoneField.layout(l -> l.widthPercent(100).height(hField));
            reqZoneField.textFieldStyle(s -> { s.textColor(0xFFFFFFFF); s.fontSize(12.0f); });
            reqZoneField.setValue(entry.requiredZone() != null ? entry.requiredZone() : "", false);
            root.addChildren(reqZoneField);

            // --- Edit Points (comma separated) ---
            root.addChildren(fieldLabel("node.capture_zone.option.edit_points", hLabel));
            var pointsField = new TextField();
            pointsField.layout(l -> l.widthPercent(100).height(hField));
            pointsField.textFieldStyle(s -> { s.textColor(0xFFFFFFFF); s.fontSize(12.0f); });
            pointsField.setValue(String.join(", ", entry.capturePoints()), false);
            root.addChildren(pointsField);

            // --- Description ---
            root.addChildren(fieldLabel("node.capture_zone.option.description", hLabel));
            var descField = new TextField();
            descField.layout(l -> l.widthPercent(100).height(hField));
            descField.textFieldStyle(s -> { s.textColor(0xFFFFFFFF); s.fontSize(12.0f); });
            descField.setValue("", false);
            root.addChildren(descField);

            // --- Buttons ---
            var btnRow = new UIElement()
                    .layout(l -> l.widthPercent(100).height(hBtn)
                            .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6));
            var saveBtn = new Button()
                    .setText(Component.translatable("gui.capture_point_graph.dialog.advanced_config.save"));
            saveBtn.layout(l -> l.flex(1).heightPercent(100));
            saveBtn.setOnClick(e -> {
                String newName = nameField.getText().trim();
                String reqZone = reqZoneField.getText().trim();
                String pointsStr = pointsField.getText().trim();
                var manager = getManager(level);
                if (manager != null) {
                    if (!newName.equals(nodeName) && !newName.isEmpty()) {
                        // 创建新区域，复制数据
                        if (!manager.getZones().containsKey(newName)) {
                            manager.createZone(newName, null);
                        }
                        // 设置依赖（同步到解锁依赖，确保对话框中配置的依赖关系生效）
                        manager.setZoneRequiredZone(newName, reqZone.isEmpty() ? null : reqZone);
                        if (!reqZone.isEmpty()) {
                            manager.setZoneUnlockDependencies(newName, java.util.List.of(reqZone));
                        }
                        // 添加据点
                        if (!pointsStr.isEmpty()) {
                            for (var pn : pointsStr.split(",")) {
                                pn = pn.trim();
                                if (!pn.isEmpty() && manager.getPoints().containsKey(pn)) {
                                    manager.addPointToZone(newName, pn);
                                }
                            }
                        }
                        // 删除旧区域
                        manager.removeZone(nodeName);
                    } else {
                        // 更新依赖（同步到解锁依赖）
                        manager.setZoneRequiredZone(nodeName, reqZone.isEmpty() ? null : reqZone);
                        if (!reqZone.isEmpty()) {
                            manager.setZoneUnlockDependencies(nodeName, java.util.List.of(reqZone));
                        } else {
                            manager.setZoneUnlockDependencies(nodeName, java.util.Collections.emptyList());
                        }
                        // 先清空再添加据点
                        for (var cpName : new java.util.ArrayList<>(entry.capturePoints())) {
                            manager.removePointFromZone(nodeName, cpName);
                        }
                        if (!pointsStr.isEmpty()) {
                            for (var pn : pointsStr.split(",")) {
                                pn = pn.trim();
                                if (!pn.isEmpty() && manager.getPoints().containsKey(pn)) {
                                    manager.addPointToZone(nodeName, pn);
                                }
                            }
                        }
                    }
                }
                ToastNotification.push(ToastNotification.Type.SUCCESS,
                        Component.translatable("toast.capture_point_graph.advanced_config.saved", newName.isEmpty() ? nodeName : newName));
                mc.setScreen(null);
                reopen(level);
            });

            var cancelBtn = new Button()
                    .setText(Component.translatable("gui.capture_point_graph.dialog.cancel"));
            cancelBtn.layout(l -> l.flex(1).heightPercent(100));
            cancelBtn.setOnClick(e -> { mc.setScreen(null); reopen(level); });
            btnRow.addChildren(saveBtn, cancelBtn);
            root.addChildren(btnRow);

            var wrap = new UIElement()
                    .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                            .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                            .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
            wrap.addChildren(root);

            var ui = ModularUI.of(UI.of(wrap));
            mc.setScreen(new ModularUIScreen(ui,
                    Component.translatable("gui.capture_point_graph.dialog.edit_properties.zone", nodeName)));
            return;
        }

        // 判断器节点：在编辑模式的选项面板中直接配置即可，此处仅提示
        if ("decision".equals(nodeType)) {
            ToastNotification.push(ToastNotification.Type.INFO,
                    Component.translatable("toast.capture_decision.edit_hint"));
            reopen(level);
            return;
        }
    }

    /** 创建一个带有标签文本的标签 UIElement（辅助方法） */
    private static UIElement fieldLabel(String langKey, int height) {
        var label = new Label().setText(Component.translatable(langKey));
        label.layout(l -> l.widthPercent(100).height(height));
        label.textStyle(s -> s.fontSize(10.0f).textColor(0xFFCCCCCC));
        return label;
    }

    /** 更新切换按钮文本 */
    private static void updateToggleText(Button btn, boolean on) {
        btn.setText(Component.translatable(
                on ? "gui.capture_point_graph.dialog.advanced_config.toggle.on"
                        : "gui.capture_point_graph.dialog.advanced_config.toggle.off"));
    }

    /**
     * 简易颜色选择器 inline 弹窗，返回 selectedColor。
     */
    private static int openColorPickerInline(Minecraft mc, int currentColor) {
        // 预设颜色列表
        int[] colors = {
                0xFFFF4444, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50,
                0xFF2196F3, 0xFF9C27B0, 0xFFFFFFFF, 0xFF000000
        };
        int cols = 4;
        float swatchSize = 36;
        float gap = 6;
        float pad = 10;
        float panelW = cols * (swatchSize + gap) + gap + pad * 2;
        float panelH = (float) Math.ceil(colors.length / (float) cols) * (swatchSize + gap) + gap + pad * 2 + 20;

        var root = new UIElement()
                .layout(l -> l.width(panelW).height(panelH).paddingAll(pad))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        var grid = new UIElement()
                .layout(l -> l.widthPercent(100).height(panelH - pad * 2 - 24)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(gap));

        final int[] result = { currentColor };
        for (int color : colors) {
            int fc = color;
            var swatch = new UIElement()
                    .layout(l -> l.width(swatchSize).height(swatchSize))
                    .style(s -> s.background(Sprites.BORDER)
                            .backgroundTexture(new ColorRectTexture(fc)));
            swatch.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, ev -> {
                result[0] = fc;
            });
            grid.addChildren(swatch);
        }
        root.addChildren(grid);

        var hint = new Label().setText(Component.translatable("gui.capture_point_block.dialog.color_picker.hint"));
        hint.layout(l -> l.widthPercent(100).height(18));
        hint.textStyle(s -> s.fontSize(9.0f).textColor(0xFF888888));
        root.addChildren(hint);

        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_block.dialog.color_picker")));

        return result[0];
    }

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
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

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
     * 立即同步所有已加载城池方块实体（从 CaptureManager 获取最新渲染数据）。
     * 在对话框修改 CaptureManager 数据后调用，保证方块及时更新。
     */
    private static void syncBlocks(Level level) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            CapturePointBlockEntity.syncAllBoundBlocks(sl);
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            var sl = mc.getSingleplayerServer().getLevel(level.dimension());
            if (sl != null) {
                CapturePointBlockEntity.syncAllBoundBlocks(sl);
            }
        }
    }

    /**
     * 重新打开节点图屏幕（同步方块后再打开）。
     */
    private static void reopen(Level level) {
        syncBlocks(level);
        Minecraft.getInstance().setScreen(null);
        new CapturePointGraphScreen(level).open();
    }

    /**
     * 获取服务端统一数据访问接口（单机可用，服务端回退到命令）。
     */
    @org.jetbrains.annotations.Nullable
    private static ICaptureDataAccess getManager(Level level) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            return ICaptureDataAccess.server(sl);
        }
        var mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            var sl = mc.getSingleplayerServer().getLevel(level.dimension());
            if (sl != null) return ICaptureDataAccess.server(sl);
        }
        return null;
    }
}
