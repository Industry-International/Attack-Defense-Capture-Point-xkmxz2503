package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * 据点管理节点图屏幕 - 使用 LDLib2 nodegraphtookit 框架提供完整的节点图编辑体验。
 */
public class CapturePointGraphScreen {

    private static final int PANEL_BG = 0xFF1A1A2E;

    private final Level level;
    private final CapturePointGraphView graphView;
    private final CapturePointGraph graph;

    public CapturePointGraphScreen(Level level) {
        this.level = level;
        this.graph = new CapturePointGraph();
        this.graphView = new CapturePointGraphView();
        this.graphView.setLevel(level);
        this.graphView.loadGraph(graph);
    }

    public void open() {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int scw = win.getGuiScaledWidth();
        int sch = win.getGuiScaledHeight();

        // 根容器 - 全屏
        var root = new UIElement()
                .layout(l -> l.width(scw).height(sch))
                .style(s -> s.background(Sprites.BORDER).backgroundTexture(new ColorRectTexture(PANEL_BG)));

        // 创建通知层（偏右上角，在顶栏下方，避免被遮挡）
        var toastContainer = new UIElement()
                .layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                        .right(8).top(36).width(280).heightPercent(80));
        root.addChildren(toastContainer);

        var toastLayer = new ToastLayer(toastContainer);
        graphView.setToastLayer(toastLayer);

        // 顶栏
        var topBar = createTopBar();
        root.addChildren(topBar);

        // 图视图 - 填充剩余空间
        graphView.layout(l -> l.widthPercent(100).flex(1));
        root.addChildren(graphView);

        // 根据 CaptureManager 加载数据到图
        loadDataToGraph();

        var ui = ModularUI.of(UI.of(root));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title")));
    }

    private UIElement createTopBar() {
        var tb = new UIElement()
                .layout(l -> l.widthPercent(100).height(32)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .gapAll(4).paddingAll(4));
        tb.style(s -> s.background(Sprites.BORDER)
                .backgroundTexture(new ColorRectTexture(0xFF16213E)));

        // 标题
        var title = new Label();
        title.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title"));
        title.layout(l -> l.widthAuto().heightPercent(100));

        // 刷新按钮
        var refreshBtn = new Button();
        refreshBtn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_refresh"));
        refreshBtn.layout(l -> l.width(60).heightPercent(100));
        refreshBtn.setOnClick(e -> {
            mc().setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        // 关闭按钮
        var closeBtn = new Button();
        closeBtn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.block.close"));
        closeBtn.layout(l -> l.width(60).heightPercent(100));
        closeBtn.setOnClick(e -> mc().setScreen(null));

        // 占位弹性空间
        var spacer = new UIElement().layout(l -> l.flex(1));

        tb.addChildren(title, spacer, refreshBtn, closeBtn);
        return tb;
    }

    private void loadDataToGraph() {
        // 从 CaptureManager 加载据点/区域数据并创建节点及连线
        try {
            var serverLevel = getServerLevel();
            if (serverLevel == null) return;

            var manager = com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager.get(serverLevel);
            var points = manager.getPoints();
            var zones = manager.getZones();

            if (points.isEmpty() && zones.isEmpty()) return;

            // 存储 node model 名称到模型的映射，用于后续连线
            var pointModels = new java.util.LinkedHashMap<String, com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel>();
            var zoneModels = new java.util.LinkedHashMap<String, com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel>();

            float startX = 100;
            float startY = 100;
            float gapX = 220;
            float gapY = 120;
            int idx = 0;

            // 创建据点节点
            for (var entry : points.values()) {
                float x = startX + (idx % 4) * gapX;
                float y = startY + (idx / 4) * gapY;
                var node = new CapturePointNode();
                var nodeModel = graph.graphModel.createNodeModel(node,
                        new org.joml.Vector2f(x, y));
                nodeModel.setName(entry.name());
                nodeModel.setTitle(net.minecraft.network.chat.Component.literal(entry.name()));
                // owner 和 position 选项只读显示，在节点定义时已有默认值
                pointModels.put(entry.name(), nodeModel);
                idx++;
            }

            // 创建区域节点
            for (var entry : zones.values()) {
                float x = startX + 250;
                float y = startY + ((idx - points.size()) % 3) * gapY + 100;
                var node = new CaptureZoneNode();
                var nodeModel = graph.graphModel.createNodeModel(node,
                        new org.joml.Vector2f(x, y));
                nodeModel.setName(entry.name());
                nodeModel.setTitle(net.minecraft.network.chat.Component.literal(entry.name()));
                // captured / required_zone / points 选项只读显示，节点定义时已有默认值
                zoneModels.put(entry.name(), nodeModel);
                idx++;
            }

            // === 建立连线（反映"区域包含据点"和"区域前后关系"） ===

            // 连线 1: 据点(point_signal 输出) → 区域(point_in 输入)
            for (var zoneEntry : zones.values()) {
                var zoneModel = zoneModels.get(zoneEntry.name());
                if (zoneModel == null) continue;
                var pointInPort = zoneModel.getInputsById().get("point_in");
                if (pointInPort == null) continue;

                for (var pointName : zoneEntry.capturePoints()) {
                    var pointModel = pointModels.get(pointName);
                    if (pointModel == null) continue;
                    var signalPort = pointModel.getOutputsById().get("point_signal");
                    if (signalPort == null) continue;

                    try {
                        graph.graphModel.createWire(signalPort, pointInPort);
                    } catch (Exception ignored) {
                        // 可能端口已连接，忽略
                    }
                }
            }

            // 连线 2: 前置区域(zone_out 输出) → 后置区域(required_zone 输入)
            for (var zoneEntry : zones.values()) {
                if (zoneEntry.requiredZone() == null || zoneEntry.requiredZone().isEmpty()) continue;

                var zoneModel = zoneModels.get(zoneEntry.name());
                if (zoneModel == null) continue;
                var reqZoneInput = zoneModel.getInputsById().get("required_zone");
                if (reqZoneInput == null) continue;

                var requiredZoneModel = zoneModels.get(zoneEntry.requiredZone());
                if (requiredZoneModel == null) continue;
                var zoneOutPort = requiredZoneModel.getOutputsById().get("zone_out");
                if (zoneOutPort == null) continue;

                try {
                    graph.graphModel.createWire(zoneOutPort, reqZoneInput);
                } catch (Exception ignored) {
                }
            }

        } catch (Exception e) {
            // 非服务端环境，跳过数据加载
        }
    }

    private net.minecraft.server.level.ServerLevel getServerLevel() {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) return sl;
        var mc = mc();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getLevel(level.dimension());
        }
        return null;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
