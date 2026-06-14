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

        // 创建通知层（在根容器最上层）
        var toastContainer = new UIElement()
                .layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                        .right(0).top(0).width(300).heightPercent(100));
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
        // 从 CaptureManager 加载据点数据并创建对应的节点
        try {
            var serverLevel = getServerLevel();
            if (serverLevel == null) return;

            var manager = com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager.get(serverLevel);
            var points = manager.getPoints();
            var zones = manager.getZones();

            float startX = 100;
            float startY = 100;
            float gapX = 220;
            float gapY = 120;
            int idx = 0;

            // 为每个据点创建节点
            for (var entry : points.values()) {
                float x = startX + (idx % 4) * gapX;
                float y = startY + (idx / 4) * gapY;
                var node = new CapturePointNode();
                var nodeModel = graph.graphModel.createNodeModel(node,
                        new org.joml.Vector2f(x, y));
                // 设置名称和选项
                nodeModel.setName(entry.name());
                nodeModel.setTitle(Component.literal(entry.name()));
                // 设置 owner 选项
                var ownerOpt = nodeModel.getNodeOptionById("owner");
                if (ownerOpt != null && entry.owner() != null) {
                    // 设置实际值
                }
                // 设置位置信息
                var posOpt = nodeModel.getNodeOptionById("position");
                if (posOpt != null) {
                    var posStr = entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ();
                }
                idx++;
            }

            // 为每个区域创建节点
            for (var entry : zones.values()) {
                float x = startX + 250;
                float y = startY + ((idx - points.size()) % 3) * gapY + 100;
                var node = new CaptureZoneNode();
                var nodeModel = graph.graphModel.createNodeModel(node,
                        new org.joml.Vector2f(x, y));
                nodeModel.setName(entry.name());
                nodeModel.setTitle(Component.literal(entry.name()));
                idx++;
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
