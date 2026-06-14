package com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.INodeWithOptions;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.CaptureManager;
import com.xkmxz.attack_defense_capture_point_xkmxz.manager.ICaptureDataAccess;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.*;

/**
 * 据点管理节点图屏幕 - 使用 LDLib2 nodegraphtookit 框架提供完整的节点图编辑体验。
 * 支持保存节点关系到 CaptureManager。
 */
public class CapturePointGraphScreen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PANEL_BG = 0xFF1A1A2E;
    private static final int EDIT_MODE_BG = 0xFF2E1A1A;

    private final Level level;
    private final CapturePointGraphView graphView;
    private final CapturePointGraph graph;
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
        // 初始立即同步节点选项数据
        refreshNodeTitles();
        // 注册选项值变更监听器，实现 GUI→Game 实时回写
        registerOptionChangeListeners();

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

        // 编辑模式切换按钮（在保存按钮左侧）
        var editToggleBtn = new Button();
        updateEditToggleButton(editToggleBtn);
        editToggleBtn.layout(l -> l.width(60).heightPercent(100));
        editToggleBtn.setOnClick(e -> {
            editMode = !editMode;
            updateEditToggleButton(editToggleBtn);
            graphView.showPanelsForEditMode(editMode);
            tb.style(s -> s.backgroundTexture(new ColorRectTexture(editMode ? EDIT_MODE_BG : 0xFF16213E)));
            if (editMode) {
                // 进入编辑模式时快照版本号，用于保存时的冲突检测
                captureSnapshotVersion();
            }
            ToastNotification.push(editMode ? ToastNotification.Type.INFO : ToastNotification.Type.SUCCESS,
                    Component.translatable(editMode ? "toast.capture_point_graph.edit_mode.on" : "toast.capture_point_graph.edit_mode.off"));
        });

        // 保存按钮
        var saveBtn = new Button();
        saveBtn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_save"));
        saveBtn.layout(l -> l.width(50).heightPercent(100));
        saveBtn.setOnClick(e -> saveGraph());

        // 刷新按钮
        var refreshBtn = new Button();
        refreshBtn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.btn_refresh"));
        refreshBtn.layout(l -> l.width(50).heightPercent(100));
        refreshBtn.setOnClick(e -> {
            mc().setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        // 关闭按钮
        var closeBtn = new Button();
        closeBtn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.block.close"));
        closeBtn.layout(l -> l.width(50).heightPercent(100));
        closeBtn.setOnClick(e -> mc().setScreen(null));

        // 占位弹性空间
        var spacer = new UIElement().layout(l -> l.flex(1));

        tb.addChildren(title, spacer, editToggleBtn, saveBtn, refreshBtn, closeBtn);
        return tb;
    }

    /** 更新编辑模式切换按钮的文本和颜色 */
    private void updateEditToggleButton(Button btn) {
        if (editMode) {
            btn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.edit_mode.on"));
            btn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFFAA3333)));
        } else {
            btn.setText(Component.translatable("gui.attack_defense_capture_point_xkmxz.graph.edit_mode.off"));
            btn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF333333)));
        }
    }

    // ================================================================
    //  保存节点图到 CaptureManager (快照模式 + 版本检测)
    // ================================================================

    /** 从当前节点模型中读取选项值 */
    private static String getOptionString(NodeModel nm, String id) {
        if (nm instanceof INodeWithOptions opts) {
            var opt = opts.getNodeOptionById(id);
            if (opt instanceof com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption nodeOpt) {
                var port = nodeOpt.getPortModel();
                if (port instanceof IFieldValueConfigurable configurable) {
                    var v = configurable.getValue();
                    return v != null ? v.toString() : "";
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
        return "true".equalsIgnoreCase(getOptionString(nm, id));
    }

    /**
     * 从节点图中构建完整数据快照。
     * 读取所有节点模型中的选项值，同时解析连线（wire）关系，
     * 构造新的 points/zones 映射。
     * <p>
     * <b>关键修复</b>：区域包含的据点列表优先从连线（wire）中解析，
     * 而非仅依赖节点选项 edit_points（该选项仅由 {@link #syncZoneOptions} 
     * 从 CaptureManager 同步时填充，新建连线后可能尚未更新）。
     */
    private Map.Entry<Map<String, CaptureManager.CapturePointEntry>, Map<String, CaptureManager.ZoneEntry>> buildSnapshotFromGraph() {
        var newPoints = new LinkedHashMap<String, CaptureManager.CapturePointEntry>();
        var newZones = new LinkedHashMap<String, CaptureManager.ZoneEntry>();

        // 第一遍：收集所有节点
        var pointModels = new HashMap<String, NodeModel>();
        var zoneModels = new HashMap<String, NodeModel>();

        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof NodeModel nm) {
                String name = nm.getName();
                if (name == null || name.isEmpty()) continue;
                if (hasOutputPort(nm, "point_signal")) {
                    pointModels.put(name, nm);
                } else if (hasOutputPort(nm, "zone_out") || hasInputPort(nm, "point_in")) {
                    zoneModels.put(name, nm);
                }
            }
        }

        // ---- 解析连线，确定据点→区域的归属关系和区域→区域的依赖关系 ----
        // wireBasedZonePoints: zoneName → [pointName, ...] 从连线中解析
        var wireBasedZonePoints = new LinkedHashMap<String, List<String>>();
        // wireBasedRequiredZone: zoneName → requiredZoneName 从区域依赖连线中解析
        var wireBasedRequiredZone = new LinkedHashMap<String, String>();

        for (var element : graph.graphModel.getGraphElementModels()) {
            if (element instanceof WireModel wire) {
                var fromPort = wire.getFromPort();
                var toPort = wire.getToPort();
                if (fromPort == null || toPort == null) continue;

                // 获取两个端口所属的 NodeModel
                var fromNode = fromPort.getNodeModel();
                var toNode = toPort.getNodeModel();
                if (!(fromNode instanceof NodeModel fromNm) || !(toNode instanceof NodeModel toNm)) continue;

                String fromName = fromNm.getName();
                String toName = toNm.getName();
                if (fromName == null || toName == null) continue;

                // 判断连线类型：
                // 1. 据点→区域连接: from=point_signal(O)  to=point_in(I)
                if (hasOutputPort(fromNm, "point_signal") && hasInputPort(toNm, "point_in")) {
                    wireBasedZonePoints.computeIfAbsent(toName, k -> new ArrayList<>()).add(fromName);
                }
                // 2. 区域→区域依赖: from=zone_out(O)  to=required_zone(I)
                else if (hasOutputPort(fromNm, "zone_out") && hasInputPort(toNm, "required_zone")) {
                    wireBasedRequiredZone.put(toName, fromName);
                }
            }
        }

        // 构建据点数据
        var player = mc().player;
        BlockPos defaultPos = player != null ? player.blockPosition() : BlockPos.ZERO;
        for (var entry : pointModels.entrySet()) {
            String name = entry.getKey();
            var nm = entry.getValue();
            // 从选项读取坐标（或使用默认）
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
            String owner = getOptionString(nm, "owner");
            double radius = getOptionDouble(nm, "radius");
            int color = getOptionInt(nm, "display_color");
            boolean showRange = getOptionBool(nm, "show_range");
            newPoints.put(name, new CaptureManager.CapturePointEntry(
                    name, pos, owner.isEmpty() ? null : owner,
                    radius, color, showRange));
        }

        // 构建区域数据（据点列表优先从连线解析，回退到 edit_points 选项）
        for (var entry : zoneModels.entrySet()) {
            String name = entry.getKey();
            var nm = entry.getValue();

            // 依赖区域：优先从连线解析，回退到选项值
            String reqZone = wireBasedRequiredZone.get(name);
            if (reqZone == null || reqZone.isEmpty()) {
                reqZone = getOptionString(nm, "required_zone");
            }

            // 据点列表：优先从连线解析，回退到 edit_points 选项
            List<String> cpList = wireBasedZonePoints.get(name);
            if (cpList == null) {
                cpList = new ArrayList<>();
                String pointsStr = getOptionString(nm, "edit_points");
                if (!pointsStr.isEmpty()) {
                    for (var pn : pointsStr.split(",")) {
                        pn = pn.trim();
                        if (!pn.isEmpty()) cpList.add(pn);
                    }
                }
            }

            newZones.put(name, new CaptureManager.ZoneEntry(
                    name, cpList, reqZone.isEmpty() ? null : reqZone));
        }

        return new AbstractMap.SimpleEntry<>(newPoints, newZones);
    }

    /**
     * 将当前节点图中的所有更改保存到 CaptureManager。
     * 使用快照方式：构建完整数据 → 版本检测 → applyGraphSnapshot 原子写入。
     */
    private void saveGraph() {
        var mgr = getServerCaptureManager();
        if (mgr == null) {
            var player = mc().player;
            if (player != null) {
                player.connection.sendCommand("capturepoint savegraph");
            }
            ToastNotification.push(ToastNotification.Type.INFO,
                    Component.translatable("toast.capture_point_graph.saved"));
            return;
        }

        try {
            // 构建数据快照
            var snapshot = buildSnapshotFromGraph();
            var newPoints = snapshot.getKey();
            var newZones = snapshot.getValue();

            // 编辑模式下进行版本冲突检测
            if (editMode && snapshotVersion >= 0) {
                long currentVersion = mgr.getVersion();
                if (currentVersion != snapshotVersion) {
                    // 数据已被外部修改（命令/方块），弹出确认对话框
                    openConflictDialog(mgr, newPoints, newZones);
                    return;
                }
            }

            // 无冲突或非编辑模式：直接应用
            mgr.applyGraphSnapshot(newPoints, newZones);
            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_graph.saved"));

        } catch (Exception e) {
            LOGGER.error("Save graph failed: {}", e.getMessage(), e);
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.literal("Save failed: " + e.getMessage()));
        }
    }

    /**
     * 打开版本冲突对话框，询问用户是否覆盖外部修改的数据。
     */
    private void openConflictDialog(ICaptureDataAccess access,
                                     Map<String, CaptureManager.CapturePointEntry> newPoints,
                                     Map<String, CaptureManager.ZoneEntry> newZones) {
        var mc = mc();
        int dw = 340, dh = 130;

        var root = new UIElement()
                .layout(l -> l.width(dw).height(dh).paddingAll(12).gapAll(8)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN))
                .style(s -> s.background(Sprites.BORDER)
                        .backgroundTexture(new ColorRectTexture(0xFF1A1A2E)));

        var title = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.conflict.title"));
        title.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(title);

        var msg = new Label().setText(
                Component.translatable("gui.capture_point_graph.dialog.conflict.message"));
        msg.layout(l -> l.widthPercent(100).heightAuto());
        root.addChildren(msg);

        var spacer = new UIElement().layout(l -> l.flex(1));

        var btnRow = new UIElement()
                .layout(l -> l.widthPercent(100).height(30)
                        .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(6));
        var overwriteBtn = new Button().setText(
                Component.translatable("gui.capture_point_graph.dialog.conflict.overwrite"));
        overwriteBtn.layout(l -> l.flex(1).heightPercent(100));
        overwriteBtn.setOnClick(e -> {
            access.applyGraphSnapshot(newPoints, newZones);
            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_graph.saved"));
            mc.setScreen(null);
            new CapturePointGraphScreen(level).open();
        });

        var cancelBtn = new Button().setText(
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

    // ================================================================
    //  从 CaptureManager 加载数据到节点图
    // ================================================================

    private void loadDataToGraph() {
        try {
            var mgr = getServerCaptureManager();
            if (mgr == null) return;

            var points = mgr.getPoints();
            var zones = mgr.getZones();
            if (points.isEmpty() && zones.isEmpty()) return;

            var pointModels = new LinkedHashMap<String, NodeModel>();
            var zoneModels = new LinkedHashMap<String, NodeModel>();

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
                nodeModel.setTitle(Component.literal(entry.name()));
                pointModels.put(entry.name(), nodeModel);
                idx++;
            }

            // 创建区域节点（右侧区域，使用独立计数器网格排列）
            int zoneIdx = 0;
            for (var entry : zones.values()) {
                float x = startX + 250 + (zoneIdx % 3) * gapX;
                float y = startY + (zoneIdx / 3) * gapY + gapY;
                var node = new CaptureZoneNode();
                var nodeModel = graph.graphModel.createNodeModel(node,
                        new org.joml.Vector2f(x, y));
                nodeModel.setName(entry.name());
                nodeModel.setTitle(Component.literal(entry.name()));
                zoneModels.put(entry.name(), nodeModel);
                zoneIdx++;
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

        } catch (Exception e) {
            LOGGER.warn("Failed to load data to graph: {}", e.getMessage());
        }
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
                        } else if (hasOutputPort(nm, "zone_out") || hasInputPort(nm, "point_in")) {
                            // 区域节点：显示名称 + 占领状态 + 点数
                            var entry = zones.get(name);
                            if (entry != null) {
                                boolean captured = mgr.isZoneCaptured(name);
                                boolean accessible = mgr.canAccessZone(name);
                                int ptCount = entry.capturePoints().size();
                                String status = captured ? "✓" : "✗";
                                String access = accessible ? "" : " 🔒";
                                nm.setTitle(Component.literal(name + " [" + status + " " + ptCount + "pts" + access + "]"));
                                // 编辑模式下不覆盖选项值
                                if (!editMode) {
                                    syncZoneOptions(nm, entry, captured);
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
     * 同步据点节点选项（captured / owner(区域名) / position / radius / display_color / show_range）
     * "所属"字段显示该据点连接的区域名称（由 mgr.findZoneForPoint 获取），
     * 区域由连线管理，此处仅做只读展示。
     * 编辑模式字段（radius / display_color / show_range）始终从 CaptureManager 同步。
     */
    private static void syncPointOptions(NodeModel nm, CaptureManager.CapturePointEntry entry, boolean isCaptured, @org.jetbrains.annotations.Nullable String zoneName) {
        setOptionValue(nm, "captured", isCaptured);
        setOptionValue(nm, "owner", zoneName != null ? zoneName : "");
        setOptionValue(nm, "position", entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ());
        // 编辑模式额外字段
        setOptionValue(nm, "radius", entry.radius());
        setOptionValue(nm, "display_color", entry.displayColor());
        setOptionValue(nm, "show_range", entry.showRange());
    }

    /**
     * 同步区域节点选项（captured / required_zone / points / description / edit_points）
     */
    private static void syncZoneOptions(NodeModel nm, CaptureManager.ZoneEntry entry, boolean captured) {
        setOptionValue(nm, "captured", captured);
        setOptionValue(nm, "required_zone", entry.requiredZone() != null ? entry.requiredZone() : "");
        setOptionValue(nm, "points", String.join(", ", entry.capturePoints()));
        // 编辑模式额外字段
        setOptionValue(nm, "description", "");
        setOptionValue(nm, "edit_points", String.join(", ", entry.capturePoints()));
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

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
