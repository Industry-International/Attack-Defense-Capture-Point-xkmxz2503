package com.xkmxz.attack_defense_capture_point_xkmxz.gui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * 攻防战据点图编辑器 — 类似 graphif.dev 风格的节点图编辑器。
 * <p>
 * 将据点和区域渲染为可拖拽的圆角节点，连接线展示归属/依赖关系。
 */
public class ControlPanelUI {

    private final Level level;

    // 存储所有创建的节点，用于连接线绘制
    private final List<GraphNode> pointNodes = new ArrayList<>();
    private final List<GraphNode> zoneNodes = new ArrayList<>();
    private ConnectionLayer connectionLayer;

    public ControlPanelUI(Level level) {
        this.level = level;
    }

    public void open() {
        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();
        int guiW = (int) (screenW * 0.88f);
        int guiH = (int) (screenH * 0.86f);
        int graphW = guiW - 12;
        int graphH = guiH - 80;

        var root = new UIElement()
                .layout(l -> l.paddingAll(4).gapAll(3).width(guiW).height(guiH))
                .style(s -> s.background(Sprites.BORDER));

        // 标题
        root.addChildren(new Label()
                .setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title")));

        // === 顶部工具栏 ===
        var toolbar = new UIElement().layout(l -> l.gapAll(3));
        toolbar.addChildren(
                new Button()
                        .setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_create_point"))
                        .setOnClick(e -> runCmd("capturepoint create P" + (int) (Math.random() * 1000) + ";")),
                new Button()
                        .setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_create_zone"))
                        .setOnClick(e -> runCmd("capturepoint zone create Z" + (int) (Math.random() * 1000) + ";")),
                new Button()
                        .setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_show_status"))
                        .setOnClick(e -> runCmd("capturepoint list")),
                new Button()
                        .setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_refresh"))
                        .setOnClick(e -> {
                            Minecraft.getInstance().setScreen(null);
                            new ControlPanelUI(level).open();
                        }),
                new Button()
                        .setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.block.close"))
                        .setOnClick(e -> Minecraft.getInstance().setScreen(null))
        );
        root.addChildren(toolbar);

        // === GraphView 画布 ===
        GraphView graph = new GraphView();
        graph.graphViewStyle(style -> {
            style.allowZoom(true);
            style.allowPan(true);
            style.minScale(0.2f);
            style.maxScale(4.0f);
        });
        graph.layout(l -> l.width(graphW).height(graphH));

        // 连接线层（必须先添加，渲染在节点下方）
        connectionLayer = new ConnectionLayer();
        graph.addContentChild(connectionLayer);

        // 从 CaptureManager 加载数据
        pointNodes.clear();
        zoneNodes.clear();
        loadData(graph);

        // 如果没有节点，显示空提示
        if (pointNodes.isEmpty() && zoneNodes.isEmpty()) {
            var hintNode = new UIElement();
            hintNode.layout(l -> l.paddingAll(8).width(200).height(50));
            hintNode.addChildren(new Label()
                    .setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.empty_hint")));
            graph.addContentChild(hintNode);
        }

        // 自动适配视图
        graph.fitToChildren(60f, 0.15f);

        // === 右侧缩放条 ===
        var zoomBar = new UIElement().layout(l -> l.gapAll(3));
        zoomBar.style(s -> s.background(Sprites.BORDER));

        zoomBar.addChildren(new Label()
                .setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.zoom")));

        int zw = 50;
        addZoomBtn(zoomBar, "25%", zw, () -> graph.fitToChildren(60f, 0.15f));
        addZoomBtn(zoomBar, "50%", zw, () -> graph.fitToChildren(60f, 0.15f));
        addZoomBtn(zoomBar, "75%", zw, () -> graph.fitToChildren(60f, 0.15f));
        addZoomBtn(zoomBar, "100%", zw, () -> graph.fitToChildren(60f, 0.15f));
        addZoomBtn(zoomBar, "150%", zw, () -> graph.fitToChildren(60f, 0.15f));
        addZoomBtn(zoomBar, "200%", zw, () -> graph.fitToChildren(60f, 0.15f));
        zoomBar.addChildren(new Label().setText(Component.literal("---")));
        addZoomBtn(zoomBar, "Fit", zw, () -> graph.fitToChildren(60f, 0.15f));

        graph.addEditorChild(zoomBar, 0);

        root.addChildren(graph);

        var ui = ModularUI.of(UI.of(root));
        Minecraft.getInstance().setScreen(
                new ModularUIScreen(ui, Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title"))
        );
    }

    /**
     * 从 CaptureManager 读取据点和区域数据，渲染为节点图。
     * <p>
     * 在客户端侧，尝试通过集成服务器（单人模式）获取 ServerLevel；
     * 在纯客户端（联机）时，数据不可用，画布显示空（与原有行为一致）。
     */
    private void loadData(GraphView graph) {
        // 尝试获取服务器端的 Level 以读取 CaptureManager
        var serverLevel = getServerLevel();
        if (serverLevel == null) return;

        var manager = CaptureManager.get(serverLevel);
        var points = manager.getPoints();
        var zones = manager.getZones();

        if (points.isEmpty() && zones.isEmpty()) return;

        // 计算布局 —— 简单的网格布局
        float startX = 80f;
        float startY = 60f;
        float gapX = 200f;
        float gapY = 100f;

        // 第一行：所有据点节点
        float curX = startX;
        float curY = startY + 60f;
        int idx = 0;
        for (var entry : points.values()) {
            boolean captured = entry.owner() != null;
            var node = new GraphNode(
                    entry.name(),
                    GraphNodeType.POINT,
                    captured,
                    captured ? entry.owner() : null
            );
            float nodeX = curX;
            float nodeY = curY;
            node.layout(l -> l
                    .positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                    .left(nodeX)
                    .top(nodeY)
                    .width(160)
                    .height(48)
            );
            graph.addContentChild(node);
            pointNodes.add(node);
            idx++;
            curX += gapX;
            if (idx % 4 == 0) {
                curX = startX;
                curY += gapY;
            }
        }

        // 第二行：所有区域节点
        curX = startX + 80f;
        curY += 60f;
        idx = 0;
        for (var entry : zones.values()) {
            var captured = manager.isZoneCaptured(entry.name());
            var accessible = manager.canAccessZone(entry.name());
            var node = new GraphNode(
                    entry.name(),
                    GraphNodeType.ZONE,
                    captured,
                    accessible ? null : entry.requiredZone()
            );
            float nodeX = curX;
            float nodeY = curY;
            node.layout(l -> l
                    .positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                    .left(nodeX)
                    .top(nodeY)
                    .width(160)
                    .height(48)
            );
            graph.addContentChild(node);
            zoneNodes.add(node);
            idx++;
            curX += gapX;
            if (idx % 4 == 0) {
                curX = startX + 80f;
                curY += gapY;
            }
        }

        // 注册连接线
        for (var entry : points.values()) {
            String pointName = entry.name();
            var pointNode = findNode(pointName, GraphNodeType.POINT);
            if (pointNode == null) continue;

            // 查找该点所属的区域
            for (var zoneEntry : zones.values()) {
                if (zoneEntry.capturePoints().contains(pointName)) {
                    var zoneNode = findNode(zoneEntry.name(), GraphNodeType.ZONE);
                    if (zoneNode != null) {
                        connectionLayer.addConnection(pointNode, zoneNode, ConnectionType.MEMBERSHIP);
                    }
                }
            }
        }

        // 区域间的依赖关系
        for (var entry : zones.values()) {
            if (entry.requiredZone() != null) {
                var zoneNode = findNode(entry.name(), GraphNodeType.ZONE);
                var depNode = findNode(entry.requiredZone(), GraphNodeType.ZONE);
                if (zoneNode != null && depNode != null) {
                    connectionLayer.addConnection(depNode, zoneNode, ConnectionType.DEPENDENCY);
                }
            }
        }
    }

    private GraphNode findNode(String name, GraphNodeType type) {
        var list = type == GraphNodeType.POINT ? pointNodes : zoneNodes;
        for (var node : list) {
            if (node.nodeName.equals(name)) return node;
        }
        return null;
    }

    // ---- UI Components ----

    /**
     * 节点类型
     */
    private enum GraphNodeType {
        POINT,
        ZONE
    }

    /**
     * 连接类型
     */
    private enum ConnectionType {
        MEMBERSHIP,  // 据点 -> 区域
        DEPENDENCY   // 依赖区域 -> 区域
    }

    /**
     * 可拖拽的图节点 — 圆角矩形 + 名称标签
     */
    private static class GraphNode extends UIElement {
        final String nodeName;
        final GraphNodeType nodeType;
        final boolean captured;
        final String ownerOrLocked;

        // 拖动状态
        private boolean dragging = false;
        private float dragStartLeft, dragStartTop;

        GraphNode(String name, GraphNodeType type, boolean captured, String ownerOrLocked) {
            this.nodeName = name;
            this.nodeType = type;
            this.captured = captured;
            this.ownerOrLocked = ownerOrLocked;

            // 根据类型和状态选择颜色
            int bgColor;
            int borderColor;
            if (type == GraphNodeType.POINT) {
                if (captured) {
                    // 已占领据点 —— 绿色调
                    bgColor = 0xCC1B5E20;
                    borderColor = 0xFF4CAF50;
                } else {
                    // 未占领据点 —— 灰蓝色调
                    bgColor = 0xCC263238;
                    borderColor = 0xFF546E7A;
                }
            } else {
                if (captured) {
                    // 已占领区域 —— 蓝色调
                    bgColor = 0xCC0D47A1;
                    borderColor = 0xFF42A5F5;
                } else if (ownerOrLocked != null) {
                    // 锁定区域 —— 橙色调
                    bgColor = 0xCCBF360C;
                    borderColor = 0xFFFF7043;
                } else {
                    // 未占领区域 —— 紫色调
                    bgColor = 0xCC4A148C;
                    borderColor = 0xFFAB47BC;
                }
            }

            // 圆角矩形背景 (使用 RECT_RD)
            style(s -> s.background(Sprites.RECT_RD_SOLID).backgroundTexture(new ColorRectTexture(bgColor)));

            // 叠加边框颜色
            style(s -> s.overlayTexture(new ColorBorderTexture(-3, borderColor)));

            // 类型图标 + 名称
            String prefix;
            if (type == GraphNodeType.POINT) {
                if (captured) {
                    prefix = "\u2691 "; // ⚑ 旗帜
                } else {
                    prefix = "\u25CB "; // ○ 空心圆
                }
            } else {
                if (captured) {
                    prefix = "\u25A0 "; // ■ 实心方块
                } else {
                    prefix = "\u25A1 "; // □ 空心方块
                }
            }

            addChildren(new Label()
                    .setText(Component.literal(prefix + name))
                    .layout(l -> l.paddingAll(4)));

            // 添加鼠标拖动支持
            addEventListener(UIEvents.MOUSE_DOWN, this::onNodeMouseDown);
            addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::onNodeDragUpdate);
        }

        private void onNodeMouseDown(UIEvent event) {
            if (event.button == 0) {
                dragging = true;
                dragStartLeft = getPositionX();
                dragStartTop = getPositionY();
                // 需要获取 GraphView 的 scale
                // 使用父链获取 GraphView
                startDrag(new NodeDragData(dragStartLeft, dragStartTop), null);
            }
        }

        private void onNodeDragUpdate(UIEvent event) {
            if (!dragging) return;
            if (!(event.dragHandler.draggingObject instanceof NodeDragData data)) return;

            // 获取 GraphView 的 scale
            float scale = getGraphViewScale();
            if (scale < 0.001f) scale = 1f;

            float dx = (event.x - event.dragStartX) / scale;
            float dy = (event.y - event.dragStartY) / scale;

            float newLeft = data.startLeft + dx;
            float newTop = data.startTop + dy;

            layout(l -> l.left(newLeft).top(newTop));
        }

        private float getGraphViewScale() {
            var parent = getParent();
            while (parent != null) {
                if (parent instanceof GraphView gv) {
                    return gv.getScale();
                }
                parent = parent.getParent();
            }
            return 1f;
        }

        private record NodeDragData(float startLeft, float startTop) {
        }
    }

    /**
     * 连接线层 — 绘制节点间的连线
     */
    private static class ConnectionLayer extends UIElement {
        private final List<Connection> connections = new ArrayList<>();

        record Connection(GraphNode from, GraphNode to, ConnectionType type) {
        }

        void addConnection(GraphNode from, GraphNode to, ConnectionType type) {
            connections.add(new Connection(from, to, type));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            // 在画布变换后的坐标系中绘制连接线
            for (var conn : connections) {
                var from = conn.from();
                var to = conn.to();

                float x1 = from.getPositionX() + from.getSizeWidth() / 2f;
                float y1 = from.getPositionY() + from.getSizeHeight();
                float x2 = to.getPositionX() + to.getSizeWidth() / 2f;
                float y2 = to.getPositionY();

                // 如果两个节点都可见
                if (from.isDisplayed() && to.isDisplayed()) {
                    int color;
                    float lineWidth;
                    if (conn.type() == ConnectionType.MEMBERSHIP) {
                        color = 0xAA90CAF9; // 淡蓝色
                        lineWidth = 1.5f;
                    } else {
                        color = 0xAAFFAB91; // 淡橙色（依赖关系）
                        lineWidth = 2.0f;
                    }

                    // 调整连接点：从 from 底部到 to 顶部
                    // 使用贝塞尔曲线或简单折线
                    drawCurvedLine(context, x1, y1, x2, y2, color, lineWidth);
                }
            }
        }

        /**
         * 绘制节点间的曲线连接
         */
        private void drawCurvedLine(GUIContext context, float x1, float y1, float x2, float y2,
                                     int color, float width) {
            float midY = (y1 + y2) / 2f;
            float controlY = midY;

            // 用分段线性近似绘制贝塞尔曲线
            var points = new ArrayList<Vector2f>();
            int segments = 20;
            for (int i = 0; i <= segments; i++) {
                float t = (float) i / segments;
                float t1 = 1 - t;

                // 简单三次贝塞尔: 起点 -> 控制点1 -> 控制点2 -> 终点
                // 控制点1: (x1, midY) 控制点2: (x2, midY)
                float px = t1 * t1 * t1 * x1 + 3 * t1 * t1 * t * x1 + 3 * t1 * t * t * x2 + t * t * t * x2;
                float py = t1 * t1 * t1 * y1 + 3 * t1 * t1 * t * controlY + 3 * t1 * t * t * controlY + t * t * t * y2;

                points.add(new Vector2f(px, py));
            }

            DrawerHelper.drawLines(context.graphics, points, color, color, width);
        }
    }

    // ---- Utility Methods ----

    /**
     * 获取服务端 Level，用于读取 CaptureManager 数据。
     * <ul>
     *   <li>如果当前已经是 ServerLevel，直接返回</li>
     *   <li>单人模式：通过集成服务器获取对应维度的 ServerLevel</li>
     *   <li>联机客户端：返回 null（数据不可用）</li>
     * </ul>
     */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.server.level.ServerLevel getServerLevel() {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            return sl;
        }
        // 单人模式：从集成服务器获取
        var mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getLevel(level.dimension());
        }
        return null;
    }

    private static void addZoomBtn(UIElement parent, String label, int width, Runnable action) {
        var btn = new Button();
        btn.setText(label);
        btn.setOnClick(e -> action.run());
        btn.layout(l -> l.width(width).height(18));
        parent.addChildren(btn);
    }

    private static void runCmd(String commands) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        for (var cmd : commands.split(";")) {
            cmd = cmd.trim();
            if (!cmd.isEmpty()) player.connection.sendCommand(cmd);
        }
    }
}
