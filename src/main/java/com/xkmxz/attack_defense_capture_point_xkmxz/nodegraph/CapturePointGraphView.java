package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.INodeWithOptions;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.stream.Stream;

/**
 * 据点管理图视图 - 使用 LDLib2 nodegraphtookit 框架提供节点图编辑体验。
 * 右键菜单功能全部独立于指令调用，直接通过命令与服务端同步。
 */
public class CapturePointGraphView extends GraphView {

    private int pendingViewportResetTicks;
    private Level level;
    private ToastLayer toastLayer;

    public CapturePointGraphView() {
        super();
        hideHeaders();
        hidePanels();
        suppressDefaultItemLibrary();
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setToastLayer(ToastLayer toastLayer) {
        this.toastLayer = toastLayer;
    }

    private void hideHeaders() {
        header.setVisible(false);
        header.setDisplay(false);
        header.layout(l -> l.width(0).height(0));
    }

    private void hidePanels() {
        getPanelLayer().clearAllChildren();
        getPanelLayer().setVisible(false);
        getPanelLayer().setDisplay(false);
    }

    private void suppressDefaultItemLibrary() {
        itemLibrary.setVisible(false);
        itemLibrary.setDisplay(false);
        itemLibrary.setAllowHitTest(false);
        itemLibrary.layout(l -> {
            l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);
            l.left(-100000f);
            l.top(-100000f);
            l.width(0);
            l.height(0);
        });
    }

    @Override
    public CapturePointGraphView loadGraph(Graph graph) {
        super.loadGraph(graph);
        suppressDefaultItemLibrary();
        pendingViewportResetTicks = 1;
        return this;
    }

    @Override
    public void screenTick() {
        hideDefaultItemLibraryIfDisplayed();
        super.screenTick();
        hideDefaultItemLibraryIfDisplayed();

        // 驱动通知气泡动画
        if (toastLayer != null) {
            toastLayer.tick();
        }

        if (pendingViewportResetTicks > 0) {
            if (graphView.getContentWidth() > 0 && graphView.getContentHeight() > 0) {
                pendingViewportResetTicks--;
                if (pendingViewportResetTicks == 0) {
                    resetViewportTransform();
                }
            }
        }
    }

    private void hideDefaultItemLibraryIfDisplayed() {
        if (itemLibrary.isDisplayed()) {
            itemLibrary.hide();
        }
        suppressDefaultItemLibrary();
    }

    private void resetViewportTransform() {
        graphView.fit(0, 0, graphView.getContentWidth(), graphView.getContentHeight(), 1f);
    }

    // ---- 右键菜单 ----

    @Override
    protected TreeBuilder.Menu createMenu(float screenX, float screenY) {
        var menu = TreeBuilder.Menu.start();

        // 创建据点
        menu.leaf(
                Component.translatable("gui.capture_point_graph.menu.create_point").getString(),
                () -> {
                    if (level != null) {
                        CapturePointGraphDialogs.openCreatePointDialog(level);
                    }
                });

        // 创建区域
        menu.leaf(
                Component.translatable("gui.capture_point_graph.menu.create_zone").getString(),
                () -> {
                    if (level != null) {
                        CapturePointGraphDialogs.openCreateZoneDialog(level);
                    }
                });

        // 如果有选中的节点，添加删除选项
        var selectedModels = getSelectedModels();
        if (!selectedModels.isEmpty()) {
            menu.leaf(
                    Component.translatable("gui.capture_point_graph.menu.delete").getString(),
                    () -> {
                        if (level != null) {
                            // 逐个处理选中的节点
                            for (var model : selectedModels) {
                                String name = model.getName();
                                boolean isZone = isZoneModel(model);
                                CapturePointGraphDialogs.openDeleteConfirmDialog(level, name, isZone);
                                // 注意：只处理第一个选中项，避免多个弹窗
                                break;
                            }
                        }
                    });
        }

        // 查看状态
        menu.leaf(Component.translatable("gui.capture_point_graph.menu.list_status").getString(), () -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.connection.sendCommand("capturepoint list");
            }
        });

        // 刷新
        menu.leaf(Component.translatable("gui.capture_point_graph.menu.refresh").getString(), () -> {
            var mc = Minecraft.getInstance();
            if (level != null) {
                mc.setScreen(null);
                new CapturePointGraphScreen(level).open();
            }
        });

        // 关闭
        menu.leaf(Component.translatable("gui.capture_point_graph.menu.close").getString(), () -> {
            Minecraft.getInstance().setScreen(null);
        });

        return menu;
    }

    /**
     * 获取选中的节点模型列表（只包含 AbstractNodeModel）。
     */
    private List<AbstractNodeModel> getSelectedModels() {
        return getSelected().stream()
                .filter(m -> m instanceof AbstractNodeModel)
                .map(m -> (AbstractNodeModel) m)
                .toList();
    }

    /**
     * 判断节点模型是否为区域节点（通过检查是否有 "captured" 选项）。
     * 据点节点有 "owner" 选项，区域节点有 "captured" 选项。
     */
    private boolean isZoneModel(AbstractNodeModel model) {
        if (model instanceof INodeWithOptions opts) {
            return opts.getNodeOptionById("captured") != null;
        }
        return false;
    }
}
