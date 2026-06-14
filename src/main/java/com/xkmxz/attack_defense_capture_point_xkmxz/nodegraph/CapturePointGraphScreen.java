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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 据点管理节点图屏幕 - 使用 LDLib2 nodegraphtookit 框架提供完整的节点图编辑体验。
 * 支持保存节点关系到 CaptureManager。
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
        // 注册实时数据刷新回调（每 ~15 tick 由 graphView.screenTick() 驱动）
        this.graphView.setRefreshCallback(this::refreshNodeTitles);
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

        tb.addChildren(title, spacer, saveBtn, refreshBtn, closeBtn);
        return tb;
    }

    // ================================================================
    //  保存节点图到 CaptureManager
    // ================================================================

    /**
     * 将当前节点图中的所有节点和连线保存到 CaptureManager。
     * 直接通过 Java API 调用，不经过命令系统。
     */
    private void saveGraph() {
        var mgr = getServerCaptureManager();
        if (mgr == null) {
            // 非单机/服务端环境，通过命令回退
            var player = mc().player;
            if (player != null) {
                player.connection.sendCommand("capturepoint savegraph");
            }
            ToastNotification.push(ToastNotification.Type.INFO,
                    Component.translatable("toast.capture_point_graph.saved"));
            return;
        }

        try {
            // 收集当前图中所有节点
            var graphPointNames = new HashSet<String>();
            var graphZoneNames = new HashSet<String>();
            // pointModels 和 zoneModels: name → NodeModel
            var pointModels = new HashMap<String, NodeModel>();
            var zoneModels = new HashMap<String, NodeModel>();

            for (var element : graph.graphModel.getGraphElementModels()) {
                if (element instanceof NodeModel nm) {
                    String name = nm.getName();
                    if (name == null || name.isEmpty()) continue;
                    // 通过检查输出端口来判断类型
                    if (hasOutputPort(nm, "point_signal")) {
                        graphPointNames.add(name);
                        pointModels.put(name, nm);
                    } else if (hasOutputPort(nm, "zone_out") || hasInputPort(nm, "point_in")) {
                        graphZoneNames.add(name);
                        zoneModels.put(name, nm);
                    }
                }
            }

            // 删除已被移除的据点/区域
            var toRemovePoints = new ArrayList<String>();
            for (var name : mgr.getPoints().keySet()) {
                if (!graphPointNames.contains(name)) toRemovePoints.add(name);
            }
            for (var name : toRemovePoints) mgr.removePoint(name);

            var toRemoveZones = new ArrayList<String>();
            for (var name : mgr.getZones().keySet()) {
                if (!graphZoneNames.contains(name)) toRemoveZones.add(name);
            }
            for (var name : toRemoveZones) mgr.removeZone(name);

            // 创建新增的据点（使用玩家位置或原点）
            var player = mc().player;
            BlockPos defaultPos = player != null ? player.blockPosition() : BlockPos.ZERO;

            for (var name : graphPointNames) {
                if (!mgr.getPoints().containsKey(name)) {
                    mgr.addOrUpdatePoint(name, defaultPos);
                }
            }

            // 创建新增的区域
            for (var name : graphZoneNames) {
                if (!mgr.getZones().containsKey(name)) {
                    mgr.createZone(name, null);
                }
            }

            // === 解析连线，重建关系 ===

            // 先清除所有区域中的据点关联和旧依赖
            for (var zoneName : new ArrayList<>(mgr.getZones().keySet())) {
                var zone = mgr.getZones().get(zoneName);
                // 清除据点关联
                for (var cpName : new ArrayList<>(zone.capturePoints())) {
                    mgr.removePointFromZone(zoneName, cpName);
                }
                // 清除旧依赖（保留据点列表，仅重置 requiredZone）
                if (zone.requiredZone() != null) {
                    mgr.setZoneRequiredZone(zoneName, null);
                }
            }

            // 构建 port UID → node name 的映射
            var portUidToNodeName = new HashMap<UUID, String>();
            for (var nm : pointModels.values()) {
                String nodeName = nm.getName();
                for (var port : nm.getInputsById().values()) portUidToNodeName.put(port.getUid(), nodeName);
                for (var port : nm.getOutputsById().values()) portUidToNodeName.put(port.getUid(), nodeName);
            }
            for (var nm : zoneModels.values()) {
                String nodeName = nm.getName();
                for (var port : nm.getInputsById().values()) portUidToNodeName.put(port.getUid(), nodeName);
                for (var port : nm.getOutputsById().values()) portUidToNodeName.put(port.getUid(), nodeName);
            }

            // 解析所有连线
            var pointZoneConnections = new ArrayList<AbstractMap.SimpleEntry<String, String>>(); // point → zone
            var zoneDependencies = new ArrayList<AbstractMap.SimpleEntry<String, String>>(); // from → to (zone → zone)

            for (var element : graph.graphModel.getGraphElementModels()) {
                if (element instanceof WireModel wire) {
                    UUID fromPortUid = wire.getFromPortUid();
                    UUID toPortUid = wire.getToPortUid();

                    String fromNode = portUidToNodeName.get(fromPortUid);
                    String toNode = portUidToNodeName.get(toPortUid);
                    if (fromNode == null || toNode == null) continue;

                    // 查找端口名称：从 fromPort 对象中获取
                    PortModel fromPort = wire.getFromPort();
                    PortModel toPort = wire.getToPort();
                    if (fromPort == null || toPort == null) continue;

                    String fromPortName = fromPort.getName();
                    String toPortName = toPort.getName();

                    // point_signal → point_in: 据点属于区域
                    if ("point_signal".equals(fromPortName) && "point_in".equals(toPortName)) {
                        if (graphPointNames.contains(fromNode) && graphZoneNames.contains(toNode)) {
                            pointZoneConnections.add(new AbstractMap.SimpleEntry<>(fromNode, toNode));
                        }
                    }
                    // zone_out → required_zone: 区域前后依赖
                    if ("zone_out".equals(fromPortName) && "required_zone".equals(toPortName)) {
                        if (graphZoneNames.contains(fromNode) && graphZoneNames.contains(toNode)) {
                            zoneDependencies.add(new AbstractMap.SimpleEntry<>(fromNode, toNode));
                        }
                    }
                }
            }

            // 重建据点→区域关联
            for (var conn : pointZoneConnections) {
                mgr.addPointToZone(conn.getValue(), conn.getKey());
            }

            // 重建区域依赖关系
            for (var dep : zoneDependencies) {
                // dep: key=前置区域(front), value=后置区域(back) → 后置区域依赖前置区域
                mgr.setZoneRequiredZone(dep.getValue(), dep.getKey());
            }

            mgr.setDirty();

            ToastNotification.push(ToastNotification.Type.SUCCESS,
                    Component.translatable("toast.capture_point_graph.saved"));

        } catch (Exception e) {
            ToastNotification.push(ToastNotification.Type.ERROR,
                    Component.literal("Save failed: " + e.getMessage()));
        }
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
                    } catch (Exception ignored) {}
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
                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}
    }

    // ================================================================
    //  实时刷新节点标题和选项（由 screenTick 每 ~0.75 秒驱动）
    // ================================================================

    /** 同步锁：当为 true 时，来自 Game→GUI 的同步不触发 GUI→Game 回写 */
    private boolean isSyncingFromGame = false;

    /**
     * 从 CaptureManager 读取最新数据，更新所有节点标题和选项以反映真实状态。
     * 使用 IFieldValueConfigurable.setValue() 正确推送值到 Constant，从而自动更新 UI。
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
                                // 同步选项数据（所属显示区域名，非玩家名）
                                syncPointOptions(nm, entry, mgr.isZoneCaptured(name), zoneName);
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
                                // 同步选项数据
                                syncZoneOptions(nm, entry, captured);
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
     * 同步据点节点选项（captured / owner(区域名) / position）
     * "所属"字段显示该据点连接的区域名称（由 mgr.findZoneForPoint 获取），
     * 区域由连线管理，此处仅做只读展示。
     */
    private static void syncPointOptions(NodeModel nm, CaptureManager.CapturePointEntry entry, boolean isCaptured, @org.jetbrains.annotations.Nullable String zoneName) {
        setOptionValue(nm, "captured", isCaptured);
        setOptionValue(nm, "owner", zoneName != null ? zoneName : "");
        setOptionValue(nm, "position", entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ());
    }

    /**
     * 同步区域节点选项（captured / required_zone / points）
     */
    private static void syncZoneOptions(NodeModel nm, CaptureManager.ZoneEntry entry, boolean captured) {
        setOptionValue(nm, "captured", captured);
        setOptionValue(nm, "required_zone", entry.requiredZone() != null ? entry.requiredZone() : "");
        setOptionValue(nm, "points", String.join(", ", entry.capturePoints()));
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
            var mgr = getServerCaptureManager();
            if (mgr == null) return;

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
                                onOptionValueChanged(mgr, name, isPointNode, optId, newValue);
                            });
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 当节点选项值被用户编辑时调用，将变更写回 CaptureManager。
     */
    private void onOptionValueChanged(CaptureManager mgr, String nodeName, boolean isPointNode,
                                       String optionId, Object newValue) {
        try {
            if (isPointNode) {
                // 据点节点：owner 可编辑，其他字段只读
                if ("owner".equals(optionId)) {
                    String ownerStr = newValue instanceof String s ? s.trim() : "";
                    mgr.setPointOwner(nodeName, ownerStr.isEmpty() ? null : ownerStr);
                }
            } else {
                // 区域节点：required_zone 可编辑（通过连线管理），其他字段只读
                // required_zone 通过连线机制管理，不在文本框中直接编辑
            }
        } catch (Exception ignored) {}
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

    /** 获取服务端 CaptureManager（单机/服务端均可用） */
    private CaptureManager getServerCaptureManager() {
        if (level instanceof ServerLevel sl) return CaptureManager.get(sl);
        var mc = mc();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return CaptureManager.get(mc.getSingleplayerServer().getLevel(level.dimension()));
        }
        return null;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
