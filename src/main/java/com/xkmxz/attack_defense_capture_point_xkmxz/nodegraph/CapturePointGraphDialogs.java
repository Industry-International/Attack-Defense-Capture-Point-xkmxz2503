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
import net.minecraft.client.Minecraft;
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
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.connection.sendCommand("capturepoint create " + name);
                        ToastNotification.push(ToastNotification.Type.SUCCESS,
                                Component.translatable("toast.capture_point.create.success", name));
                    }
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
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.connection.sendCommand("capturepoint zone create " + name);
                        ToastNotification.push(ToastNotification.Type.SUCCESS,
                                Component.translatable("toast.capture_zone.create.success", name));
                    }
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
            var player = mc.player;
            if (player != null) {
                if (isZone) {
                    player.connection.sendCommand("capturepoint zone remove " + name);
                } else {
                    player.connection.sendCommand("capturepoint remove " + name);
                }
                ToastNotification.push(ToastNotification.Type.SUCCESS,
                        Component.translatable(
                                isZone ? "toast.capture_zone.delete.success" : "toast.capture_point.delete.success",
                                name));
            }
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
}
