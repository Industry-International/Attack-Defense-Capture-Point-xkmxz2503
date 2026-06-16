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
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.INodeWithOptions;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;
import com.xkmxz.attack_defense_capture_point_xkmxz.block.entity.CapturePointBlockEntity;
import com.xkmxz.attack_defense_capture_point_xkmxz.gui.CapturePointTheme;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.ICaptureDataAccess;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * 据点管理节点图屏幕 - 使用 LDLib2 nodegraphtookit 框架提供完整的节点图编辑体验。
 * 支持保存节点关系到 CaptureManager。
 */
public class CapturePointGraphScreen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Level level;
    private final CapturePointGraphView graphView;
    private final CapturePointGraph graph;
    private final LinkedHashMap<String, CaptureManager.CapturePointEntry> trayPoints = new LinkedHashMap<>();
    @Nullable
    private UIElement trayPanel;
    @Nullable
    private UIElement trayList;
    private boolean editMode = false;
    private long snapshotVersion = -1; // 进入编辑模式时捕获的版本号，用于冲突检测

    public CapturePointGraphScreen(Level level) {
        this.level = level;
        this.graph = new CapturePointGraph();
        this.graphView = new CapturePointGraphView();
        this.graphView.setLevel(level);
        this.graphView.setScreen(this);
        this.graphView.loadGraph(graph);
        // 注册实时数据刷新回调（每 ~15 tick 由 graphView.screenTick() 驱动）
        this.graphView.setRefreshCallback(this::refreshNodeTitles);
    }

    /** 返回当前是否处于编辑模式，GraphView 可用此判断菜单行为 */
    public boolean isEditMode() {
        return editMode;
    }

    /** 获取图模型实例，供 GraphView 创建节点使用 */
    public CapturePointGraph getGraph() {
        return graph;
    }

    /** 进入编辑模式时快照当前版本号 */
    private void captureSnapshotVersion() {
        var mgr = getServerCaptureManager();
        snapshotVersion = mgr != null ? mgr.getVersion() : -1;
    }

    public void open() {
        var mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int scw = win.getGuiScaledWidth();
        int sch = win.getGuiScaledHeight();

        // 根容器 - 全屏，使用 SDF 圆角背景
        var root = CapturePointTheme.panel(CapturePointTheme.ROOT_COLOR)
                .layout(l -> l.width(scw).height(sch));

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

        // 左侧收纳器
        var tray = createPointTray();
        root.addChildren(tray);

        // 图视图 - 填充剩余空间
        graphView.layout(l -> l.widthPercent(100).flex(1).marginLeft(170));
        root.addChildren(graphView);

        // 根据 CaptureManager 加载数据到图
        loadDataToGraph();
        refreshPointTray();
        // 初始立即同步节点选项数据
        refreshNodeTitles();
        // 注册选项值变更监听器，实现 GUI→Game 实时回写
        registerOptionChangeListeners();

        // 恢复保存的视角状态，或自动 fit 到所有节点
        restoreOrFitViewState();

        var ui = ModularUI.of(UI.of(root));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title")));
    }

    /**
     * 恢复保存的视角状态，或自动 fit 到所有节点。
     * 视角状态保存在 CaptureManager 中，保存时实时记录，打开时延迟到下一帧应用。
     */
    private void restoreOrFitViewState() {
        try {
            var mgr = getCaptureManager();
            if (mgr == null) return;
            var savedViewState = mgr.getViewState();
            if (savedViewState != null) {
                graphView.setCanvasSize(savedViewState.canvasWidth(), savedViewState.canvasHeight());
                graphView.setPendingViewState(
                        savedViewState.offsetX(),
                        savedViewState.offsetY(),
                        savedViewState.scale()
                );
            } else {
                graphView.requestFitOnNextTick();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to restore view state: {}", e.getMessage());
            graphView.requestFitOnNextTick();
        }
    }

    private UIElement createTopBar() {
        var tb = CapturePointTheme.panel(CapturePointTheme.FIELD_COLOR)
                .layout(l -> l.widthPercent(100).height(32)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW)
                        .gapAll(6).paddingAll(4));

        // 标题
        var title = CapturePointTheme.titleLabel(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.title"));
        title.layout(l -> l.widthAuto().heightPercent(100));

        // 编辑模式切换按钮
        var editToggleBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.edit_mode.off"));
        editToggleBtn.layout(l -> l.width(60).heightPercent(100));
        editToggleBtn.setOnClick(e -> {
            editMode = !editMode;
            updateEditToggleButton(editToggleBtn);
            graphView.showPanelsForEditMode(editMode);
            tb.style(s -> s.backgroundTexture(CapturePointTheme.panelBg(editMode ? 0xFF2E1A1A : CapturePointTheme.FIELD_COLOR)));
            if (editMode) {
                captureSnapshotVersion();
            }
            ToastNotification.push(editMode ? ToastNotification.Type.INFO : ToastNotification.Type.SUCCESS,
                    Component.translatable(editMode ? "toast.capture_point_graph.edit_mode.on" : "toast.capture_point_graph.edit_mode.off"));
        });

        // 保存按钮
        var saveBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_save"));
        saveBtn.layout(l -> l.width(50).heightPercent(100));
        saveBtn.setOnClick(e -> saveGraph());

        var canvasBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.capture_point_graph.btn_canvas"));
        canvasBtn.layout(l -> l.width(52).heightPercent(100));
        canvasBtn.setOnClick(e -> openCanvasConfigDialog());

        // 刷新按钮
        var refreshBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_refresh"));
        refreshBtn.layout(l -> l.width(50).heightPercent(100));
        refreshBtn.setOnClick(e -> {
            mc().setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        // 关闭按钮
        var closeBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.attack_defense_capture_point_xkmxz.block.close"));
        closeBtn.layout(l -> l.width(50).heightPercent(100));
        closeBtn.setOnClick(e -> mc().setScreen(null));

        // 占位弹性空间
        var spacer = new UIElement().layout(l -> l.flex(1));

        tb.addChildren(title, spacer, editToggleBtn, saveBtn, canvasBtn, refreshBtn, closeBtn);
        return tb;
    }

    private UIElement createPointTray() {
        var tray = CapturePointTheme.panel(CapturePointTheme.FIELD_COLOR)
                .layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                        .left(8).top(40).width(156).bottom(8)
                        .paddingAll(8).gapAll(6)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN));

        var title = CapturePointTheme.titleLabel(Component.translatable("gui.capture_point_graph.tray.title"));
        title.layout(l -> l.widthPercent(100).heightAuto());
        tray.addChildren(title);

        var hint = CapturePointTheme.secondaryLabel(Component.translatable("gui.capture_point_graph.tray.hint"));
        hint.layout(l -> l.widthPercent(100).heightAuto());
        tray.addChildren(hint);

        var list = new UIElement()
                .layout(l -> l.widthPercent(100).flex(1)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN).gapAll(4));
        tray.addChildren(list);

        this.trayPanel = tray;
        this.trayList = list;
        return tray;
    }

    private void refreshPointTray() {
        if (trayPanel == null || trayList == null) return;
        trayPanel.setDisplay(editMode && !trayPoints.isEmpty());
        trayPanel.setVisible(editMode && !trayPoints.isEmpty());
        trayList.clearAllChildren();

        for (var entry : trayPoints.entrySet()) {
            String pointName = entry.getKey();
            var row = new UIElement()
                    .layout(l -> l.widthPercent(100).height(26)
                            .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(4));

            var label = new Label().setText(Component.literal(pointName));
            label.layout(l -> l.flex(1).heightPercent(100));
            label.textStyle(s -> s.fontSize(9.5f).textColor(0xFFECECEC));

            var addBtn = new Button().setText(Component.translatable("gui.capture_point_graph.tray.add"));
            addBtn.layout(l -> l.width(42).heightPercent(100));
            addBtn.setOnClick(e -> moveTrayPointIntoGraph(pointName));

            row.addChildren(label, addBtn);
            trayList.addChildren(row);
        }
    }

    private void moveTrayPointIntoGraph(String pointName) {
        var entry = trayPoints.remove(pointName);
        if (entry == null) return;

        var center = graphView.getGraphCoordsAtScreen(
                mc().getWindow().getGuiScaledWidth() * 0.6f,
                mc().getWindow().getGuiScaledHeight() * 0.5f
        );
        var node = new CapturePointNode();
        var nodeModel = graph.graphModel.createNodeModel(node, new org.joml.Vector2f(center.x, center.y));
        nodeModel.setName(pointName);
        nodeModel.setTitle(Component.literal(pointName));
        syncPointOptions(nodeModel, entry, entry.captured(), getCaptureManager() != null ? getCaptureManager().findZoneForPoint(pointName) : null);
        refreshPointTray();
        refreshNodeTitles();
    }

    private void openCanvasConfigDialog() {
        var mc = mc();
        int dw = 300;
        int dh = 180;

        var root = CapturePointTheme.panel()
                .layout(l -> l.width(dw).height(dh).paddingAll(12).gapAll(8)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN));

        var title = CapturePointTheme.titleLabel(Component.translatable("gui.capture_point_graph.canvas.title"));
        title.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(title);

        var widthLabel = CapturePointTheme.secondaryLabel(Component.translatable("gui.capture_point_graph.canvas.width"));
        widthLabel.layout(l -> l.widthPercent(100).heightAuto());
        var widthField = new TextField();
        widthField.layout(l -> l.widthPercent(100).height(26));
        widthField.setValue(String.valueOf((int) graphView.getCanvasWidth()), false);

        var heightLabel = CapturePointTheme.secondaryLabel(Component.translatable("gui.capture_point_graph.canvas.height"));
        heightLabel.layout(l -> l.widthPercent(100).heightAuto());
        var heightField = new TextField();
        heightField.layout(l -> l.widthPercent(100).height(26));
        heightField.setValue(String.valueOf((int) graphView.getCanvasHeight()), false);

        root.addChildren(widthLabel, widthField, heightLabel, heightField, new UIElement().layout(l -> l.flex(1)));

        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(28)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6));
        var applyBtn = CapturePointTheme.styledButton(Component.translatable("gui.capture_point_graph.dialog.confirm"));
        applyBtn.layout(l -> l.flex(1).heightPercent(100));
        applyBtn.setOnClick(e -> {
            try {
                float width = Float.parseFloat(widthField.getText().trim());
                float height = Float.parseFloat(heightField.getText().trim());
                graphView.setCanvasSize(width, height);
                mc.setScreen(null);
                open();
            } catch (NumberFormatException ignored) {
                ToastNotification.push(ToastNotification.Type.ERROR,
                        Component.translatable("toast.capture_point_graph.canvas.invalid"));
            }
        });
        var cancelBtn = CapturePointTheme.styledButton(Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.flex(1).heightPercent(100));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            open();
        });
        btnRow.addChildren(applyBtn, cancelBtn);
        root.addChildren(btnRow);

        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui, Component.translatable("gui.capture_point_graph.canvas.title")));
    }

    /** 更新编辑模式切换按钮的文本和颜色 */
    private void updateEditToggleButton(Button btn) {
        if (editMode) {
            btn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.edit_mode.on"));
            btn.style(s -> s.backgroundTexture(CapturePointTheme.buttonBg(0xFFAA3333)));
        } else {
            btn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.edit_mode.off"));
            btn.style(s -> s.backgroundTexture(CapturePointTheme.buttonBg(CapturePointTheme.BUTTON_COLOR)));
        }
    }

    // ================================================================
    //  保存节点图到 CaptureManager (快照模式 + 版本检测)
    // ================================================================

    /** 从当前节点模型中读取选项值（使用序列化ID而非toString） */
    private static String getOptionString(NodeModel nm, String id) {
        if (nm instanceof INodeWithOptions opts) {
            var opt = opts.getNodeOptionById(id);
            if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                var port = nodeOpt.getPortModel();
                if (port instanceof IFieldValueConfigurable configurable) {
                    var v = configurable.getValue();
                    if (v == null) return "";
                    // 枚举类型使用 getSerializationId() 确保序列化的是原始ID而非本地化文本
                    if (v instanceof CaptureConditionNode.PropertyType pt) return pt.getSerializationId();
                    if (v instanceof CaptureConditionNode.OperatorType ot) return ot.getSerializationId();
                    if (v instanceof LogicGateNode.GateType gt) return gt.getSerializationId();
                    if (v instanceof CaptureActionNode.ActionType at) return at.getSerializationId();
                    return v.toString();
                }
            }
        }
        return "";
    }

    private static double getOptionDouble(NodeModel nm, String id) {
        try {
            return Double.parseDouble(getOptionString(nm, id));
        } catch (Exception e) { return 5.0; }
    }

    private static int getOptionInt(NodeModel nm, String id) {
        try {
            return Integer.parseInt(getOptionString(nm, id));
        } catch (Exception e) { return 0xFFFF4444; }
    }

    private static boolean getOptionBool(NodeModel nm, String id) {
        String val = getOptionString(nm, id);
        if (val == null || val.isEmpty()) return false;
        return "true".equalsIgnoreCase(val) || "1".equals(val) || "yes".equalsIgnoreCase(val);
    }

    private List<CaptureManager.GraphWireData> collectGraphWires() {
        var wires = new ArrayList<CaptureManager.GraphWireData>();
        for (var element : graph.graphModel.getGraphElementModels()) {
            if (!(element instanceof WireModel wire)) continue;
            var fromPort = wire.getFromPort();
            var toPort = wire.getToPort();
            if (fromPort == null || toPort == null) continue;
            if (!(fromPort.getNodeModel() instanceof NodeModel fromNm) || !(toPort.getNodeModel() instanceof NodeModel toNm)) continue;

            String fromName = fromNm.getName();
            String toName = toNm.getName();
            String fromPortName = fromPort.getUniqueName();
            String toPortName = toPort.getUniqueName();
            if (fromName == null || toName == null || fromPortName == null || toPortName == null) continue;

            // 结构关系已经由 points / zones 自身数据持久化，不重复存进 graphWires。
            if ("point_signal".equals(fromPortName) && "point_in".equals(toPortName)) continue;
            if ("zone_out".equals(fromPortName) && "required_zone".equals(toPortName)) continue;

            wires.add(new CaptureManager.GraphWireData(fromName, fromPortName, toName, toPortName));
        }
        return wires;
    }

    /**
     * 从节点图中构建完整数据快照。<br>
     * <br>
     * <b>处理流程：</b><br>
     * 1. 收集所有节点（据点/区域/条件/逻辑门/动作/常量）<br>
     * 2. 解析连线：直连（point→zone）、区域依赖（zone→required_zone）<br>
     * 3. 构建据点数据<br>
     * 4. 条件链评估：利用 CaptureConditionNode + LogicGateNode 评估条件，<br>
     *    替代旧的硬编码 CaptureDecisionNode 条件路由<br>
     * 5. 构建区域数据（直连据点）<br>
     * 6. 🔓 解锁信号条件路由：通过条件/逻辑门链路路由解锁信号<br>
     * 7. 双向同步规则
     */
    private Map.Entry<Map<String, CaptureManager.CapturePointEntry>, Map<String, CaptureManager.ZoneEntry>> buildSnapshotFromGraph() {
        var newPoints = new LinkedHashMap<String, CaptureManager.CapturePointEntry>();
        var newZones = new LinkedHashMap<String, CaptureManager.ZoneEntry>();

        // ---- Phase 1: 收集所有节点 ----
        var pointModels = new HashMap<String, NodeModel>();
        var zoneModels = new HashMap<String, NodeModel>();
        var conditionModels = new HashMap<String, NodeModel>();
        var gateModels = new HashMap<String, NodeModel>();
        var actionModels = new HashMap<String, NodeModel>();
        // 旧版判断器节点（兼容）
        var oldDecisionModels = new HashMap<String, NodeModel>();

        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof NodeModel nm) {
                String name = nm.getName();
                if (name == null || name.isEmpty()) continue;
                if (hasOutputPort(nm, "point_signal")) {
                    pointModels.put(name, nm);
                } else if (hasInputPort(nm, "point_target") || hasInputPort(nm, "zone_target")) {
                    // 新版条件节点
                    conditionModels.put(name, nm);
                } else if (hasInputPort(nm, "in_a")) {
                    // 逻辑门节点
                    gateModels.put(name, nm);
                } else if (hasInputPort(nm, "trigger")) {
                    // 动作节点
                    actionModels.put(name, nm);
                } else if (hasOutputPort(nm, "zone_out") || hasInputPort(nm, "point_in")) {
                    zoneModels.put(name, nm);
                } else if (hasInputPort(nm, "target")) {
                    // 旧版判断器节点（兼容）
                    oldDecisionModels.put(name, nm);
                }
            }
        }

        // ---- Phase 2: 解析连线 ----
        // wireBasedZonePoints: zoneName → [pointName, ...] 直连
        var wireBasedZonePoints = new LinkedHashMap<String, List<String>>();
        // wireBasedRequiredZone: zoneName → requiredZoneName 区域依赖
        var wireBasedRequiredZone = new LinkedHashMap<String, String>();
        // decisionInputs: decisionName → [pointName, ...] 判断器输入（连接到target端口的据点）
        var decisionInputs = new LinkedHashMap<String, List<String>>();
        // decisionOutputs: decisionName → (portName → [zoneName, ...]) 判断器各输出端口连接的区域
        var decisionOutputs = new LinkedHashMap<String, Map<String, List<String>>>();

        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;

                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;

                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;

                // 1. 据点→区域直连: from=point_signal(O)  to=point_in(I)
                if (hasOutputPort(fromNm, "point_signal") && hasInputPort(toNm, "point_in")) {
                    wireBasedZonePoints.computeIfAbsent(toName, k -> new ArrayList<>()).add(fromName);
                }
                // 2. 据点→判断器: from=point_signal(O)  to=target(I)
                else if (hasOutputPort(fromNm, "point_signal") && hasInputPort(toNm, "target")) {
                    decisionInputs.computeIfAbsent(toName, k -> new ArrayList<>()).add(fromName);
                }
                // 3. 判断器→区域: from=true_out/false_out(O)  to=point_in(I)
                else if (hasInputPort(toNm, "point_in") && hasOutputPort(fromNm, "true_out")) {
                    decisionOutputs.computeIfAbsent(fromName, k -> new LinkedHashMap<>())
                            .computeIfAbsent("true_out", k -> new ArrayList<>()).add(toName);
                }
                else if (hasInputPort(toNm, "point_in") && hasOutputPort(fromNm, "false_out")) {
                    decisionOutputs.computeIfAbsent(fromName, k -> new LinkedHashMap<>())
                            .computeIfAbsent("false_out", k -> new ArrayList<>()).add(toName);
                }
                // 4. 区域→区域依赖: from=zone_out(O)  to=required_zone(I)
                else if (hasOutputPort(fromNm, "zone_out") && hasInputPort(toNm, "required_zone")) {
                    wireBasedRequiredZone.put(toName, fromName);
                }
            }
        }

        // ---- Phase 3: 构建据点数据 ----
        var player = mc().player;
        BlockPos defaultPos = player != null ? player.blockPosition() : BlockPos.ZERO;
        for (var entry : pointModels.entrySet()) {
            String name = entry.getKey();
            var nm = entry.getValue();
            String posStr = getOptionString(nm, "position");
            BlockPos pos = defaultPos;
            if (!posStr.isEmpty()) {
                String[] parts = posStr.split(",");
                if (parts.length == 3) {
                    try {
                        pos = new BlockPos(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim())
                        );
                    } catch (Exception ignored) {}
                }
            }
            double radius = getOptionDouble(nm, "radius");
            int color = getOptionInt(nm, "display_color");
            boolean showRange = getOptionBool(nm, "show_range");
            boolean captured = getOptionBool(nm, "captured");
            String ownerTeam = getOptionString(nm, "owner_team");
            String ownerTeamOrNull = ownerTeam.isEmpty() ? null : ownerTeam;
            newPoints.put(name, new CaptureManager.CapturePointEntry(
                    name, pos, captured,
                    ownerTeamOrNull, null, 0, null,
                    radius, color, showRange));
        }

        // 收纳器中的据点不在当前画布中，但仍需保留到快照里，避免保存时丢失。
        for (var entry : trayPoints.entrySet()) {
            newPoints.putIfAbsent(entry.getKey(), entry.getValue());
        }

        // ---- Phase 4: 条件链评估（替代旧硬编码 CaptureDecisionNode） ----
        // conditionRoutedPoints: zoneName → [pointName, ...] 通过条件链路由到区域的据点
        var conditionRoutedPoints = new LinkedHashMap<String, List<String>>();

        // 4a. 评估条件节点：对每个连接了 point_target 的条件节点，评估其条件
        // 并记录每个条件节点的每个输出端口指向的最终目标（zone 或 action）
        var conditionResults = new LinkedHashMap<String, Boolean>(); // conditionNodeName → boolean result
        var conditionInputPoints = new LinkedHashMap<String, List<String>>(); // conditionNodeName → [pointNames]

        // 收集条件节点的输入据点
        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;
                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;
                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;

                // 据点的 point_signal → 条件节点的 point_target
                if (hasOutputPort(fromNm, "point_signal") && hasInputPort(toNm, "point_target")) {
                    conditionInputPoints.computeIfAbsent(toName, k -> new ArrayList<>()).add(fromName);
                }
            }
        }

        // 评估每个条件节点
        for (var entry : conditionModels.entrySet()) {
            String condName = entry.getKey();
            var nm = entry.getValue();
            String propertyStr = getOptionString(nm, "property");
            String operatorStr = getOptionString(nm, "operator");
            String compareValue = getOptionString(nm, "compare_value");
            var property = CaptureConditionNode.PropertyType.fromId(propertyStr);
            var operator = CaptureConditionNode.OperatorType.fromId(operatorStr);

            List<String> inputPoints = conditionInputPoints.get(condName);
            if (inputPoints == null || inputPoints.isEmpty()) continue;

            for (String pointName : inputPoints) {
                var pointEntry = newPoints.get(pointName);
                if (pointEntry == null) continue;

                boolean result = evaluateNewCondition(property, operator, compareValue, pointEntry, null);
                String outputPort = result ? "true_out" : "false_out";

                // 查找条件节点的输出连线 → 区域/动作/逻辑门
                routeConditionOutput(condName, outputPort, pointName,
                        conditionRoutedPoints, conditionModels, gateModels,
                        zoneModels, newPoints, graph, property, operator, compareValue);
            }
        }

        // 4b. 兼容旧版判断器节点（CaptureDecisionNode）
        var oldDecisionRoutedPoints = new LinkedHashMap<String, List<String>>();

        for (var decisionEntry : oldDecisionModels.entrySet()) {
            String decisionName = decisionEntry.getKey();
            var nm = decisionEntry.getValue();

            String oldCondition = getOptionString(nm, "condition");
            String targetTeam = getOptionString(nm, "target_team");

            List<String> inputPoints = decisionInputs.get(decisionName);
            if (inputPoints == null || inputPoints.isEmpty()) continue;

            Map<String, List<String>> outputs = decisionOutputs.get(decisionName);
            if (outputs == null || outputs.isEmpty()) continue;

            for (String pointName : inputPoints) {
                var pointEntry = newPoints.get(pointName);
                if (pointEntry == null) continue;

                boolean conditionMet = evaluateOldCondition(oldCondition, targetTeam, pointEntry);

                String outputPort = conditionMet ? "true_out" : "false_out";
                List<String> targetZones = outputs.get(outputPort);
                if (targetZones == null || targetZones.isEmpty()) continue;

                for (String zoneName : targetZones) {
                    oldDecisionRoutedPoints.computeIfAbsent(zoneName, k -> new ArrayList<>()).add(pointName);
                }
            }
        }

        // ---- Phase 5: 构建区域数据 + 双向同步 ----
        for (var entry : zoneModels.entrySet()) {
            String name = entry.getKey();
            var nm = entry.getValue();

            boolean zoneCaptured = getOptionBool(nm, "captured");

            // 依赖区域：仅从连线（zone_out → required_zone）解析
            String reqZone = wireBasedRequiredZone.get(name);

            // 合并据点：直连 + 条件路由 + 旧判断器路由
            var cpList = new ArrayList<String>();
            var directPoints = wireBasedZonePoints.get(name);
            if (directPoints != null) {
                cpList.addAll(directPoints);
            }
            var routedPoints = conditionRoutedPoints.get(name);
            if (routedPoints != null) {
                for (String rp : routedPoints) {
                    if (!cpList.contains(rp)) {
                        cpList.add(rp);
                    }
                }
            }
            // 旧判断器路由（兼容）
            var oldRouted = oldDecisionRoutedPoints.get(name);
            if (oldRouted != null) {
                for (String rp : oldRouted) {
                    if (!cpList.contains(rp)) {
                        cpList.add(rp);
                    }
                }
            }

            // 规则③：区域占领状态 → 强制同步到所有子据点
            for (var cpName : cpList) {
                var existing = newPoints.get(cpName);
                if (existing != null) {
                    newPoints.put(cpName, existing.withCaptured(zoneCaptured));
                }
            }

            newZones.put(name, new CaptureManager.ZoneEntry(
                    name, cpList, reqZone, zoneCaptured, null, new ArrayList<>(), false));
        }

        // 收纳器中的据点如果原本已属于某个区域，则保留该关系。
        var currentManager = getCaptureManager();
        if (currentManager != null) {
            for (var trayPointName : trayPoints.keySet()) {
                String zoneName = currentManager.findZoneForPoint(trayPointName);
                if (zoneName == null) continue;
                var zone = newZones.get(zoneName);
                if (zone == null || zone.capturePoints().contains(trayPointName)) continue;
                var cpList = new ArrayList<>(zone.capturePoints());
                cpList.add(trayPointName);
                newZones.put(zoneName, zone.withCapturePoints(cpList));
            }
        }

        // ---- Phase 6: 条件/逻辑门→unlock_in / lock_in 锁定信号路由 ----
        // 规则：
        // 1. lock_in 任意一个为 true → 区域锁定
        // 2. 若存在 unlock_in 连线，则至少一个 unlock_in 为 true 才解锁
        var lockedZones = new LinkedHashSet<String>();
        var unlockedZones = new LinkedHashSet<String>();
        var zonesWithUnlockInput = new LinkedHashSet<String>();

        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;
                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;
                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;

                // 条件节点 true_out/false_out → 区域 unlock_in / lock_in
                if (conditionModels.containsKey(fromName) && (hasInputPort(toNm, "unlock_in") || hasInputPort(toNm, "lock_in"))) {
                    String portName = fromPort.getUniqueName();
                    String targetPort = toPort.getUniqueName();
                    if (portName == null) continue;
                    if (targetPort == null) continue;

                    List<String> inputPoints = conditionInputPoints.get(fromName);
                    if (inputPoints == null || inputPoints.isEmpty()) continue;

                    for (String pointName : inputPoints) {
                        var pointEntry = newPoints.get(pointName);
                        if (pointEntry == null) continue;

                        String propStr = getOptionStringStatic(fromNm, "property");
                        String opStr = getOptionStringStatic(fromNm, "operator");
                        String compareValue = getOptionStringStatic(fromNm, "compare_value");
                        var prop = CaptureConditionNode.PropertyType.fromId(propStr);
                        var op = CaptureConditionNode.OperatorType.fromId(opStr);
                        boolean result = evaluateNewCondition(prop, op, compareValue, pointEntry, newZones.get(toName));

                        boolean outputValue = "true_out".equals(portName) ? result : !result;
                        if ("unlock_in".equals(targetPort)) {
                            zonesWithUnlockInput.add(toName);
                            if (outputValue) {
                                unlockedZones.add(toName);
                            }
                        } else if ("lock_in".equals(targetPort) && outputValue) {
                            lockedZones.add(toName);
                        }
                    }
                }
                // 逻辑门 result → 区域 unlock_in / lock_in
                else if (gateModels.containsKey(fromName) && (hasInputPort(toNm, "unlock_in") || hasInputPort(toNm, "lock_in"))) {
                    String portName = fromPort.getUniqueName();
                    String targetPort = toPort.getUniqueName();
                    if (portName == null || !portName.equals("result")) continue;
                    if (targetPort == null) continue;

                    // 简化的门评估：查找条件节点输入到此门的所有路径
                    boolean gateResult = evaluateGateSimple(fromName, conditionModels, gateModels,
                            conditionInputPoints, newPoints, graph);

                    if ("unlock_in".equals(targetPort)) {
                        zonesWithUnlockInput.add(toName);
                        if (gateResult) {
                            unlockedZones.add(toName);
                        }
                    } else if ("lock_in".equals(targetPort) && gateResult) {
                        lockedZones.add(toName);
                    }
                }
            }
        }

        // 应用锁定状态：lock_in 优先；若存在 unlock_in 但没有任何 true，也视为锁定
        for (var zoneName : newZones.keySet()) {
            boolean isLocked = lockedZones.contains(zoneName)
                    || (zonesWithUnlockInput.contains(zoneName) && !unlockedZones.contains(zoneName));
            if (isLocked) {
                var existing = newZones.get(zoneName);
                newZones.put(zoneName, existing.withLocked(true));
            } else if (unlockedZones.contains(zoneName) || zonesWithUnlockInput.contains(zoneName)) {
                var existing = newZones.get(zoneName);
                newZones.put(zoneName, existing.withLocked(false));
            }
        }

        // 解锁依赖已经不再通过 save-time 连线解析管理，
        // 完全由 CaptureActionNode 在运行时通过 CaptureManager API 控制。

        // 规则②：根据所有子据点的占领状态重新计算区域 captured
        for (var zoneName : List.copyOf(newZones.keySet())) {
            var zone = newZones.get(zoneName);
            boolean allCaptured = !zone.capturePoints().isEmpty();
            for (var cpName : zone.capturePoints()) {
                var cp = newPoints.get(cpName);
                if (cp == null || !cp.captured()) {
                    allCaptured = false;
                    break;
                }
            }
            newZones.put(zoneName, zone.withCaptured(allCaptured));
        }

        return new AbstractMap.SimpleEntry<>(newPoints, newZones);
    }

    /**
     * 从据点/区域实体中获取指定属性的字符串值。
     */
    private static String getPropertyValue(CaptureConditionNode.PropertyType property,
                                            @Nullable CaptureManager.CapturePointEntry pointEntry,
                                            @Nullable CaptureManager.ZoneEntry zoneEntry) {
        return switch (property) {
            case CAPTURED -> String.valueOf(pointEntry != null && pointEntry.captured());
            case OWNER_TEAM -> pointEntry != null && pointEntry.ownerTeam() != null ? pointEntry.ownerTeam() : "";
            case CAPTURING_TEAM -> pointEntry != null && pointEntry.capturingTeam() != null ? pointEntry.capturingTeam() : "";
            case PROGRESS -> String.valueOf(pointEntry != null ? pointEntry.captureProgress() : 0);
            case ZONE_CAPTURED -> String.valueOf(zoneEntry != null && zoneEntry.captured());
            case ZONE_OWNER_TEAM -> zoneEntry != null && zoneEntry.ownerTeam() != null ? zoneEntry.ownerTeam() : "";
            case ZONE_ACCESSIBLE -> String.valueOf(zoneEntry != null); // 简化：区域存在即可访问
        };
    }

    /**
     * 评估新版条件节点：提取输入值 → 运算符比较。
     * 逻辑：输入值 [operator] 比较值
     */
    private static boolean evaluateNewCondition(CaptureConditionNode.PropertyType property,
                                                  CaptureConditionNode.OperatorType operator,
                                                  String compareValue,
                                                  @Nullable CaptureManager.CapturePointEntry pointEntry,
                                                  @Nullable CaptureManager.ZoneEntry zoneEntry) {
        if (property == null || operator == null) return false;
        String actualValue = getPropertyValue(property, pointEntry, zoneEntry);
        String cv = compareValue != null ? compareValue : "";
        return operator.evaluate(actualValue, cv);
    }

    /**
     * 简化版逻辑门评估：仅评估从条件节点直接输入到此门的布尔值。
     * 用于 unlock_in 路由中的门评估。
     */
    private static boolean evaluateGateSimple(String gateName,
                                               Map<String, NodeModel> conditionModels,
                                               Map<String, NodeModel> gateModels,
                                               Map<String, List<String>> conditionInputPoints,
                                               Map<String, CaptureManager.CapturePointEntry> newPoints,
                                               CapturePointGraph graph) {
        var nm = gateModels.get(gateName);
        if (nm == null) return false;
        String gateTypeStr = getOptionStringStatic(nm, "gate_type");
        var gateType = LogicGateNode.GateType.fromId(gateTypeStr);

        boolean inputA = false, inputB = false;
        boolean hasA = false, hasB = false;

        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;
                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;
                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;
                if (!toName.equals(gateName)) continue;
                String toPortName = toPort.getUniqueName();
                if (toPortName == null) continue;

                if (conditionModels.containsKey(fromName)) {
                    String propStr = getOptionStringStatic(fromNm, "property");
                    String opStr = getOptionStringStatic(fromNm, "operator");
                    String cmpVal = getOptionStringStatic(fromNm, "compare_value");
                    var prop = CaptureConditionNode.PropertyType.fromId(propStr);
                    var op = CaptureConditionNode.OperatorType.fromId(opStr);
                    List<String> pts = conditionInputPoints.get(fromName);
                    boolean condResult = false;
                    if (pts != null && !pts.isEmpty()) {
                        var pe = newPoints.get(pts.get(0));
                        condResult = evaluateNewCondition(prop, op, cmpVal, pe, null);
                    }
                    if ("in_a".equals(toPortName)) { inputA = condResult; hasA = true; }
                    else if ("in_b".equals(toPortName)) { inputB = condResult; hasB = true; }
                } else if (gateModels.containsKey(fromName)) {
                    boolean subResult = evaluateGateSimple(fromName, conditionModels, gateModels,
                            conditionInputPoints, newPoints, graph);
                    if ("in_a".equals(toPortName)) { inputA = subResult; hasA = true; }
                    else if ("in_b".equals(toPortName)) { inputB = subResult; hasB = true; }
                } else if (hasOutputPort(fromNm, "value")) {
                    boolean constVal = getOptionBoolStatic(fromNm, "constant_value");
                    if ("in_a".equals(toPortName)) { inputA = constVal; hasA = true; }
                    else if ("in_b".equals(toPortName)) { inputB = constVal; hasB = true; }
                }
            }
        }
        return gateType.evaluate(inputA, gateType == LogicGateNode.GateType.NOT ? false : inputB);
    }

    /**
     * 评估旧版 CaptureDecisionNode 的条件（兼容旧数据）。
     */
    private static boolean evaluateOldCondition(String condition, String targetTeam,
                                                  CaptureManager.CapturePointEntry pointEntry) {
        if (condition == null || condition.isEmpty()) condition = "captured";

        return switch (condition) {
            case "captured" -> pointEntry.captured();
            case "not_captured" -> !pointEntry.captured();
            case "owner_team" -> {
                if (targetTeam == null || targetTeam.isEmpty()) yield false;
                yield targetTeam.equals(pointEntry.ownerTeam());
            }
            case "capturing" -> {
                if (targetTeam == null || targetTeam.isEmpty()) {
                    yield pointEntry.capturingTeam() != null;
                }
                yield targetTeam.equals(pointEntry.capturingTeam());
            }
            case "not_capturing" -> pointEntry.capturingTeam() == null;
            default -> false;
        };
    }

    /**
     * 从条件节点的输出端口出发，沿连线追踪到目标区域/逻辑门，递归路由据点。
     */
    private static void routeConditionOutput(String sourceNodeName, String sourceOutputPort,
                                               String pointName,
                                               Map<String, List<String>> conditionRoutedPoints,
                                               Map<String, NodeModel> conditionModels,
                                               Map<String, NodeModel> gateModels,
                                               Map<String, NodeModel> zoneModels,
                                               Map<String, CaptureManager.CapturePointEntry> newPoints,
                                               CapturePointGraph graph,
                                               CaptureConditionNode.PropertyType property,
                                               CaptureConditionNode.OperatorType operator,
                                               String compareValue) {
        // 遍历连线，查找从 sourceNodeName.sourceOutputPort 出发的连线
        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;
                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;
                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;

                // 匹配源节点和输出端口
                if (!fromName.equals(sourceNodeName)) continue;
                String portName = fromPort.getUniqueName();
                if (portName == null || !portName.equals(sourceOutputPort)) continue;

                // 目标是区域：路由据点
                if (zoneModels.containsKey(toName)) {
                    conditionRoutedPoints.computeIfAbsent(toName, k -> new ArrayList<>()).add(pointName);
                }
                // 目标是逻辑门：需要进一步追踪门的输出
                else if (gateModels.containsKey(toName)) {
                    boolean gateResult = evaluateGateRecursive(toName, pointName,
                            conditionModels, gateModels, zoneModels,
                            newPoints, conditionRoutedPoints, graph,
                            property, operator, compareValue);
                }
            }
        }
    }

    /**
     * 递归评估逻辑门节点，沿输出追踪到最终区域。
     */
    private static boolean evaluateGateRecursive(String gateName, String pointName,
                                                   Map<String, NodeModel> conditionModels,
                                                   Map<String, NodeModel> gateModels,
                                                   Map<String, NodeModel> zoneModels,
                                                   Map<String, CaptureManager.CapturePointEntry> newPoints,
                                                   Map<String, List<String>> conditionRoutedPoints,
                                                   CapturePointGraph graph,
                                                   CaptureConditionNode.PropertyType property,
                                                   CaptureConditionNode.OperatorType operator,
                                                   String compareValue) {
        var nm = gateModels.get(gateName);
        if (nm == null) return false;

        String gateTypeStr = getOptionStringStatic(nm, "gate_type");
        var gateType = LogicGateNode.GateType.fromId(gateTypeStr);

        // 从连线收集此门的输入值
        boolean inputA = false;
        boolean inputB = false;
        boolean hasA = false;
        boolean hasB = false;

        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;
                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;
                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;

                if (!toName.equals(gateName)) continue;
                String toPortName = toPort.getUniqueName();
                if (toPortName == null) continue;

                // 上游是条件节点
                if (conditionModels.containsKey(fromName)) {
                    String propStr = getOptionStringStatic(fromNm, "property");
                    String opStr = getOptionStringStatic(fromNm, "operator");
                    String cmpVal = getOptionStringStatic(fromNm, "compare_value");
                    var prop = CaptureConditionNode.PropertyType.fromId(propStr);
                    var op = CaptureConditionNode.OperatorType.fromId(opStr);
                    var pointEntry = newPoints.get(pointName);
                    boolean result = evaluateNewCondition(prop, op, cmpVal, pointEntry, null);

                    if ("in_a".equals(toPortName)) { inputA = result; hasA = true; }
                    else if ("in_b".equals(toPortName)) { inputB = result; hasB = true; }
                }
                // 上游是另一个逻辑门（递归）
                else if (gateModels.containsKey(fromName)) {
                    boolean subResult = evaluateGateRecursive(fromName, pointName,
                            conditionModels, gateModels, zoneModels,
                            newPoints, conditionRoutedPoints, graph,
                            property, operator, compareValue);
                    if ("in_a".equals(toPortName)) { inputA = subResult; hasA = true; }
                    else if ("in_b".equals(toPortName)) { inputB = subResult; hasB = true; }
                }
                // 上游是常量节点
                else if (hasOutputPort(fromNm, "value")) {
                    boolean constVal = getOptionBoolStatic(fromNm, "constant_value");
                    if ("in_a".equals(toPortName)) { inputA = constVal; hasA = true; }
                    else if ("in_b".equals(toPortName)) { inputB = constVal; hasB = true; }
                }
            }
        }

        // NOT 门只使用 inputA
        boolean gateResult = gateType.evaluate(inputA, gateType == LogicGateNode.GateType.NOT ? false : inputB);

        // 从此门的 result 输出端口继续追踪
        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;
                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;
                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;

                if (!fromName.equals(gateName)) continue;
                String portName = fromPort.getUniqueName();
                if (portName == null || !portName.equals("result")) continue;

                // 输出到区域
                if (zoneModels.containsKey(toName)) {
                    conditionRoutedPoints.computeIfAbsent(toName, k -> new ArrayList<>()).add(pointName);
                }
                // 输出到另一个门（级联）
                else if (gateModels.containsKey(toName)) {
                    evaluateGateRecursive(toName, pointName,
                            conditionModels, gateModels, zoneModels,
                            newPoints, conditionRoutedPoints, graph,
                            property, operator, compareValue);
                }
            }
        }

        return gateResult;
    }

    /**
     * 静态版本 getOptionString，用于 routeConditionOutput 中访问节点模型。
     */
    private static String getOptionStringStatic(NodeModel nm, String id) {
        try {
            if (nm instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.INodeWithOptions opts) {
                var opt = opts.getNodeOptionById(id);
                if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                    var port = nodeOpt.getPortModel();
                    if (port instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable cfg) {
                        var v = cfg.getValue();
                        if (v == null) return "";
                        if (v instanceof CaptureConditionNode.PropertyType pt) return pt.getSerializationId();
                        if (v instanceof CaptureConditionNode.OperatorType ot) return ot.getSerializationId();
                        if (v instanceof LogicGateNode.GateType gt) return gt.getSerializationId();
                        if (v instanceof CaptureActionNode.ActionType at) return at.getSerializationId();
                        return v.toString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * 静态版本 getOptionBool。
     */
    private static boolean getOptionBoolStatic(NodeModel nm, String id) {
        String val = getOptionStringStatic(nm, id);
        if (val == null || val.isEmpty()) return false;
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    /**
     * 将当前节点图中的所有更改保存到 CaptureManager。
     * 使用快照方式：构建完整数据 → 版本检测 → applyGraphSnapshot 原子写入。
     */
    private void saveGraph() {
        var mgr = getCaptureManager();
        if (mgr == null) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.translatable("toast.capture_point_graph.no_server_access"));
            return;
        }

        try {
            // 构建数据快照
            var snapshot = buildSnapshotFromGraph();
            var newPoints = snapshot.getKey();
            var newZones = snapshot.getValue();

            // 收集所有节点的布局信息 + 判断器节点数据 + 通用节点选项 + 连线数据 + 视角状态
            var layouts = new LinkedHashMap<String, CaptureManager.NodeLayout>();
            var decisions = new LinkedHashMap<String, CaptureManager.DecisionNodeData>();
            var nodeOpts = new LinkedHashMap<String, Map<String, String>>();
            var wireData = collectGraphWires();
            // 获取当前视角状态
            CaptureManager.ViewState currentViewState = null;
            try {
                currentViewState = graphView.getCurrentViewState();
            } catch (Exception ignored) {}

            for (var element : graph.graphModel.getGraphElementModels()) {
                if (element instanceof NodeModel nm) {
                    String name = nm.getName();
                    if (name == null || name.isEmpty()) continue;
                    var pos = nm.getPosition();
                    if (pos != null) {
                        layouts.put(name, new CaptureManager.NodeLayout(pos.x(), pos.y()));
                    }
                    // 收集此节点的所有选项值（条件/逻辑门/动作/常量等）
                    var optMap = new LinkedHashMap<String, String>();
                    if (nm instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.INodeWithOptions opts) {
                        for (var opt : nm.getNodeOptions()) {
                            var optId = opt.getId();
                            if (optId == null || optId.isEmpty()) continue;
                            var port = opt.getPortModel();
                            if (port instanceof IFieldValueConfigurable cfg) {
                                var val = cfg.getValue();
                                String serialized;
                                if (val == null) {
                                    serialized = "";
                                } else if (val instanceof CaptureConditionNode.PropertyType pt) {
                                    serialized = pt.getSerializationId();
                                } else if (val instanceof CaptureConditionNode.OperatorType ot) {
                                    serialized = ot.getSerializationId();
                                } else if (val instanceof LogicGateNode.GateType gt) {
                                    serialized = gt.getSerializationId();
                                } else if (val instanceof CaptureActionNode.ActionType at) {
                                    serialized = at.getSerializationId();
                                } else {
                                    serialized = val.toString();
                                }
                                optMap.put(optId, serialized);
                            }
                        }
                    }
                    if (!optMap.isEmpty()) {
                        nodeOpts.put(name, optMap);
                    }
                }
            }

            // 编辑模式下进行版本冲突检测
            if (editMode && snapshotVersion >= 0) {
                long currentVersion = mgr.getVersion();
                if (currentVersion != snapshotVersion) {
                    // 数据已被外部修改（命令/方块），弹出确认对话框
                    openConflictDialog(mgr, newPoints, newZones, layouts, decisions, nodeOpts, wireData, currentViewState);
                    return;
                }
            }

            // 无冲突或非编辑模式：直接应用（含布局 + 判断器 + 节点选项 + 连线数据 + 视角状态）
            mgr.applyGraphSnapshotWithLayout(newPoints, newZones, layouts, decisions, nodeOpts, wireData, currentViewState);

            // 立即同步所有已加载方块实体的渲染缓存
            var serverLevel = getServerLevel();
            if (serverLevel != null) {
                CapturePointBlockEntity.syncAllBoundBlocks(serverLevel);
            }

            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_graph.saved"));

        } catch (Exception e) {
            LOGGER.error("Save graph failed: {}", e.getMessage(), e);
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.translatable("toast.capture_point_graph.save_failed", e.getMessage()));
        }
    }

    /** 读取节点模型的 String 类型选项值 */
    @Nullable
    private static String readOptionString(NodeModel nm, String optionId) {
        try {
            if (nm instanceof INodeWithOptions opts) {
                var opt = opts.getNodeOptionById(optionId);
                if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                    var port = nodeOpt.getPortModel();
                    if (port instanceof IFieldValueConfigurable cfg) {
                        var val = cfg.getValue();
                        return val != null ? val.toString() : null;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 读取节点模型的 int 类型选项值 */
    private static int readOptionInt(NodeModel nm, String optionId) {
        try {
            if (nm instanceof INodeWithOptions opts) {
                var opt = opts.getNodeOptionById(optionId);
                if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                    var port = nodeOpt.getPortModel();
                    if (port instanceof IFieldValueConfigurable cfg) {
                        var val = cfg.getValue();
                        if (val instanceof Number n) return n.intValue();
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * 打开版本冲突对话框，询问用户是否覆盖外部修改的数据。
     */
    private void openConflictDialog(CaptureManager captureManager,
                                     Map<String, CaptureManager.CapturePointEntry> newPoints,
                                     Map<String, CaptureManager.ZoneEntry> newZones,
                                     Map<String, CaptureManager.NodeLayout> layouts,
                                     Map<String, CaptureManager.DecisionNodeData> decisions,
                                     Map<String, Map<String, String>> nodeOpts,
                                     List<CaptureManager.GraphWireData> wireData,
                                     @Nullable CaptureManager.ViewState viewState) {
        var mc = mc();
        int scw = mc.getWindow().getGuiScaledWidth();
        int sch = mc.getWindow().getGuiScaledHeight();
        int dw = Math.min(scw * 50 / 100, 340);
        int dh = Math.min(130, (int)(sch * 0.8));

        var root = CapturePointTheme.panel()
                .layout(l -> l.width(dw).height(dh).paddingAll(12).gapAll(8)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN));

        var title = CapturePointTheme.titleLabel(
                Component.translatable("gui.capture_point_graph.dialog.conflict.title"));
        title.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(title);

        var msg = CapturePointTheme.secondaryLabel(
                Component.translatable("gui.capture_point_graph.dialog.conflict.message"));
        msg.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(msg);

        var spacer = new UIElement().layout(l -> l.flex(1));

        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(30)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6));
        var overwriteBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.capture_point_graph.dialog.conflict.overwrite"));
        overwriteBtn.layout(l -> l.flex(1).heightPercent(100));
        overwriteBtn.setOnClick(e -> {
            var overwriteMgr = getCaptureManager();
            if (overwriteMgr != null) {
                overwriteMgr.applyGraphSnapshotWithLayout(newPoints, newZones, layouts, decisions, nodeOpts, wireData, viewState);
            } else {
                captureManager.applyGraphSnapshotWithLayout(newPoints, newZones, layouts, decisions, nodeOpts, wireData, viewState);
            }

            var sl = getServerLevel();
            if (sl != null) {
                CapturePointBlockEntity.syncAllBoundBlocks(sl);
            }

            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_graph.saved"));
            mc.setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        var cancelBtn = CapturePointTheme.styledButton(
                Component.translatable("gui.capture_point_graph.dialog.cancel"));
        cancelBtn.layout(l -> l.flex(1).heightPercent(100));
        cancelBtn.setOnClick(e -> {
            mc.setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        btnRow.addChildren(overwriteBtn, cancelBtn);
        root.addChildren(title, msg, spacer, btnRow);

        var wrap = new UIElement()
                .layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(0).gapAll(0)
                        .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                        .alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER));
        wrap.addChildren(root);

        var ui = ModularUI.of(UI.of(wrap));
        mc.setScreen(new ModularUIScreen(ui,
                Component.translatable("gui.capture_point_graph.dialog.conflict.title")));
    }

    /** 检查节点是否有指定名称的输出端口 */
    private static boolean hasOutputPort(NodeModel nm, String portName) {
        return nm.getOutputsById().containsKey(portName);
    }

    /** 检查节点是否有指定名称的输入端口 */
    private static boolean hasInputPort(NodeModel nm, String portName) {
        return nm.getInputsById().containsKey(portName);
    }

    @Nullable
    private NodeModel findNodeModelByName(String nodeName) {
        if (nodeName == null || nodeName.isEmpty()) return null;
        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof NodeModel nm && nodeName.equals(nm.getName())) {
                return nm;
            }
        }
        return null;
    }

    // ================================================================
    //  从 CaptureManager 加载数据到节点图
    // ================================================================

    private void loadDataToGraph() {
        try {
            var mgr = getCaptureManager();
            if (mgr == null) return;
            trayPoints.clear();

            var points = mgr.getPoints();
            var zones = mgr.getZones();
            if (points.isEmpty() && zones.isEmpty()) return;

            // 获取保存的节点布局（如果有的话）
            var savedLayouts = mgr.getNodeLayouts();
            // 获取保存的通用节点选项
            var savedNodeOptions = mgr.getNodeOptions();
            var pointModels = new LinkedHashMap<String, NodeModel>();
            var zoneModels = new LinkedHashMap<String, NodeModel>();

            float startX = 100;
            float startY = 100;
            float gapX = 220;
            float gapY = 120;
            int idx = 0;

            // 创建据点节点（优先使用保存的布局）
            for (var entry : points.values()) {
                var saved = savedLayouts.get(entry.name());
                if (saved == null) {
                    trayPoints.put(entry.name(), entry);
                    continue;
                }
                float x = saved != null ? saved.x() : (startX + (idx % 4) * gapX);
                float y = saved != null ? saved.y() : (startY + (idx / 4) * gapY);
                var node = new CapturePointNode();
                var nodeModel = graph.graphModel.createNodeModel(node,
                        new org.joml.Vector2f(x, y));
                nodeModel.setName(entry.name());
                nodeModel.setTitle(Component.literal(entry.name()));
                pointModels.put(entry.name(), nodeModel);
                idx++;
            }

            // 创建区域节点（优先使用保存的布局）
            int zoneIdx = 0;
            for (var entry : zones.values()) {
                var saved = savedLayouts.get(entry.name());
                float x = saved != null ? saved.x() : (startX + 250 + (zoneIdx % 3) * gapX);
                float y = saved != null ? saved.y() : (startY + (zoneIdx / 3) * gapY + gapY);
                var node = new CaptureZoneNode();
                var nodeModel = graph.graphModel.createNodeModel(node,
                        new org.joml.Vector2f(x, y));
                nodeModel.setName(entry.name());
                nodeModel.setTitle(Component.literal(entry.name()));
                zoneModels.put(entry.name(), nodeModel);
                zoneIdx++;
            }

            // 重建逻辑节点（条件/逻辑门/动作/常量）：从 nodeOptions 检测类型
            var allKnownNames = new java.util.HashSet<String>();
            allKnownNames.addAll(pointModels.keySet());
            allKnownNames.addAll(zoneModels.keySet());
            var logicNodeNames = new java.util.LinkedHashSet<String>();
            logicNodeNames.addAll(savedLayouts.keySet());
            logicNodeNames.addAll(savedNodeOptions.keySet());
            logicNodeNames.removeAll(allKnownNames);

            for (var name : logicNodeNames) {
                if (name == null || name.isEmpty()) continue;
                var saved = savedLayouts.get(name);
                float x = saved != null ? saved.x() : 200;
                float y = saved != null ? saved.y() : 200;
                var opts = savedNodeOptions.get(name);

                com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node logicNode = null;
                if (opts != null) {
                    if (opts.containsKey("property") || opts.containsKey("condition_type")) {
                        logicNode = new CaptureConditionNode();
                    } else if (opts.containsKey("gate_type")) {
                        logicNode = new LogicGateNode();
                    } else if (opts.containsKey("action_type")) {
                        logicNode = new CaptureActionNode();
                    } else if (opts.containsKey("constant_value")) {
                        logicNode = new ConstantNode();
                    }
                }
                if (logicNode == null) continue;

                var nm = graph.graphModel.createNodeModel(logicNode,
                        new org.joml.Vector2f(x, y));
                nm.setName(name);
                nm.setTitle(Component.literal(name));
            }

            // 建立连线
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
                    } catch (Exception e) {
                        LOGGER.warn("Failed to create wire from point '{}' to zone '{}': {}",
                                pointName, zoneEntry.name(), e.getMessage());
                    }
                }
            }

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
                } catch (Exception e) {
                    LOGGER.warn("Failed to create dependency wire from zone '{}' to zone '{}': {}",
                            zoneEntry.requiredZone(), zoneEntry.name(), e.getMessage());
                }
            }

            // 恢复保存的逻辑连线（条件/逻辑门/动作/常量之间）
            for (var wireData : mgr.getGraphWires()) {
                restoreWire(wireData.fromNode(), wireData.fromPort(),
                        wireData.toNode(), wireData.toPort());
            }

            // 恢复通用节点选项（条件/逻辑门/动作/常量的选项值）
            for (var element : graph.graphModel.getGraphElementModels()) {
                if (element instanceof NodeModel nm) {
                    String name = nm.getName();
                    if (name == null || name.isEmpty()) continue;
                    var savedOpts = savedNodeOptions.get(name);
                    if (savedOpts == null || savedOpts.isEmpty()) continue;
                    for (var optEntry : savedOpts.entrySet()) {
                        String optId = optEntry.getKey();
                        String optVal = optEntry.getValue();
                        if (optId == null || optVal == null) continue;
                        Object typedVal = convertOptionValue(optId, optVal);
                        if (typedVal != null) {
                            setOptionValue(nm, optId, typedVal);
                        }
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to load data to graph: {}", e.getMessage());
        }
    }

    /**
     * 从保存的连线数据中恢复一条连线。
     * 在 graphModel 中查找对应的节点和端口，然后创建连线。
     */
    private void restoreWire(String fromNodeName, String fromPortName,
                              String toNodeName, String toPortName) {
        try {
            for (var element : graph.graphModel.getGraphElementModels()) {
                if (element instanceof NodeModel nm) {
                    String name = nm.getName();
                    if (name == null) continue;
                    if (name.equals(fromNodeName)) {
                        var fromPort = nm.getOutputsById().get(fromPortName);
                        var toNode = findNodeModel(toNodeName);
                        if (fromPort != null && toNode != null) {
                            var toPort = toNode.getInputsById().get(toPortName);
                            if (toPort != null) {
                                graph.graphModel.createWire(fromPort, toPort);
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to restore wire {}:{} -> {}:{}: {}",
                    fromNodeName, fromPortName, toNodeName, toPortName, e.getMessage());
        }
    }

    /** 按名称查找节点模型 */
    @Nullable
    private NodeModel findNodeModel(String name) {
        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof NodeModel nm) {
                if (name.equals(nm.getName())) return nm;
            }
        }
        return null;
    }

    /**
     * 查找连接到此条件节点的第一个据点名称（通过 point_target 连线）。
     * 用于 refreshNodeTitles 中的实时条件评估。
     */
    @Nullable
    private String findConnectedPointName(NodeModel conditionNm) {
        String condName = conditionNm.getName();
        if (condName == null) return null;
        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;
                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;
                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;
                // 据点的 point_signal → 条件节点的 point_target
                if (toName.equals(condName) && hasInputPort(toNm, "point_target")
                        && hasOutputPort(fromNm, "point_signal")) {
                    return fromName;
                }
            }
        }
        return null;
    }

    /**
     * 将字符串选项值转换为正确的类型（枚举/布尔/数值），<br>
     * 避免直接将字符串传给 IFieldValueConfigurable.setValue() 导致 ClassCastException。
     */
    @Nullable
    private static Object convertOptionValue(String optId, String optVal) {
        if (optVal == null) return null;

        // 枚举类型：必须使用枚举实例，不能传字符串
        switch (optId) {
            case "property":
                return CaptureConditionNode.PropertyType.fromId(optVal);
            case "operator":
                return CaptureConditionNode.OperatorType.fromId(optVal);
            case "gate_type":
                return LogicGateNode.GateType.fromId(optVal);
            case "action_type":
                return CaptureActionNode.ActionType.fromId(optVal);
        }

        // 布尔值
        if ("true".equalsIgnoreCase(optVal) || "false".equalsIgnoreCase(optVal)) {
            return Boolean.parseBoolean(optVal);
        }

        // 字符串类型的选项ID（不进行数值转换，防止 Integer→String 类型冲突）
        if (isStringOption(optId)) {
            return optVal;
        }

        // 尝试数值
        try {
            if (optVal.contains(".")) {
                return Double.parseDouble(optVal);
            }
            return Integer.parseInt(optVal);
        } catch (NumberFormatException ignored) {}

        // 字符串（如 target_name, compare_value 等）
        return optVal;
    }

    /** 已知为字符串类型的选项 ID 列表 */
    private static boolean isStringOption(String optId) {
        return switch (optId) {
            case "compare_value", "target_name", "action_value",
                 "position", "owner_team", "required_zone",
                 "points", "description", "edit_points",
                 "zone_progress" -> true;
            default -> false;
        };
    }

    // ================================================================
    //  实时刷新节点标题和选项（由 screenTick 每 ~0.75 秒驱动）
    // ================================================================

    /** 同步锁：当为 true 时，来自 Game→GUI 的同步不触发 GUI→Game 回写 */
    private boolean isSyncingFromGame = false;

    /**
     * 从 CaptureManager 读取最新数据，更新节点标题。
     * 非编辑模式：同步所有选项值（只读展示）
     * 编辑模式：只更新节点标题，不覆盖选项值（用户正在编辑）
     */
    public void refreshNodeTitles() {
        try {
            var mgr = getServerCaptureManager();
            if (mgr == null) return;

            var points = mgr.getPoints();
            var zones = mgr.getZones();

            isSyncingFromGame = true;
            try {
                for (var element : graph.graphModel.getGraphElementModels()) {
                    if (element instanceof NodeModel nm) {
                        String name = nm.getName();
                        if (name == null || name.isEmpty()) continue;

                        if (hasOutputPort(nm, "point_signal")) {
                            // 据点节点：显示名称 + 所属区域
                            var entry = points.get(name);
                            if (entry != null) {
                                String zoneName = mgr.findZoneForPoint(name);
                                String zoneDisplay = zoneName != null ? zoneName : "—";
                                nm.setTitle(Component.literal(name + " [" + zoneDisplay + "]"));
                                // 编辑模式下不覆盖选项值（用户正在编辑）
                                if (!editMode) {
                                    syncPointOptions(nm, entry, mgr.isZoneCaptured(name), zoneName);
                                }
                            } else {
                                nm.setTitle(Component.literal(name));
                            }
                        } else if (hasInputPort(nm, "target")) {
                            // 旧版判断器节点（兼容）：显示名称
                            nm.setTitle(Component.literal(name + " [旧判断器]"));
                        } else if (hasInputPort(nm, "point_target") || hasInputPort(nm, "zone_target")) {
                            // 条件节点：显示属性 + 运算符 + 比较值 + 实时评估布尔结果
                            String propStr = getOptionString(nm, "property");
                            String opStr = getOptionString(nm, "operator");
                            String cmpVal = getOptionString(nm, "compare_value");
                            var prop = CaptureConditionNode.PropertyType.fromId(propStr);
                            var op = CaptureConditionNode.OperatorType.fromId(opStr);
                            String propDisplay = prop.getDisplayName().getString();
                            String opDisplay = op.getDisplayName().getString();
                            String cmpDisplay = cmpVal != null && !cmpVal.isEmpty() ? " " + cmpVal : "";

                            // 尝试实时评估条件
                            String statusIcon = "?";
                            if (!editMode) {
                                try {
                                    String pointName = findConnectedPointName(nm);
                                    if (pointName != null && points.containsKey(pointName)) {
                                        var pEntry = points.get(pointName);
                                        boolean evalResult = evaluateNewCondition(prop, op, cmpVal, pEntry, null);
                                        statusIcon = evalResult ? "1" : "0"; // 统一逻辑：1=true, 0=false
                                    }
                                } catch (Exception ignored) {}
                            }

                            nm.setTitle(Component.literal(name + " [")
                                    .append(Component.literal(propDisplay + " " + opDisplay + cmpDisplay + " → " + statusIcon + "]")));
                        } else if (hasInputPort(nm, "in_a")) {
                            // 逻辑门节点：显示门类型 + 当前评估结果
                            String gtStr = getOptionString(nm, "gate_type");
                            if (gtStr == null || gtStr.isEmpty()) gtStr = "and";
                            var gt = LogicGateNode.GateType.fromId(gtStr);

                            // 尝试评估门的当前布尔值
                            String gateStatus = "?";
                            if (!editMode) {
                                try {
                                    boolean inputA = false, inputB = false;
                                    // 从连线收集输入
                                    for (var wireEl : graph.graphModel.getGraphElementModels()) {
                                        if (wireEl instanceof WireModel wire) {
                                            var fPort = wire.getFromPort();
                                            var tPort = wire.getToPort();
                                            if (fPort == null || tPort == null) continue;
                                            var fNode = fPort.getNodeModel();
                                            var tNode = tPort.getNodeModel();
                                            if (!(fNode instanceof NodeModel fNm) || !(tNode instanceof NodeModel tNm)) continue;
                                            String fName = fNm.getName();
                                            String tName = tNm.getName();
                                            if (fName == null || tName == null) continue;
                                            if (!tName.equals(name)) continue;
                                            String tPortName = tPort.getUniqueName();
                                            if (tPortName == null) continue;
                                            // 上游是条件节点
                                            if (hasInputPort(fNm, "point_target") || hasInputPort(fNm, "zone_target")) {
                                                String propStr2 = getOptionStringStatic(fNm, "property");
                                                String opStr2 = getOptionStringStatic(fNm, "operator");
                                                String cv2 = getOptionStringStatic(fNm, "compare_value");
                                                var prop2 = CaptureConditionNode.PropertyType.fromId(propStr2);
                                                var op2 = CaptureConditionNode.OperatorType.fromId(opStr2);
                                                String connPt = findConnectedPointName(fNm);
                                                var pEntry2 = connPt != null ? points.get(connPt) : null;
                                                boolean r = evaluateNewCondition(prop2, op2, cv2, pEntry2, null);
                                                if ("in_a".equals(tPortName)) inputA = r;
                                                else if ("in_b".equals(tPortName)) inputB = r;
                                            } else if (hasOutputPort(fNm, "value")) {
                                                boolean cv = getOptionBoolStatic(fNm, "constant_value");
                                                if ("in_a".equals(tPortName)) inputA = cv;
                                                else if ("in_b".equals(tPortName)) inputB = cv;
                                            }
                                        }
                                    }
                                    gateStatus = gt.evaluate(inputA, gt == LogicGateNode.GateType.NOT ? false : inputB) ? "1" : "0";
                                } catch (Exception ignored) {}
                            }

                            nm.setTitle(Component.literal(name + " [")
                                    .append(gt.getDisplayName())
                                    .append(Component.literal(" → " + gateStatus + "]")));
                        } else if (hasInputPort(nm, "trigger")) {
                            // 动作节点
                            String actionType = getOptionString(nm, "action_type");
                            if (actionType == null || actionType.isEmpty()) actionType = "set_captured";
                            var at = CaptureActionNode.ActionType.fromId(actionType);
                            String target = getOptionString(nm, "target_name");
                            String tgtDisplay = target != null && !target.isEmpty() ? " " + target : "";
                            nm.setTitle(Component.literal(name + " [")
                                    .append(at.getDisplayName())
                                    .append(Component.literal(tgtDisplay + "]")));
                        } else if (hasOutputPort(nm, "value")) {
                            // 常量节点
                            boolean constVal = getOptionBool(nm, "constant_value");
                            nm.setTitle(Component.literal(name + " [")
                                    .append(Component.literal(String.valueOf(constVal)))
                                    .append(Component.literal("]")));
                        } else if (hasOutputPort(nm, "zone_out") || hasInputPort(nm, "point_in")) {
                            // 区域节点：显示名称 + 占领状态 + 点数 + 锁定状态
                            var entry = zones.get(name);
                            if (entry != null) {
                                boolean captured = mgr.isZoneCaptured(name);
                                boolean accessible = mgr.canAccessZone(name);
                                int ptCount = entry.capturePoints().size();
                                String status = captured ? "✓" : "✗";
                                String access = accessible ? "" : " 🔒";
                                String lockIndicator = entry.locked() ? " ⛔" : "";
                                nm.setTitle(Component.literal(name + " [" + status + " " + ptCount + "pts" + access + lockIndicator + "]"));
                                // 编辑模式下不覆盖选项值
                                if (!editMode) {
                                    syncZoneOptions(nm, entry, captured, points);
                                }
                            } else {
                                nm.setTitle(Component.literal(name));
                            }
                        }
                    }
                }
            } finally {
                isSyncingFromGame = false;
            }
        } catch (Exception ignored) {}
    }

    /**
     * 通过 IFieldValueConfigurable API 正确设置节点选项值。
     * 这比反射可靠得多，会通过 Constant 更新值并自动刷新 UI。
     */
    private static void setOptionValue(NodeModel nm, String optionId, Object value) {
        try {
            if (nm instanceof INodeWithOptions opts) {
                var opt = opts.getNodeOptionById(optionId);
                // NodeOption 是 INodeOption 的实现类，包含 portModel 字段
                if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                    var port = nodeOpt.getPortModel();
                    if (port instanceof IFieldValueConfigurable configurable) {
                        configurable.setValue(value);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 同步据点节点选项（captured / position / radius / display_color / show_range）
     * "所属"信息由 mgr.findZoneForPoint 获取并在标题中展示。
     * 编辑模式字段（radius / display_color / show_range）始终从 CaptureManager 同步。
     */
    private static void syncPointOptions(NodeModel nm, CaptureManager.CapturePointEntry entry, boolean isCaptured, @org.jetbrains.annotations.Nullable String zoneName) {
        setOptionValue(nm, "captured", entry.captured());
        setOptionValue(nm, "owner_team", entry.ownerTeam() != null ? entry.ownerTeam() : "");
        setOptionValue(nm, "position", entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ());
        // 编辑模式额外字段
        setOptionValue(nm, "radius", entry.radius());
        setOptionValue(nm, "display_color", entry.displayColor());
        setOptionValue(nm, "show_range", entry.showRange());
    }

    /**
     * 同步区域节点选项（captured / required_zone / points / description / edit_points）
     */
    private static void syncZoneOptions(NodeModel nm, CaptureManager.ZoneEntry entry, boolean captured,
                                         Map<String, CaptureManager.CapturePointEntry> points) {
        setOptionValue(nm, "captured", captured);
        setOptionValue(nm, "owner_team", entry.ownerTeam() != null ? entry.ownerTeam() : "");
        setOptionValue(nm, "required_zone", entry.requiredZone() != null ? entry.requiredZone() : "");
        setOptionValue(nm, "points", String.join(", ", entry.capturePoints()));
        // 计算区域控制进度
        String progressStr = buildZoneProgressString(entry, points);
        setOptionValue(nm, "zone_progress", progressStr);
        // 编辑模式额外字段
        setOptionValue(nm, "description", "");
        setOptionValue(nm, "edit_points", String.join(", ", entry.capturePoints()));
    }

    /** 构建区域控制进度字符串，如 "红:75% 蓝:25%" */
    private static String buildZoneProgressString(CaptureManager.ZoneEntry entry,
                                                   Map<String, CaptureManager.CapturePointEntry> points) {
        var teamCount = new LinkedHashMap<String, Integer>();
        int total = 0;
        for (var cpName : entry.capturePoints()) {
            var cp = points.get(cpName);
            if (cp != null && cp.ownerTeam() != null) {
                teamCount.merge(cp.ownerTeam(), 1, Integer::sum);
                total++;
            }
        }
        if (total == 0) return "无";
        var sb = new StringBuilder();
        for (var e : teamCount.entrySet()) {
            if (!sb.isEmpty()) sb.append(" ");
            int pct = e.getValue() * 100 / total;
            sb.append(e.getKey()).append(":").append(pct).append("%");
        }
        return sb.toString();
    }

    // ================================================================
    //  GUI→Game 回写机制
    // ================================================================

    /**
     * 为所有节点选项的常量注册值变更监听器。
     * 当用户在 UI 中编辑选项（如修改 owner）时，自动写回 CaptureManager。
     * 配合 isSyncingFromGame 锁避免 Game→GUI 同步时触发回写造成循环。
     */
    private void registerOptionChangeListeners() {
        try {
            var access = getServerCaptureManager();
            if (access == null) return;

            for (var element : graph.graphModel.getGraphElementModels()) {
                if (element instanceof NodeModel nm) {
                    String name = nm.getName();
                    if (name == null || name.isEmpty()) continue;

                    boolean isPointNode = hasOutputPort(nm, "point_signal");
                    for (var opt : nm.getNodeOptions()) {
                        var port = opt.getPortModel();
                        var constant = port.getConfigurableConstant();
                        if (constant != null) {
                            var optId = opt.getId(); // 捕获变量
                            constant.addListener(newValue -> {
                                // 正在从游戏同步时不触发回写
                                if (isSyncingFromGame) return;
                                onOptionValueChanged(access, name, isPointNode, optId, newValue);
                            });
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 当节点选项值被用户编辑时调用。
     * 编辑模式下：变更仅保存在节点模型的 Constant 中，不写入 CaptureManager，
     * 所有改动通过 "保存" 按钮的 buildSnapshotFromGraph → applyGraphSnapshot 批量写入。
     * 非编辑模式：忽略回写（只读显示）。
     */
    private void onOptionValueChanged(ICaptureDataAccess access, String nodeName, boolean isPointNode,
                                       String optionId, Object newValue) {
        // 编辑模式下变更仅存储于节点模型的本地 Constant 中，
        // 等待用户点击"保存"时通过 buildSnapshotFromGraph → applyGraphSnapshot 批量写入。
        // 此处不做任何 CaptureManager 写入操作，以保持数据一致性。
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private ServerLevel getServerLevel() {
        if (level instanceof ServerLevel sl) return sl;
        var mc = mc();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getLevel(level.dimension());
        }
        return null;
    }

    /** 获取服务端统一数据访问接口（单机/服务端均可用） */
    private ICaptureDataAccess getServerCaptureManager() {
        if (level instanceof ServerLevel sl) return ICaptureDataAccess.server(sl);
        var mc = mc();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            var sl = mc.getSingleplayerServer().getLevel(level.dimension());
            if (sl != null) return ICaptureDataAccess.server(sl);
        }
        return null;
    }

    /** 获取 CaptureManager 原始实例（用于布局持久化等接口未暴露的操作） */
    @Nullable
    private CaptureManager getCaptureManager() {
        if (level instanceof ServerLevel sl) return CaptureManager.get(sl);
        var mc = mc();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            var sl = mc.getSingleplayerServer().getLevel(level.dimension());
            if (sl != null) return CaptureManager.get(sl);
        }
        return null;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
