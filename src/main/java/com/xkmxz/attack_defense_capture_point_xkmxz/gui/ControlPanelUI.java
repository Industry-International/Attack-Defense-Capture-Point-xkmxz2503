package com.xkmxz.attack_defense_capture_point_xkmxz.gui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
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
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * 攻防战据点图编辑器 — 仿 graphif 布局风格的全屏 Canvas2D 节点图。
 * <p>
 * 布局：全屏画布 + 浮动工具栏 + 右侧浮动缩放面板。
 * 所有绘制都在 drawBackgroundAdditional 中完成，不依赖子 UIElement 布局。
 */
public class ControlPanelUI {

    // ---- 颜色常量 ----
    private static final int COL_BG_DARK = 0xCC1A1A2E;
    private static final int COL_BG_POINT_OWNED = 0xCC1B5E20;
    private static final int COL_BORDER_POINT_OWNED = 0xFF66BB6A;
    private static final int COL_BG_POINT_FREE = 0xCC263238;
    private static final int COL_BORDER_POINT_FREE = 0xFF78909C;
    private static final int COL_BG_ZONE_CAPTURED = 0xCC0D47A1;
    private static final int COL_BORDER_ZONE_CAPTURED = 0xFF42A5F5;
    private static final int COL_BG_ZONE_LOCKED = 0xCCBF360C;
    private static final int COL_BORDER_ZONE_LOCKED = 0xFFFF7043;
    private static final int COL_BG_ZONE_FREE = 0xCC4A148C;
    private static final int COL_BORDER_ZONE_FREE = 0xFFAB47BC;
    private static final int COL_EDGE_MEMBER = 0xAA90CAF9;
    private static final int COL_EDGE_DEP = 0xAAFFAB91;
    private static final int COL_SEL = 0xFFFFFFFF;
    private static final int COL_TEXT = 0xFFFFFFFF;
    private static final int COL_TEXT_DIM = 0xAAFFFFFF;
    private static final int COL_PANEL_BG = 0xCC1E1E2E;
    private static final int COL_PANEL_BORDER = 0xFF444466;

    private final Level level;
    private final List<NodeData> nodes = new ArrayList<>();
    private final List<EdgeData> edges = new ArrayList<>();

    private NodeData selNode;
    private NodeData dragNode;
    private float dragMX, dragMY;
    private float dragOX, dragOY;

    private GraphView graphView;
    private GraphCanvas canvas;
    private Label zoomLabel;
    private ZoomSlider zoomSlider;

    public ControlPanelUI(Level level) {
        this.level = level;
    }

    public void open() {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int sw = win.getGuiScaledWidth();
        int sh = win.getGuiScaledHeight();

        // 全屏根容器 — 使用百分比填满，不固定像素
        var root = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0))
                .style(s -> s.background(Sprites.RECT_SOLID)
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        // === 全屏 Canvas 层 ===
        graphView = new GraphView();
        graphView.graphViewStyle(style -> {
            style.allowZoom(true);
            style.allowPan(true);
            style.minScale(0.2f);
            style.maxScale(4.0f);
            // 使用内置网格背景
        });
        graphView.layout(l -> l.widthPercent(100).heightPercent(100));

        canvas = new GraphCanvas();
        graphView.addContentChild(canvas);
        root.addChildren(graphView);

        // 加载数据
        loadData();

        // 无论有无数据都先适配
        if (!nodes.isEmpty()) {
            graphView.fitToChildren(60f, 0.15f);
        } else {
            // 空状态提示通过 canvas 绘制
        }

        // === 浮动工具栏（底部居中 — 仿 graphif 底部 Toolbar） ===
        var toolbar = new UIElement()
                .layout(l -> l
                        .positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                        .leftPercent(50)
                        .bottom(8)
                        .widthAuto()
                        .height(28)
                        .gapAll(2)
                        .paddingAll(2))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(COL_PANEL_BG)));

        // 用负的 marginLeft 实现居中（因为 leftPercent(50) 是容器左边缘在 50% 处）
        // 改用 flex 居中：在外层套一个全宽容器
        var toolbarWrapper = new UIElement()
                .layout(l -> l
                        .positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                        .left(0).right(0).bottom(8)
                        .height(32)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER));

        toolbar.addChildren(
                makeBtn("\u2691", () -> runCmd("capturepoint create P" + (int)(Math.random()*1000) + ";")),
                makeBtn("\u25A0", () -> runCmd("capturepoint zone create Z" + (int)(Math.random()*1000) + ";")),
                makeBtn("\u2630", () -> runCmd("capturepoint list")),
                makeBtn("\u21BB", () -> { mc.setScreen(null); new ControlPanelUI(level).open(); }),
                makeBtn("\u2715", () -> mc.setScreen(null))
        );
        toolbarWrapper.addChildren(toolbar);
        root.addChildren(toolbarWrapper);

        // === 右侧浮动缩放面板（仿 graphif right-toolbar） ===
        var rightPanel = new UIElement()
                .layout(l -> l
                        .positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                        .right(8)
                        .topPercent(50)
                        .width(44)
                        .height(200)
                        .paddingAll(4)
                        .gapAll(4)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(COL_PANEL_BG)));

        // Zoom label
        zoomLabel = new Label();
        zoomLabel.setText(getZoomText(graphView.getScale()));
        zoomLabel.layout(l -> l.widthPercent(100));
        rightPanel.addChildren(zoomLabel);

        // Zoom slider
        zoomSlider = new ZoomSlider(graphView);
        zoomSlider.layout(l -> l.width(12).height(100));
        var sliderWrap = new UIElement()
                .layout(l -> l.justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .widthPercent(100));
        sliderWrap.addChildren(zoomSlider);
        rightPanel.addChildren(sliderWrap);

        // Fit 按钮
        var fitBtn = new Button();
        fitBtn.setText("Fit");
        fitBtn.layout(l -> l.widthPercent(100).height(18));
        fitBtn.setOnClick(e -> {
            if (!nodes.isEmpty()) graphView.fitToChildren(60f, 0.15f);
            zoomLabel.setText(getZoomText(graphView.getScale()));
        });
        rightPanel.addChildren(fitBtn);

        // 滚轮缩放同步更新文字
        graphView.addEventListener(UIEvents.MOUSE_WHEEL, ev -> {
            zoomLabel.setText(getZoomText(graphView.getScale()));
            zoomSlider.syncFromScale(graphView.getScale());
        });

        root.addChildren(rightPanel);

        var ui = ModularUI.of(UI.of(root));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title")));
    }

    private static Button makeBtn(String icon, Runnable action) {
        var btn = new Button();
        btn.setText(icon);
        btn.layout(l -> l.width(24).heightPercent(100));
        btn.setOnClick(e -> action.run());
        return btn;
    }

    // ================================================================
    //  数据模型
    // ================================================================

    enum NodeKind { POINT, ZONE }

    static class NodeData {
        String name;
        NodeKind kind;
        boolean owned;
        String ownerOrLocked;
        float x, y, w = 160, h = 48;
        boolean selected;

        NodeData(String name, NodeKind kind, boolean owned, String ownerOrLocked) {
            this.name = name; this.kind = kind; this.owned = owned; this.ownerOrLocked = ownerOrLocked;
        }
    }

    enum EdgeKind { MEMBERSHIP, DEPENDENCY }
    record EdgeData(NodeData from, NodeData to, EdgeKind kind) {}

    // ================================================================
    //  数据加载
    // ================================================================

    private void loadData() {
        var sl = getServerLevel();
        if (sl == null) return;
        var mgr = CaptureManager.get(sl);
        var pts = mgr.getPoints();
        var zns = mgr.getZones();
        if (pts.isEmpty() && zns.isEmpty()) return;

        float sx = 80f, sy = 60f, gx = 200f, gy = 100f;
        float cx = sx, cy = sy + 60f;
        int idx = 0;
        for (var e : pts.values()) {
            boolean o = e.owner() != null;
            var n = new NodeData(e.name(), NodeKind.POINT, o, o ? e.owner() : null);
            n.x = cx + 80; n.y = cy + 24;
            nodes.add(n);
            if (++idx % 4 == 0) { cx = sx; cy += gy; } else cx += gx;
        }
        cx = sx + 80f; cy += 60f; idx = 0;
        for (var e : zns.values()) {
            boolean c = mgr.isZoneCaptured(e.name());
            boolean a = mgr.canAccessZone(e.name());
            var n = new NodeData(e.name(), NodeKind.ZONE, c, a ? null : e.requiredZone());
            n.x = cx + 80; n.y = cy + 24;
            nodes.add(n);
            if (++idx % 4 == 0) { cx = sx + 80f; cy += gy; } else cx += gx;
        }

        for (var e : pts.values()) {
            var pn = find(e.name()); if (pn == null) continue;
            for (var ze : zns.values()) {
                if (ze.capturePoints().contains(e.name())) {
                    var zn = find(ze.name());
                    if (zn != null) edges.add(new EdgeData(pn, zn, EdgeKind.MEMBERSHIP));
                }
            }
        }
        for (var e : zns.values()) {
            if (e.requiredZone() != null) {
                var zn = find(e.name()); var dn = find(e.requiredZone());
                if (zn != null && dn != null) edges.add(new EdgeData(dn, zn, EdgeKind.DEPENDENCY));
            }
        }
    }

    private NodeData find(String name) {
        for (var n : nodes) if (n.name.equals(name)) return n;
        return null;
    }

    // ================================================================
    //  Canvas 渲染层
    // ================================================================

    private class GraphCanvas extends UIElement {
        GraphCanvas() {
            addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);
            addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::onDragUpdate);
            addEventListener(UIEvents.DRAG_END, this::onDragEnd);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext ctx) {
            // 空状态
            if (nodes.isEmpty()) {
                String msg = "Create points & zones using the toolbar";
                Font f = Minecraft.getInstance().font;
                float tx = (getSizeWidth() - f.width(msg)) / 2f;
                float ty = getSizeHeight() / 2f - f.lineHeight / 2f;
                DrawerHelper.drawText(ctx.graphics, msg, tx, ty, 1.0f, COL_TEXT_DIM);
                return;
            }
            // 边
            for (var e : edges) drawEdge(ctx, e);
            // 节点
            for (var n : nodes) drawNode(ctx, n);
            // 选中信息
            if (selNode != null) drawInfo(ctx, selNode);
        }

        private void drawNode(GUIContext ctx, NodeData n) {
            float rx = n.x - n.w/2f, ry = n.y - n.h/2f;
            int bg, border;

            if (n.kind == NodeKind.POINT) {
                bg = n.owned ? COL_BG_POINT_OWNED : COL_BG_POINT_FREE;
                border = n.selected ? COL_SEL : (n.owned ? COL_BORDER_POINT_OWNED : COL_BORDER_POINT_FREE);
            } else {
                if (n.owned) { bg = COL_BG_ZONE_CAPTURED; border = n.selected ? COL_SEL : COL_BORDER_ZONE_CAPTURED; }
                else if (n.ownerOrLocked != null) { bg = COL_BG_ZONE_LOCKED; border = n.selected ? COL_SEL : COL_BORDER_ZONE_LOCKED; }
                else { bg = COL_BG_ZONE_FREE; border = n.selected ? COL_SEL : COL_BORDER_ZONE_FREE; }
            }

            DrawerHelper.drawSolidRect(ctx.graphics, rx, ry, n.w, n.h, bg);
            if (n.selected) DrawerHelper.drawBorder(ctx.graphics, rx-2, ry-2, n.w+4, n.h+4, COL_SEL, 2);
            DrawerHelper.drawBorder(ctx.graphics, rx, ry, n.w, n.h, border, 1);

            String icon = switch (n.kind) {
                case POINT -> n.owned ? "\u2691" : "\u25CB";
                case ZONE -> n.owned ? "\u25A0" : "\u25A1";
            };
            Font f = Minecraft.getInstance().font;
            DrawerHelper.drawText(ctx.graphics, icon + " " + n.name, rx+8, ry+(n.h-f.lineHeight)/2f+1, 1f, COL_TEXT);

            String status = switch (n.kind) {
                case POINT -> n.owned ? n.ownerOrLocked : "";
                case ZONE -> n.owned ? "Cap" : (n.ownerOrLocked != null ? "Lock" : "Free");
            };
            if (!status.isEmpty()) {
                float sw = f.width(status);
                DrawerHelper.drawText(ctx.graphics, status, rx+n.w-sw-6, ry+n.h-f.lineHeight-2, 0.7f, COL_TEXT_DIM);
            }
        }

        private void drawEdge(GUIContext ctx, EdgeData e) {
            float x1 = e.from.x, y1 = e.from.y + e.from.h/2f;
            float x2 = e.to.x, y2 = e.to.y - e.to.h/2f;
            int col = e.kind == EdgeKind.MEMBERSHIP ? COL_EDGE_MEMBER : COL_EDGE_DEP;
            float w = e.kind == EdgeKind.MEMBERSHIP ? 1.5f : 2f;
            drawBezier(ctx, x1, y1, x2, y2, col, w);
        }

        private void drawBezier(GUIContext ctx, float x1, float y1, float x2, float y2, int col, float w) {
            float my = (y1+y2)/2f;
            var pts = new ArrayList<Vector2f>();
            for (int i = 0; i <= 24; i++) {
                float t = (float)i/24f, t1 = 1-t;
                pts.add(new Vector2f(
                    t1*t1*t1*x1 + 3*t1*t1*t*x1 + 3*t1*t*t*x2 + t*t*t*x2,
                    t1*t1*t1*y1 + 3*t1*t1*t*my + 3*t1*t*t*my + t*t*t*y2
                ));
            }
            DrawerHelper.drawLines(ctx.graphics, pts, col, col, w);
            // 箭头
            float dx=x2-x1, dy=y2-y1, len=(float)Math.sqrt(dx*dx+dy*dy);
            if (len<1f) return;
            float ux=dx/len, uy=dy/len, as=8f;
            float px=x2-ux*6f, py=y2-uy*6f, ax=-uy*as*0.4f, ay=ux*as*0.4f;
            var arr = new ArrayList<Vector2f>();
            arr.add(new Vector2f(x2,y2)); arr.add(new Vector2f(px+ax,py+ay)); arr.add(new Vector2f(px-ax,py-ay));
            DrawerHelper.drawLines(ctx.graphics, arr, col, col, 1f);
        }

        private void drawInfo(GUIContext ctx, NodeData n) {
            String info = switch (n.kind) {
                case POINT -> n.owned ? "Owner: " + n.ownerOrLocked : "Free point";
                case ZONE -> n.owned ? "Captured" : (n.ownerOrLocked != null ? "Requires: " + n.ownerOrLocked : "Free zone");
            };
            DrawerHelper.drawText(ctx.graphics, info, n.x-n.w/2f, n.y+n.h/2f+4, 0.8f, COL_TEXT_DIM);
        }

        // ---- 交互 ----
        private void onMouseDown(UIEvent ev) {
            if (ev.button != 0) return;
            float scale = getGVScale();
            float mx = (ev.x - getPositionX())/scale + graphView.getOffsetX();
            float my = (ev.y - getPositionY())/scale + graphView.getOffsetY();

            NodeData hit = null;
            for (int i=nodes.size()-1; i>=0; i--) {
                var n = nodes.get(i);
                if (mx>=n.x-n.w/2f && mx<=n.x+n.w/2f && my>=n.y-n.h/2f && my<=n.y+n.h/2f) { hit=n; break; }
            }
            for (var n : nodes) n.selected = false;
            if (hit != null) {
                hit.selected = true; selNode = hit; dragNode = hit;
                dragOX = hit.x; dragOY = hit.y;
                dragMX = ev.x; dragMY = ev.y;
            } else {
                selNode = null; dragNode = null;
            }
        }

        private void onDragUpdate(UIEvent ev) {
            if (dragNode == null) return;
            float s = getGVScale();
            dragNode.x = dragOX + (ev.x-dragMX)/s;
            dragNode.y = dragOY + (ev.y-dragMY)/s;
        }

        private void onDragEnd(UIEvent ev) { dragNode = null; }

        private float getGVScale() {
            var p = getParent(); while(p!=null) { if(p instanceof GraphView gv) return gv.getScale(); p=p.getParent(); }
            return 1f;
        }
    }

    // ================================================================
    //  缩放滑块
    // ================================================================

    private static class ZoomSlider extends UIElement {
        private final GraphView target;
        private float value = 0.5f;
        private static final int TRACK = 0x44333333;
        private static final int FILL = 0xCC666666;
        private static final int THUMB = 0xFFAAAAAA;

        ZoomSlider(GraphView target) {
            this.target = target;
            addEventListener(UIEvents.MOUSE_DOWN, this::onDown);
            addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::onDrag);
        }

        void syncFromScale(float scale) {
            float mn = target.getGraphViewStyle().minScale();
            float mx = target.getGraphViewStyle().maxScale();
            value = Mth.clamp((scale-mn)/(mx-mn), 0f, 1f);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext ctx) {
            float h = getSizeHeight(), w = getSizeWidth(), th = 16f, fh = h*value, ty = h-fh-th/2f;
            DrawerHelper.drawSolidRect(ctx.graphics, 0,0,w,h,TRACK);
            DrawerHelper.drawSolidRect(ctx.graphics,0,h-fh,w,fh,FILL);
            DrawerHelper.drawSolidRect(ctx.graphics,0,ty,w,th,THUMB);
        }

        private void onDown(UIEvent ev) { if(ev.button==0) { update(ev.y); startDrag(this,null); } }
        private void onDrag(UIEvent ev) { update(ev.y); }

        private void update(float my) {
            float h = getSizeHeight(); if (h<=0) return;
            value = Mth.clamp(1f - (my-getPositionY())/h, 0f, 1f);
            float mn=target.getGraphViewStyle().minScale(), mx=target.getGraphViewStyle().maxScale();
            float ns = Mth.clamp(mn+(mx-mn)*value, mn, mx);
            try {
                var f = GraphView.class.getDeclaredField("scale"); f.setAccessible(true); f.setFloat(target, ns);
                var m = GraphView.class.getDeclaredMethod("refreshContentTransform"); m.setAccessible(true); m.invoke(target);
            } catch (Exception ignored) {}
        }
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private static String getZoomText(float s) { return (int)(s*100)+"%"; }

    @org.jetbrains.annotations.Nullable
    private net.minecraft.server.level.ServerLevel getServerLevel() {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) return sl;
        var mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null)
            return mc.getSingleplayerServer().getLevel(level.dimension());
        return null;
    }

    private static void runCmd(String cmd) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        for (var c : cmd.split(";")) { c = c.trim(); if (!c.isEmpty()) p.connection.sendCommand(c); }
    }
}
