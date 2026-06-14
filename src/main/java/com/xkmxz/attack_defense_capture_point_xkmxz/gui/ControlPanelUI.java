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
 * 攻防战据点图编辑器 — 纯 Canvas 绘制 + 手动事件管理。
 * 使用 GraphView 作为相机（缩放/平移），所有交互在 GraphView 层监听。
 */
public class ControlPanelUI {

    // 颜色
    private static final int C_PO_BG=0xCC1B5E20, C_PO_BD=0xFF66BB6A, C_PF_BG=0xCC263238, C_PF_BD=0xFF78909C;
    private static final int C_ZC_BG=0xCC0D47A1, C_ZC_BD=0xFF42A5F5, C_ZL_BG=0xCCBF360C, C_ZL_BD=0xFFFF7043;
    private static final int C_ZF_BG=0xCC4A148C, C_ZF_BD=0xFFAB47BC;
    private static final int C_EM=0xAA90CAF9, C_ED=0xAAFFAB91, C_SEL=0xFFFFFFFF, C_TX=0xFFFFFFFF, C_TD=0xAAFFFFFF;
    private static final int C_PN=0xCC1E1E2E, C_PB=0xFF444466, C_ST=0x44333333, C_SF=0xCC666666, C_SH=0xFFAAAAAA;
    private static final int GUI_W=80; // 占屏幕百分比

    private final Level level;
    private final List<NodeData> nodes = new ArrayList<>();
    private final List<EdgeData> edges = new ArrayList<>();

    private NodeData selNode, dragNode;
    private float dOX, dOY, dMX, dMY;

    // 菜单
    private float ctxMX, ctxMY;
    private boolean ctxOpen;
    // 滑块
    private float sldVal = 0.5f;

    private GraphView gv;
    private GraphCanvas canvas;
    private Label zl;

    public ControlPanelUI(Level level) { this.level = level; }

    @SuppressWarnings("unchecked")
    public void open() {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int scw = win.getGuiScaledWidth();
        int sch = win.getGuiScaledHeight();

        int gw = scw * GUI_W / 100;
        int gh = sch * GUI_W / 100;
        // 根容器 — 居中，80%
        var root = new UIElement()
                .layout(l -> l.width(gw).height(gh))
                .style(s -> s.background(Sprites.BORDER).backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        // GraphView — 画布，填充剩余空间
        gv = new GraphView();
        gv.graphViewStyle(s -> { s.allowZoom(true); s.allowPan(true); s.minScale(0.2f); s.maxScale(4.0f); });
        gv.layout(l -> l.widthPercent(100).heightPercent(100));

        // === 事件全部在 GraphView 层处理 ===
        // 注意：GraphView 的 onMouseDown 只处理 event.target==this 的情况启动平移拖拽，
        // 但我们的 contentChild 被点击时 event.target 是那个 child，所以 gv 的平移不会启动。
        // 然而 gv 的 DRAG 事件会被 gv 内部的拖拽逻辑消耗，
        // 所以我们需要通过另一种方式：在 contentChild（canvas）上监听事件。
        // 但 canvas 作为 contentChild 收不到事件？问题就在于这里。
        // 
        // 解决方案：手动启用 gv 的拖拽，同时在拖拽更新中处理节点拖拽
        // 这样 gv 的平移和节点拖拽可以共存。

        canvas = new GraphCanvas();
        gv.addContentChild(canvas);
        root.addChildren(gv);

        // 所有鼠标事件在 gv 层监听
        gv.addEventListener(UIEvents.MOUSE_DOWN, this::onMD);
        gv.addEventListener(UIEvents.MOUSE_UP, this::onMU);
        gv.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::onDrag);

        loadData();
        if (!nodes.isEmpty()) gv.fitToChildren(60f, 0.15f);

        // === 底部工具栏 ===
        var tb = new UIElement().layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).left(0).right(0).bottom(8).height(32)
                .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER));
        var tbi = new UIElement().layout(l -> l.widthAuto().height(28).gapAll(2).paddingAll(2))
                .style(s -> s.background(Sprites.BORDER).backgroundTexture(new ColorRectTexture(C_PN)));
        tbi.addChildren(btn("\u21BB",()->{mc.setScreen(null);new ControlPanelUI(level).open();}), btn("\u2715",()->mc.setScreen(null)));
        tb.addChildren(tbi);
        root.addChildren(tb);

        // === 右侧缩放面板 ===
        var rp = new UIElement().layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                .right(8).topPercent(50).width(44).height(200).paddingAll(4).gapAll(4)
                .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER).backgroundTexture(new ColorRectTexture(C_PN)));
        zl = new Label(); zl.setText(pct()); zl.layout(l -> l.widthPercent(100)); rp.addChildren(zl);
        // 滑块
        var sld = new Sld();
        sld.layout(l -> l.width(12).height(100));
        var sw = new UIElement().layout(l -> l.justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER).widthPercent(100));
        sw.addChildren(sld); rp.addChildren(sw);
        var fb = new Button(); fb.setText("Fit"); fb.layout(l -> l.widthPercent(100).height(18));
        fb.setOnClick(e -> { if (!nodes.isEmpty()) gv.fitToChildren(60f, 0.15f); zl.setText(pct()); syncSld(); });
        rp.addChildren(fb);
        root.addChildren(rp);

        gv.addEventListener(UIEvents.MOUSE_WHEEL, ev -> { zl.setText(pct()); syncSld(); });

        // 窗口居中
        var wrap = new UIElement().layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui, Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title")));
    }

    // ================================================================
    //  事件处理（全在 GraphView 层）
    // ================================================================

    private boolean menuHit; // 左键点中了菜单项
    private boolean nodeHit; // 左键点中了节点

    private void onMD(UIEvent ev) {
        float sc = gv.getScale();
        float wx = (ev.x - gv.getPositionX())/sc + gv.getOffsetX();
        float wy = (ev.y - gv.getPositionY())/sc + gv.getOffsetY();

        if (ev.button == 1) { // 右键
            ctxOpen = true;
            ctxMX = ev.x - 40; ctxMY = ev.y - 20;
            return;
        }
        if (ev.button == 0) {
            // 菜单点击
            if (ctxOpen) {
                int idx = (int)((ev.y - ctxMY - 2) / 17f);
                if (ev.x >= ctxMX && ev.x <= ctxMX+120 && idx >= 0 && idx <= 4) {
                    execCtx(idx); ctxOpen = false; return;
                }
                ctxOpen = false;
            }
            // 节点命中
            NodeData hit = null;
            for (int i = nodes.size()-1; i >= 0; i--) {
                var n = nodes.get(i);
                if (wx >= n.x-n.w/2f && wx <= n.x+n.w/2f && wy >= n.y-n.h/2f && wy <= n.y+n.h/2f) { hit = n; break; }
            }
            for (var n : nodes) n.selected = false;
            if (hit != null) {
                hit.selected = true; selNode = hit;
                dragNode = hit; dOX = hit.x; dOY = hit.y; dMX = ev.x; dMY = ev.y;
                nodeHit = true;
            } else {
                selNode = null; dragNode = null; nodeHit = false;
            }
        }
    }

    private void onMU(UIEvent ev) {
        // 鼠标松开
    }

    private void onDrag(UIEvent ev) {
        if (dragNode != null) {
            float s = gv.getScale();
            dragNode.x = dOX + (ev.x - dMX) / s;
            dragNode.y = dOY + (ev.y - dMY) / s;
        }
    }

    private void execCtx(int idx) {
        switch (idx) {
            case 0 -> runCmd("capturepoint create P"+(int)(Math.random()*1000)+";");
            case 1 -> runCmd("capturepoint zone create Z"+(int)(Math.random()*1000)+";");
            case 2 -> runCmd("capturepoint list");
            case 3 -> { Minecraft.getInstance().setScreen(null); new ControlPanelUI(level).open(); }
            case 4 -> Minecraft.getInstance().setScreen(null);
        }
    }

    // ================================================================
    //  滑块
    // ================================================================

    private class Sld extends UIElement {
        private float sSV, sMY;
        private boolean drag;

        Sld() {
            // MOUSE_DOWN 在自身监听（根级 ABSOLUTE，事件可达）
            addEventListener(UIEvents.MOUSE_DOWN, this::onDown);
            // DRAG 在 gv 层监听（因为 gv 的拖拽系统消耗了全局 DRAG 事件）
            gv.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::onDrag);
            gv.addEventListener(UIEvents.DRAG_END, ev -> drag = false);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext ctx) {
            float h = getSizeHeight(), w = getSizeWidth(), th = 16f, fh = h * sldVal, ty = h - fh - th/2f;
            DrawerHelper.drawSolidRect(ctx.graphics, 0,0,w,h,C_ST);
            DrawerHelper.drawSolidRect(ctx.graphics,0,h-fh,w,fh,C_SF);
            DrawerHelper.drawSolidRect(ctx.graphics,0,ty,w,th,C_SH);
        }

        private void onDown(UIEvent ev) {
            if (ev.button != 0) return;
            drag = true; sSV = sldVal; sMY = ev.y;
            float h = getSizeHeight(); if (h <= 0) return;
            // 点击立即跳转 — 使用 screenYLocal 计算相对于滑块顶部的 Y
            float ly = absY(ev.y);
            sldVal = Mth.clamp(1f - ly/h, 0f, 1f);
            applySld();
            zl.setText(pct());
        }

        private void onDrag(UIEvent ev) {
            if (!drag) return;
            float h = getSizeHeight(); if (h <= 0) return;
            float dy = (ev.y - sMY) / h;
            sldVal = Mth.clamp(sSV - dy, 0f, 1f);
            applySld();
            zl.setText(pct());
        }

        private float absY(float sy) {
            float y = 0; UIElement e = this;
            while (e != null) { y += e.getPositionY(); e = e.getParent(); }
            return sy - y;
        }
    }

    private void applySld() {
        float mn = gv.getGraphViewStyle().minScale(), mx = gv.getGraphViewStyle().maxScale();
        float ns = Mth.clamp(mn + (mx-mn)*sldVal, mn, mx);
        try {
            var f = GraphView.class.getDeclaredField("scale"); f.setAccessible(true); f.setFloat(gv, ns);
            var m = GraphView.class.getDeclaredMethod("refreshContentTransform"); m.setAccessible(true); m.invoke(gv);
        } catch (Exception ignored) {}
    }

    private void syncSld() {
        float sc = gv.getScale(), mn = gv.getGraphViewStyle().minScale(), mx = gv.getGraphViewStyle().maxScale();
        sldVal = Mth.clamp((sc-mn)/(mx-mn), 0f, 1f);
    }

    private String pct() { return (int)(gv.getScale()*100)+"%"; }

    // ================================================================
    //  数据模型 & 加载
    // ================================================================

    enum NK { POINT, ZONE }
    static class NodeData {
        String name; NK kind; boolean owned; String info;
        float x,y,w=160,h=48; boolean selected;
        NodeData(String n, NK k, boolean o, String i) { name=n; kind=k; owned=o; info=i; }
    }
    enum EK { M, D }
    record EdgeData(NodeData f, NodeData t, EK k) {}

    private void loadData() {
        var sl = getSL(); if (sl == null) return;
        var mgr = CaptureManager.get(sl);
        var pts = mgr.getPoints(); var zns = mgr.getZones();
        if (pts.isEmpty() && zns.isEmpty()) return;
        float sx=80f,sy=60f,gx=200f,gy=100f,cx=sx,cy=sy+60f; int idx=0;
        for (var e : pts.values()) { boolean o=e.owner()!=null; var n=new NodeData(e.name(),NK.POINT,o,o?e.owner():null); n.x=cx+80; n.y=cy+24; nodes.add(n); if(++idx%4==0){cx=sx;cy+=gy;}else cx+=gx; }
        cx=sx+80f;cy+=60f;idx=0;
        for (var e : zns.values()) { boolean c=mgr.isZoneCaptured(e.name()),a=mgr.canAccessZone(e.name()); var n=new NodeData(e.name(),NK.ZONE,c,a?null:e.requiredZone()); n.x=cx+80; n.y=cy+24; nodes.add(n); if(++idx%4==0){cx=sx+80f;cy+=gy;}else cx+=gx; }
        for (var e : pts.values()) { var pn=find(e.name()); if(pn==null)continue; for(var ze:zns.values()){if(ze.capturePoints().contains(e.name())){var zn=find(ze.name());if(zn!=null)edges.add(new EdgeData(pn,zn,EK.M));}} }
        for (var e : zns.values()) { if(e.requiredZone()!=null){var zn=find(e.name());var dn=find(e.requiredZone());if(zn!=null&&dn!=null)edges.add(new EdgeData(dn,zn,EK.D));} }
    }
    private NodeData find(String n) { for(var nd:nodes)if(nd.name.equals(n))return nd; return null; }

    // ================================================================
    //  渲染
    // ================================================================

    private class GraphCanvas extends UIElement {
        @Override
        public void drawBackgroundAdditional(GUIContext ctx) {
            if (nodes.isEmpty()) {
                String msg = "Right-click for options";
                Font f = Minecraft.getInstance().font;
                DrawerHelper.drawText(ctx.graphics, msg, (getSizeWidth()-f.width(msg))/2f, getSizeHeight()/2f-f.lineHeight/2f, 1f, C_TD);
                return;
            }
            for (var e : edges) drawEdge(ctx, e);
            for (var n : nodes) drawNode(ctx, n);
            if (selNode != null) drawInfo(ctx, selNode);
            if (ctxOpen) drawCtx(ctx);
        }

        private void drawNode(GUIContext ctx, NodeData n) {
            float rx=n.x-n.w/2f, ry=n.y-n.h/2f; int bg,bd;
            if (n.kind==NK.POINT) { bg=n.owned?C_PO_BG:C_PF_BG; bd=n.selected?C_SEL:(n.owned?C_PO_BD:C_PF_BD); }
            else if (n.owned) { bg=C_ZC_BG; bd=n.selected?C_SEL:C_ZC_BD; }
            else if (n.info!=null) { bg=C_ZL_BG; bd=n.selected?C_SEL:C_ZL_BD; }
            else { bg=C_ZF_BG; bd=n.selected?C_SEL:C_ZF_BD; }
            DrawerHelper.drawSolidRect(ctx.graphics,rx,ry,n.w,n.h,bg);
            if (n.selected) DrawerHelper.drawBorder(ctx.graphics,rx-2,ry-2,n.w+4,n.h+4,C_SEL,2);
            DrawerHelper.drawBorder(ctx.graphics,rx,ry,n.w,n.h,bd,1);
            String ic=n.kind==NK.POINT?(n.owned?"\u2691":"\u25CB"):(n.owned?"\u25A0":"\u25A1");
            Font f=Minecraft.getInstance().font;
            DrawerHelper.drawText(ctx.graphics,ic+" "+n.name,rx+8,ry+(n.h-f.lineHeight)/2f+1,1f,C_TX);
            String st=n.kind==NK.POINT?(n.owned?n.info:""):(n.owned?"Cap":(n.info!=null?"Lock":"Free"));
            if(!st.isEmpty()){float sw=f.width(st);DrawerHelper.drawText(ctx.graphics,st,rx+n.w-sw-6,ry+n.h-f.lineHeight-2,0.7f,C_TD);}
        }

        private void drawEdge(GUIContext ctx, EdgeData e) {
            float x1=e.f.x,y1=e.f.y+e.f.h/2f,x2=e.t.x,y2=e.t.y-e.t.h/2f;
            int c=e.k==EK.M?C_EM:C_ED; float w=e.k==EK.M?1.5f:2f,my=(y1+y2)/2f;
            var pts=new ArrayList<Vector2f>();
            for(int i=0;i<=24;i++){float t=i/24f,t1=1-t;pts.add(new Vector2f(t1*t1*t1*x1+3*t1*t1*t*x1+3*t1*t*t*x2+t*t*t*x2,t1*t1*t1*y1+3*t1*t1*t*my+3*t1*t*t*my+t*t*t*y2));}
            DrawerHelper.drawLines(ctx.graphics,pts,c,c,w);
            float dx=x2-x1,dy=y2-y1,l=(float)Math.sqrt(dx*dx+dy*dy);if(l<1f)return;
            float ux=dx/l,uy=dy/l,px=x2-ux*6f,py=y2-uy*6f,ax=-uy*8f*0.4f,ay=ux*8f*0.4f;
            var arr=new ArrayList<Vector2f>();arr.add(new Vector2f(x2,y2));arr.add(new Vector2f(px+ax,py+ay));arr.add(new Vector2f(px-ax,py-ay));
            DrawerHelper.drawLines(ctx.graphics,arr,c,c,1f);
        }

        private void drawInfo(GUIContext ctx, NodeData n) {
            String s=n.kind==NK.POINT?(n.owned?"Owner: "+n.info:"Free point"):(n.owned?"Captured":(n.info!=null?"Requires: "+n.info:"Free zone"));
            DrawerHelper.drawText(ctx.graphics,s,n.x-n.w/2f,n.y+n.h/2f+4,0.8f,C_TD);
        }

        private void drawCtx(GUIContext ctx) {
            float mx=ctxMX,my=ctxMY,iw=120f,ih=16f,g=1f,mh=5*(ih+g)+4f;
            DrawerHelper.drawSolidRect(ctx.graphics,mx,my,iw,mh,0xEE1E1E2E);
            DrawerHelper.drawBorder(ctx.graphics,mx,my,iw,mh,C_PB,1);
            String[] ls={"Create Point","Create Zone","List Status","\u21BB Refresh","\u2715 Close"};
            float iy=my+2;for(var l:ls){DrawerHelper.drawText(ctx.graphics,l,mx+4,iy,0.85f,C_TX);iy+=ih+g;}
        }
    }

    // ================================================================
    //  工具
    // ================================================================

    private static Button btn(String t, Runnable a) { var b=new Button(); b.setText(t); b.layout(l->l.width(24).heightPercent(100)); b.setOnClick(e->a.run()); return b; }
    private net.minecraft.server.level.ServerLevel getSL() {
        if(level instanceof net.minecraft.server.level.ServerLevel sl)return sl;
        var mc=Minecraft.getInstance();
        if(mc.hasSingleplayerServer()&&mc.getSingleplayerServer()!=null)return mc.getSingleplayerServer().getLevel(level.dimension());
        return null;
    }
    private static void runCmd(String c){var p=Minecraft.getInstance().player;if(p==null)return;for(var s:c.split(";")){s=s.trim();if(!s.isEmpty())p.connection.sendCommand(s);}}
}
